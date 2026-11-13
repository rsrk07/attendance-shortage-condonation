# React frontend

Vite + React 19 + Tailwind CSS 4 single-page app. One website with a view per role:

| Role | Login | Screens |
|---|---|---|
| Student | register number, e.g. `RA2311004010101` | My requests (attendance per course against the 75% line), New request (course, reason, **document array builder**), request page with the **live route slip**, Fee payments (pay-fee task) |
| Faculty Advisor / Academic Advisor / HOD | `fa`, `aa`, `hod` | **Inbox** (custom Tasklist on Camunda user tasks), review screen with documents, automatic checks and earlier approvers' stamps, All requests |
| Admin | `admin` | Inbox (fix failed SIS updates), All requests, Settings (simulated SIS failure, SIS records) |

All demo users share the password `demo123` unless `DEMO_PASSWORD` is changed.

## How it maps to the process

| Screen | Backend call | Camunda |
|---|---|---|
| New request: Submit request | `POST /api/requests` (documents uploaded first via `POST /api/files`) | starts `attendance-shortage-condonation` with `documents = [{documentId, type, fileName, …}]` → parallel multi-instance `validate-document` |
| Request page: route slip | `GET /api/requests/{id}/status` every 4 s until the process ends | element instances + variables of the process instance |
| Inbox | `GET /api/tasks` every 8 s | user tasks by candidate group (`FA`, `AA`, `HOD`, `admin`) or assignee (student) |
| Review: Approve request / Reject request | `POST /api/tasks/{key}/review` | completes `UserTask_Review` in the `condonation-approval` call activity |
| Pay the condonation fee | `POST /api/tasks/{key}/pay-fee` | completes `UserTask_PayFee` → `verify-payment` worker |
| Fix the SIS update | `POST /api/tasks/{key}/admin-fix` | completes `UserTask_AdminFix` → retries `update-sis-record` |

## Validation

- **New request:** course must be below 75% with no open request; reason category required; reason 20–1000 characters; 1–5 documents, each with a type and an uploaded file; only PDF/JPG/PNG up to 5 MB (checked before upload, and again by the server); the same file can't be attached twice. Server field errors are shown next to the fields.
- **Review:** a decision is required; remarks are required when rejecting.
- **Pay fee:** transaction reference must be 8–20 letters/digits, amount > 0, confirmation ticked. Paying less than the fee is allowed but warned about, because the `verify-payment` worker rejects it (`PAYMENT_INVALID`).
- **Fix SIS update:** note and confirmation required.

## Structure

```
src/
  App.jsx                routes per role
  lib/api.js             fetch wrapper (Bearer token, JSON errors, file upload/download)
  lib/auth.jsx           login state, RequireAuth
  lib/useApi.js          data loading with polling (pauses when the tab is hidden)
  lib/format.js          status words, ₹ and % formatting
  components/            Layout, RouteSlip (live tracker), Stamp, DocumentBuilder (array builder), DocumentList, ui
  pages/                 Login, MyRequests, NewRequest, RequestDetail, Inbox, Task, AllRequests, Admin
```

## Run

```bash
cd frontend-react
npm install
npm run dev          # http://localhost:5173, /api is proxied to http://localhost:8080
```

Set `VITE_API_TARGET` to proxy to another backend URL. `npm run build` writes the static site to `dist/` (served by nginx in Docker).
