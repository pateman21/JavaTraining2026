# Module 8: React SPA Development with Secure OAuth
## Lab 4.7 -- Solutions and Checkpoint Answers

> **Course:** MD282 - Java Full-Stack Development
> **Purpose:** This file contains completed code for every code block in Lab 4.7
> and written answers to every Reflection question. Use it to verify your work
> after attempting each task yourself. Reading the solution before attempting
> the task defeats the purpose of the exercise.

---

## How to Use This File

Each section below corresponds to an exercise in the lab. The lab itself is
mostly cut-and-paste; this file confirms the final state of each modified file
plus the answers to the Reflection questions at the end.

---

## Exercise 1 -- Register the SPA's Redirect URI with the Auth Server

The relevant section of the auth server's `AuthorizationServerConfig.java` after
adding the second redirect URI:

```java
RegisteredClient bankClientBff = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("bank-client-bff")
        .clientSecret("{noop}bank-client-bff-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        // Direct access (Lab 4.6 standalone testing)
        .redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
        // Through Vite proxy (Lab 4.7 React integration)
        .redirectUri("http://localhost:5173/login/oauth2/code/bank-auth")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("read:accounts")
        .scope("read:transactions")
        .scope("write:accounts")
        .scope("write:transactions")
        .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .build())
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(60))
                .refreshTokenTimeToLive(Duration.ofDays(1))
                .build())
        .build();
```

### Why two redirect URIs

The OAuth Authorization Code flow requires the auth server to redirect the user's
browser back to a pre-registered URL after login. Different testing scenarios need
different URLs:

- **Lab 4.6 standalone testing**: the user navigates directly to
  `http://localhost:8080/oauth2/authorization/bank-auth`. The browser is talking
  to the BFF directly. The auth server redirects back to the BFF on port 8080.

- **Lab 4.7 React integration**: the user navigates to the React app at
  `http://localhost:5173`. Clicking "Sign in" sends the request through the Vite
  proxy. Because the proxy uses `changeOrigin: false`, the BFF receives a request
  with `Host: localhost:5173` and builds the redirect URI to point back to port
  5173. The auth server must accept that URL too.

Registering both URIs lets both labs work without further configuration changes.
In production, this is similar to how a real app might register multiple URIs
for staging environments, mobile deep links, and so on.

---

## Exercise 2 -- Configure the Vite Dev Server Proxy

The complete `vite.config.ts`:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api':     { target: 'http://localhost:8080', changeOrigin: false },
      '/oauth2':  { target: 'http://localhost:8080', changeOrigin: false },
      '/login':   { target: 'http://localhost:8080', changeOrigin: false },
      '/logout':  { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
});
```

### Why `changeOrigin: false` matters

By default Vite would set `changeOrigin: true`, which rewrites the `Host` header
of forwarded requests from `localhost:5173` to `localhost:8080`. That sounds
helpful but breaks OAuth: the BFF builds its redirect URI based on the request's
host, so when it sends a 302 back to the browser at the start of the OAuth flow,
the URL would point back to `localhost:8080` and bypass the proxy. Keeping the
original `Host` header makes the BFF generate redirects that point back through
Vite, keeping everything on the `localhost:5173` origin.

---

## Exercise 3 -- Remove Mock Service Worker

No code to verify; just confirm:

- `src/main.tsx` no longer imports or calls `enableMocking`
- `src/mocks/` is deleted
- `package.json` no longer lists `msw` as a dependency
- `public/mockServiceWorker.js` is deleted

The complete `src/main.tsx` after this exercise (before Exercise 4 adds the AuthProvider):

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

---

## Exercise 4 -- Build the Authentication Context

### Complete src/api/types.ts

```ts
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

export type User = {
  username: string;
  name: string;
  roles: string[];
};
```

### Complete src/api/client.ts

```ts
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
    return null;
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

### Complete src/auth/AuthContext.tsx

```tsx
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

### Complete src/main.tsx (after Exercise 4)

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

---

## Exercise 5 -- Build the Sign-In Screen and Conditional UI

### Complete src/components/SignInScreen.tsx

```tsx
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

### Complete src/components/Header.tsx

```tsx
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

### Complete src/App.tsx

```tsx
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

The CSS additions are exactly as shown in the lab handout.

### Complete src/components/AccountList.tsx

After refactoring to receive accounts via props:

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

### Complete src/components/TransferForm.tsx

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

### Why hoist data ownership to App.tsx

In Lab 4.5 each component owned its own data: `AccountList` had a `useEffect` to
fetch accounts, `TransferForm` had its own logic to refresh accounts after a
transfer. That worked because there was no authentication step.

Now that authentication gates the data, the rules change:

1. **Accounts can only be fetched after login.** App.tsx waits for the auth check
   to complete and only loads accounts when a user is present. Component-owned
   data fetching could fire before the user is authenticated.

