# Module 8: React SPA Development with Secure OAuth
## Lab 4.7 -- Wiring the React SPA to the BFF

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 8 - React SPA Development with Secure OAuth
> **Estimated time:** 75-90 minutes

---

## Overview

In Lab 4.5 you built a React SPA that displayed accounts and processed transfers using a fake backend (Mock Service Worker) running in the browser. In Lab 4.6 you built a real backend: a Spring Boot Backend-for-Frontend (BFF) that authenticates users via OAuth2 and proxies API calls to a resource server.

This lab is the moment they meet. You will:

1. Register a new redirect URI on the auth server so the React-app-via-Vite-proxy flow works
2. Add a Vite dev server proxy so the browser sees a single origin (no CORS to deal with in development)
3. Remove MSW from the React app and point its fetch calls at the real BFF
4. Add a `/api/me` call that runs on app load to detect whether the user is logged in
5. Add a Sign-in screen for unauthenticated users that links to the BFF's OAuth login URL
6. Add a Sign-out button to the header that POSTs to `/logout`
7. Display the logged-in user's name in the header
8. Refactor AccountList and TransferForm to receive their data as props from App

By the end of this lab the full system works end to end: alice opens the React app, clicks "Sign in", logs in on the auth server, returns to the SPA, sees her four bank accounts (the same four that have been there since Lab 4.5, but now coming from the real resource server through the BFF), makes a transfer, and sees the balance update.

**This lab assumes Lab 4.5 and Lab 4.6 are complete and working.** You will modify three projects in this lab: the auth server, the React app, and the BFF.

By the end of this lab you will be able to:

- Configure a Vite dev server proxy to forward API calls to a backend
- Replace browser-side mocks with real backend calls
- Implement an authentication context using React Context
- Wire a React app to an OAuth-protected backend with cookie-based sessions
- Hoist component state to a parent so multiple sibling components can share data

> **A note on security hardening.** This lab connects the SPA to the BFF and gets the full system working end to end. It does not turn on CSRF protection or apply the other production-grade security measures that a real banking application would need. Those topics are covered in a separate lab on application hardening.

---

## Before You Start

You will need three services running for this lab:

| Service | Port | How to start |
|---|---|---|
| Authorization Server | 9000 | Run from IntelliJ |
| Banking Resource Server (`bankserver`) | 8081 | Run from IntelliJ |
| Banking BFF (`bankbff`) | 8080 | Run from IntelliJ |

