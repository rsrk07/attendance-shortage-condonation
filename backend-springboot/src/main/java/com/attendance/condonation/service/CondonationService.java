package com.attendance.condonation.service;

import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.config.AppProperties;
import com.attendance.condonation.dto.CourseOptionDto;
import com.attendance.condonation.dto.CreateRequestDto;
import com.attendance.condonation.dto.DocumentInput;
import com.attendance.condonation.dto.RequestDetailDto;
import com.attendance.condonation.dto.RequestSummaryDto;
import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.CourseAttendance;
import com.attendance.condonation.entity.RequestStatus;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.entity.StoredFile;
import com.attendance.condonation.entity.Student;
import com.attendance.condonation.entity.SupportingDocument;
import com.attendance.condonation.exception.ApiException;
import com.attendance.condonation.repository.CondonationRequestRepository;
import com.attendance.condonation.repository.CourseAttendanceRepository;
import com.attendance.condonation.repository.StoredFileRepository;
import com.attendance.condonation.repository.StudentRepository;
import com.attendance.condonation.repository.SupportingDocumentRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Submitting, listing and reading condonation requests. */
@Service
public class CondonationService {

  private static final Logger log = LoggerFactory.getLogger(CondonationService.class);
  private static final BigDecimal REQUIRED_PERCENTAGE = BigDecimal.valueOf(75);

  public static final Set<RequestStatus> OPEN_STATUSES =
      EnumSet.of(
          RequestStatus.SUBMITTED,
          RequestStatus.UNDER_REVIEW,
          RequestStatus.AWAITING_PAYMENT,
          RequestStatus.PROCESSING);

  private final StudentRepository studentRepository;
  private final CourseAttendanceRepository courseRepository;
  private final CondonationRequestRepository requestRepository;
  private final StoredFileRepository fileRepository;
  private final SupportingDocumentRepository documentRepository;
  private final ProcessService processService;
  private final SettingsService settingsService;
  private final AppProperties properties;
  private final DtoMapper mapper;
  private final TransactionTemplate tx;

  public CondonationService(
      StudentRepository studentRepository,
      CourseAttendanceRepository courseRepository,
      CondonationRequestRepository requestRepository,
      StoredFileRepository fileRepository,
      SupportingDocumentRepository documentRepository,
      ProcessService processService,
      SettingsService settingsService,
      AppProperties properties,
      DtoMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.studentRepository = studentRepository;
    this.courseRepository = courseRepository;
    this.requestRepository = requestRepository;
    this.fileRepository = fileRepository;
    this.documentRepository = documentRepository;
    this.processService = processService;
    this.settingsService = settingsService;
    this.properties = properties;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(transactionManager);
  }

  // ---------------------------------------------------------------- courses

  @Transactional(readOnly = true)
  public List<CourseOptionDto> coursesFor(AuthUser user) {
    user.require(Role.STUDENT);
    return courseRepository.findByStudentStudentIdOrderByCourseCodeAsc(user.studentId()).stream()
        .map(
            c ->
                new CourseOptionDto(
                    c.getCourseCode(),
                    c.getCourseName(),
                    c.getAttended(),
                    c.getConducted(),
                    c.getPercentage(),
                    c.getPercentage().compareTo(REQUIRED_PERCENTAGE) < 0,
                    requestRepository.existsByStudentStudentIdAndCourseCodeAndStatusIn(
                        user.studentId(), c.getCourseCode(), OPEN_STATUSES)))
        .toList();
  }

  // ---------------------------------------------------------------- submit

  private record Prepared(Long id, Map<String, Object> variables) {}

  /**
   * Validates and saves the request, then starts the Camunda process with the request and its
   * documents as variables. If the process cannot be started, the saved request is removed again.
   */
  public RequestDetailDto submit(CreateRequestDto dto, AuthUser user) {
    user.require(Role.STUDENT);
    Prepared prepared = tx.execute(status -> prepare(dto, user));
    Objects.requireNonNull(prepared);

    final long processInstanceKey = startProcess(prepared);
    log.info("Started process instance {} for {}", processInstanceKey, prepared.variables().get("requestId"));

    return tx.execute(
        status -> {
          CondonationRequest request = requestRepository.findById(prepared.id()).orElseThrow();
          request.setProcessInstanceKey(processInstanceKey);
          return mapper.detail(request);
        });
  }

  private long startProcess(Prepared prepared) {
    try {
      return processService.startCondonation(prepared.variables());
    } catch (RuntimeException e) {
      tx.executeWithoutResult(status -> requestRepository.deleteById(prepared.id()));
      throw e;
    }
  }