2. **A transfer must update both the AccountList and the TransferForm.** After a
   transfer, the new balances need to appear in the table AND in the dropdown
   options of the form (so the user sees the updated balance next to each
   account). The cleanest way is to have App.tsx own the accounts and pass them
   down -- a refresh from any source updates everywhere.

3. **The component contract is clearer.** AccountList and TransferForm now have
   no API knowledge. They are presentation-and-form components. Testing them in
   isolation is trivial.

This pattern -- "lift state up" -- is one of the most common React refactors as
applications grow.

---

## Reflection Questions (lab-4.7-notes.md answers)

### Question 1: The Vite proxy made cross-origin calls disappear in development. What would you need to add or change to deploy this app to production where the React build is served by a separate web server?

In development, the Vite proxy makes the SPA and the BFF look like one origin
(`localhost:5173`). In production this convenience disappears. There are two
approaches:

**Approach A: keep one origin in production too.** Build the React app with
`npm run build`. The result is a `dist/` folder containing static HTML, JS, and
CSS files. Configure the BFF (or a reverse proxy in front of it like nginx) to
serve those static files from the same origin as the API. Requests to `/api/*`
are handled by the BFF; requests to `/static/*` and `/index.html` are served as
files. Cookies and the OAuth flow all work without modification because there
is still only one origin.

**Approach B: use two origins with proper CORS setup.** The SPA is served from
`spa.example.com` and the BFF is at `api.example.com`. In this case you need:

- BFF: a CORS configuration allowing the SPA's origin
- SPA: every fetch call needs `credentials: 'include'`
- BFF: cookies must be set with `SameSite=None; Secure`, which requires HTTPS
  on both origins
- BFF: the OAuth redirect URI registered with the auth server must use the
  BFF's origin, not the SPA's

Approach A is simpler and what most production deployments do. The lab's Vite
proxy already mimics this architecture, so the transition to production is small.

### Question 2: The Sign-in element is an `<a>` tag, not a `<button>` with an onClick handler. Why? What would happen if you tried to do `fetch('/oauth2/authorization/bank-auth')` instead?

OAuth login involves a sequence of full-page navigations and cross-origin
redirects: the BFF redirects to the auth server, the user types credentials and
submits a form, the auth server redirects back to the BFF's callback URL, and
finally the BFF redirects to the post-login page in the SPA. The browser must
be in control of this navigation -- updating the address bar, accepting the
auth server's cookies, rendering the auth server's HTML login form.

A `fetch` call cannot do any of this. Specifically:

- `fetch` does not update the URL bar
- `fetch` cannot render an HTML page; it can only return data to JavaScript
- `fetch` cannot submit the user's credentials to the auth server's form
- `fetch` follows redirects internally up to a limit, but cannot follow a
  redirect to an HTML page on a different origin in any meaningful way

If you tried `fetch('/oauth2/authorization/bank-auth')`, the call would receive
the BFF's 302 redirect. The browser would automatically follow the redirect to
the auth server. The auth server would respond with HTML. Your `fetch` call
would receive the HTML as a string -- and it would do nothing useful with it.
The user would not see the login page. They would not be authenticated.

The `<a>` tag triggers a **navigation**, which is exactly what is needed. The
browser updates the URL bar, follows redirects naturally, and renders whatever
HTML it receives. This is why every OAuth-aware SPA uses an anchor tag (or a
button that programmatically sets `window.location`) for the Sign-in action.

### Question 3: The auth server now has two registered redirect URIs for `bank-client-bff`: one on port 8080 and one on port 5173. In production, you would typically have only one. Why is the development setup different, and what would the production registration look like?

The two registered URIs serve two different testing scenarios in development:

- **Port 8080** lets you test the BFF in isolation without running the React
  app at all (the Lab 4.6 standalone testing pattern).
