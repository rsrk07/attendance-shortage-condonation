import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import DocumentBuilder, { emptyDocumentRow, validateDocumentRow } from "../components/DocumentBuilder.jsx";
import { ErrorNotice, FieldError, Loading, Notice, PageHeader } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { percent } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

const SUGGESTED_DOCUMENT = {
  MEDICAL: "MEDICAL_CERTIFICATE",
  ON_DUTY: "ON_DUTY_LETTER",
  FAMILY_EMERGENCY: "PARENT_LETTER",
};

const REASON_MIN = 20;
const REASON_MAX = 1000;

export default function NewRequestPage() {
  const navigate = useNavigate();
  const options = useApi(() => api("/meta/options"), []);
  const courses = useApi(() => api("/meta/my-courses"), []);

  const [courseCode, setCourseCode] = useState("");
  const [reasonCategory, setReasonCategory] = useState("");
  const [reason, setReason] = useState("");
  const [documents, setDocuments] = useState(() => [emptyDocumentRow()]);
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Pre-select the matching document type for the first, still empty row
  useEffect(() => {
    const suggested = SUGGESTED_DOCUMENT[reasonCategory];
    if (!suggested) return;
    setDocuments((rows) =>
      rows.map((row, i) => (i === 0 && !row.type && !row.file ? { ...row, type: suggested } : row)),
    );
  }, [reasonCategory]);

  if (options.loading || courses.loading) return <Loading label="Loading the form" />;
  if (options.error || courses.error) {
    return <ErrorNotice error={options.error || courses.error} onRetry={() => (options.reload(), courses.reload())} />;
  }

  const eligibleCourses = courses.data.filter((c) => c.shortage && !c.hasOpenRequest);

  function validate() {
    const next = {};
    if (!courseCode) next.courseCode = "Choose the course you are applying for.";
    if (!reasonCategory) next.reasonCategory = "Choose the reason for your absence.";
    const trimmed = reason.trim();
    if (trimmed.length < REASON_MIN) next.reason = `Describe the reason in at least ${REASON_MIN} characters.`;
    else if (trimmed.length > REASON_MAX) next.reason = `Keep the reason under ${REASON_MAX} characters.`;
    const docErrors = documents.map(validateDocumentRow);
    if (docErrors.some((e) => Object.keys(e).length)) next.documents = docErrors;
    if (documents.length === 0) next.documentsGeneral = "Attach at least one supporting document.";
    const fileIds = documents.map((d) => d.file?.fileId).filter(Boolean);
    if (new Set(fileIds).size !== fileIds.length) next.documentsGeneral = "The same file is attached twice.";
    return next;
  }

  async function submit(event) {
    event.preventDefault();
    setSubmitError(null);
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) {
      document.querySelector("[aria-invalid='true']")?.focus();
      return;
    }
    setSubmitting(true);
    try {
      const created = await api("/requests", {
        method: "POST",
        body: {
          courseCode,
          reasonCategory,
          reason: reason.trim(),
          documents: documents.map((d) => ({
            type: d.type,
            fileId: d.file.fileId,
            description: d.description.trim() || null,
          })),
        },
      });
      navigate(`/requests/${created.requestId}`, {
        state: { flash: `Request ${created.requestId} submitted. It is being checked now.` },
      });
    } catch (e) {
      setSubmitError(e.message);
      const { documents: documentsMessage, ...fieldErrors } = e.fieldErrors || {};
      setErrors((current) => ({
        ...current,
        ...fieldErrors,
        ...(documentsMessage ? { documentsGeneral: documentsMessage } : {}),
      }));
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="New condonation request"
        description="Choose the course, explain why you were absent, and attach proof. You can't edit the request after submitting it."
      />

      {eligibleCourses.length === 0 ? (
        <Notice tone="info" title="No course needs condonation right now">
          All your courses are at 75% or above, or already have an open request.{" "}
          <Link to="/requests" className="link">
            Back to my requests
          </Link>
        </Notice>
      ) : (
        <form onSubmit={submit} noValidate className="space-y-8">
          <fieldset>
            <legend className="mb-2 font-display text-2xl font-semibold">Course</legend>
            <div className="sheet divide-y divide-rule" role="radiogroup" aria-invalid={Boolean(errors.courseCode)}>
              {courses.data.map((c) => {
                const disabled = !c.shortage || c.hasOpenRequest;
                return (
                  <label
                    key={c.courseCode}
                    className={`flex items-center gap-4 px-4 py-3 ${disabled ? "opacity-60" : "cursor-pointer hover:bg-paper"}`}
                  >
                    <input
                      type="radio"
                      name="course"
                      value={c.courseCode}
                      checked={courseCode === c.courseCode}
                      disabled={disabled || submitting}
                      onChange={() => setCourseCode(c.courseCode)}
                      className="h-4 w-4 accent-register"
                    />
                    <span className="flex-1">
                      <span className="font-medium">
                        {c.courseCode} {c.courseName}
                      </span>
                      <span className="block text-sm text-ink-soft">
                        {c.attended} of {c.conducted} classes
                        {c.hasOpenRequest ? ", you already have an open request" : !c.shortage ? ", no condonation needed" : ""}
                      </span>
                    </span>
                    <span className={`font-semibold ${c.shortage ? "text-pending" : "text-approve"}`}>{percent(c.percentage)}</span>
                  </label>
                );
              })}
            </div>
            <FieldError message={errors.courseCode} />
          </fieldset>

          <fieldset className="space-y-4">
            <legend className="mb-2 font-display text-2xl font-semibold">Reason</legend>
            <div>
              <label htmlFor="reasonCategory" className="mb-1 block text-sm font-medium">
                Reason category
              </label>
              <select
                id="reasonCategory"
                className={`field max-w-sm ${errors.reasonCategory ? "field-error" : ""}`}
                value={reasonCategory}
                disabled={submitting}
                aria-invalid={Boolean(errors.reasonCategory)}
                aria-describedby="reasonCategory-hint"
                onChange={(e) => setReasonCategory(e.target.value)}
              >
                <option value="">Choose…</option>
                {options.data.reasonCategories.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
              <p id="reasonCategory-hint" className="mt-1 text-sm text-ink-soft">
                Between 65% and 70%, only medical, on-duty and family-emergency reasons qualify. Between 60% and 65%,
                only medical reasons do.
              </p>
              <FieldError message={errors.reasonCategory} />
            </div>
            <div>
              <label htmlFor="reason" className="mb-1 block text-sm font-medium">
                What happened?
              </label>
              <textarea
                id="reason"
                rows={5}
                className={`field ${errors.reason ? "field-error" : ""}`}
                value={reason}
                maxLength={REASON_MAX}
                disabled={submitting}
                aria-invalid={Boolean(errors.reason)}
                placeholder="For example: I was admitted to hospital with typhoid from 3 to 14 August and missed 11 classes."
                onChange={(e) => setReason(e.target.value)}
              />
              <p className="mt-1 text-right text-xs text-ink-soft">
                {reason.trim().length} / {REASON_MAX}
              </p>
              <FieldError message={errors.reason} />
            </div>
          </fieldset>

          <fieldset>
            <legend className="mb-2 font-display text-2xl font-semibold">Supporting documents</legend>
            <p className="mb-3 text-sm text-ink-soft">
              Each document is checked automatically. A file that is not a real PDF, JPG or PNG stops the request.
            </p>
            <DocumentBuilder
              rows={documents}
              onChange={setDocuments}
              options={options.data}
              errors={errors.documents || []}
              disabled={submitting}
            />
            <FieldError message={errors.documentsGeneral} />
          </fieldset>

          {submitError && (
            <Notice tone="error" title="The request was not submitted">
              {submitError}
            </Notice>
          )}

          <div className="flex flex-wrap items-center gap-4 border-t border-rule pt-6">
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? "Submitting…" : "Submit request"}
            </button>
            <Link to="/requests" className="link">
              Cancel
            </Link>
          </div>
        </form>
      )}
    </div>
  );
}
