import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import DocumentList from "../components/DocumentList.jsx";
import RouteSlip from "../components/RouteSlip.jsx";
import { ErrorNotice, Facts, Loading, Notice, StatusBadge } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { useAuth } from "../lib/auth.jsx";
import {
  FINAL_STATUSES,
  REJECTION_STAGES,
  formatDateTime,
  formatTime,
  percent,
  rupees,
  slabLabel,
} from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

export default function RequestDetailPage() {
  const { requestId } = useParams();
  const location = useLocation();
  const { user } = useAuth();
  const [polling, setPolling] = useState(true);
  const status = useApi(() => api(`/requests/${requestId}/status`), [requestId], { interval: 4000, active: polling });
  const finished = Boolean(
    status.data && FINAL_STATUSES.includes(status.data.status) && status.data.processState !== "ACTIVE",
  );

  // Poll every 4 seconds until the process has finished
  useEffect(() => {
    setPolling(!finished);
  }, [finished]);

  if (status.loading) return <Loading label="Loading the request" />;
  if (!status.data) return <ErrorNotice error={status.error} onRetry={status.reload} />;

  const { request, steps } = status.data;
  const back = user.role === "STUDENT" ? "/requests" : "/all";
  const feeDue = user.role === "STUDENT" && steps.some((s) => s.key === "FEE" && s.state === "ACTIVE");

  return (
    <div>
      <Link to={back} className="link text-sm">
        {user.role === "STUDENT" ? "My requests" : "All requests"}
      </Link>

      <div className="mb-6 mt-2 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl">
            {request.requestId}: {request.courseCode} {request.courseName}
          </h1>
          <p className="mt-1 text-ink-soft">
            {request.studentName} ({request.studentId}), submitted {formatDateTime(request.submittedAt)}
          </p>
        </div>
        <div className="text-right">
          <StatusBadge status={status.data.status} />
          <LiveIndicator live={status.data.live} finished={finished} refreshedAt={status.refreshedAt} />
        </div>
      </div>

      {location.state?.flash && (
        <Notice tone="success" className="mb-6">
          {location.state.flash}
        </Notice>
      )}
      {status.data.message && (
        <Notice tone="info" className="mb-6">
          {status.data.message}
        </Notice>
      )}
      {status.error && (
        <Notice tone="warning" className="mb-6">
          Live updates paused: {status.error.message}
        </Notice>
      )}
      {feeDue && (
        <Notice tone="warning" title="Your request is approved. Pay the fee to finish." className="mb-6">
          <Link to="/inbox" className="link">
            Pay {rupees(request.fee)} now
          </Link>
        </Notice>
      )}
      {request.status === "REJECTED" && request.rejectionReason && (
        <Notice tone="error" title="Why the request was not condoned" className="mb-6">
          <p>{request.rejectionReason}</p>
          {request.rejectionStage && (
            <p className="mt-1 text-ink-soft">Stopped at: {REJECTION_STAGES[request.rejectionStage] || request.rejectionStage}</p>
          )}
        </Notice>
      )}

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <section className="sheet p-6" aria-live="polite">
          <h2 className="mb-5 text-2xl">Route slip</h2>
          <RouteSlip steps={steps} approvals={request.approvals} />
        </section>

        <div className="space-y-8">
          <section className="sheet p-6">
            <h2 className="mb-4 text-2xl">Request</h2>
            <Facts
              items={[
                { label: "Attendance", value: percent(request.attendancePercentage) },
                { label: "Shortfall", value: request.shortfall != null ? percent(request.shortfall) : "–" },
                { label: "Slab", value: slabLabel(request.slab) },
                { label: "Fee", value: request.fee ? rupees(request.fee) : "–" },
                { label: "Reason", value: request.reasonCategoryLabel },
                request.paymentReference && {
                  label: "Payment",
                  value: `${rupees(request.amountPaid)} via ${request.paymentMode}, ref. ${request.paymentReference}`,
                },
                request.sisReference && { label: "SIS reference", value: request.sisReference },
              ]}
            />
            <p className="mt-4 whitespace-pre-line border-t border-rule pt-4 text-sm">{request.reason}</p>
          </section>

          <section className="sheet p-6">
            <h2 className="mb-4 text-2xl">Documents</h2>
            <DocumentList documents={request.documents} />
          </section>
        </div>
      </div>
    </div>
  );
}

function LiveIndicator({ live, finished, refreshedAt }) {
  if (finished) return <p className="mt-1 text-xs text-ink-soft">Process finished</p>;
  return (
    <p className="mt-1 flex items-center justify-end gap-1.5 text-xs text-ink-soft">
      <span className={`h-1.5 w-1.5 rounded-full ${live ? "bg-approve" : "bg-ink-soft"}`} aria-hidden="true" />
      {live ? "Live" : "Waiting for Camunda"}
      {refreshedAt ? `, updated ${formatTime(refreshedAt)}` : ""}
    </p>
  );
}
