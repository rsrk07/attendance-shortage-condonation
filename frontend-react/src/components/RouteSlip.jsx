import { formatDateTime } from "../lib/format.js";
import Stamp from "./Stamp.jsx";

const APPROVER_STEPS = { FA: "FA", AA: "AA", HOD: "HOD" };

const MARKERS = {
  COMPLETED: "border-approve bg-approve text-white",
  ACTIVE: "border-register bg-sheet text-register slip-active",
  FAILED: "border-reject bg-reject text-white",
  INCIDENT: "border-reject bg-sheet text-reject slip-active",
  SKIPPED: "border-rule bg-paper text-ink-soft",
  PENDING: "border-rule bg-sheet text-ink-soft",
};

const STATE_WORDS = {
  COMPLETED: "Done",
  ACTIVE: "In progress",
  FAILED: "Stopped here",
  INCIDENT: "Needs attention",
  SKIPPED: "Not needed",
  PENDING: "Waiting",
};

function Marker({ state, index }) {
  const symbol = state === "COMPLETED" ? "✓" : state === "FAILED" ? "✕" : state === "INCIDENT" ? "!" : index + 1;
  return (
    <span
      className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-sm font-bold ${MARKERS[state]}`}
      aria-hidden="true"
    >
      {symbol}
    </span>
  );
}

/**
 * The route slip: every office the request passes through, in order, with the
 * approvers' stamps. Built from GET /api/requests/{id}/status.
 */
export default function RouteSlip({ steps, approvals = [] }) {
  return (
    <ol className="relative">
      {steps.map((step, index) => {
        const role = APPROVER_STEPS[step.key];
        const decision = role ? [...approvals].reverse().find((a) => a.role === role) : null;
        const showStamp =
          (role && decision && (step.state === "COMPLETED" || step.state === "FAILED")) ||
          (step.key === "OUTCOME" && (step.state === "COMPLETED" || step.state === "FAILED"));
        const last = index === steps.length - 1;
        const muted = step.state === "SKIPPED" || step.state === "PENDING";
        return (
          <li key={step.key} className="relative flex gap-4 pb-6 last:pb-0">
            {!last && (
              <span
                className={`absolute left-4 top-8 -ml-px h-[calc(100%-2rem)] w-0.5 ${
                  step.state === "COMPLETED" ? "bg-approve/50" : "bg-rule"
                }`}
                aria-hidden="true"
              />
            )}
            <Marker state={step.state} index={index} />
            <div className="min-w-0 flex-1 pt-1">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4">
                <p className={`font-semibold ${muted ? "text-ink-soft" : ""}`}>{step.label}</p>
                <p className="text-xs text-ink-soft">
                  <span className="sr-only">Status: </span>
                  {STATE_WORDS[step.state]}
                  {step.completedAt && step.state !== "ACTIVE" ? `, ${formatDateTime(step.completedAt)}` : ""}
                </p>
              </div>
              {step.detail && !showStamp && (
                <p className={`mt-0.5 text-sm ${step.state === "FAILED" ? "text-reject" : "text-ink-soft"}`}>
                  {step.detail}
                </p>
              )}
              {showStamp && role && (
                <div className="mt-2 flex flex-wrap items-center gap-4">
                  <Stamp
                    approved={decision.approved}
                    title={decision.approved ? `${role} approved` : `${role} rejected`}
                    name={decision.reviewerName}
                    date={decision.decidedAt}
                    rotate={index % 2 === 0 ? -4 : 3}
                  />
                  {decision.remarks && <p className="max-w-md text-sm italic text-ink-soft">“{decision.remarks}”</p>}
                </div>
              )}
              {showStamp && step.key === "OUTCOME" && (
                <div className="mt-3">
                  <Stamp
                    approved={step.state === "COMPLETED"}
                    title={step.state === "COMPLETED" ? "Condoned" : "Not condoned"}
                    date={step.completedAt}
                    size="lg"
                    rotate={-6}
                  />
                  {step.detail && <p className="mt-3 text-sm text-reject">{step.detail}</p>}
                </div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
