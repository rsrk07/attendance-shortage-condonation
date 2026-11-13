// Thin fetch wrapper for the Spring Boot API (always same-origin under /api).

const TOKEN_KEY = "condonation.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

let unauthorizedHandler = () => {};
export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

export async function api(path, { method = "GET", body, form } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(`/api${path}`, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, "The server can't be reached. Check that the backend is running, then try again.");
  }

  if (response.status === 401 && path !== "/auth/login") {
    unauthorizedHandler();
  }
  if (!response.ok) {
    let data = null;
    try {
      data = await response.json();
    } catch {
      // not JSON
    }
    throw new ApiError(
      response.status,
      data?.message || `The server answered with status ${response.status}.`,
      data?.fieldErrors,
    );
  }
  if (response.status === 204) return null;
  const type = response.headers.get("content-type") || "";
  return type.includes("application/json") ? response.json() : response.blob();
}

export function uploadFile(file) {
  const form = new FormData();
  form.append("file", file);
  return api("/files", { method: "POST", form });
}

/** Downloads a supporting document (needs the auth header, so it can't be a plain link). */
export async function downloadFile(fileId, fileName) {
  const blob = await api(`/files/${fileId}`);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName || "document";
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
