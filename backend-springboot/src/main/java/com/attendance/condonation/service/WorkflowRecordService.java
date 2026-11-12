package com.attendance.condonation.service;

import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.RequestStatus;
import com.attendance.condonation.entity.SisRecord;
import com.attendance.condonation.entity.StoredFile;
import com.attendance.condonation.entity.SupportingDocument;
import com.attendance.condonation.repository.CondonationRequestRepository;
import com.attendance.condonation.repository.SisRecordRepository;
import com.attendance.condonation.repository.StoredFileRepository;
import com.attendance.condonation.repository.SupportingDocumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database work done on behalf of the Zeebe job workers (each method is one transaction). */
@Service
public class WorkflowRecordService {

  private static final Logger log = LoggerFactory.getLogger(WorkflowRecordService.class);
  private static final DateTimeFormatter SIS_REF = DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

  private final CondonationRequestRepository requestRepository;
  private final SupportingDocumentRepository documentRepository;
  private final StoredFileRepository fileRepository;
  private final SisRecordRepository sisRepository;

  public WorkflowRecordService(
      CondonationRequestRepository requestRepository,
      SupportingDocumentRepository documentRepository,
      StoredFileRepository fileRepository,
      SisRecordRepository sisRepository) {
    this.requestRepository = requestRepository;
    this.documentRepository = documentRepository;
    this.fileRepository = fileRepository;
    this.sisRepository = sisRepository;
  }

  /** File name and bytes of an attached document, if both the document and its file exist. */
  public record DocumentContent(String fileName, byte[] data) {}

  @Transactional(readOnly = true)
  public Optional<DocumentContent> documentContent(Long documentId) {
    if (documentId == null) {
      return Optional.empty();
    }
    return documentRepository
        .findById(documentId)
        .flatMap(doc -> fileRepository.findById(doc.getFileId()))
        .map((StoredFile file) -> new DocumentContent(file.getFileName(), file.getData()));
  }

  @Transactional
  public void recordDocumentCheck(Long documentId, boolean valid, String note) {
    if (documentId == null) {
      return;
    }
    documentRepository
        .findById(documentId)
        .ifPresent(
            (SupportingDocument doc) -> {
              if (valid) {
                doc.markVerified(note);
                doc.getRequest().advanceTo(RequestStatus.UNDER_REVIEW);
              } else {
                doc.markRejected(note);
              }
            });
  }

  /** The other request that already used this payment reference, if any. */
  @Transactional(readOnly = true)
  public Optional<String> requestUsingPaymentReference(String reference, String requestId) {
    return requestRepository
        .findFirstByPaymentReferenceIgnoreCaseAndRequestIdNot(reference, requestId == null ? "" : requestId)
        .filter(other -> other.getPaymentVerifiedAt() != null)
        .map(CondonationRequest::getRequestId);
  }

  @Transactional
  public void recordPaymentVerified(String requestId, String mode, String reference, BigDecimal amount) {
    findRequest(requestId)
        .ifPresent(
            request -> {
              request.setPaymentMode(mode);
              request.setPaymentReference(reference);
              request.setAmountPaid(amount);
              request.setPaymentVerifiedAt(Instant.now());
              request.advanceTo(RequestStatus.PROCESSING);
            });
  }

  /** Writes the condonation to the (simulated) SIS and returns the SIS reference. */
  @Transactional
  public String updateSis(
      String requestId, String studentId, String courseCode, BigDecimal percentage, String slab, Integer fee) {
    String reference = "SIS-" + SIS_REF.format(Instant.now());
    SisRecord record =
        sisRepository
            .findByStudentIdAndCourseCode(studentId, courseCode)
            .orElseGet(() -> new SisRecord(studentId, courseCode));
    record.apply(requestId == null ? "-" : requestId, reference, percentage, slab, fee);
    sisRepository.save(record);
    findRequest(requestId)
        .ifPresent(
            request -> {
              request.setSisUpdated(true);
              request.setSisReference(reference);
            });
    return reference;
  }

  /** Final status written by the record-outcome worker. Returns false if the request is not in the DB. */
  @Transactional
  public boolean recordOutcome(
      String requestId,
      boolean approved,
      BigDecimal percentage,
      BigDecimal shortfall,
      String slab,
      Integer fee,
      Boolean condonable,
      String rejectionReason,
      String rejectionStage) {
    Optional<CondonationRequest> found = findRequest(requestId);
    if (found.isEmpty()) {
      log.warn("record-outcome: request {} is not in the database (started outside the app?) - skipping", requestId);
      return false;
    }
    CondonationRequest request = found.get();
    if (percentage != null) {
      request.setAttendancePercentage(percentage);
      request.setShortfall(shortfall);
    }
    if (slab != null) {
      request.setSlab(slab);
      request.setFee(fee);
      request.setCondonable(condonable);
    }
    if (!approved) {
      request.setRejectionReason(rejectionReason);
      request.setRejectionStage(rejectionStage);
    }
    request.advanceTo(approved ? RequestStatus.APPROVED : RequestStatus.REJECTED);
    return true;
  }

  private Optional<CondonationRequest> findRequest(String requestId) {
    return requestId == null ? Optional.empty() : requestRepository.findByRequestId(requestId);
  }
}