The React dev server will run on port 5173 (Vite's default).

Confirm all three backend services are healthy before starting:

```
http://localhost:9000/.well-known/openid-configuration
```

Should return JSON.

```
http://localhost:8081/accounts
```

Should return 401 Unauthorized (this is correct -- the resource server requires a token).

```
http://localhost:8080/oauth2/authorization/bank-auth
```

In a browser, should redirect you to the auth server's login page.

If any of these fail, return to Lab 4.6 to fix the BFF before continuing.

---

## Architecture

```
   ┌──────────────────────┐
   │   Browser            │
   │                      │
   │  React SPA           │
   │  (loaded from        │
   │   Vite dev server)   │
   └──────────┬───────────┘
              │
              │ fetch('/api/...') and similar
              │ all to localhost:5173
              ▼
   ┌──────────────────────┐         ┌─────────────────────┐         ┌──────────────────┐
   │   Vite Dev Server    │         │   Banking BFF       │         │  Resource Server │
   │   Port 5173          │ proxies │   (Spring Boot)     │◄───────►│   (Spring Boot)  │
   │                      │ /api,   │   Port 8080         │ bearer  │   Port 8081      │
   │   Serves React app   ├────────►│                     │ token   │                  │
   │   Forwards /api,     │ /oauth2,│   From Lab 4.6      │         │   From Lab 4.6   │
   │   /oauth2, /login,   │ /login, │                     │         │                  │
   │   /logout to BFF     │ /logout │                     │         │                  │
   └──────────────────────┘         └──────────┬──────────┘         └──────────────────┘
                                               │
                                               │ OAuth flow
                                               ▼
                                    ┌─────────────────────┐
                                    │  Authorization      │
                                    │  Server             │
                                    │  Port 9000          │
                                    └─────────────────────┘
```

The Vite proxy is the key element that makes development simple. As far as the browser is concerned, every URL is `http://localhost:5173/something`. There is only one origin, so cookies, the OAuth redirect flow, and same-origin restrictions all behave naturally without any cross-origin special-casing. The proxy quietly forwards `/api/*`, `/oauth2/*`, `/login/*`, and `/logout` to the BFF.

In production, the React build artifacts would typically be served by the BFF itself (as static files), so there genuinely is one origin. The Vite proxy in development gives you the same behavior locally.

---

## Exercise 1 -- Register the SPA's Redirect URI with the Auth Server

**Estimated time:** 10 minutes
**Topics covered:** OAuth client registration, redirect URI matching, single-origin development with a proxy

### Context

The React dev server runs on port 5173. The BFF runs on port 8080. From the browser's perspective, both look like the same origin (`localhost:5173`) thanks to the Vite proxy you will configure in Exercise 2. There is a subtlety in how the OAuth flow handles redirects, and you need to tell the auth server about it before it will work.

When the React app starts the OAuth flow, the browser sends `GET http://localhost:5173/oauth2/authorization/bank-auth`. Vite proxies this to the BFF on port 8080. The BFF builds an OAuth authorization URL containing a `redirect_uri` parameter and sends a 302 redirect to the auth server.

**Critical detail:** The BFF builds the `redirect_uri` parameter using the `Host` header of the incoming request. Because the proxy is configured with `changeOrigin: false` (you will see this in Exercise 2), the Host stays as `localhost:5173`. So the `redirect_uri` ends up being `http://localhost:5173/login/oauth2/code/bank-auth`, not `http://localhost:8080/...`.

This is the behavior we want. After login, the browser must come back to port 5173 (where the React app lives), not port 8080. The Vite proxy then forwards that callback request to the BFF. Cookies stay scoped to `localhost:5173`, so the session set during the proxied OAuth start request is preserved.

The auth server only accepts `redirect_uri` values that have been pre-registered. In Lab 4.6 you registered only the port-8080 redirect URI. To support the React app integration, you need to add the port-5173 redirect URI as well.

### Task 1.1 -- Open the auth server project

In IntelliJ, open the auth server project. Navigate to `src/main/java/com/example/authserver/config/AuthorizationServerConfig.java`.

### Task 1.2 -- Add a second redirect URI to bank-client-bff

Find the `bankClientBff` `RegisteredClient` registration. It currently has one `.redirectUri(...)` line:

```java
.redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
```

Add a second `.redirectUri(...)` call right below it:

```java
// Direct access (Lab 4.6 standalone testing)
.redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
// Through Vite proxy (Lab 4.7 React integration)
.redirectUri("http://localhost:5173/login/oauth2/code/bank-auth")
```

The auth server now accepts both redirect URIs. Lab 4.6's standalone testing still works (browser hits port 8080 directly), and Lab 4.7's React integration also works (browser hits port 5173, proxy forwards to BFF, BFF requests redirect to port 5173, browser comes back to port 5173, proxy forwards the callback to BFF).

### Task 1.3 -- Restart the auth server

Stop the auth server in IntelliJ (red Stop button). Start it again (green Run button). Wait for `Started AuthServerApplication` in the console.

The new redirect URI is registered. When you try to log in via the React app later, the auth server will accept the callback URL.

### Verify Exercise 1

There is no direct way to test that a redirect URI is registered (the auth server does not expose a "list registered URIs" endpoint). The verification will come implicitly when you complete the OAuth flow in Exercise 5. If the registration is missing or wrong, you will see an `[invalid_redirect_uri]` error from the auth server during login.

---

## Exercise 2 -- Configure the Vite Dev Server Proxy

**Estimated time:** 10 minutes
**Topics covered:** Vite configuration, dev server proxying, single-origin architecture

### Context

Vite's dev server can transparently forward requests to a backend. You configure which URL prefixes get forwarded and where they go, and from then on the React app makes requests as if everything were on one server.

### Task 2.1 -- Open the React project

Open the `banking-ui` project from Lab 4.5 in VS Code.

### Task 2.2 -- Add the proxy configuration

Open `vite.config.ts` in the project root. Replace its contents with:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Forward all backend paths to the BFF on port 8080.
      // The browser only ever talks to localhost:5173 in development.
      '/api':     { target: 'http://localhost:8080', changeOrigin: false },
      '/oauth2':  { target: 'http://localhost:8080', changeOrigin: false },
      '/login':   { target: 'http://localhost:8080', changeOrigin: false },
      '/logout':  { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
});
```

Notes on the proxy configuration:

- `target` is where requests get forwarded.
- `changeOrigin: false` keeps the original `Host` header (`localhost:5173`). This matters for OAuth: when the BFF builds redirect URIs, it uses the Host header it sees. Keeping it as `localhost:5173` means the redirect URI is `http://localhost:5173/login/oauth2/code/bank-auth`, which sends the browser back through the proxy on the callback (preserving cookies). If we used `changeOrigin: true`, the Host would become `localhost:8080`, and the auth server would redirect the browser directly to port 8080, bypassing the proxy and losing the session cookie.
- The four prefixes cover everything: `/api/*` is the application API, `/oauth2/*` starts the OAuth flow, `/login/*` is where the OAuth callback lands, `/logout` ends the session.

