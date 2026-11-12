package com.attendance.condonation.worker;

import com.attendance.condonation.service.WorkflowRecordService;
import com.attendance.condonation.worker.WorkerVariables.OutcomeInput;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job type {@code record-outcome}: stores the final result (APPROVED / REJECTED, slab, fee,
 * rejection reason) in the database. Used on both the approved and the rejected path.
 */
@Component
public class OutcomeRecorderWorker {

  private static final Logger log = LoggerFactory.getLogger(OutcomeRecorderWorker.class);

  private final WorkflowRecordService records;

  public OutcomeRecorderWorker(WorkflowRecordService records) {
    this.records = records;
  }

  public record Output(String outcomeRecordedAt) {}

  @JobWorker(type = "record-outcome")
  public Output record(@VariablesAsType OutcomeInput input) {
    boolean approved = "APPROVED".equals(input.outcome());
    boolean stored =
        records.recordOutcome(
            input.requestId(),
            approved,
            input.attendance() == null ? null : input.attendance().percentage(),
            input.attendance() == null ? null : input.attendance().shortfall(),
            input.eligibility() == null ? null : input.eligibility().slab(),
            input.eligibility() == null ? null : input.eligibility().fee(),
            input.eligibility() == null ? null : input.eligibility().condonable(),
            input.rejectionReason(),
            input.rejectionStage());
    if (stored) {
      log.info("{}: outcome {} recorded", input.requestId(), input.outcome());
    }
    return new Output(Instant.now().toString());
  }
}
