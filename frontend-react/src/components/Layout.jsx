import { NavLink, Outlet } from "react-router";
import { api } from "../lib/api.js";
import { useAuth } from "../lib/auth.jsx";
import { useApi } from "../lib/useApi.js";

const NAV = {
  STUDENT: [
    { to: "/requests", label: "My requests", end: true },
    { to: "/requests/new", label: "New request" },
    { to: "/inbox", label: "Fee payments", inbox: true },
  ],
  FA: [
    { to: "/inbox", label: "Inbox", inbox: true },
    { to: "/all", label: "All requests" },
  ],
  AA: [
    { to: "/inbox", label: "Inbox", inbox: true },
    { to: "/all", label: "All requests" },
  ],
  HOD: [
    { to: "/inbox", label: "Inbox", inbox: true },
    { to: "/all", label: "All requests" },
  ],
  ADMIN: [
    { to: "/inbox", label: "Inbox", inbox: true },
    { to: "/all", label: "All requests" },
    { to: "/admin", label: "Settings" },
  ],
};

export default function Layout() {
  const { user, logout } = useAuth();
  const tasks = useApi(() => api("/tasks"), [user.username], { interval: 15000 });
  const openTasks = tasks.data?.length || 0;

  return (
    <div className="min-h-screen">
      <header className="border-b border-rule bg-sheet">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-2 px-4 py-3 sm:px-6">
          <div className="mr-auto">
            <p className="font-display text-2xl font-semibold leading-none">Attendance condonation</p>
            <p className="text-sm text-ink-soft">For courses below 75% attendance</p>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <div className="sm:text-right">
              <p className="font-semibold">{user.displayName}</p>
              <p className="text-ink-soft">{user.roleTitle}{user.studentId ? `, ${user.studentId}` : ""}</p>
            </div>
            <button type="button" className="btn-quiet px-3 py-1.5 text-sm" onClick={logout}>
              Log out
            </button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-2 sm:px-4" aria-label="Main">
          {NAV[user.role].map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-2 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium ${
                  isActive ? "border-register text-register" : "border-transparent text-ink-soft hover:text-ink"
                }`
              }
            >
              {item.label}
              {item.inbox && openTasks > 0 && (
                <span className="rounded-full bg-register px-2 text-xs font-semibold leading-5 text-white">
                  {openTasks}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
