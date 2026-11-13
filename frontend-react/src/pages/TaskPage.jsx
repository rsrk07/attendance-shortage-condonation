import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import DocumentList from "../components/DocumentList.jsx";
import Stamp from "../components/Stamp.jsx";
import { ErrorNotice, Facts, FieldError, Loading, Notice } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { useAuth } from "../lib/auth.jsx";
import { ROLE_TITLES, formatDateTime, percent, rupees, slabLabel } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";
import { taskTitle } from "./InboxPage.jsx";

export default function TaskPage() {
  const { taskKey } = useParams();
  const { user } = useAuth();
  const detail = useApi(() => api(`/tasks/${taskKey}`), [taskKey]);
  const [claiming, setClaiming] = useState(false);
  const [claimError, setClaimError] = useState(null);

  if (detail.loading) return <Loading label="Loading the task" />;
  if (!detail.data) {
    return (
      <div className="max-w-2xl">
        <ErrorNotice error={detail.error} />
        <Link to="/inbox" className="link mt-4 inline-block">
          Back to the inbox
        </Link>
      </div>
    );
  }

  const { task, request, variables } = detail.data;
  const claimedByOther = task.assignee && task.assignee !== user.username;

  async function claim() {
    setClaiming(true);
    setClaimError(null);
    try {
      await api(`/tasks/${taskKey}/claim`, { method: "POST" });
      await detail.reload();
    } catch (e) {
      setClaimError(e.message);
    } finally {
      setClaiming(false);
    }
  }

  return (
    <div>
      <Link to="/inbox" className="link text-sm">
        {user.role === "STUDENT" ? "Fee payments" : "Inbox"}
      </Link>
      <div className="mb-6 mt-2 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl">{taskTitle(task)}</h1>
          <p className="mt-1 text-ink-soft">
            {task.requestId ? (
              <Link to={`/requests/${task.requestId}`} className="link">
                {task.requestId}
              </Link>
            ) : (
              "Request"
            )}
            {task.studentName ? `, ${task.studentName} (${task.studentId})` : ""}, waiting since{" "}
            {formatDateTime(task.createdAt)}
          </p>
        </div>
        {user.role !== "STUDENT" && (
          <div className="text-right text-sm">
            {task.assignee ? (
              <p className="text-ink-soft">
                {task.assignee === user.username ? "Claimed by you" : `Claimed by ${task.assignee}`}
              </p>
            ) : (
              <button type="button" className="btn-quiet text-sm" onClick={claim} disabled={claiming}>
                {claiming ? "Claiming…" : "Claim this task"}
              </button>
            )}
            {claimError && <p className="mt-1 text-reject">{claimError}</p>}
          </div>
        )}
      </div>

      {claimedByOther && (
        <Notice tone="warning" className="mb-6">
          {task.assignee} is already working on this task, so you can't complete it.
        </Notice>
      )}

      {task.kind === "REVIEW" && (
        <ReviewTask task={task} request={request} variables={variables} disabled={claimedByOther} />
      )}
      {task.kind === "PAY_FEE" && <PayFeeTask task={task} request={request} variables={variables} />}
      {task.kind === "ADMIN_FIX" && (
        <AdminFixTask task={task} request={request} variables={variables} disabled={claimedByOther} />
      )}
      {task.kind === "OTHER" && (
        <Notice tone="info">This task type is handled in Camunda Tasklist, not in this app.</Notice>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ shared */

function RequestSummary({ request, variables }) {
  const attendance = variables.attendance || {};
  const eligibility = variables.eligibility || {};
  return (
    <section className="sheet p-6">
      <h2 className="mb-4 text-2xl">Request</h2>
      <Facts
        items={[
          { label: "Student", value: request ? `${request.studentName} (${request.studentId})` : variables.studentName },
          { label: "Course", value: `${variables.courseCode || request?.courseCode} ${attendance.courseName || request?.courseName || ""}` },
          {
            label: "Attendance",
            value: `${percent(attendance.percentage ?? request?.attendancePercentage)}${
              attendance.attended != null ? ` (${attendance.attended} of ${attendance.conducted} classes)` : ""
            }`,
          },
          { label: "Shortfall", value: percent(attendance.shortfall ?? request?.shortfall) },
          { label: "Slab", value: slabLabel(eligibility.slab || request?.slab) },
          { label: "Fee", value: rupees(eligibility.fee ?? request?.fee) },
          { label: "Reason", value: request?.reasonCategoryLabel || variables.reasonCategory },
        ]}
      />
      {(request?.reason || variables.reason) && (
        <p className="mt-4 whitespace-pre-line border-t border-rule pt-4 text-sm">{request?.reason || variables.reason}</p>
      )}
    </section>
  );
}

function useSubmit(onDone) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  async function run(fn) {
    setBusy(true);
    setError(null);
    try {
      await fn();
      onDone();
    } catch (e) {
      setError(e.message);
      setFieldErrors(e.fieldErrors || {});
      setBusy(false);
    }
  }
  return { busy, error, fieldErrors, setFieldErrors, run };
}

/* ------------------------------------------------------------------ FA / AA / HOD */

function ReviewTask({ task, request, variables, disabled }) {
  const navigate = useNavigate();
  const [decision, setDecision] = useState("");
  const [remarks, setRemarks] = useState("");
  const submit = useSubmit(() =>
    navigate("/inbox", {
      state: {
        flash: `${task.requestId || "The request"} ${decision === "APPROVED" ? "approved" : "rejected"}.${
          decision === "APPROVED" && task.approverRole !== "HOD" ? " It has moved to the next approver." : ""
        }`,
      },
    }),
  );
  const role = task.approverRole;
  const earlier = (request?.approvals || []).filter((a) => a.role !== role);
  const checks = variables.documentChecks || [];

  function validate() {
    const errors = {};
    if (!decision) errors.decision = "Choose approve or reject.";
    if (decision === "REJECTED" && remarks.trim().length < 5) {
      errors.remarks = "Tell the student why the request is rejected.";
    }
    return errors;
  }

  function onSubmit(event) {
    event.preventDefault();
    const errors = validate();
    submit.setFieldErrors(errors);
    if (Object.keys(errors).length) return;
    submit.run(() =>
      api(`/tasks/${task.taskKey}/review`, { method: "POST", body: { decision, remarks: remarks.trim() } }),
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_26rem]">
      <div className="space-y-8">
        <RequestSummary request={request} variables={variables} />
        <section className="sheet p-6">
          <h2 className="mb-4 text-2xl">Documents</h2>
          {request ? (
            <DocumentList documents={request.documents} />
          ) : (
            <ul className="text-sm">
              {(variables.documents || []).map((d) => (
                <li key={d.documentId ?? d.fileName}>
                  {d.typeLabel || d.type}: {d.fileName}
                </li>
              ))}
            </ul>
          )}
          {checks.length > 0 && (
            <p className="mt-4 border-t border-rule pt-4 text-sm text-ink-soft">
              Automatic check: {checks.filter((c) => c.valid).length} of {checks.length} documents verified.
            </p>
          )}
        </section>
      </div>

      <div className="space-y-8">
        {earlier.length > 0 && (
          <section className="sheet p-6">
            <h2 className="mb-4 text-2xl">Earlier decisions</h2>
            <ul className="space-y-5">
              {earlier.map((a) => (
                <li key={`${a.role}-${a.decidedAt}`}>
                  <Stamp
                    approved={a.approved}
                    title={`${a.role} ${a.approved ? "approved" : "rejected"}`}
                    name={a.reviewerName}
                    date={a.decidedAt}
                    rotate={-3}
                  />
                  {a.remarks && <p className="mt-2 text-sm italic text-ink-soft">“{a.remarks}”</p>}
                </li>
              ))}
            </ul>
          </section>
        )}

        <section className="sheet p-6">
          <h2 className="mb-1 text-2xl">Your decision</h2>
          <p className="mb-4 text-sm text-ink-soft">
            As {ROLE_TITLES[role] || role}.{" "}
            {role === "HOD" ? "If you approve, the student is asked to pay the fee." : "If you approve, it goes to the next approver."}
          </p>
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <fieldset>
              <legend className="sr-only">Decision</legend>
              <div className="grid grid-cols-2 gap-3" aria-invalid={Boolean(submit.fieldErrors.decision)}>
                {[
                  { value: "APPROVED", label: "Approve", tone: "peer-checked:border-approve peer-checked:bg-approve/5 peer-checked:text-approve" },
                  { value: "REJECTED", label: "Reject", tone: "peer-checked:border-reject peer-checked:bg-reject/5 peer-checked:text-reject" },
                ].map((option) => (
                  <label key={option.value} className="cursor-pointer">
                    <input
                      type="radio"
                      name="decision"
                      value={option.value}
                      className="peer sr-only"
                      checked={decision === option.value}
                      disabled={disabled || submit.busy}
                      onChange={() => setDecision(option.value)}
                    />
                    <span
                      className={`block rounded border-2 border-rule px-3 py-2 text-center font-semibold peer-focus-visible:outline-2 peer-focus-visible:outline-register ${option.tone}`}
                    >
                      {option.label}
                    </span>
                  </label>
                ))}
              </div>
              <FieldError message={submit.fieldErrors.decision} />
            </fieldset>
            <div>
              <label htmlFor="remarks" className="mb-1 block text-sm font-medium">
                Remarks {decision === "REJECTED" ? "" : <span className="font-normal text-ink-soft">(optional)</span>}
              </label>
              <textarea
                id="remarks"
                rows={4}
                maxLength={1000}
                className={`field ${submit.fieldErrors.remarks ? "field-error" : ""}`}
                value={remarks}
                disabled={disabled || submit.busy}
                aria-invalid={Boolean(submit.fieldErrors.remarks)}
                placeholder={decision === "REJECTED" ? "The student sees this reason." : "Visible to the student and the next approvers."}
                onChange={(e) => setRemarks(e.target.value)}
              />
              <FieldError message={submit.fieldErrors.remarks} />
            </div>
            {submit.error && <Notice tone="error">{submit.error}</Notice>}
            <button
              type="submit"
              className={decision === "REJECTED" ? "btn-danger w-full" : "btn-primary w-full"}
              disabled={disabled || submit.busy}
            >
              {submit.busy
                ? "Saving…"
                : decision === "REJECTED"
                  ? "Reject request"
                  : decision === "APPROVED"
                    ? "Approve request"
                    : "Record decision"}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ student: pay fee */

const REFERENCE_PATTERN = /^[A-Za-z0-9]{8,20}$/;

function PayFeeTask({ task, request, variables }) {
  const navigate = useNavigate();
  const options = useApi(() => api("/meta/options"), []);
  const fee = variables.eligibility?.fee ?? request?.fee;
  const [paymentMode, setPaymentMode] = useState("UPI");
  const [paymentReference, setPaymentReference] = useState("");
  const [amountPaid, setAmountPaid] = useState(fee != null ? String(fee) : "");
  const [paymentConfirmed, setPaymentConfirmed] = useState(false);
  const submit = useSubmit(() =>
    navigate(task.requestId ? `/requests/${task.requestId}` : "/requests", {
      state: { flash: "Payment submitted. It is being verified now." },
    }),
  );

  const amount = Number(amountPaid);
  const underpaid = fee != null && amountPaid !== "" && amount > 0 && amount < fee;

  function validate() {
    const errors = {};
    if (!paymentMode) errors.paymentMode = "Choose how you paid.";
    if (!REFERENCE_PATTERN.test(paymentReference.trim())) {
      errors.paymentReference = "Enter the 8 to 20 letter or digit reference from your payment receipt.";
    }
    if (!amountPaid || !(amount > 0)) errors.amountPaid = "Enter the amount you paid.";
    if (!paymentConfirmed) errors.paymentConfirmed = "Confirm that these details are correct.";
    return errors;
  }

  function onSubmit(event) {
    event.preventDefault();
    const errors = validate();
    submit.setFieldErrors(errors);
    if (Object.keys(errors).length) return;
    submit.run(() =>
      api(`/tasks/${task.taskKey}/pay-fee`, {
        method: "POST",
        body: { paymentMode, paymentReference: paymentReference.trim(), amountPaid: amount, paymentConfirmed },
      }),
    );
  }

  const errors = submit.fieldErrors;
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_26rem]">
      <RequestSummary request={request} variables={variables} />
      <section className="sheet p-6">
        <p className="text-sm text-ink-soft">Condonation fee for {slabLabel(variables.eligibility?.slab || request?.slab)}</p>
        <p className="font-display text-5xl font-semibold">{rupees(fee)}</p>
        <p className="mb-5 mt-2 text-sm text-ink-soft">
          Pay through the university payment portal, then enter the transaction details here. In this demo no
          money is collected; any reference of 8 to 20 letters or digits works.
        </p>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="paymentMode" className="mb-1 block text-sm font-medium">
              Payment mode
            </label>
            <select
              id="paymentMode"
              className={`field ${errors.paymentMode ? "field-error" : ""}`}
              value={paymentMode}
              disabled={submit.busy}
              onChange={(e) => setPaymentMode(e.target.value)}
            >
              {(options.data?.paymentModes || [{ value: "UPI", label: "UPI" }]).map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
            <FieldError message={errors.paymentMode} />
          </div>
          <div>
            <label htmlFor="paymentReference" className="mb-1 block text-sm font-medium">
              Transaction reference
            </label>
            <input
              id="paymentReference"
              className={`field uppercase ${errors.paymentReference ? "field-error" : ""}`}
              value={paymentReference}
              maxLength={20}
              autoComplete="off"
              disabled={submit.busy}
              aria-invalid={Boolean(errors.paymentReference)}
              placeholder="UPI4829301756"
              onChange={(e) => setPaymentReference(e.target.value.replace(/\s/g, ""))}
            />
            <FieldError message={errors.paymentReference} />
          </div>
          <div>
            <label htmlFor="amountPaid" className="mb-1 block text-sm font-medium">
              Amount paid (₹)
            </label>
            <input
              id="amountPaid"
              type="number"
              min="1"
              step="1"
              inputMode="numeric"
              className={`field ${errors.amountPaid ? "field-error" : ""}`}
              value={amountPaid}
              disabled={submit.busy}
              aria-invalid={Boolean(errors.amountPaid)}
              onChange={(e) => setAmountPaid(e.target.value)}
            />
            <FieldError message={errors.amountPaid} />
            {underpaid && (
              <p className="mt-1 text-sm text-pending">
                This is less than the fee of {rupees(fee)}. The payment check will reject it and the request will
                not be condoned.
              </p>
            )}
          </div>
          <div>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 accent-register"
                checked={paymentConfirmed}
                disabled={submit.busy}
                aria-invalid={Boolean(errors.paymentConfirmed)}
                onChange={(e) => setPaymentConfirmed(e.target.checked)}
              />
              I confirm that the payment details above are correct.
            </label>
            <FieldError message={errors.paymentConfirmed} />
          </div>
          {submit.error && <Notice tone="error">{submit.error}</Notice>}
          <button type="submit" className="btn-primary w-full" disabled={submit.busy}>
            {submit.busy ? "Submitting…" : `Submit payment of ${rupees(amountPaid || 0)}`}
          </button>
        </form>
      </section>
    </div>
  );
}

/* ------------------------------------------------------------------ admin: fix SIS update */

function AdminFixTask({ task, request, variables, disabled }) {
  const navigate = useNavigate();
  const [adminNote, setAdminNote] = useState("");
  const [sisRecordFixed, setSisRecordFixed] = useState(false);
  const submit = useSubmit(() =>
    navigate("/inbox", { state: { flash: `SIS update for ${task.requestId || "the request"} is being retried.` } }),
  );

  function onSubmit(event) {
    event.preventDefault();
    const errors = {};
    if (!adminNote.trim()) errors.adminNote = "Describe what you fixed.";
    if (!sisRecordFixed) errors.sisRecordFixed = "Tick this once the record is fixed.";
    submit.setFieldErrors(errors);
    if (Object.keys(errors).length) return;
    submit.run(() =>
      api(`/tasks/${task.taskKey}/admin-fix`, {
        method: "POST",
        body: { adminNote: adminNote.trim(), sisRecordFixed },
      }),
    );
  }

  const errors = submit.fieldErrors;
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_26rem]">
      <div className="space-y-8">
        <Notice tone="error" title="The SIS update failed">
          {variables.sisError || "The student information system rejected the update."}
        </Notice>
        <RequestSummary request={request} variables={variables} />
      </div>
      <section className="sheet p-6">
        <h2 className="mb-1 text-2xl">Fix and retry</h2>
        <p className="mb-4 text-sm text-ink-soft">
          Fix the record in the student information system, then retry. The update runs again straight away.
        </p>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="adminNote" className="mb-1 block text-sm font-medium">
              What did you fix?
            </label>
            <textarea
              id="adminNote"
              rows={4}
              maxLength={1000}
              className={`field ${errors.adminNote ? "field-error" : ""}`}
              value={adminNote}
              disabled={disabled || submit.busy}
              aria-invalid={Boolean(errors.adminNote)}
              placeholder="Unlocked the course record for semester 5"
              onChange={(e) => setAdminNote(e.target.value)}
            />
            <FieldError message={errors.adminNote} />
          </div>
          <div>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 accent-register"
                checked={sisRecordFixed}
                disabled={disabled || submit.busy}
                aria-invalid={Boolean(errors.sisRecordFixed)}
                onChange={(e) => setSisRecordFixed(e.target.checked)}
              />
              The SIS record is fixed.
            </label>
            <FieldError message={errors.sisRecordFixed} />
          </div>
          {submit.error && <Notice tone="error">{submit.error}</Notice>}
          <button type="submit" className="btn-primary w-full" disabled={disabled || submit.busy}>
            {submit.busy ? "Retrying…" : "Retry SIS update"}
          </button>
        </form>
      </section>
    </div>
  );
}