  private Prepared prepare(CreateRequestDto dto, AuthUser user) {
    Student student =
        studentRepository
            .findById(user.studentId())
            .orElseThrow(() -> ApiException.notFound("Student record " + user.studentId() + " was not found."));
    String courseCode = dto.courseCode().trim().toUpperCase();
    CourseAttendance course =
        courseRepository
            .findByStudentStudentIdAndCourseCode(student.getStudentId(), courseCode)
            .orElseThrow(() -> ApiException.badRequest("Course " + courseCode + " is not in your attendance record."));

    if (course.getPercentage().compareTo(REQUIRED_PERCENTAGE) >= 0) {
      throw ApiException.badRequest(
          "Your attendance in " + courseCode + " is " + course.getPercentage()
              + "%, so condonation is not required.");
    }
    if (requestRepository.existsByStudentStudentIdAndCourseCodeAndStatusIn(
        student.getStudentId(), courseCode, OPEN_STATUSES)) {
      throw ApiException.conflict("You already have an open condonation request for " + courseCode + ".");
    }

    CondonationRequest request =
        new CondonationRequest(
            student, courseCode, course.getCourseName(), dto.reasonCategory(), dto.reason().trim());

    Set<String> seenFiles = new HashSet<>();
    for (DocumentInput input : dto.documents()) {
      if (!seenFiles.add(input.fileId())) {
        throw ApiException.badRequest("The same file is attached twice.");
      }
      StoredFile file =
          fileRepository
              .findById(input.fileId())
              .orElseThrow(() -> ApiException.badRequest("An uploaded file was not found. Please upload it again."));
      if (!file.getUploadedBy().equals(user.username())) {
        throw ApiException.forbidden("You can only attach files you uploaded.");
      }
      if (documentRepository.existsByFileId(file.getId())) {
        throw ApiException.badRequest("'" + file.getFileName() + "' is already attached to another request.");
      }
      String description = input.description() == null ? null : input.description().trim();
      request.addDocument(new SupportingDocument(input.type(), description, file));
    }

    request = requestRepository.save(request);
    request.setRequestId("REQ-" + (1000 + request.getId()));
    requestRepository.flush();
    return new Prepared(request.getId(), processVariables(request));
  }

  /** The start variables of the main BPMN process (see bpmn-models/README.md). */
  private Map<String, Object> processVariables(CondonationRequest request) {
    Student student = request.getStudent();
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("requestId", request.getRequestId());
    vars.put("studentId", student.getStudentId());
    vars.put("studentName", student.getName());
    vars.put("studentEmail", recipient(student.getEmail()));
    vars.put("department", student.getDepartment());
    vars.put("courseCode", request.getCourseCode());
    vars.put("reasonCategory", request.getReasonCategory().name());
    vars.put("reason", request.getReason());
    vars.put(
        "documents",
        request.getDocuments().stream()
            .map(
                d -> {
                  Map<String, Object> doc = new LinkedHashMap<>();
                  doc.put("documentId", d.getId());
                  doc.put("type", d.getDocumentType().name());
                  doc.put("typeLabel", d.getDocumentType().label());
                  doc.put("fileName", d.getFileName());
                  doc.put("contentType", d.getContentType());
                  doc.put("sizeBytes", d.getSizeBytes());
                  doc.put("description", d.getDescription() == null ? "" : d.getDescription());
                  doc.put("url", "/api/files/" + d.getFileId());
                  return doc;
                })
            .toList());
    AppProperties.ApproverEmails approvers = properties.approverEmails();
    vars.put(
        "approverEmails",
        Map.of(
            "FA", recipient(approvers.fa()),
            "AA", recipient(approvers.aa()),
            "HOD", recipient(approvers.hod())));
    vars.put("feeDeadline", properties.feeDeadline());
    vars.put("reminderInterval", properties.reminderInterval());
    vars.put("simulateSisFailure", settingsService.simulateSisFailure());
    return vars;
  }

  /** In demo mode every e-mail goes to NOTIFICATION_EMAIL so it reaches a real inbox. */
  private String recipient(String address) {
    if (properties.hasNotificationOverride()) {
      return properties.notificationEmail().trim();
    }
    return address == null ? "" : address;
  }

  // ---------------------------------------------------------------- read

  @Transactional(readOnly = true)
  public List<RequestSummaryDto> list(AuthUser user, RequestStatus status) {
    List<CondonationRequest> requests =
        user.role() == Role.STUDENT
            ? requestRepository.findByStudentStudentIdOrderBySubmittedAtDesc(user.studentId())
            : status != null
                ? requestRepository.findByStatusInOrderBySubmittedAtDesc(List.of(status))
                : requestRepository.findAllByOrderBySubmittedAtDesc();
    return requests.stream()
        .filter(r -> status == null || r.getStatus() == status)
        .map(mapper::summary)
        .toList();
  }

  @Transactional(readOnly = true)
  public RequestDetailDto detail(String requestId, AuthUser user) {
    return mapper.detail(load(requestId, user));
  }

  /** Loads a request and checks that the user may see it (students only their own). */
  public CondonationRequest load(String requestId, AuthUser user) {
    CondonationRequest request =
        requestRepository
            .findByRequestId(requestId)
            .orElseThrow(() -> ApiException.notFound("Request " + requestId + " was not found."));
    if (user.role() == Role.STUDENT && !request.getStudent().getStudentId().equals(user.studentId())) {
      throw ApiException.forbidden("This request belongs to another student.");
    }
    return request;
  }

  /**
   * Copies the values the process has decided (attendance, slab, fee, rejection) into the
   * database, so lists can show them without calling Camunda.
   */
  @Transactional
  public void applyProcessVariables(String requestId, Map<String, Object> vars) {
    requestRepository
        .findByRequestId(requestId)
        .ifPresent(
            request -> {
              BigDecimal percentage = Vars.decimal(Vars.path(vars, "attendance", "percentage"));
              if (percentage != null) {
                request.setAttendancePercentage(percentage);
                request.setShortfall(Vars.decimal(Vars.path(vars, "attendance", "shortfall")));
              }
              Map<String, Object> eligibility = Vars.map(vars.get("eligibility"));
              if (!eligibility.isEmpty()) {
                request.setSlab(Vars.string(eligibility.get("slab")));
                request.setFee(Vars.integer(eligibility.get("fee")));
                request.setCondonable(Vars.bool(eligibility.get("condonable")));
              }
              if (vars.get("rejectionReason") != null) {
                request.setRejectionReason(Vars.string(vars.get("rejectionReason")));
                request.setRejectionStage(Vars.string(vars.get("rejectionStage")));
              }
            });
  }
}
