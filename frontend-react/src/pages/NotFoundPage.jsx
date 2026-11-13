import { Link } from "react-router";
import { homeFor, useAuth } from "../lib/auth.jsx";

export default function NotFoundPage() {
  const { user } = useAuth();
  return (
    <div className="mx-auto max-w-lg py-20 text-center">
      <h1 className="text-5xl">Page not found</h1>
      <p className="mt-3 text-ink-soft">The address may be mistyped, or the page was moved.</p>
      <Link to={user ? homeFor(user) : "/login"} className="btn-primary mt-6">
        Go to the start page
      </Link>
    </div>
  );
}
