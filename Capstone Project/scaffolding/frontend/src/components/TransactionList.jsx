/**
 * Renders a table of transactions, or an empty-state message.
 *
 * Props:
 *   transactions – array of { transactionId, createdAt, type, amount, status, description }
 */
export default function TransactionList({ transactions }) {
  if (transactions.length === 0) {
    return <p>No transactions yet.</p>;
  }

  return (
    <table className="transactions">
      <thead>
        <tr>
          <th>Date</th>
          <th>Type</th>
          <th>Amount</th>
          <th>Status</th>
          <th>Note</th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((t) => (
          <tr key={t.transactionId}>
            <td>{t.createdAt}</td>
            <td>{t.type}</td>
            <td>{t.amount}</td>
            <td>{t.status}</td>
            <td>{t.description}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
