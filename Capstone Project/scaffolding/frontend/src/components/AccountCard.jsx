import { Link } from "react-router-dom";

/**
 * Renders a single account row as a linked list item.
 *
 * Props:
 *   account – { accountId, accountType, currency, balance }
 */
export default function AccountCard({ account }) {
  return (
    <li key={account.accountId}>
      <Link to={`/accounts/${account.accountId}`}>
        <strong>{account.accountType}</strong>
        <span> — {account.currency} {account.balance}</span>
      </Link>
    </li>
  );
}
