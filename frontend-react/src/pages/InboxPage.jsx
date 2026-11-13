import { Link, useLocation } from "react-router";
import { ErrorNotice, Loading, Notice, PageHeader } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { useAuth } from "../lib/auth.jsx";
import { ROLE_TITLES, formatDateTime } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

export function taskTitle(task) {
  switch (task.kind) {
    case "REVIEW":
      return `Review as ${ROLE_TITLES[task.approverRole] || task.approverRole}`;
    case "PAY_FEE":
      return "Pay the condonation fee";
    case "ADMIN_FIX":
      return "Fix the SIS update";
    default:
      return task.name;
  }
}

const DESCRIPTIONS = {
  STUDENT: "Requests that are approved and waiting for your fee payment.",
  FA: "Requests waiting for your review as Faculty Advisor. They reach the Academic Advisor after you approve.",
  AA: "Requests the Faculty Advisor approved. They reach the HOD after you approve.",
  HOD: "Requests approved by the Faculty Advisor and the Academic Advisor. Yours is the final decision.",
  ADMIN: "Student information system updates that failed and need a fix before they are retried.",
};

export default function InboxPage() {
  const { user } = useAuth();
  const location = useLocation();
  const tasks = useApi(() => api("/tasks"), [], { interval: 8000 });

  return (
    <div>
      <PageHeader title={user.role === "STUDENT" ? "Fee payments" : "Inbox"} description={DESCRIPTIONS[user.role]}>
        <button type="button" className="btn-quiet text-sm" onClick={tasks.reload}>
          Refresh
        </button>
      </PageHeader>

      {location.state?.flash && (
        <Notice tone="success" className="mb-6">
          {location.state.flash}
        </Notice>
      )}

      {tasks.loading ? (
        <Loading label="Loading your inbox" />
      ) : tasks.error && !tasks.data ? (
        <ErrorNotice error={tasks.error} onRetry={tasks.reload} />
      ) : tasks.data.length === 0 ? (
        <div className="sheet p-6">
          <p className="font-semibold">Nothing is waiting for you.</p>
          <p className="mt-1 text-sm text-ink-soft">
            New tasks appear here on their own, usually a few seconds after the previous step finishes.
          </p>
        </div>
      ) : (
        <ul className="sheet divide-y divide-rule">
          {tasks.data.map((task) => (
            <li key={task.taskKey}>
              <Link
                to={`/tasks/${task.taskKey}`}
                className="grid gap-1 px-5 py-4 hover:bg-paper sm:grid-cols-[1fr_auto] sm:items-center"
              >
                <span>
                  <span className="font-semibold">{taskTitle(task)}</span>
                  <span className="block text-sm text-ink-soft">
                    {task.requestId ?? "Request"}
                    {task.studentName ? `, ${task.studentName} (${task.studentId})` : ""}
                    {task.courseCode ? `, ${task.courseCode}` : ""}
                  </span>
                </span>
                <span className="text-sm text-ink-soft sm:text-right">
                  {formatDateTime(task.createdAt)}
                  <span className="block">
                    {task.assignee ? (task.assignee === user.username ? "Claimed by you" : `Claimed by ${task.assignee}`) : "Not claimed"}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
