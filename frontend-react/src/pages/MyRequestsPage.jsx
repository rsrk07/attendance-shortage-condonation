import { Link, useLocation } from "react-router";
import { ErrorNotice, Loading, Notice, PageHeader, StatusBadge } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { formatDateTime, percent, rupees, slabLabel } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

export default function MyRequestsPage() {
  const location = useLocation();
  const requests = useApi(() => api("/requests"), [], { interval: 10000 });
  const courses = useApi(() => api("/meta/my-courses"), []);

  return (
    <div>
      <PageHeader title="My requests" description="Your condonation requests and where each one is right now.">
        <Link to="/requests/new" className="btn-primary">
          New request
        </Link>
      </PageHeader>

      {location.state?.flash && (
        <Notice tone="success" className="mb-6">
          {location.state.flash}
        </Notice>
      )}

      <section className="mb-10">
        <h2 className="mb-3 text-2xl">Your attendance</h2>
        {courses.loading ? (
          <Loading label="Loading your courses" />
        ) : courses.error ? (
          <ErrorNotice error={courses.error} onRetry={courses.reload} />
        ) : (
          <ul className="sheet divide-y divide-rule">
            {courses.data.map((c) => (
              <AttendanceRow key={c.courseCode} course={c} />
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-2xl">Requests</h2>
        {requests.loading ? (
          <Loading label="Loading your requests" />
        ) : requests.error ? (
          <ErrorNotice error={requests.error} onRetry={requests.reload} />
        ) : requests.data.length === 0 ? (
          <div className="sheet p-6">
            <p className="font-semibold">You haven't applied for condonation yet.</p>
            <p className="mt-1 text-sm text-ink-soft">
              Courses marked short above can be condoned. Start a request and attach your medical certificate, OD
              letter or other proof.
            </p>
            <Link to="/requests/new" className="btn-primary mt-4">
              Start a request
            </Link>
          </div>
        ) : (
          <ul className="sheet divide-y divide-rule">
            {requests.data.map((r) => (
              <li key={r.requestId}>
                <Link
                  to={`/requests/${r.requestId}`}
                  className="grid gap-2 px-5 py-4 hover:bg-paper sm:grid-cols-[7rem_1fr_auto] sm:items-center"
                >
                  <span className="font-semibold text-register">{r.requestId}</span>
                  <span>
                    <span className="font-medium">
                      {r.courseCode} {r.courseName}
                    </span>
                    <span className="block text-sm text-ink-soft">
                      Submitted {formatDateTime(r.submittedAt)}
                      {r.attendancePercentage != null && `, attendance ${percent(r.attendancePercentage)}`}
                      {r.slab && `, ${slabLabel(r.slab)}`}
                      {r.fee ? `, fee ${rupees(r.fee)}` : ""}
                    </span>
                  </span>
                  <StatusBadge status={r.status} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

/** One course with a bar against the 75% requirement. */
function AttendanceRow({ course }) {
  const pct = Number(course.percentage);
  return (
    <li className="grid gap-2 px-5 py-4 sm:grid-cols-[1fr_16rem_6rem] sm:items-center">
      <div>
        <p className="font-medium">
          {course.courseCode} {course.courseName}
        </p>
        <p className="text-sm text-ink-soft">
          {course.attended} of {course.conducted} classes attended
        </p>
      </div>
      <div className="relative h-2.5 rounded-full bg-paper" aria-hidden="true">
        <div
          className={`h-full rounded-full ${course.shortage ? "bg-pending" : "bg-approve"}`}
          style={{ width: `${Math.min(pct, 100)}%` }}
        />
        <span className="absolute -top-1 h-4.5 w-0.5 bg-ink" style={{ left: "75%" }} title="75% required" />
      </div>
      <p className={`text-right font-semibold ${course.shortage ? "text-pending" : "text-approve"}`}>
        {percent(pct)}
        <span className="block text-xs font-normal text-ink-soft">
          {course.hasOpenRequest ? "Request open" : course.shortage ? "Short" : "Meets 75%"}
        </span>
      </p>
    </li>
  );
}