- **Port 5173** lets you test the React app talking to the BFF through the
  Vite proxy (this lab's pattern).

Both ports point at `localhost`, which is allowed by OAuth specifications as a
special case for development. In a production environment:

- There is typically one URL that serves both the SPA and the API (Approach A
  from Question 1), or two URLs that are both real domains with HTTPS.
- The redirect URI is something like `https://app.examplebank.com/login/oauth2/code/bank-auth`.
- Only that one URI is registered with the auth server.
- Different environments (production, staging, development) each have their
  own URLs and their own client registrations -- they do not share registrations
  across environments.

Registering multiple redirect URIs in production is uncommon and is sometimes
considered a security risk, because each registered URI is a place an attacker
could potentially target if they could trick the OAuth flow into using it.
Production OAuth registrations therefore tend to be tightly scoped to a single
canonical redirect URI per environment.

### Question 4: AccountList and TransferForm used to fetch their own data in Lab 4.5. Now App.tsx fetches and passes the data down as props. Describe at least two specific behaviors that this refactor enables that would have been awkward or impossible with the old structure.

**Behavior 1: The components only run when a user is authenticated.**

In Lab 4.5, AccountList's `useEffect` fired on mount, regardless of authentication
state. With the old structure, you would have had to add an authentication check
inside AccountList itself (or skip mounting the component until the user logged
in). That blurs responsibilities: AccountList would need to know about the auth
context.

In the new structure, App.tsx waits for `user` to be present before calling
`loadAccounts`. AccountList only ever sees data when it makes sense to show
data. The component itself has no idea authentication exists.

**Behavior 2: A successful transfer refreshes both the list and the form.**

After a transfer, the new balances need to appear in two places: the table
showing all accounts AND the dropdown options inside TransferForm (which display
each account's current balance next to its number). With the old structure,
TransferForm would have to either re-fetch and re-render its own data, or somehow
signal to AccountList to re-fetch its data, or share state with AccountList via
some external store. Each option introduces complexity.

In the new structure, App.tsx owns the accounts. After a successful transfer,
TransferForm calls `onTransferComplete()`, which calls `loadAccounts()` in
App.tsx, which updates the single source of truth. Both AccountList and
TransferForm receive the new data automatically because they read from the same
`accounts` prop.

**Other benefits worth mentioning:**

- AccountList and TransferForm are now trivially testable in isolation. You can
  pass them mock props and snapshot the rendered output without setting up a
  fetch mock.
- If you wanted to add a third component that needs the same accounts list (say
  a sidebar showing total balance across all accounts), you can pass the same
  `accounts` prop. No additional fetch logic.

### Question 5: The `useAuth()` hook reads from a Context. What would change about the application's structure if every component needed to know about the user, but you used prop-drilling instead of Context?

Without Context, you would have to pass `user` (and possibly `loading` and
`refresh`) as props from the top-level App component down through every
intermediate component to wherever it is needed. Some specific consequences:

- **App.tsx would need to read auth state and pass it down.** Where the auth
  state lives moves up to App.tsx. It would still need a way to expose `refresh`
  outward to the Header (so the Header can re-check after sign-out).

- **Intermediate components would have to forward props they don't use.** If
  Header is inside a Layout component, Layout would need to accept `user` and
  pass it through to Header, even though Layout itself doesn't care about
  auth. Every intermediate component along the path becomes a useless conduit.

- **TypeScript types pollute every signature.** Every component that touches
  the chain has to declare `user`, `refresh`, and so on in its props type, even
  if it doesn't use them.

- **Refactoring becomes painful.** Adding a new piece of auth-related state
  (like a `roles` array or a `permissions` object) means adding a prop to every
  component along every chain that uses any of it.

Context exists specifically to solve this problem. It lets state "tunnel" past
intermediate components that don't care about it. The tradeoff is that
component-to-component data flow becomes invisible (you can't tell from a
component's props what it depends on), so Context is best reserved for
truly cross-cutting concerns: authentication, theming, internationalization,
feature flags. For data that flows linearly (like a parent passing one piece of
state to its direct children), props are clearer.

### Question 6: Coming from C#, how does this React + BFF architecture compare to a Blazor Server or ASP.NET MVC application? What are the trade-offs?

Open-ended question; sample answer:

In **ASP.NET MVC** (server-rendered pages):

- The browser receives complete HTML from the server. There is no SPA. Every
  user action is a request to the server, which responds with new HTML.
- Authentication is handled entirely server-side using ASP.NET Identity or a
  similar framework. The browser holds an authentication cookie. There is no
  separate frontend code to worry about.
- No tokens are exposed to the browser at all (similar to the BFF pattern).
- Pros: simple security model, fewer moving parts.
- Cons: every interaction requires a full server round-trip, which feels less
  responsive. Limited offline support. Harder to provide rich, interactive
  UIs.

In **Blazor Server**:

- The browser still does not hold tokens. The C# code runs on the server, and
  the UI is updated via SignalR.
- This is even closer to the BFF pattern in terms of security: the user's
  session lives entirely on the server.
- Pros: rich interactive UI without exposing tokens to the browser. Use C# for
  both backend and "frontend" code.
- Cons: requires a persistent SignalR connection, which scales differently
  than stateless HTTP. The server is much more involved per user.

In **React + BFF** (what we just built):

- The browser runs the UI as a SPA, with no tokens stored client-side.
- The BFF is the OAuth client and holds tokens server-side.
- Pros: independent deployment of frontend and backend, rich client-side UX,
  can deploy the SPA via a CDN, framework-flexibility (could swap React for
  Vue, Svelte, etc.).
- Cons: more moving parts (SPA, BFF, resource server, auth server). More to
  configure (cookies, OAuth, scopes). Two codebases (TypeScript + Java) to
  maintain.

The React + BFF approach has won for most modern web applications because the
flexibility, independent deployability, and ecosystem advantages outweigh the
extra setup cost. For internal tools where rich UX matters less, ASP.NET MVC
or Blazor Server can still be the better choice -- fewer pieces, less to break.
