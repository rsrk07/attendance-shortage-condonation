package com.attendance.condonation.worker;

import com.attendance.condonation.service.DocumentInspector;
import com.attendance.condonation.service.WorkflowRecordService;
import com.attendance.condonation.service.WorkflowRecordService.DocumentContent;
import com.attendance.condonation.worker.WorkerVariables.DocumentCheck;
import com.attendance.condonation.worker.WorkerVariables.DocumentRef;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job type {@code validate-document}: runs once per submitted document (parallel multi-instance).
 * Checks that the file was uploaded, its size and type, and that its content really is a
 * PDF / PNG / JPEG. Throws the BPMN error {@code DOC_INVALID} when a document fails.
 */
@Component
public class DocumentValidationWorker {

  private static final Logger log = LoggerFactory.getLogger(DocumentValidationWorker.class);

  private final WorkflowRecordService records;
  private final DocumentInspector inspector;

  public DocumentValidationWorker(WorkflowRecordService records, DocumentInspector inspector) {
    this.records = records;
    this.inspector = inspector;
  }

  /** Output for the multi-instance: {@code documentCheck} is collected into {@code documentChecks}. */
  public record Output(DocumentCheck documentCheck) {}

  @JobWorker(type = "validate-document")
  public Output validate(
      @Variable(name = "document") DocumentRef document, @Variable(name = "requestId") String requestId) {
    if (document == null) {
      throw invalid(null, "the document entry is missing");
    }
    String name = document.fileName() == null ? "document" : document.fileName();
    Optional<DocumentContent> content = records.documentContent(document.documentId());
    if (content.isEmpty()) {
      throw invalid(document, "'" + name + "' was not uploaded through the condonation portal");
    }

    DocumentInspector.Result result = inspector.inspect(content.get().fileName(), content.get().data());
    records.recordDocumentCheck(document.documentId(), result.valid(), result.note());
    if (!result.valid()) {
      throw invalid(document, result.note());
    }

    log.info("{}: document {} verified ({})", requestId, name, result.note());
    return new Output(
        new DocumentCheck(
            document.documentId(), document.type(), name, true, result.note(), Instant.now().toString()));
  }

  private RuntimeException invalid(DocumentRef document, String note) {
    log.info("Document rejected: {}", note);
    return CamundaError.bpmnError("DOC_INVALID", note, Map.of("documentRejectionNote", note));
  }
}
