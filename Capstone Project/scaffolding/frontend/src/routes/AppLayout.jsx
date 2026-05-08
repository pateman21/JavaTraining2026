import { Outlet, NavLink } from "react-router-dom";
import { useMe } from "../hooks/useMe";
import { apiFetch } from "../api/apiClient";

/** Google G logo as inline SVG */
function GoogleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908C16.658 14.013 17.64 11.705 17.64 9.2z" fill="#4285F4"/>
      <path d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 009 18z" fill="#34A853"/>
      <path d="M3.964 10.707A5.41 5.41 0 013.682 9c0-.593.102-1.17.282-1.707V4.961H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.039l3.007-2.332z" fill="#FBBC05"/>
      <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 00.957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z" fill="#EA4335"/>
    </svg>
  );
}

/**
 * Full-page sign-in card shown when no session exists.
 *
 * Both links navigate the browser directly to the BFF's OAuth2
 * authorization endpoint — not React Router routes.
 */
function SignInPage() {
  return (
    <div className="app">
      <main className="app-main">
        <div className="login">
          <div className="login-brand">🏦 SecureBank</div>
          <h1>Welcome back</h1>
          <p>Sign in to access your accounts and manage your finances securely.</p>
          <div className="login-options">
            <a href="/oauth2/authorization/mock-auth" className="login-btn login-btn-primary">
              <span role="img" aria-label="lock">🔐</span>
              Sign in (Demo)
            </a>
            <div className="login-divider">or</div>
            <a href="/oauth2/authorization/google" className="login-btn">
              <GoogleLogo />
              Sign in with Google
            </a>
          </div>
        </div>
      </main>
    </div>
  );
}

/**
 * Shared layout: sticky header with nav + sign-out + <Outlet/>.
 *
 * Sign out uses apiFetch (not a form POST) so the X-XSRF-TOKEN header
 * is sent correctly — same path as all other API mutations.
 *
 * Admin link: hidden when user is not ADMIN (UX-only; server enforces 403).
 */
export default function AppLayout() {
  const { user, loading } = useMe();

  // Show full-page sign-in card until we know the user is authenticated
  if (!loading && !user) {
    return <SignInPage />;
  }

  function handleSignOut(e) {
    e.preventDefault();
    apiFetch("/logout", { method: "POST" })
      .catch(() => {}) // session cleared regardless
      .finally(() => window.location.assign("/"));
  }

  return (
    <div className="app">
      <header className="app-header">
        <nav className="app-nav">
          <NavLink to="/">Accounts</NavLink>
          <NavLink to="/transactions/new">New Transaction</NavLink>
          {user?.role === "ADMIN" && (
            <NavLink to="/admin/users">Admin: Users</NavLink>
          )}
        </nav>

        <div className="user-menu">
          {loading && <span>…</span>}
          {!loading && user && (
            <>
              <span>{user.email}</span>
              <button type="button" onClick={handleSignOut}>Sign out</button>
            </>
          )}
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
