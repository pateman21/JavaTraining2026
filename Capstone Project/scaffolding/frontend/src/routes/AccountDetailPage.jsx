import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getAccount, getTransactions } from "../api/accounts.js";
import TransactionList from "../components/TransactionList.jsx";

/**
 * Account detail + transaction history. The :accountId param is always a
 * string — never assume it's a number even if the backend uses numeric IDs.
 */
export default function AccountDetailPage() {
  const { accountId } = useParams();
  const [account, setAccount] = useState(null);
  const [transactions, setTransactions] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getAccount(accountId), getTransactions(accountId)])
      .then(([a, t]) => { setAccount(a); setTransactions(t); })
      .catch((e) => setError(e.message));
  }, [accountId]);

  if (error) return <p className="error">{error}</p>;
  if (!account || !transactions) return <p>Loading…</p>;

  return (
    <section>
      <h1>{account.accountType} — {account.currency} {account.balance}</h1>
      <p>
        <Link to="/transactions/new">New transaction</Link>
      </p>
      <h2>Transactions</h2>
      <TransactionList transactions={transactions} />
    </section>
  );
}
