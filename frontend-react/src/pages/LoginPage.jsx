import { useRef, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { Notice } from "../components/ui.jsx";
import { api } from "../lib/api.js";
import { homeFor, useAuth } from "../lib/auth.jsx";
import { useApi } from "../lib/useApi.js";

const ROUTE = ["Faculty Advisor", "Academic Advisor", "Head of Department"];

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const passwordRef = useRef(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const demoUsers = useApi(() => api("/auth/demo-users"), []);

  if (user) return <Navigate to={homeFor(user)} replace />;

  async function submit(event) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setError("Enter your username and password.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const loggedIn = await login(username.trim(), password);
      navigate(location.state?.from || homeFor(loggedIn), { replace: true });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function pick(demo) {
    setUsername(demo.username);
    setError(null);
    passwordRef.current?.focus();
  }

  const students = demoUsers.data?.filter((u) => u.role === "STUDENT") || [];
  const staff = demoUsers.data?.filter((u) => u.role !== "STUDENT") || [];

  return (
    <div className="mx-auto grid min-h-screen max-w-5xl content-center gap-10 px-4 py-10 md:grid-cols-[1fr_24rem] md:px-6">
      <section className="self-center">
        <h1 className="text-5xl sm:text-6xl">Attendance condonation</h1>
        <p className="mt-4 max-w-lg text-lg text-ink-soft">
          If your attendance in a course is below 75%, apply here with your supporting documents. Your request
          is checked automatically, then reviewed in this order:
        </p>
        <ol className="mt-6 flex flex-wrap items-center gap-2 text-sm font-semibold">
          {ROUTE.map((office, i) => (
            <li key={office} className="flex items-center gap-2">
              <span className="rounded border-2 border-ink/70 px-2.5 py-1 font-display text-base uppercase tracking-wider">
                {office}
              </span>
              {i < ROUTE.length - 1 && <span className="text-ink-soft" aria-hidden="true">then</span>}
            </li>
          ))}
        </ol>
        <p className="mt-6 max-w-lg text-sm text-ink-soft">
          Once all three approve, you pay the condonation fee and your record in the student information system
          is updated. You can follow every step live.
        </p>
      </section>

      <section className="sheet p-6 shadow-sm">
        <h2 className="text-3xl">Log in</h2>
        {location.state?.expired && (
          <Notice tone="warning" className="mt-4">
            Your session ended. Log in again to continue.
          </Notice>
        )}
        <form onSubmit={submit} className="mt-4 space-y-4" noValidate>
          <div>
            <label htmlFor="username" className="mb-1 block text-sm font-medium">
              Username or register number
            </label>
            <input
              id="username"
              className="field"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="password" className="mb-1 block text-sm font-medium">
              Password
            </label>
            <input
              id="password"
              ref={passwordRef}
              type="password"
              className="field"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && <p className="text-sm text-reject" role="alert">{error}</p>}
          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? "Logging in…" : "Log in"}
          </button>
        </form>

        <div className="mt-6 border-t border-rule pt-4">
          <p className="text-sm font-semibold">Demo accounts</p>
          <p className="text-sm text-ink-soft">
            Pick one to fill in the username. The password is <span className="font-semibold">demo123</span> unless
            it was changed in <code>.env</code>.
          </p>
          {demoUsers.error && <p className="mt-2 text-sm text-reject">{demoUsers.error.message}</p>}
          <div className="mt-3 space-y-3">
            <DemoGroup title="Students" users={students} onPick={pick} showId />
            <DemoGroup title="Staff" users={staff} onPick={pick} />
          </div>
        </div>
      </section>
    </div>
  );
}

function DemoGroup({ title, users, onPick, showId }) {
  if (!users.length) return null;
  return (
    <div>
      <p className="mb-1 text-xs font-semibold text-ink-soft">{title}</p>
      <ul className="flex flex-wrap gap-2">
        {users.map((u) => (
          <li key={u.username}>
            <button
              type="button"
              className="rounded border border-rule px-2.5 py-1 text-left text-sm hover:border-register hover:text-register"
              onClick={() => onPick(u)}
              title={showId ? u.username : u.roleTitle}
            >
              {showId ? u.displayName : `${u.username} (${u.roleTitle})`}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
