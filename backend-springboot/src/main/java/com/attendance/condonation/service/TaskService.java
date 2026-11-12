package com.attendance.condonation.service;

import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.AdminFixDto;
import com.attendance.condonation.dto.FeePaymentDto;
import com.attendance.condonation.dto.RequestDetailDto;
import com.attendance.condonation.dto.ReviewDecisionDto;
import com.attendance.condonation.dto.TaskDetailDto;
import com.attendance.condonation.dto.TaskDto;
import com.attendance.condonation.dto.TaskDto.TaskKind;
import com.attendance.condonation.entity.ApprovalRecord;
import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.RequestStatus;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.exception.ApiException;
import com.attendance.condonation.repository.CondonationRequestRepository;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The custom Tasklist: lists, claims and completes the Camunda user tasks of the logged-in user.
 * FA / AA / HOD see the review tasks of their candidate group, students see the pay-fee task
 * assigned to them, the admin sees the SIS-fix task.
 */
@Service
public class TaskService {

  private static final Logger log = LoggerFactory.getLogger(TaskService.class);

  private static final Map<String, TaskKind> KINDS =
      Map.of(
          "UserTask_Review", TaskKind.REVIEW,
          "UserTask_PayFee", TaskKind.PAY_FEE,
          "UserTask_AdminFix", TaskKind.ADMIN_FIX);

  /** Process variables a task screen may show. */
  private static final Set<String> SCREEN_VARIABLES =
      Set.of(
          "requestId", "approverRole", "studentId", "studentName", "courseCode", "reasonCategory", "reason",
          "attendance", "eligibility", "documents", "documentChecks", "documentSummary",
          "faApproval", "aaApproval", "hodApproval", "sisError", "feeDeadline");

  private final ProcessService processService;
  private final CondonationService condonationService;
  private final CondonationRequestRepository requestRepository;
  private final DtoMapper mapper;
  private final TransactionTemplate tx;

  public TaskService(
      ProcessService processService,
      CondonationService condonationService,
      CondonationRequestRepository requestRepository,
      DtoMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.processService = processService;
    this.condonationService = condonationService;
    this.requestRepository = requestRepository;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(transactionManager);
  }

  // ---------------------------------------------------------------- inbox

