import { apiFetch } from "./apiClient.js";

export const submitTransaction = (payload) =>
  apiFetch("/api/v1/transactions", {
    method: "POST",
    body: JSON.stringify(payload),
  });
