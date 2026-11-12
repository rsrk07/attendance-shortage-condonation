package com.attendance.condonation.service;

import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.RequestDetailDto;
import com.attendance.condonation.dto.StatusDto;
import com.attendance.condonation.dto.StepDto;
import com.attendance.condonation.dto.StepDto.StepState;
import com.attendance.condonation.entity.ApprovalRecord;
import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.entity.VerificationStatus;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Builds the live status tracker of a request from the element instances and variables of its
 * Camunda process instance. Falls back to the database values when Camunda is not reachable.
 */
@Service
public class StatusTrackerService {

  private static final Logger log = LoggerFactory.getLogger(StatusTrackerService.class);

  /** Tracker steps, the BPMN elements behind them, and the rejectionStage that marks them failed. */
  private record StepDef(String key, String label, List<String> elementIds, String rejectionStage) {}

  private static final List<StepDef> STEPS =
      List.of(
          new StepDef("SUBMITTED", "Request submitted", List.of("Start_Submitted"), null),
          new StepDef(
              "ATTENDANCE", "Attendance fetched", List.of("Task_FetchAttendance", "Task_CalculateShortfall"), "ATTENDANCE"),
          new StepDef(
              "ELIGIBILITY", "Eligibility & fee decided", List.of("Task_DecideEligibility", "Gateway_Condonable"), "ELIGIBILITY"),
          new StepDef("DOCUMENTS", "Documents verified", List.of("SubProcess_Documents"), "DOCUMENTS"),
          new StepDef("FA", "Faculty Advisor approval", List.of("CallActivity_FA"), "FA_APPROVAL"),
          new StepDef("AA", "Academic Advisor approval", List.of("CallActivity_AA"), "AA_APPROVAL"),
          new StepDef("HOD", "Head of Department approval", List.of("CallActivity_HOD"), "HOD_APPROVAL"),
          new StepDef("FEE", "Condonation fee paid", List.of("SubProcess_Fee"), "FEE"),
          new StepDef("SIS", "SIS record updated", List.of("Task_UpdateSIS", "UserTask_AdminFix"), null),
          new StepDef("OUTCOME", "Outcome", List.of("End_Condoned", "End_Rejected"), null));

  private final CondonationService condonationService;
  private final ProcessService processService;
  private final DtoMapper mapper;
  private final TransactionTemplate readTx;

