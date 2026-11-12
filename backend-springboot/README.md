# Spring Boot backend

Spring Boot 3.5 (Java 21) service for the **Attendance Shortage Condonation** workflow. It:

- exposes the REST API used by the React frontend (login, request submission with documents, live status, custom Tasklist),
- stores students, requests, documents, approvals and the simulated SIS in **H2** through **JPA repositories**,
- starts and drives the Camunda 8 process through the **Camunda Java client** (`camunda-spring-boot-3-starter` 8.10),
- implements the four BPMN service tasks as typed **`@JobWorker`** methods that throw **BPMN errors**,
- deploys `bpmn-models/` (BPMN, DMN, forms) to the cluster on startup.

## Layers

```
controller/   REST endpoints + GlobalExceptionHandler (JSON errors, field validation messages)
service/      business logic, Camunda calls (ProcessService), status tracker, task inbox
repository/   Spring Data JPA interfaces
entity/       JPA entities and enums
dto/          request/response records with Bean Validation annotations
worker/       Zeebe job workers (@JobWorker)
auth/         token check (AuthInterceptor) and the logged-in user (AuthUser)
config/       app properties, CORS, startup seeding and BPMN deployment
```

## Job workers

| Job type | Worker | Reads | Writes | BPMN error |
|---|---|---|---|---|
| `validate-document` | `DocumentValidationWorker` | `document` (multi-instance element), `requestId` | `documentCheck` → collected into `documentChecks` | `DOC_INVALID` + `documentRejectionNote` |
| `verify-payment` | `PaymentVerificationWorker` | `paymentMode`, `paymentReference`, `amountPaid`, `paymentConfirmed`, `eligibility` | `paymentVerified`, `paymentVerifiedAt` | `PAYMENT_INVALID` + `paymentRejectionNote` |
| `update-sis-record` | `SisUpdateWorker` | `studentId`, `courseCode`, `attendance`, `eligibility`, `simulateSisFailure`, `sisRecordFixed` | `sisUpdated`, `sisReference` | `SIS_UPDATE_FAILED` + `sisError` |
| `record-outcome` | `OutcomeRecorderWorker` | `outcome`, `attendance`, `eligibility`, `rejectionReason`, `rejectionStage` | `outcomeRecordedAt` | — |

Inputs are mapped to Java records (`@Variable`, `@VariablesAsType`), so only the listed variables are fetched. Errors are thrown with `CamundaError.bpmnError(code, message, variables)`; in Camunda 8.10 this replaces the older `ZeebeBpmnError`.

**What the document check does:** the file must have been uploaded through the portal, be a PDF/JPG/PNG of at most 5 MB, and its first bytes must match the file type (a text file renamed to `.pdf` is rejected with `DOC_INVALID`).

**What the payment check does:** the student confirmed the payment, the reference is 8–20 letters/digits, the amount is at least the DMN fee, and the reference has not already been used for another verified payment. The REST API only checks the format; the business rules live in the worker so the `PAYMENT_INVALID` path is reachable.

## REST API

All endpoints except login need `Authorization: Bearer <token>`.

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | everyone | `{username, password}` → token + user |
| GET | `/api/auth/demo-users` | everyone | demo accounts for the login page (no passwords) |
| GET | `/api/auth/me` · POST `/api/auth/logout` | logged in | current user / log out |
| GET | `/api/meta/options` | logged in | reason categories, document types, payment modes, upload limits |
| GET | `/api/meta/my-courses` | student | courses with attendance %, shortage flag, open-request flag |
| POST | `/api/files` | student | upload one document (multipart field `file`) → `fileId` |
| GET | `/api/files/{fileId}` | owner / staff | download a document |
| POST | `/api/requests` | student | submit `{courseCode, reasonCategory, reason, documents:[{type, fileId, description}]}` and start the process |
| GET | `/api/requests` | student: own · staff: all | list (optional `?status=`) |
| GET | `/api/requests/{requestId}` | owner / staff | request details, documents, approvals |
| GET | `/api/requests/{requestId}/status` | owner / staff | live status tracker built from Camunda element instances and variables |
| GET | `/api/tasks` | logged in | open user tasks (FA/AA/HOD: their candidate group; student: assigned to them; admin: `admin` group) |
| GET | `/api/tasks/{taskKey}` | task owner | task + request + process variables for the task screen |
| POST | `/api/tasks/{taskKey}/claim` | task owner | assign the task to yourself |
| POST | `/api/tasks/{taskKey}/review` | FA / AA / HOD | `{decision: "APPROVED" or "REJECTED", remarks}` |
| POST | `/api/tasks/{taskKey}/pay-fee` | student | `{paymentMode, paymentReference, amountPaid, paymentConfirmed}` |
| POST | `/api/tasks/{taskKey}/admin-fix` | admin | `{adminNote, sisRecordFixed}` |
| GET / PUT | `/api/admin/settings` | admin | toggle the simulated SIS failure for new requests |
| GET | `/api/admin/sis-records` | staff | contents of the simulated SIS |

Errors are returned as `{status, message, fieldErrors, timestamp}`.

## Data

- On startup `DataSeeder` loads `src/main/resources/data/attendance/*.json` (the same files the REST connector fetches from GitHub) into `student` / `course_attendance`, and creates the demo users.
- Demo users: the four student register numbers (`RA2311004010101` … `104`), `fa`, `aa`, `hod`, `admin`. All use `DEMO_PASSWORD` (default `demo123`).
- H2 file database in `./data/` (in Docker: a named volume). The H2 console is at `/h2-console` when running locally (JDBC URL `jdbc:h2:file:./data/condonation`, user `sa`, empty password).

## Configuration

All settings come from environment variables (see `../.env.example`); nothing secret is in `application.yml`.

| Variable | Default | Meaning |
|---|---|---|
| `CAMUNDA_CLUSTER_ID`, `CAMUNDA_REGION` | –, `sin-1` | SaaS cluster |
| `CAMUNDA_CLIENT_ID`, `CAMUNDA_CLIENT_SECRET` | – | API client credentials (**secret**) |
| `DEPLOY_ON_STARTUP` | `true` | deploy `bpmn-models/` on startup |
| `DEMO_PASSWORD` | `demo123` | password of all demo users |
| `NOTIFICATION_EMAIL` | – | send every notification to this inbox (demo) |
| `FA_EMAIL`, `AA_EMAIL`, `HOD_EMAIL` | `*@example.com` | approver e-mail addresses |
| `FEE_DEADLINE`, `REMINDER_INTERVAL` | `P7D`, `PT24H` | process timers |
| `SIMULATE_SIS_FAILURE` | `false` | start requests with a simulated SIS failure |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | H2 file | datasource |
| `CORS_ORIGINS` | localhost + `*.app.github.dev` | allowed browser origins |

## Run

```bash
cd backend-springboot
export CAMUNDA_CLIENT_ID=... CAMUNDA_CLIENT_SECRET=...   # or use the root .env with docker compose
mvn spring-boot:run
```

`mvn package` builds `target/condonation-backend.jar` and runs the unit tests. The `bpmn-models/` folder is packaged into the jar under `bpmn/`.
