export default function ErrorBanner({ error }) {
  if (!error) return null;
  return (
    <div className="error-banner" role="alert">
      <strong>Error: </strong>
      <span>{error.message || String(error)}</span>
    </div>
  );
}
