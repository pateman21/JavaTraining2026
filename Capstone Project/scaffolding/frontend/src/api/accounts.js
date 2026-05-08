import { apiFetch } from "./apiClient.js";

export const listAccounts = () => apiFetch("/api/v1/accounts");
export const getAccount = (id) => apiFetch(`/api/v1/accounts/${id}`);
export const getTransactions = (id) =>
  apiFetch(`/api/v1/accounts/${id}/transactions`);
