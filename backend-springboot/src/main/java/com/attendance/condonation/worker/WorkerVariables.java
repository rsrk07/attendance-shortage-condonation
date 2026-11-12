package com.attendance.condonation.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Typed views of the process variables the job workers read and write. */
public final class WorkerVariables {

  private WorkerVariables() {}

  /** One element of the {@code documents} collection (multi-instance input element {@code document}). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentRef(Long documentId, String type, String fileName, String contentType, Long sizeBytes) {}

  /** Result of one document check; collected by the multi-instance into {@code documentChecks}. */
  public record DocumentCheck(
      Long documentId, String type, String fileName, boolean valid, String note, String verifiedAt) {}

  /** The {@code eligibility} variable produced by the DMN decision. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Eligibility(String slab, Boolean condonable, Integer fee, String remark) {}

  /** The {@code attendance} variable produced by the FEEL script task. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AttendanceSummary(
      String courseName, Integer attended, Integer conducted, BigDecimal percentage, BigDecimal shortfall) {}

  /** Variables fetched by the verify-payment worker. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PaymentInput(
      String requestId,
      String paymentMode,
      String paymentReference,
      BigDecimal amountPaid,
      Boolean paymentConfirmed,
      Eligibility eligibility) {}

  /** Variables fetched by the update-sis-record worker. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SisInput(
      String requestId,
      String studentId,
      String courseCode,
      AttendanceSummary attendance,
      Eligibility eligibility,
      Boolean simulateSisFailure,
      Boolean sisRecordFixed) {}

  /** Variables fetched by the record-outcome worker. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OutcomeInput(
      String requestId,
      String outcome,
      AttendanceSummary attendance,
      Eligibility eligibility,
      String rejectionReason,
      String rejectionStage) {}
}
