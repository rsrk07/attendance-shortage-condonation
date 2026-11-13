import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { api, onUnauthorized, setToken } from "./api.js";

const USER_KEY = "condonation.user";
const AuthContext = createContext(null);

function storedUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(storedUser);
  const navigate = useNavigate();

  const clear = useCallback(() => {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  useEffect(() => {
    onUnauthorized(() => {
      clear();
      navigate("/login", { replace: true, state: { expired: true } });
    });
  }, [clear, navigate]);

  const login = useCallback(async (username, password) => {
    const result = await api("/auth/login", { method: "POST", body: { username, password } });
    setToken(result.token);
    localStorage.setItem(USER_KEY, JSON.stringify(result.user));
    setUser(result.user);
    return result.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } catch {
      // the session may already be gone
    }
    clear();
    navigate("/login", { replace: true });
  }, [clear, navigate]);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

export function homeFor(user) {
  return user?.role === "STUDENT" ? "/requests" : "/inbox";
}

export function RequireAuth({ roles, children }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user)} replace />;
  return children;
}
