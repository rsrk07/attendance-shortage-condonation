import { Navigate, Route, Routes } from "react-router";
import Layout from "./components/Layout.jsx";
import { RequireAuth, homeFor, useAuth } from "./lib/auth.jsx";
import AdminPage from "./pages/AdminPage.jsx";
import AllRequestsPage from "./pages/AllRequestsPage.jsx";
import InboxPage from "./pages/InboxPage.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import MyRequestsPage from "./pages/MyRequestsPage.jsx";
import NewRequestPage from "./pages/NewRequestPage.jsx";
import NotFoundPage from "./pages/NotFoundPage.jsx";
import RequestDetailPage from "./pages/RequestDetailPage.jsx";
import TaskPage from "./pages/TaskPage.jsx";

const STAFF = ["FA", "AA", "HOD", "ADMIN"];

function Home() {
  const { user } = useAuth();
  return <Navigate to={user ? homeFor(user) : "/login"} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        <Route path="requests" element={<RequireAuth roles={["STUDENT"]}><MyRequestsPage /></RequireAuth>} />
        <Route path="requests/new" element={<RequireAuth roles={["STUDENT"]}><NewRequestPage /></RequireAuth>} />
        <Route path="requests/:requestId" element={<RequestDetailPage />} />
        <Route path="inbox" element={<InboxPage />} />
        <Route path="tasks/:taskKey" element={<TaskPage />} />
        <Route path="all" element={<RequireAuth roles={STAFF}><AllRequestsPage /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth roles={["ADMIN"]}><AdminPage /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