### Verify Exercise 2

Stop the React dev server if it is running, then start it again:

```bash
npm run dev
```

The server should start on port 5173 with no errors. You will not see the proxy do anything yet because we have not modified the React code to call the BFF.

---

## Exercise 3 -- Remove Mock Service Worker

**Estimated time:** 10 minutes
**Topics covered:** Removing development scaffolding, real backend integration

### Context

The React app from Lab 4.5 used MSW to intercept fetch calls in the browser and return canned data. With the BFF and resource server providing real data, MSW is no longer needed. You will remove it cleanly.

### Task 3.1 -- Stop the MSW worker from starting

Open `src/main.tsx`. It currently looks something like this:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';

async function enableMocking() {
  const { worker } = await import('./mocks/browser');
  return worker.start({
    onUnhandledRequest: 'bypass',
  });
}

enableMocking().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
});
```

Replace its contents with:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

The `enableMocking` wrapper is gone. The app starts directly.

### Task 3.2 -- Delete the mocks folder

In VS Code's file explorer, right-click `src/mocks/` and delete it. Two files go away: `handlers.ts` and `browser.ts`.

### Task 3.3 -- Uninstall the MSW package

In a terminal at the project root:

```bash
npm uninstall msw
```

This removes `msw` from `package.json` and from `node_modules`.

### Task 3.4 -- Delete the MSW service worker file

In `public/`, delete the file `mockServiceWorker.js`. It was generated by MSW during installation and is no longer needed. (If your browser cached it, you can ignore that for now -- it will not interfere.)

### Verify Exercise 3

Restart the dev server:

```bash
npm run dev
```

Open `http://localhost:5173` in a browser. You will see the Header at the top, but the accounts table area will be empty or show errors. The SPA is now sending real fetch calls to the BFF, but the BFF rejects them (no session cookie). Open DevTools, go to the Network tab, and look at the failed `/api/accounts` request. You should see:

- Request URL: `http://localhost:5173/api/accounts` (the SPA still talks to its own origin)
- Response status: 401 Unauthorized
- The Vite proxy forwarded the request to the BFF on 8080, the BFF responded with 401, and the proxy returned that response back to the SPA

The proxy is working. The next exercise will add the authentication flow that turns that 401 into a 200.

---

## Exercise 4 -- Build the Authentication Context

**Estimated time:** 25 minutes
**Topics covered:** React Context, useState, useEffect, conditional rendering, auth state propagation

### Context