  public StatusTrackerService(
      CondonationService condonationService,
      ProcessService processService,
      DtoMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.condonationService = condonationService;
    this.processService = processService;
    this.mapper = mapper;
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  public StatusDto track(String requestId, AuthUser user) {
    // access check + key lookup
    Long processInstanceKey =
        readTx.execute(status -> condonationService.load(requestId, user).getProcessInstanceKey());

    List<ElementInstance> elements = List.of();
    Map<String, Object> vars = Map.of();
    Optional<ProcessInstance> instance = Optional.empty();
    boolean live = false;
    String message = null;
    if (processInstanceKey != null) {
      try {
        instance = processService.findProcessInstance(processInstanceKey);
        elements = processService.elementInstances(processInstanceKey);
        vars = processService.rootVariables(processInstanceKey);
        condonationService.applyProcessVariables(requestId, vars);
        live = instance.isPresent();
        if (instance.isEmpty()) {
          message = "Camunda has not indexed this process instance yet - refreshing shortly.";
        }
      } catch (RuntimeException e) {
        log.warn("Live status for {} unavailable: {}", requestId, e.getMessage());
        message = "Live workflow status is temporarily unavailable; showing the last saved status.";
      }
    }

    final List<ElementInstance> elementList = elements;
    final Map<String, Object> variables = vars;
    final Optional<ProcessInstance> processInstance = instance;
    final boolean isLive = live;
    final String note = message;
    return readTx.execute(
        status -> {
          CondonationRequest request = condonationService.load(requestId, user);
          RequestDetailDto detail = mapper.detail(request);
          String processState =
              processInstance.map(p -> String.valueOf(p.getState())).orElse(isLive ? "UNKNOWN" : "NOT_AVAILABLE");
          boolean finished = processInstance.map(p -> p.getEndDate() != null).orElse(request.getStatus().isFinal());
          List<StepDto> steps = buildSteps(elementList, variables, request, finished);
          String current =
              steps.stream()
                  .filter(s -> s.state() == StepState.ACTIVE || s.state() == StepState.INCIDENT)
                  .map(StepDto::key)
                  .findFirst()
                  .orElse(null);
          return new StatusDto(
              requestId, request.getStatus(), processState, isLive, note, current, steps, detail, Instant.now());
        });
  }

  private List<StepDto> buildSteps(
      List<ElementInstance> elements, Map<String, Object> vars, CondonationRequest request, boolean finished) {
    String rejectionStage =
        Optional.ofNullable(Vars.string(vars.get("rejectionStage"))).orElse(request.getRejectionStage());
    boolean rejected = rejectionStage != null;
    boolean pastRejection = false;
    List<StepDto> steps = new ArrayList<>();

    for (StepDef def : STEPS) {
      List<ElementInstance> mine =
          elements.stream()
              .filter(e -> def.elementIds().contains(e.getElementId()))
              .sorted(Comparator.comparing(ElementInstance::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
              .toList();
      OffsetDateTime started = mine.isEmpty() ? null : mine.get(0).getStartDate();
      OffsetDateTime ended =
          mine.stream().map(ElementInstance::getEndDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

      StepState state;
      if (def.key().equals("OUTCOME")) {
        state = outcomeState(mine, request, finished, rejected);
      } else if (mine.stream().anyMatch(e -> Boolean.TRUE.equals(e.getIncident()))) {
        state = StepState.INCIDENT;
      } else if (mine.stream().anyMatch(e -> e.getState() == ElementInstanceState.ACTIVE)) {
        state = StepState.ACTIVE;
      } else if (def.rejectionStage() != null && def.rejectionStage().equals(rejectionStage)) {
        state = StepState.FAILED;
      } else if (pastRejection) {
        state = StepState.SKIPPED;
      } else if (mine.stream().anyMatch(e -> e.getState() == ElementInstanceState.COMPLETED)) {
        state = StepState.COMPLETED;
      } else if (!mine.isEmpty()) {
        state = StepState.FAILED;
      } else if (finished || rejected && !def.key().equals("SUBMITTED")) {
        state = StepState.SKIPPED;
      } else if (def.key().equals("SUBMITTED") && request.getProcessInstanceKey() != null) {
        state = StepState.COMPLETED; // start event may not be indexed yet
      } else {
        state = StepState.PENDING;
      }
      if (state == StepState.FAILED && rejected) {
        pastRejection = true;
      }
      String label = def.key().equals("OUTCOME") ? outcomeLabel(state) : def.label();
      steps.add(
          new StepDto(def.key(), label, state, detail(def.key(), state, vars, request, mine), started, ended));
    }
    return steps;
  }

  private StepState outcomeState(
      List<ElementInstance> mine, CondonationRequest request, boolean finished, boolean rejected) {
    boolean condoned = mine.stream().anyMatch(e -> e.getElementId().equals("End_Condoned"));
    boolean rejectedEnd = mine.stream().anyMatch(e -> e.getElementId().equals("End_Rejected"));
    if (condoned) {
      return StepState.COMPLETED;
    }
    if (rejectedEnd) {
      return StepState.FAILED;
    }
    if (finished) {
      return switch (request.getStatus()) {
        case APPROVED -> StepState.COMPLETED;
        case REJECTED -> StepState.FAILED;
        default -> StepState.SKIPPED;
      };
    }
    return rejected ? StepState.ACTIVE : StepState.PENDING;
  }

  private static String outcomeLabel(StepState state) {
    return switch (state) {
      case COMPLETED -> "Attendance condoned";
      case FAILED -> "Request rejected";
      case ACTIVE -> "Closing the request";
      default -> "Outcome";
    };
  }

  private String detail(
      String key, StepState state, Map<String, Object> vars, CondonationRequest request, List<ElementInstance> mine) {
    if (state == StepState.INCIDENT) {
      return "A technical problem occurred - the admin has been alerted in Camunda Operate.";
    }
    return switch (key) {
      case "SUBMITTED" -> request.getDocuments().size() + " supporting document(s) attached";
      case "ATTENDANCE" -> attendanceDetail(vars, request, state);
      case "ELIGIBILITY" -> eligibilityDetail(vars, request);
      case "DOCUMENTS" -> documentsDetail(vars, request, state);
      case "FA", "AA", "HOD" -> approvalDetail(key, vars, request, state);
      case "FEE" -> feeDetail(request, state);
      case "SIS" -> sisDetail(vars, request, state, mine);
      case "OUTCOME" ->
          state == StepState.FAILED || state == StepState.ACTIVE
              ? Optional.ofNullable(Vars.string(vars.get("rejectionReason"))).orElse(request.getRejectionReason())
              : null;
      default -> null;
    };
  }

  private String attendanceDetail(Map<String, Object> vars, CondonationRequest request, StepState state) {
    BigDecimal percentage = Vars.decimal(Vars.path(vars, "attendance", "percentage"));
    if (percentage == null) {
      percentage = request.getAttendancePercentage();
    }
    if (percentage == null) {
      return state == StepState.FAILED ? request.getRejectionReason() : null;
    }
    Integer attended = Vars.integer(Vars.path(vars, "attendance", "attended"));
    Integer conducted = Vars.integer(Vars.path(vars, "attendance", "conducted"));
    String classes = attended != null && conducted != null ? " (" + attended + "/" + conducted + " classes)" : "";
    return percentage.stripTrailingZeros().toPlainString() + "% attendance" + classes;
  }

  private String eligibilityDetail(Map<String, Object> vars, CondonationRequest request) {
    Map<String, Object> e = Vars.map(vars.get("eligibility"));
    String slab = e.isEmpty() ? request.getSlab() : Vars.string(e.get("slab"));
    Integer fee = e.isEmpty() ? request.getFee() : Vars.integer(e.get("fee"));
    if (slab == null) {
      return null;
    }
    String remark = Vars.string(e.get("remark"));
    String text = slab.replace('_', ' ') + (fee != null && fee > 0 ? " - fee Rs. " + fee : "");
    return remark == null || remark.isBlank() ? text : text + ". " + remark;
  }

  private String documentsDetail(Map<String, Object> vars, CondonationRequest request, StepState state) {
    long total = request.getDocuments().size();
    long verified =
        request.getDocuments().stream()
            .filter(d -> d.getVerificationStatus() == VerificationStatus.VERIFIED)
            .count();
    if (state == StepState.FAILED) {
      return Optional.ofNullable(Vars.string(vars.get("documentRejectionNote"))).orElse("A document was rejected.");
    }
    if (state == StepState.PENDING || state == StepState.SKIPPED) {
      return null;
    }
    return verified + " of " + total + " document(s) verified";
  }

  private String approvalDetail(String role, Map<String, Object> vars, CondonationRequest request, StepState state) {
    Map<String, Object> approval = Vars.map(vars.get(role.toLowerCase() + "Approval"));
    if (state == StepState.ACTIVE) {
      return "Waiting for the " + Role.valueOf(role).title() + " to review";
    }
    String remarks = Vars.string(approval.get("remarks"));
    String reviewer =
        request.getApprovals().stream()
            .filter(a -> a.getRole().name().equals(role))
            .reduce((a, b) -> b)
            .map(ApprovalRecord::getReviewerName)
            .orElse(Vars.string(approval.get("reviewedBy")));
    if (approval.isEmpty() && reviewer == null) {
      return null;
    }
    String verdict = Boolean.TRUE.equals(Vars.bool(approval.get("approved"))) || state == StepState.COMPLETED
        ? "Approved" : "Rejected";
    StringBuilder text = new StringBuilder(verdict);
    if (reviewer != null) {
      text.append(" by ").append(reviewer);
    }
    if (remarks != null && !remarks.isBlank()) {
      text.append(": \"").append(remarks).append('"');
    }
    return text.toString();
  }

  private String feeDetail(CondonationRequest request, StepState state) {
    if (state == StepState.ACTIVE) {
      return request.getFee() == null ? "Waiting for payment" : "Rs. " + request.getFee() + " due - pay from your inbox";
    }
    if (state == StepState.COMPLETED && request.getPaymentReference() != null) {
      return "Paid via " + request.getPaymentMode() + " (ref. " + request.getPaymentReference() + ")";
    }
    if (state == StepState.FAILED) {
      return request.getRejectionReason();
    }
    return null;
  }

  private String sisDetail(
      Map<String, Object> vars, CondonationRequest request, StepState state, List<ElementInstance> mine) {
    boolean adminFixActive =
        mine.stream()
            .anyMatch(e -> e.getElementId().equals("UserTask_AdminFix") && e.getState() == ElementInstanceState.ACTIVE);
    if (adminFixActive) {
      return "SIS update failed - waiting for the admin to fix it";
    }
    if (state == StepState.COMPLETED && request.getSisReference() != null) {
      return "Updated (" + request.getSisReference() + ")";
    }
    return null;
  }
}
