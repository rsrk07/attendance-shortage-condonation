import { useState } from "react";
import { Link } from "react-router";
import { ErrorNotice, Loading, PageHeader, StatusBadge } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { STATUS, formatDateTime, percent, rupees, slabLabel } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

const FILTERS = [{ value: "", label: "All" }, ...Object.entries(STATUS).map(([value, s]) => ({ value, label: s.label }))];

export default function AllRequestsPage() {
  const [filter, setFilter] = useState("");
  const requests = useApi(() => api(`/requests${filter ? `?status=${filter}` : ""}`), [filter], { interval: 15000 });

  return (
    <div>
      <PageHeader title="All requests" description="Every condonation request, newest first. Open one to see its route slip." />

      <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filter by status">
        {FILTERS.map((f) => (
          <button
            key={f.value || "all"}
            type="button"
            aria-pressed={filter === f.value}
            onClick={() => setFilter(f.value)}
            className={`rounded-full border px-3 py-1 text-sm font-medium ${
              filter === f.value ? "border-register bg-register text-white" : "border-rule bg-sheet text-ink hover:border-ink-soft"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {requests.loading ? (
        <Loading label="Loading requests" />
      ) : requests.error && !requests.data ? (
        <ErrorNotice error={requests.error} onRetry={requests.reload} />
      ) : requests.data.length === 0 ? (
        <p className="sheet p-6 text-ink-soft">No requests{filter ? " with this status" : " yet"}.</p>
      ) : (
        <div className="sheet overflow-x-auto">
          <table className="w-full min-w-[48rem] text-left text-sm">
            <thead className="border-b border-rule text-ink-soft">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Request</th>
                <th scope="col" className="px-4 py-3 font-medium">Student</th>
                <th scope="col" className="px-4 py-3 font-medium">Course</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Attendance</th>
                <th scope="col" className="px-4 py-3 font-medium">Slab and fee</th>
                <th scope="col" className="px-4 py-3 font-medium">Submitted</th>
                <th scope="col" className="px-4 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-rule">
              {requests.data.map((r) => (
                <tr key={r.requestId} className="hover:bg-paper">
                  <td className="px-4 py-3">
                    <Link to={`/requests/${r.requestId}`} className="link">
                      {r.requestId}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    {r.studentName}
                    <span className="block text-xs text-ink-soft">{r.studentId}</span>
                  </td>
                  <td className="px-4 py-3">
                    {r.courseCode}
                    <span className="block text-xs text-ink-soft">{r.courseName}</span>
                  </td>
                  <td className="px-4 py-3 text-right">{percent(r.attendancePercentage)}</td>
                  <td className="px-4 py-3">
                    {r.slab ? slabLabel(r.slab) : "–"}
                    {r.fee ? <span className="block text-xs text-ink-soft">{rupees(r.fee)}</span> : null}
                  </td>
                  <td className="px-4 py-3">{formatDateTime(r.submittedAt)}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={r.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
