# BPMN models

All models target **Camunda 8.10** (SaaS) and are executable.

| File | Process / decision ID | Purpose |
|---|---|---|
| `condonation-main.bpmn` | `attendance-shortage-condonation` | Main end-to-end condonation workflow |
| `approval-child.bpmn` | `condonation-approval` | Child process (Call Activity) — one approver's review; called as a **sequential multi-instance** over the approvers decided by the DMN |
| `notification-child.bpmn` | `send-notification` | Reusable child process (Call Activity) — sends an email through the **Email connector**; used for student notifications and approver reminders |
| `condonation-eligibility.dmn` | `condonation-eligibility` | Eligibility slab, fee and required approvers *(Part 3)* |
| `forms/*.form` | — | Camunda Forms linked to the user tasks *(Part 3)* |

## Main process at a glance

```
Request submitted
  → Fetch attendance record            REST connector (GitHub-hosted dataset)   [error boundary: 404 → Student not found]
  → Calculate attendance & shortfall   Script task (FEEL)
  → Decide eligibility slab & fee      Business rule task (DMN)
  → Condonable?                        Exclusive gateway
  → [Sub-process] Collect & verify documents
        Upload supporting documents    User task (student)
        Validate document              Service task, PARALLEL multi-instance (job worker)
        boundary: Timer (documentDeadline) · Error DOC_INVALID
  → Approval by required approvers     Call Activity, SEQUENTIAL multi-instance → condonation-approval
  → All approved?                      Exclusive gateway
  → [Sub-process] Fee payment
        Pay condonation fee            User task (student)
        Verify payment                 Service task (job worker)
        boundary: Timer (feeDeadline) · Error PAYMENT_INVALID
  → Update SIS record                  Service task (job worker)   [error boundary: SIS_UPDATE_FAILED → admin fix → retry]
  → Parallel: Notify student (Call Activity → send-notification) + Record outcome (job worker)
  → Attendance condoned

Every rejection path passes a milestone event that sets rejectionReason / rejectionStage,
then: Notify student (rejected) → Record outcome → Request rejected
```

## Variables the backend must supply at start

| Variable | Example | Used by |
|---|---|---|
| `requestId` | `"REQ-1001"` | notifications, job workers |
| `studentId` | `"RA2311004010101"` | REST connector URL, task assignment |
| `studentName`, `studentEmail` | | notifications |
| `courseCode` | `"21ECC301T"` | attendance lookup |
| `reasonCategory` | `"MEDICAL"` | DMN input |
| `approverEmails` | `{FA: "...", AA: "...", HOD: "..."}` | approver reminders |
| `documentDeadline`, `feeDeadline` | `"P3D"`, `"P7D"` (demo: `"PT5M"`) | timer boundary events |
| `reminderInterval` | `"PT24H"` (demo: `"PT2M"`) | approver reminder timer |

## Job worker types (implemented in `backend-springboot`)

| Job type | BPMN error it may throw |
|---|---|
| `validate-document` | `DOC_INVALID` |
| `verify-payment` | `PAYMENT_INVALID` |
| `update-sis-record` | `SIS_UPDATE_FAILED` |
| `record-outcome` | — |

## Validation

All three models were imported into Camunda Web Modeler (Camunda Hub, project folder `full-stack-v2`) and checked against **Camunda 8.10**: no errors. The remaining notes are informational (linked DMN/forms/child processes are resolved at deploy time). The email connector uses the `{{secrets.NAME}}` connector-secret format, which 8.10 marks as "legacy" but still fully supports.

## Connector secrets (Camunda Console → Cluster → Connector secrets)

| Secret | Value |
|---|---|
| `SMTP_USERNAME` | Gmail address used to send notifications |
| `SMTP_PASSWORD` | Gmail **app password** for that address |