Several parts of the application need to know who is logged in: the Header (to show the user's name), the AccountList (to load accounts only when authenticated), the TransferForm (to allow transfers only when authenticated), and the App (to decide between showing the login screen or the main UI).

Passing this state down through props at every level (prop-drilling) gets ugly fast. React Context lets us put the auth state in one place and let any descendant component read it directly.

You will build a single `AuthContext` that:

- Calls `/api/me` on app startup to determine whether the user is logged in
- Exposes the current user (or null) plus a function to refresh the auth state
- Wraps the entire app, so any component can call `useAuth()` to read it

### Task 4.1 -- Create the User type

Open `src/api/types.ts` and add a `User` type at the bottom of the file. The full file should look like this (existing types unchanged, new type added at the end):

```ts
/**
 * TypeScript types for the banking API.
 */

export type AccountStatus = 'ACTIVE' | 'INACTIVE';
export type AccountType = 'SAVINGS' | 'CHECKING';

export type Account = {
  accountNumber: string;
  status: AccountStatus;
  balance: number;
  type: AccountType;
};

export type Customer = {
  customerId: string;
  name: string;
  email: string;
};

export type TransactionStatus = 'COMPLETE' | 'FAILED';
export type TransactionType = 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL';

export type Transaction = {
  transactionId: string;
  date: string;
  type: TransactionType;
  amount: number;
  account1: string;
  account2: string | null;
  status: TransactionStatus;
};

export type TransferRequest = {
  fromAccountNumber: string;
  toAccountNumber: string;
  amount: number;
};

export type TransferResponse = {
  transactionId: string;
  status: TransactionStatus;
};

// New: matches the UserInfoDto returned by the BFF's /api/me endpoint.
export type User = {
  username: string;
  name: string;
  roles: string[];
};
```

### Task 4.2 -- Add a getCurrentUser API function

Open `src/api/client.ts` and replace its contents with:

```ts
/**
 * API client for the banking backend.
 *
 * All HTTP communication with the backend goes through this file.
 * Requests use same-origin URLs that the Vite proxy forwards to the
 * BFF on port 8080.
 */

import type {
  Account,
  TransferRequest,
  TransferResponse,
  User,
} from './types';

export async function getCurrentUser(): Promise<User | null> {
  const response = await fetch('/api/me', {
    headers: { 'Accept': 'application/json' },
  });
  if (response.status === 401) {
    return null;  // not logged in
  }
  if (!response.ok) {
    throw new Error(`Failed to load user: ${response.status}`);
  }
  return response.json();
}

export async function getAccounts(): Promise<Account[]> {
  const response = await fetch('/api/accounts', {
    headers: { 'Accept': 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Failed to load accounts: ${response.status}`);
  }
  return response.json();
}

export async function postTransfer(
  request: TransferRequest
): Promise<TransferResponse> {
  const response = await fetch('/api/transfers', {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const message = await safeReadErrorMessage(response);
    throw new Error(message || `Transfer failed: ${response.status}`);
  }
  return response.json();
}

export async function logout(): Promise<void> {
  const response = await fetch('/logout', {
    method: 'POST',
    headers: { 'Accept': 'application/json' },
  });
  if (!response.ok && response.status !== 302) {
    throw new Error(`Logout failed: ${response.status}`);
  }
}

async function safeReadErrorMessage(response: Response): Promise<string | null> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string') {
      return body.message;
    }
    return null;
  } catch {
    return null;
  }
}
```

Notes:

- Every request includes `Accept: application/json`. This tells the BFF "I am a JSON API client; if you need authentication, return 401 instead of redirecting me to a login page." Without this header, the BFF would respond with a 302 redirect to the auth server's login HTML, which the SPA cannot interpret.
- `getCurrentUser` returns `null` instead of throwing when the user is not logged in. This is a normal state, not an error.
- `logout` accepts both 200 and 302 as success (Spring Security's default logout returns 302).

### Task 4.3 -- Create the AuthContext

Create a new file `src/auth/AuthContext.tsx`. (Create the `auth` folder first.)

```tsx
/**
 * Authentication context.
 *
 * Exposes the current logged-in user (or null) and a function to
 * re-check authentication state with the BFF. Wraps the whole app
 * so any component can read auth state via the useAuth() hook.
 */

/* eslint-disable react-refresh/only-export-components */

import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { getCurrentUser } from '../api/client';
import type { User } from '../api/types';

