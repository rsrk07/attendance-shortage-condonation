# BPMN models

All models target **Camunda 8.10** (SaaS) and are executable.

| File | Process / decision ID | Purpose |
|---|---|---|
| `condonation-main.bpmn` | `attendance-shortage-condonation` | Main end-to-end condonation workflow |
| `approval-child.bpmn` | `condonation-approval` | Reusable child process (Call Activity) — one approver's review with a reminder timer; called three times: FA → AA → HOD |
| `notification-child.bpmn` | `send-notification` | Reusable child process (Call Activity) — sends an email through the **Email connector**; used for student notifications, approver reminders and approver updates |
| `condonation-eligibility.dmn` | `condonation-eligibility` | Eligibility slab and condonation fee *(Part 3)* |
| `forms/*.form` | — | Camunda Forms linked to the user tasks *(Part 3)* |

## Main process at a glance

```
Request submitted (student details + supporting documents)
  → Fetch attendance record            REST connector (GitHub-hosted dataset)   [error boundary: 404 → Student not found]
  → Calculate attendance & shortfall   Script task (FEEL)
  → Decide eligibility slab & fee      Business rule task (DMN)
  → Condonable?                        Exclusive gateway
  → [Sub-process] Verify submitted documents
        Validate document              Service task, PARALLEL multi-instance over the submitted documents (job worker)
        Summarise document checks      Script task (FEEL)
        boundary: Error DOC_INVALID
  → FA approval  → FA approved?        Call Activity → condonation-approval, then exclusive gateway
  → AA approval  → AA approved?        Call Activity → condonation-approval, then exclusive gateway
  → HOD approval → HOD approved?       Call Activity → condonation-approval, then exclusive gateway
  → [Sub-process] Fee payment
        Pay condonation fee            User task (student)
        Verify payment                 Service task (job worker)
        boundary: Timer (feeDeadline) · Error PAYMENT_INVALID
  → Update SIS record                  Service task (job worker)   [error boundary: SIS_UPDATE_FAILED → admin fix → retry]
  → Parallel gateway:
        Notify student (approved)      Call Activity → send-notification
        Inform approvers (FA, AA, HOD) Call Activity → send-notification, SEQUENTIAL multi-instance in hierarchy order
        Record outcome (approved)      Service task (job worker)
  → Attendance condoned

Every rejection path passes a milestone event that sets rejectionReason / rejectionStage,
then: Notify student (rejected) → Record outcome → Request rejected
```

## Variables the backend supplies at start

| Variable | Example | Used by |
|---|---|---|
| `requestId` | `"REQ-1001"` | notifications, job workers |
| `studentId` | `"RA2311004010101"` | REST connector URL, task assignment |
| `studentName`, `studentEmail` | | notifications |
| `courseCode` | `"21ECC301T"` | attendance lookup |
| `reasonCategory` | `"MEDICAL"` | DMN input |
| `reason` | free text | approver review |
| `documents` | `[{type: "MEDICAL_CERTIFICATE", fileName: "...", url: "..."}]` | parallel multi-instance (built with the React array builder) |
| `approverEmails` | `{FA: "...", AA: "...", HOD: "..."}` | approver reminders and updates |
| `feeDeadline` | `"P7D"` (demo: `"PT5M"`) | fee timer boundary event |
| `reminderInterval` | `"PT24H"` (demo: `"PT2M"`) | approver reminder timer |

## Job worker types (implemented in `backend-springboot`)

| Job type | BPMN error it may throw |
|---|---|
| `validate-document` | `DOC_INVALID` |
| `verify-payment` | `PAYMENT_INVALID` |
| `update-sis-record` | `SIS_UPDATE_FAILED` |
| `record-outcome` | — |

## Validation

All three models are imported into Camunda Web Modeler (Camunda Hub, project folder `full-stack-v2`) and checked against **Camunda 8.10** with no errors. The remaining notes are informational (linked DMN/forms/child processes are resolved at deploy time). The email connector uses the `{{secrets.NAME}}` connector-secret format, which 8.10 marks as "legacy" but still fully supports.

## Connector secrets (Camunda Console → Cluster → Connector secrets)

| Secret | Value |
|---|---|
| `SMTP_USERNAME` | Gmail address used to send notifications |
| `SMTP_PASSWORD` | Gmail **app password** for that address |
