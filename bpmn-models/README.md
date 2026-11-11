# BPMN models

All models target **Camunda 8.10** (SaaS) and are executable.

| File | Process / decision ID | Purpose |
|---|---|---|
| `condonation-main.bpmn` | `attendance-shortage-condonation` | Main end-to-end condonation workflow |
| `approval-child.bpmn` | `condonation-approval` | Reusable child process (Call Activity) — one approver's review with a reminder timer; called three times: FA → AA → HOD |
| `notification-child.bpmn` | `send-notification` | Reusable child process (Call Activity) — sends an email through the **Email connector**; used for student notifications, approver reminders and approver updates |
| `condonation-eligibility.dmn` | `condonation-eligibility` | DMN decision table: eligibility slab and condonation fee |
| `forms/review-request.form` | `review-request` | FA / AA / HOD review (approval child process) |
| `forms/pay-fee.form` | `pay-fee` | Student pays the condonation fee |
| `forms/admin-fix.form` | `admin-fix` | Admin fixes a failed SIS update |

## DMN: `condonation-eligibility` (hit policy FIRST)

Inputs: `attendance.percentage` (number), `reasonCategory` (string). Result variable in the BPMN: `eligibility = {slab, condonable, fee, remark}`.

| # | Attendance % | Reason category | Slab | Condonable | Fee (Rs.) |
|---|---|---|---|---|---|
| 1 | `>= 75` | any | `NOT_REQUIRED` | false | 0 |
| 2 | `[70..75)` | any | `SLAB_A` | true | 500 |
| 3 | `[65..70)` | `MEDICAL`, `ON_DUTY`, `FAMILY_EMERGENCY` | `SLAB_B` | true | 1000 |
| 4 | `[60..65)` | `MEDICAL` | `SLAB_C` | true | 2000 |
| 5 | any (incl. missing) | any | `NOT_ELIGIBLE` | false | 0 |

Sample data coverage (`backend-springboot/src/main/resources/data/attendance/`):

| Student | 21ECC301T | 21ECC302J | 21ECC303T |
|---|---|---|---|
| RA2311004010101 | 72.22% → A | 65.28% → B (not with PERSONAL) | 91.67% → not required |
| RA2311004010102 | 62.50% → C (MEDICAL only) | 52.78% → not eligible | 77.78% → not required |
| RA2311004010103 | 70.83% → A | 69.44% → B | 68.06% → B |
| RA2311004010104 | 97.22% → not required | 94.44% → not required | 41.67% → not eligible |

## Forms

| Form | Shows | Submits |
|---|---|---|
| `review-request` | student, course, attendance, slab, fee, reason, document table, automatic verification table, earlier approvers' remarks | `decision` (`APPROVED` / `REJECTED`), `remarks` |
| `pay-fee` | fee amount and slab | `paymentMode`, `paymentReference` (8–20 letters/digits), `amountPaid`, `paymentConfirmed` |
| `admin-fix` | SIS error (`sisError`) | `adminNote`, `sisRecordFixed` |

The custom React Tasklist (Part 6) renders its own screens for these tasks; the Camunda Forms make the same tasks usable in Camunda's built-in Tasklist too.

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