type AuthContextValue = {
  user: User | null;
  loading: boolean;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

type AuthProviderProps = {
  children: ReactNode;
};

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getCurrentUser();
      setUser(result);
    } catch (e) {
      console.error('Failed to refresh auth state:', e);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <AuthContext.Provider value={{ user, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
```

A few things worth noticing:

- `AuthProvider` is the component that wraps the app. It owns the user state and runs the `/api/me` check on mount.
- `useAuth` is the hook other components call. The `if (!ctx)` check is a safety net: if a component calls `useAuth()` outside of an `AuthProvider`, the error message tells you why instead of producing a confusing crash.
- `refresh` is exposed so components can ask the context to re-check after a login or logout completes.
- The `/* eslint-disable react-refresh/only-export-components */` comment at the top suppresses an ESLint warning about exporting both a component (`AuthProvider`) and a hook (`useAuth`) from the same file. The pattern is standard and works correctly; the warning is overly aggressive about Vite's hot-module-reload optimization. Disabling it for this file is the cleanest option.

### Task 4.4 -- Wrap the app in the AuthProvider

Open `src/main.tsx` and wrap the `<App />` element in `<AuthProvider>`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>
);
```

### Verify Exercise 4

Restart the dev server (`npm run dev`) and open `http://localhost:5173`. Open DevTools (F12) and look at the Network tab. You should see a single `GET /api/me` request that returns 401. That is the AuthProvider running its initial check.

The page itself will still show a broken accounts area because we have not yet built the conditional UI. That is the next exercise.

---

## Exercise 5 -- Build the Sign-In Screen and Conditional UI

**Estimated time:** 25 minutes
**Topics covered:** Conditional rendering, anchor tags vs button-driven navigation, React Context consumption, lifting state up

### Context

Now that the AuthContext knows whether the user is logged in, the App component can decide what to show:

- **While the auth check is in progress (`loading: true`)**: a loading indicator
- **When no user is logged in (`user === null`)**: a Sign-in screen with a link to start the OAuth flow
- **When a user is logged in (`user !== null`)**: the existing accounts list and transfer form

You will also restructure how data flows through the app. In Lab 4.5 each component owned its own data: `AccountList` had its own `useEffect` to fetch accounts, `TransferForm` had its own logic to refresh after a transfer. With authentication added, that pattern no longer fits. The accounts can only be fetched after login, and a successful transfer needs to update both the list and the form. The clean solution is to hoist the data ownership up to App.tsx and pass it down to both components as props.

### Task 5.1 -- Create the SignInScreen component

Create `src/components/SignInScreen.tsx`:

```tsx
/**
 * Sign-in screen shown to unauthenticated users.
 *
 * Renders a single anchor tag pointing at the BFF's OAuth login URL.
 * We use an anchor (full page navigation) rather than a fetch call
 * because the OAuth flow involves redirects through the auth server
 * that the browser must follow on its own. fetch and AJAX cannot
 * follow cross-origin redirects to HTML pages.
 */

export function SignInScreen() {
  return (
    <section className="sign-in-screen">
      <h2>Welcome to MD282 Bank</h2>
      <p>Sign in to view your accounts and transfer funds.</p>
      <a href="/oauth2/authorization/bank-auth" className="sign-in-button">
        Sign in
      </a>
    </section>
  );
}
```

The most important detail: the Sign-in element is an `<a href="...">` tag, not a `<button>` with an onClick handler. The OAuth flow needs the browser to navigate -- following the redirect from the BFF to the auth server, then back. A `fetch` call cannot do that because it cannot follow a redirect to an HTML page on a different origin.

### Task 5.2 -- Update the Header to show the logged-in user

Open `src/components/Header.tsx`. Replace its contents with:

```tsx
/**
 * Header component.
 *
 * Reads the current user from the auth context. When a user is logged in,
 * shows their name and a Sign-out button. When no user is logged in,
 * shows nothing extra (the Sign-in button lives in SignInScreen).
 */

import { useAuth } from '../auth/AuthContext';
import { logout as logoutApi } from '../api/client';

export function Header() {
  const { user, refresh } = useAuth();

  async function handleSignOut() {
    try {
      await logoutApi();
    } catch (e) {
      console.error('Logout failed:', e);
    } finally {
      // Re-check auth state regardless of whether the call succeeded.
      await refresh();
    }
  }

  return (
    <header className="header">
      <div className="header-content">
        <div>
          <h1>MD282 Bank</h1>
          <p className="tagline">Online Banking</p>
        </div>
        {user && (
          <div className="header-user">
            <span className="user-name">Hello, {user.name}</span>
            <button type="button" onClick={handleSignOut} className="sign-out-button">
              Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
```

The new pattern here is `{user && (...)}`. In JSX, `false` and `null` render as nothing, so this expression renders the user info block only when `user` is truthy.

### Task 5.3 -- Update App.tsx to render conditionally

Open `src/App.tsx` and replace its contents:

```tsx
/**
 * Root component.
 *
 * Reads the auth state and decides what to render:
 *  - loading: show a loading message
 *  - not logged in: show the SignInScreen
 *  - logged in: show the accounts list and transfer form
 *
 * App.tsx now owns the accounts data. It loads accounts after the
 * user is authenticated and passes the data down to AccountList
 * and TransferForm as props. After a successful transfer, the
 * onTransferComplete callback re-fetches accounts so balances update.
 */

import { useState, useEffect, useCallback } from 'react';
import { Header } from './components/Header';
import { AccountList } from './components/AccountList';
import { TransferForm } from './components/TransferForm';
import { SignInScreen } from './components/SignInScreen';
import { useAuth } from './auth/AuthContext';
import { getAccounts } from './api/client';
import type { Account } from './api/types';
import './App.css';

export function App() {
  const { user, loading: authLoading } = useAuth();

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountsLoading, setAccountsLoading] = useState<boolean>(false);
  const [accountsError, setAccountsError] = useState<string | null>(null);

  const loadAccounts = useCallback(async () => {
    setAccountsLoading(true);
    setAccountsError(null);
    try {
      const data = await getAccounts();
      setAccounts(data);
    } catch (e) {
      setAccountsError(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setAccountsLoading(false);
    }
  }, []);

  // Load accounts once a user becomes available.
  useEffect(() => {
    if (user) {
      loadAccounts();
    }
  }, [user, loadAccounts]);

  return (
    <div className="app">
      <Header />
      <main>
        {authLoading && <p className="status-message">Checking sign-in state...</p>}
        {!authLoading && !user && <SignInScreen />}
        {!authLoading && user && (
          <>
            <AccountList
              accounts={accounts}
              loading={accountsLoading}
              error={accountsError}
            />
            <TransferForm
              accounts={accounts}
              onTransferComplete={loadAccounts}
            />
          </>
        )}
      </main>
    </div>
  );
}
```

The three states are clearly visible: the loading message, the sign-in screen, and the wrapped accounts/transfer pair. The `<>...</>` is a React Fragment, used because conditional rendering in JSX needs a single root element when wrapping multiple children.

### Task 5.4 -- Refactor AccountList and TransferForm to accept props

App.tsx now passes `accounts`, `loading`, and `error` as props to `AccountList`, and passes `accounts` and `onTransferComplete` to `TransferForm`. The components in your Lab 4.5 codebase do not yet accept these props. Saving App.tsx without updating the components first produces TypeScript errors. Update them now.

This is a deliberate design choice. By hoisting the data ownership to App.tsx:

- The accounts are loaded only after the user is authenticated (App.tsx waits for `user` before calling `loadAccounts`).
- After a successful transfer, App.tsx can re-fetch the accounts and pass the new list down to AccountList so balances update in the UI, and to TransferForm so the dropdown options reflect the new balances.
- AccountList and TransferForm are simpler. They render what they are given without knowing anything about the API.

This pattern is called "lifting state up" and is one of the most common React refactors as applications grow.

#### Update src/components/AccountList.tsx

Replace the contents with:

```tsx
import type { Account } from '../api/types';

type AccountListProps = {
  accounts: Account[];
  loading: boolean;
  error: string | null;
};

export function AccountList({ accounts, loading, error }: AccountListProps) {
  if (loading) {
    return <p className="status-message">Loading accounts...</p>;
  }

  if (error) {
    return <p className="error-message">Error loading accounts: {error}</p>;
  }

  if (accounts.length === 0) {
    return <p className="status-message">No accounts found.</p>;
  }

  return (
    <section className="account-list">
      <h2>Your Accounts</h2>
      <table>
        <thead>
          <tr>
            <th>Account Number</th>
            <th>Type</th>
            <th>Status</th>
            <th>Balance</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((account) => (
            <tr key={account.accountNumber}>
              <td>{account.accountNumber}</td>
              <td>{account.type}</td>
              <td>{account.status}</td>
              <td>${account.balance.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
```

If your Lab 4.5 version had different JSX structure (different table columns, different CSS classes, different empty-state message), keep those details and just adjust the prop signature and the conditional rendering at the top.

#### Update src/components/TransferForm.tsx

Replace the contents with:

```tsx
import { useState } from 'react';
import { postTransfer } from '../api/client';
import type { Account } from '../api/types';

type TransferFormProps = {
  accounts: Account[];
  onTransferComplete: () => void;
};

export function TransferForm({ accounts, onTransferComplete }: TransferFormProps) {
  const [fromAccount, setFromAccount] = useState<string>('');
  const [toAccount, setToAccount] = useState<string>('');
  const [amount, setAmount] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageType, setMessageType] = useState<'success' | 'error' | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    setMessageType(null);

    const amountNumber = parseFloat(amount);
    if (!fromAccount || !toAccount || isNaN(amountNumber) || amountNumber <= 0) {
      setMessage('Please fill in all fields with valid values.');
      setMessageType('error');
      return;
    }

    if (fromAccount === toAccount) {
      setMessage('From and to accounts must be different.');
      setMessageType('error');
      return;
    }

    setSubmitting(true);
    try {
      const result = await postTransfer({
        fromAccountNumber: fromAccount,
        toAccountNumber: toAccount,
        amount: amountNumber,
      });
      setMessage(`Transfer complete. Transaction ID: ${result.transactionId}`);
      setMessageType('success');
      setFromAccount('');
      setToAccount('');
      setAmount('');
      onTransferComplete();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Transfer failed.');
      setMessageType('error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="transfer-form">
      <h2>Transfer Funds</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <label htmlFor="from-account">From Account</label>
          <select
            id="from-account"
            value={fromAccount}
            onChange={(e) => setFromAccount(e.target.value)}
          >
            <option value="">-- Select --</option>
            {accounts.map((a) => (
              <option key={a.accountNumber} value={a.accountNumber}>
                {a.accountNumber} ({a.type}, ${a.balance.toFixed(2)})
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label htmlFor="to-account">To Account</label>
          <select
            id="to-account"
            value={toAccount}
            onChange={(e) => setToAccount(e.target.value)}
          >
            <option value="">-- Select --</option>
            {accounts.map((a) => (
              <option key={a.accountNumber} value={a.accountNumber}>
                {a.accountNumber} ({a.type}, ${a.balance.toFixed(2)})
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label htmlFor="amount">Amount</label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Processing...' : 'Submit Transfer'}
        </button>
        {message && (
          <p className={messageType === 'success' ? 'success-message' : 'error-message'}>
            {message}
          </p>
        )}
      </form>
    </section>
  );
}
```

Adjust to match your Lab 4.5 version's JSX structure and styling. The key changes from Lab 4.5:

1. Add the `TransferFormProps` type and accept it in the function signature.
2. Remove any internal accounts-fetching code -- the parent provides them.
3. After a successful transfer, call `onTransferComplete()` so App.tsx can re-fetch and refresh balances.
4. Remove any internal "refetch accounts" or success-then-reload logic that lived inside TransferForm in Lab 4.5.

After saving both component files, the TypeScript errors in App.tsx will clear.

### Task 5.5 -- Add CSS for the new components

Open `src/App.css` and add the following styles at the bottom of the file:

```css
/* Header user info */

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-name {
  font-size: 0.9375rem;
}

.sign-out-button {
  padding: 0.375rem 1rem;
  background-color: rgba(255, 255, 255, 0.15);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 4px;
  font-size: 0.875rem;
  transition: background-color 0.15s ease;
}

.sign-out-button:hover {
  background-color: rgba(255, 255, 255, 0.25);
}

/* Sign-in screen */

.sign-in-screen {
  background-color: white;
  padding: 3rem 2rem;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  text-align: center;
}

.sign-in-screen h2 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  color: #2d3748;
}

.sign-in-screen p {
  margin: 0 0 2rem 0;
  color: #4a5568;
}

.sign-in-button {
  display: inline-block;
  padding: 0.75rem 2rem;
  background-color: #1f6feb;
  color: white;
  text-decoration: none;
  border-radius: 4px;
  font-weight: 500;
  font-size: 1rem;
  transition: background-color 0.15s ease;
}

.sign-in-button:hover {
  background-color: #1858c4;
}
```

### Verify Exercise 5

Restart the dev server if Vite has not picked up the file structure changes. Open `http://localhost:5173`.

You should see:

1. Briefly: "Checking sign-in state..."
2. Then: the Welcome screen with a "Sign in" button

Click "Sign in". You should be redirected to `http://localhost:9000/login` (the auth server's login page). Log in as `alice` / `password`.

After login, the auth server redirects back to `/login/oauth2/code/bank-auth`, the BFF processes the callback, and the browser ends up at `/` (the SPA's root). The SPA loads, the AuthContext runs `/api/me` again, gets a 200 with alice's info, and renders the header with "Hello, alice", the accounts list with four accounts, and the transfer form below it.

Try a transfer of $50 from ACC-001 to ACC-002. The success message appears, and the AccountList table updates to reflect the new balances (because `onTransferComplete()` calls `loadAccounts()`, which re-fetches and re-renders).

Click Sign out. You should return to the welcome screen with no user info in the header.

If all of this works, the React app and BFF are fully integrated.

---

## Exercise 6 -- Final End-to-End Test

**Estimated time:** 10 minutes

Run through the complete user journey to confirm everything works:

1. **Open the app fresh.** Open a private/incognito browser window. Navigate to `http://localhost:5173`. You should briefly see "Checking sign-in state..." then the Welcome screen.

2. **Sign in.** Click "Sign in". The browser redirects to the auth server. Log in as `alice` / `password`. After the redirect chain completes, you should be back at `http://localhost:5173` with the header showing "Hello, alice" and the accounts list showing four accounts.

3. **Make a transfer.** Use the form to transfer $250 from ACC-001 to ACC-002. The success message appears, the balances update.

4. **Reload the page.** With the page still open, hit reload (F5). The auth check runs again, you stay logged in, and the accounts reload (showing the post-transfer balances).

5. **Open a new tab.** In a new tab in the same browser, open `http://localhost:5173`. You should be logged in immediately (the BFF_SESSION cookie is shared between tabs in the same browser).

6. **Sign out.** Click "Sign out" in the header. You should return to the Welcome screen. The BFF_SESSION cookie is cleared. If you reload now, you stay on the Welcome screen.

7. **Sign in as bob.** Click Sign in again. Log in as `bob` / `password`. The header should now show "Hello, bob". (The accounts are the same -- the lab does not separate accounts by user. In a real banking app this would obviously be different.)

If all seven steps work, you have built a complete OAuth-secured React + Spring Boot system using the BFF pattern.

---

## What You Have Built

You have built the full reference architecture for a modern web application that uses OAuth for authentication:

- **A React SPA** that owns the user interface and renders to the browser. It has no idea what an OAuth token looks like. It has no API keys. The application logic is cleanly separated from authentication mechanics.

- **A Spring Boot BFF** that owns authentication. It performs the full OAuth Authorization Code flow on behalf of users, holds tokens server-side in the user's HTTP session, refreshes them transparently when they expire, and proxies API calls to the resource server with bearer tokens attached.

- **A Spring Boot Resource Server** that owns the business data. It validates JWT bearer tokens against the auth server's JWKS endpoint and serves account information and transfers. It does not know or care who the BFF is; it only checks that incoming tokens are valid.

This architecture scales. To add a new feature, you typically only need to:

- Add a new endpoint to the resource server
- Add a thin proxy method to the BFF
- Add a fetch call from the React app

The authentication and token handling do not need to change. They were built once and now apply to every endpoint.

This lab focused on getting the system working end to end. A separate lab on application hardening covers the security measures a real production deployment would add: CSRF protection, secure cookie flags, content security policies, and so on.

---

## Reflection Questions

In a new file `lab-4.7-notes.md` in the `banking-ui` project root, answer these:

1. The Vite proxy made cross-origin calls disappear in development. What would you need to add or change to deploy this app to production where the React build is served by a separate web server?

2. The Sign-in element is an `<a>` tag, not a `<button>` with an onClick handler. Why? What would happen if you tried to do `fetch('/oauth2/authorization/bank-auth')` instead?

3. The auth server now has two registered redirect URIs for `bank-client-bff`: one on port 8080 and one on port 5173. In production, you would typically have only one. Why is the development setup different, and what would the production registration look like?

4. AccountList and TransferForm used to fetch their own data in Lab 4.5. Now App.tsx fetches and passes the data down as props. Describe at least two specific behaviors that this refactor enables that would have been awkward or impossible with the old structure.

5. The `useAuth()` hook reads from a Context. What would change about the application's structure if every component needed to know about the user, but you used prop-drilling instead of Context?

6. Coming from C#, how does this React + BFF architecture compare to a Blazor Server or ASP.NET MVC application? What are the trade-offs?

---

## What's Next

You have completed the React-to-BFF integration. The full system runs end to end with real OAuth-based authentication. Subsequent labs cover:

- **Application hardening:** CSRF protection, secure cookies, and other production-grade security measures
- **Real-world OAuth providers:** swapping the local mock auth server for Google or another commercial identity provider

Together, these labs prepare you to build production-quality OAuth-protected applications for your capstone project.
