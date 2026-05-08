import { BrowserRouter, Routes, Route } from "react-router-dom";
import AppLayout from "./routes/AppLayout";
import AccountsPage from "./routes/AccountsPage";
import AccountDetailPage from "./routes/AccountDetailPage";
import NewTransactionPage from "./routes/NewTransactionPage";
import AdminUsersPage from "./routes/AdminUsersPage";
import NotFoundPage from "./routes/NotFoundPage";

/**
 * Application routes — flat, no auth providers or guards.
 *
 * Spring Security gates access at the network layer via the BFF.
 * If a route's API call returns 401, apiFetch redirects to the
 * BFF's login endpoint automatically.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<AccountsPage />} />
          <Route path="/accounts/:accountId" element={<AccountDetailPage />} />
          <Route path="/transactions/new" element={<NewTransactionPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
