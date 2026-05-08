import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiClient.js";

/**
 * Hook to fetch the current user's profile.
 *
 * Returns { user, loading }:
 *   - user is null if not signed in (401 bounces to login via apiFetch)
 *   - loading is true until the first response settles
 *
 * Used by AppLayout to show "Sign in" vs "Sign out (alice)" in the header,
 * and by AdminUsersPage to conditionally show the admin nav link.
 */
export function useMe() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch("/api/v1/users/me")
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  return { user, loading };
}
