package com.attendance.condonation.worker;

import com.attendance.condonation.service.WorkflowRecordService;
import com.attendance.condonation.worker.WorkerVariables.SisInput;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.exception.CamundaError;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job type {@code update-sis-record}: writes the condonation into the (simulated) Student
 * Information System. When the request was started with {@code simulateSisFailure = true} and the
 * admin has not fixed it yet, it throws the BPMN error {@code SIS_UPDATE_FAILED}, which routes the
 * process to the admin-fix user task and then back here.
 */
@Component
public class SisUpdateWorker {

  private static final Logger log = LoggerFactory.getLogger(SisUpdateWorker.class);

  private final WorkflowRecordService records;

  public SisUpdateWorker(WorkflowRecordService records) {
    this.records = records;
  }

  public record Output(boolean sisUpdated, String sisReference) {}

  @JobWorker(type = "update-sis-record")
  public Output update(@VariablesAsType SisInput input) {
    if (Boolean.TRUE.equals(input.simulateSisFailure()) && !Boolean.TRUE.equals(input.sisRecordFixed())) {
      String error =
          "SIS rejected the update for " + input.studentId() + " / " + input.courseCode()
              + ": the course record is locked for the current semester (simulated failure).";
      log.info("{}: {}", input.requestId(), error);
      throw CamundaError.bpmnError("SIS_UPDATE_FAILED", error, Map.of("sisError", error));
    }

    String reference =
        records.updateSis(
            input.requestId(),
            input.studentId(),
            input.courseCode(),
            input.attendance() == null ? null : input.attendance().percentage(),
            input.eligibility() == null ? null : input.eligibility().slab(),
            input.eligibility() == null ? null : input.eligibility().fee());
    log.info("{}: SIS updated ({})", input.requestId(), reference);
    return new Output(true, reference);
  }
}
