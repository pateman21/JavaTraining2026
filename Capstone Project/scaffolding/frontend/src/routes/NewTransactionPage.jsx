import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../api/accounts.js";
import { submitTransaction } from "../api/transactions.js";
import TransactionForm from "../components/TransactionForm.jsx";

/**
 * Submit a transaction. Loads accounts, delegates form rendering and field
 * state to TransactionForm, handles the API call and navigation here.
 */
export default function NewTransactionPage() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    listAccounts().then(setAccounts);
  }, []);

  async function handleSubmit(formData) {
    setError(null);
    setSubmitting(true);
    try {
      await submitTransaction(formData);
      navigate(`/accounts/${formData.accountId}`);
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!accounts.length) return <p>Loading accounts…</p>;

  return (
    <TransactionForm
      accounts={accounts}
      onSubmit={handleSubmit}
      submitting={submitting}
      error={error}
    />
  );
}
