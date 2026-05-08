/**
 * API client — the entire auth surface in JavaScript.
 *
 * Same-origin: all requests go through the Vite proxy to the BFF on :8080.
 * The browser sends the JSESSIONID cookie automatically (same-origin).
 * No tokens, no localStorage, no oidc-client-ts.
 *
 * CSRF: Spring sets an XSRF-TOKEN cookie (JS-readable). On mutations
 * (POST, PUT, DELETE, PATCH), we read it and send X-XSRF-TOKEN.
 *
 * On 401: throw ApiError so callers (useMe etc.) can set user=null and
 * render sign-in options. No redirect — avoids infinite reload loops.
 */

function readCsrfToken() {
  return document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch(path, init = {}) {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers ?? {});

  // Default Content-Type for bodies
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }

  // Attach CSRF token for mutating requests
  if (method !== "GET" && method !== "HEAD") {
    const csrf = readCsrfToken();
    if (csrf) headers.set("X-XSRF-TOKEN", csrf);
  }

  const res = await fetch(path, {
    ...init,
    headers,
    credentials: "same-origin",
  });

  if (res.status === 401) {
    throw new ApiError(401, { detail: "Unauthorized" });
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }

  return res.status === 204 ? undefined : res.json();
}
