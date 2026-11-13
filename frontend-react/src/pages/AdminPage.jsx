import { useState } from "react";
import { ErrorNotice, Loading, Notice, PageHeader } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { formatDateTime, percent, rupees, slabLabel } from "../lib/format.js";
import { useApi } from "../lib/useApi.js";

export default function AdminPage() {
  const settings = useApi(() => api("/admin/settings"), []);
  const sis = useApi(() => api("/admin/sis-records"), [], { interval: 15000 });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);

  async function toggle(value) {
    setSaving(true);
    setMessage(null);
    try {
      await api("/admin/settings", { method: "PUT", body: { simulateSisFailure: value } });
      await settings.reload();
      setMessage(value ? "New requests will hit a simulated SIS failure." : "New requests will update the SIS normally.");
    } catch (e) {
      setMessage(e.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-10">
      <PageHeader title="Settings" description="Demo controls and the simulated student information system (SIS)." />

      <section className="sheet max-w-3xl p-6">
        <h2 className="text-2xl">Simulated SIS failure</h2>
        <p className="mt-1 text-sm text-ink-soft">
          When this is on, every request submitted afterwards fails at the SIS update. The failure lands in your
          inbox as a “Fix the SIS update” task; when you fix it, the update is retried and the request completes.
          Requests already in progress are not affected.
        </p>
        {settings.loading ? (
          <Loading />
        ) : settings.error ? (
          <ErrorNotice error={settings.error} onRetry={settings.reload} />
        ) : (
          <label className="mt-4 flex items-center gap-3">
            <input
              type="checkbox"
              className="h-5 w-5 accent-register"
              checked={settings.data.simulateSisFailure}
              disabled={saving}
              onChange={(e) => toggle(e.target.checked)}
            />
            <span className="font-medium">Simulate an SIS failure for new requests</span>
          </label>
        )}
        {message && (
          <Notice tone="info" className="mt-4">
            {message}
          </Notice>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-2xl">SIS records</h2>
        {sis.loading ? (
          <Loading />
        ) : sis.error && !sis.data ? (
          <ErrorNotice error={sis.error} onRetry={sis.reload} />
        ) : sis.data.length === 0 ? (
          <p className="sheet p-6 text-ink-soft">No condonations have reached the SIS yet.</p>
        ) : (
          <div className="sheet overflow-x-auto">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="border-b border-rule text-ink-soft">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">SIS reference</th>
                  <th scope="col" className="px-4 py-3 font-medium">Student</th>
                  <th scope="col" className="px-4 py-3 font-medium">Course</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Attendance</th>
                  <th scope="col" className="px-4 py-3 font-medium">Slab</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Fee paid</th>
                  <th scope="col" className="px-4 py-3 font-medium">Exam eligible</th>
                  <th scope="col" className="px-4 py-3 font-medium">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-rule">
                {sis.data.map((r) => (
                  <tr key={`${r.studentId}-${r.courseCode}`}>
                    <td className="px-4 py-3 font-medium">{r.sisReference}</td>
                    <td className="px-4 py-3">{r.studentId}</td>
                    <td className="px-4 py-3">{r.courseCode}</td>
                    <td className="px-4 py-3 text-right">{percent(r.attendancePercentage)}</td>
                    <td className="px-4 py-3">{slabLabel(r.slab)}</td>
                    <td className="px-4 py-3 text-right">{rupees(r.feePaid)}</td>
                    <td className="px-4 py-3">{r.examEligible ? "Yes" : "No"}</td>
                    <td className="px-4 py-3">{formatDateTime(r.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