  public List<TaskDto> inbox(AuthUser user) {
    return processService.openTasksFor(user.role(), user.username()).stream()
        .sorted(Comparator.comparing(UserTask::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(this::toDto)
        .toList();
  }

  public TaskDetailDto detail(long taskKey, AuthUser user) {
    UserTask task = processService.getTask(taskKey);
    checkAccess(task, user);
    TaskDto dto = toDto(task);

    Map<String, Object> variables = new LinkedHashMap<>();
    processService.rootVariables(task.getProcessInstanceKey()).forEach(
        (name, value) -> {
          if (SCREEN_VARIABLES.contains(name)) {
            variables.put(name, value);
          }
        });

    RequestDetailDto request = null;
    if (dto.requestId() != null) {
      condonationService.applyProcessVariables(dto.requestId(), variables);
      request =
          tx.execute(
              status -> requestRepository.findByRequestId(dto.requestId()).map(mapper::detail).orElse(null));
    }
    return new TaskDetailDto(dto, request, variables);
  }

  public TaskDto claim(long taskKey, AuthUser user) {
    UserTask task = openTask(taskKey, user, null);
    ensureAssigned(task, user);
    return toDto(processService.getTask(taskKey));
  }

  // ---------------------------------------------------------------- complete

  public void completeReview(long taskKey, ReviewDecisionDto dto, AuthUser user) {
    user.require(Role.FA, Role.AA, Role.HOD);
    UserTask task = openTask(taskKey, user, TaskKind.REVIEW);
    ensureAssigned(task, user);

    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("approved", dto.approved());
    vars.put("decision", dto.decision());
    vars.put("remarks", dto.remarks() == null ? "" : dto.remarks().trim());
    vars.put("reviewedBy", user.displayName());
    processService.completeTask(taskKey, vars);
    log.info("{} {} task {}", user.username(), dto.decision(), taskKey);

    requestIdFor(task)
        .ifPresent(
            requestId ->
                tx.executeWithoutResult(
                    status ->
                        requestRepository
                            .findByRequestId(requestId)
                            .ifPresent(request -> recordReview(request, dto, user))));
  }

  private void recordReview(CondonationRequest request, ReviewDecisionDto dto, AuthUser user) {
    request.addApproval(
        new ApprovalRecord(
            user.role(),
            dto.approved(),
            dto.remarks() == null ? "" : dto.remarks().trim(),
            user.username(),
            user.displayName()));
    if (!dto.approved()) {
      request.advanceTo(RequestStatus.REJECTED);
    } else if (user.role() == Role.HOD) {
      request.advanceTo(RequestStatus.AWAITING_PAYMENT);
    } else {
      request.advanceTo(RequestStatus.UNDER_REVIEW);
    }
  }

  public void completePayFee(long taskKey, FeePaymentDto dto, AuthUser user) {
    user.require(Role.STUDENT);
    UserTask task = openTask(taskKey, user, TaskKind.PAY_FEE);
    ensureAssigned(task, user);

    String reference = dto.paymentReference().trim().toUpperCase();
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("paymentMode", dto.paymentMode());
    vars.put("paymentReference", reference);
    vars.put("amountPaid", dto.amountPaid());
    vars.put("paymentConfirmed", Boolean.TRUE.equals(dto.paymentConfirmed()));
    vars.put("paidAt", Instant.now().toString());
    processService.completeTask(taskKey, vars);

    requestIdFor(task)
        .ifPresent(
            requestId ->
                tx.executeWithoutResult(
                    status ->
                        requestRepository
                            .findByRequestId(requestId)
                            .ifPresent(
                                request -> {
                                  request.setPaymentMode(dto.paymentMode());
                                  request.setPaymentReference(reference);
                                  request.setAmountPaid(dto.amountPaid());
                                })));
  }

  public void completeAdminFix(long taskKey, AdminFixDto dto, AuthUser user) {
    user.require(Role.ADMIN);
    UserTask task = openTask(taskKey, user, TaskKind.ADMIN_FIX);
    ensureAssigned(task, user);

    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("adminNote", dto.adminNote().trim());
    vars.put("sisRecordFixed", Boolean.TRUE.equals(dto.sisRecordFixed()));
    vars.put("fixedBy", user.displayName());
    processService.completeTask(taskKey, vars);
  }

  // ---------------------------------------------------------------- helpers

  private UserTask openTask(long taskKey, AuthUser user, TaskKind expected) {
    UserTask task = processService.getTask(taskKey);
    checkAccess(task, user);
    if (task.getState() != UserTaskState.CREATED) {
      throw ApiException.conflict("This task is already " + String.valueOf(task.getState()).toLowerCase() + ".");
    }
    if (expected != null && kindOf(task) != expected) {
      throw ApiException.badRequest("This action does not match the task type.");
    }
    return task;
  }

  private void checkAccess(UserTask task, AuthUser user) {
    boolean allowed =
        user.role() == Role.STUDENT
            ? user.username().equals(task.getAssignee())
            : task.getCandidateGroups() != null && task.getCandidateGroups().contains(user.role().candidateGroup());
    if (!allowed) {
      throw ApiException.forbidden("This task is not in your inbox.");
    }
  }

  /** Claims the task for the user if nobody has it yet; refuses if a colleague already claimed it. */
  private void ensureAssigned(UserTask task, AuthUser user) {
    String assignee = task.getAssignee();
    if (assignee == null || assignee.isBlank()) {
      processService.assignTask(task.getUserTaskKey(), user.username());
    } else if (!assignee.equals(user.username())) {
      throw ApiException.conflict("This task is already claimed by " + assignee + ".");
    }
  }

  private TaskDto toDto(UserTask task) {
    TaskKind kind = kindOf(task);
    Optional<CondonationRequest> request = requestFor(task);
    String requestId =
        request.map(CondonationRequest::getRequestId).or(() -> requestIdFromVariables(task)).orElse(null);
    String approverRole =
        kind == TaskKind.REVIEW && task.getCandidateGroups() != null && !task.getCandidateGroups().isEmpty()
            ? task.getCandidateGroups().get(0)
            : null;
    OffsetDateTime created = task.getCreationDate();
    return new TaskDto(
        task.getUserTaskKey(),
        task.getName(),
        task.getElementId(),
        kind,
        approverRole,
        requestId,
        request.map(r -> r.getStudent().getStudentId()).orElse(null),
        request.map(r -> r.getStudent().getName()).orElse(null),
        request.map(CondonationRequest::getCourseCode).orElse(null),
        task.getAssignee(),
        task.getCandidateGroups(),
        created);
  }

  private static TaskKind kindOf(UserTask task) {
    return KINDS.getOrDefault(task.getElementId(), TaskKind.OTHER);
  }

  /** The request behind a task: tasks in the approval child process point to it via the root instance. */
  private Optional<CondonationRequest> requestFor(UserTask task) {
    Long root = task.getRootProcessInstanceKey() != null ? task.getRootProcessInstanceKey() : task.getProcessInstanceKey();
    return Optional.ofNullable(
        tx.execute(status -> requestRepository.findByProcessInstanceKey(root).orElse(null)));
  }

  private Optional<String> requestIdFor(UserTask task) {
    return requestFor(task).map(CondonationRequest::getRequestId).or(() -> requestIdFromVariables(task));
  }

  private Optional<String> requestIdFromVariables(UserTask task) {
    try {
      return processService.findVariable(task.getProcessInstanceKey(), "requestId").map(String::valueOf);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
