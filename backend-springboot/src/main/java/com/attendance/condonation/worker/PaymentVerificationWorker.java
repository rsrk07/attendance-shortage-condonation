package com.attendance.condonation.worker;

import com.attendance.condonation.service.PaymentVerifier;
import com.attendance.condonation.service.WorkflowRecordService;
import com.attendance.condonation.worker.WorkerVariables.PaymentInput;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.exception.CamundaError;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job type {@code verify-payment}: checks the fee payment entered in the pay-fee task against the
 * fee decided by the DMN, and that the transaction reference was not used before. Throws the BPMN
 * error {@code PAYMENT_INVALID} otherwise.
 */
@Component
public class PaymentVerificationWorker {

  private static final Logger log = LoggerFactory.getLogger(PaymentVerificationWorker.class);

  private final PaymentVerifier verifier;
  private final WorkflowRecordService records;

  public PaymentVerificationWorker(PaymentVerifier verifier, WorkflowRecordService records) {
    this.verifier = verifier;
    this.records = records;
  }

  public record Output(boolean paymentVerified, String paymentVerifiedAt) {}

  @JobWorker(type = "verify-payment")
  public Output verify(@VariablesAsType PaymentInput input) {
    Integer fee = input.eligibility() == null ? null : input.eligibility().fee();
    Optional<String> problem =
        verifier.problem(input.paymentConfirmed(), input.paymentReference(), input.amountPaid(), fee);
    if (problem.isEmpty()) {
      problem =
          records
              .requestUsingPaymentReference(input.paymentReference(), input.requestId())
              .map(other -> "transaction reference " + input.paymentReference() + " was already used for " + other);
    }
    if (problem.isPresent()) {
      log.info("{}: payment rejected - {}", input.requestId(), problem.get());
      throw CamundaError.bpmnError(
          "PAYMENT_INVALID", problem.get(), Map.of("paymentRejectionNote", problem.get()));
    }

    records.recordPaymentVerified(
        input.requestId(), input.paymentMode(), input.paymentReference(), input.amountPaid());
    log.info("{}: payment {} verified", input.requestId(), input.paymentReference());
    return new Output(true, Instant.now().toString());
  }
}
