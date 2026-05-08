import { useState } from "react";

/**
 * Transaction submission form. Manages field state internally; delegates
 * submission logic to the parent via `onSubmit`.
 *
 * Props:
 *   accounts   – array of { accountId, accountType, currency, balance }
 *   onSubmit   – async (formData) => void  called with validated field values
 *   submitting – boolean  true while the parent is awaiting the API call
 *   error      – string | null  error message to display
 */
export default function TransactionForm({ accounts, onSubmit, submitting, error }) {
  const [accountId, setAccountId] = useState(accounts[0]?.accountId ?? "");
  const [type, setType] = useState("DEPOSIT");
  const [amount, setAmount] = useState("");
  const [counterparty, setCounterparty] = useState("");
  const [description, setDescription] = useState("");

  const counterpartyRequired = type === "TRANSFER_OUT";

  function handleSubmit(e) {
    e.preventDefault();
    onSubmit({
      accountId,
      type,
      amount: Number(amount),
      counterparty: counterpartyRequired ? counterparty : null,
      description: description || null,
    });
  }

  return (
    <form onSubmit={handleSubmit} className="tx-form">
      <h1>New transaction</h1>

      <label>
        Account
        <select value={accountId} onChange={(e) => setAccountId(e.target.value)} required>
          {accounts.map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.accountType} ({a.currency} {a.balance})
            </option>
          ))}
        </select>
      </label>

      <label>
        Type
        <select value={type} onChange={(e) => setType(e.target.value)} required>
          <option value="DEPOSIT">Deposit</option>
          <option value="WITHDRAWAL">Withdrawal</option>
          <option value="TRANSFER_OUT">Transfer</option>
        </select>
      </label>

      <label>
        Amount
        <input
          type="number" step="0.01" min="0.01" required
          value={amount} onChange={(e) => setAmount(e.target.value)}
        />
      </label>

      {counterpartyRequired && (
        <label>
          Counterparty account ID
          <input
            type="text" required
            value={counterparty}
            onChange={(e) => setCounterparty(e.target.value)}
          />
        </label>
      )}

      <label>
        Description (optional)
        <input
          type="text" maxLength={255}
          value={description} onChange={(e) => setDescription(e.target.value)}
        />
      </label>

      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? "Submitting…" : "Submit"}
      </button>
    </form>
  );
}
