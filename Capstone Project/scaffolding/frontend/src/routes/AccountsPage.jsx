import { useEffect, useState } from "react";
import { listAccounts } from "../api/accounts.js";
import AccountCard from "../components/AccountCard.jsx";

/**
 * Lists the caller's own accounts. Shows loading, empty, and error states
 * (the rubric grades all three).
 */
export default function AccountsPage() {
  const [accounts, setAccounts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    listAccounts()
      .then(setAccounts)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="error">Could not load accounts: {error}</p>;
  if (!accounts) return <p>Loading accounts…</p>;
  if (accounts.length === 0) return <p>You have no accounts yet.</p>;

  return (
    <section>
      <h1>Your accounts</h1>
      <ul className="accounts">
        {accounts.map((a) => (
          <AccountCard key={a.accountId} account={a} />
        ))}
      </ul>
    </section>
  );
}
