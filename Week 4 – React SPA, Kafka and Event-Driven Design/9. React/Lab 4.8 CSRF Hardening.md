# Module 8: React SPA Development with Secure OAuth
## Lab 4.8 -- Hardening the BFF with CSRF Protection

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 8 - React SPA Development with Secure OAuth
> **Estimated time:** 75-90 minutes

---

## Overview

In Lab 4.7 you wired the React SPA to the BFF and got the full system working end to end. To keep the focus on integration, the BFF was left with CSRF protection disabled (the same configuration it had in Lab 4.6). This is acceptable for a development environment that is not exposed to other websites, but it is not acceptable for production.

There is also a subtle issue with the logout flow as it stands. Click Sign out, then click Sign in again, and the user is right back in the app without re-entering credentials. The reason: clicking Sign out only clears the BFF's session. The auth server still considers the user logged in, so the next OAuth flow silently issues a fresh authorization code without showing a login screen. This lab fixes both issues.

In this lab you will:

1. Turn on CSRF protection at the BFF and update the React app to send CSRF tokens with every state-changing request
2. Configure RP-Initiated Logout (an OpenID Connect feature) so that signing out of the app also signs the user out of the auth server, requiring real re-authentication on the next login

By the end of this lab the system will:

1. Set a `BFF_SESSION` cookie (HttpOnly, used for session identification)
2. Set an `XSRF-TOKEN` cookie (NOT HttpOnly, readable by JavaScript)
3. Require the React app to read the `XSRF-TOKEN` cookie and send its value in the `X-XSRF-TOKEN` header on every POST and PUT request
4. Reject any state-changing request that does not include a valid CSRF token
5. Clear both the BFF's session AND the auth server's session on Sign out, so the next Sign in requires real credentials

You will also explore why the CSRF pattern works (the "double-submit cookie" pattern), what attacks it does and does not protect against, and how OIDC RP-Initiated Logout coordinates session lifecycle across two independent servers.

**This lab assumes Lab 4.7 is complete and working.** You will modify three projects: the auth server (`auth-server`), the BFF (`bankbff`), and the React app (`banking-ui`).

By the end of this lab you will be able to:

- Explain CSRF (Cross-Site Request Forgery) attacks and the double-submit cookie defense pattern
- Configure Spring Security CSRF protection appropriately for a JavaScript SPA
- Read cookie values from JavaScript and attach them as request headers
- Articulate which threats CSRF protection mitigates and which it does not (defense in depth)
- Configure OIDC RP-Initiated Logout to coordinate sign-out across the BFF and the auth server
- Recognize when an OAuth flow requires real browser navigation (not `fetch`) and apply the form-submission pattern

---

## Before You Start

You will need three services running for this lab, the same set as Lab 4.7:

| Service | Port | How to start |
|---|---|---|
| Authorization Server | 9000 | Run from IntelliJ |
| Banking Resource Server (`bankserver`) | 8081 | Run from IntelliJ |
| Banking BFF (`bankbff`) | 8080 | Run from IntelliJ |

The React dev server runs on port 5173 (Vite's default).

Confirm Lab 4.7 still works end to end before starting this lab. Open `http://localhost:5173`, sign in as alice, view the accounts list, complete a transfer, and sign out. If any of those steps fails, return to Lab 4.7 and fix the integration before continuing.

### Files you will modify in this lab

| Project | File | Exercise |
|---|---|---|
| `bankbff` | `src/main/java/com/example/bankbff/config/SecurityConfig.java` | 1, 4 |
| `banking-ui` | `src/utils/cookies.ts` (new file) | 2 |
| `banking-ui` | `src/api/client.ts` | 2, 4 |
| `banking-ui` | `src/components/Header.tsx` | 4 |
| `auth-server` | `src/main/java/com/example/authserver/config/AuthorizationServerConfig.java` | 4 |

Exercise 3 has no code changes; it is a verification exercise using the IntelliJ HTTP client.

---

## Architecture

The architecture is identical to Lab 4.7. What changes is the cookies the BFF sets and the headers the React app sends:

**Lab 4.7 cookie state after login:**

```
BFF_SESSION  (HttpOnly, session cookie)
```

**Lab 4.8 cookie state after login:**

```
BFF_SESSION  (HttpOnly, session cookie)
XSRF-TOKEN   (NOT HttpOnly, readable by JavaScript, value rotates per session)
```

**Lab 4.7 request headers on POST /api/transfers:**

```
Accept: application/json
Content-Type: application/json
Cookie: BFF_SESSION=...
```

**Lab 4.8 request headers on POST /api/transfers:**

```
Accept: application/json
Content-Type: application/json
Cookie: BFF_SESSION=...; XSRF-TOKEN=...
X-XSRF-TOKEN: <same value as XSRF-TOKEN cookie>
```

The browser sends both cookies automatically (they are scoped to `localhost`). The new addition is the `X-XSRF-TOKEN` header, which the SPA must add by reading the cookie value and copying it into the header. Spring Security verifies that the header matches the cookie before processing the request.

This is the **double-submit cookie** pattern. The defense works because of the same-origin policy: an attacker's website (`evil.com`) cannot read the `XSRF-TOKEN` cookie set by `localhost:5173`, so it cannot set the `X-XSRF-TOKEN` header to the matching value, so its forged request fails the CSRF check.

---

## Exercise 1 -- Enable CSRF Protection in the BFF

**Estimated time:** 20 minutes
**Topics covered:** Spring Security CSRF configuration, CookieCsrfTokenRepository, the difference between XOR-encoded and raw CSRF tokens
**File modified:** `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java`

### Context

Spring Security has built-in CSRF protection. It is enabled by default. In Lab 4.6 we explicitly disabled it (`csrf(AbstractHttpConfigurer::disable)`) to keep that lab focused on the OAuth flow. To turn it back on, we replace that disable call with a configuration block that tells Spring Security how the SPA will deliver the CSRF token.

There are two pieces of configuration:

1. **Where the token lives.** `CookieCsrfTokenRepository.withHttpOnlyFalse()` puts the token in a cookie called `XSRF-TOKEN`. The `withHttpOnlyFalse()` part is critical: by default Spring Security would set the cookie as HttpOnly (unreadable by JavaScript), but the SPA needs to read it. Marking the cookie as not-HttpOnly is exactly what lets the double-submit pattern work.

2. **How the token is encoded.** Spring Security 6 introduced XOR-encoded CSRF tokens by default to mitigate the BREACH attack. This requires the SPA to perform the same XOR step before sending the token back, which is awkward to implement. For SPAs, the simpler approach is to revert to raw token comparison using `CsrfTokenRequestAttributeHandler` with `setCsrfRequestAttributeName(null)`. The trade-off is acceptable because the BREACH attack requires conditions that are unlikely in a properly configured BFF.

### Task 1.1 -- Update SecurityConfig.java in the BFF

Open `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java`.

Find the `csrf` line at the top of the security filter chain configuration. It currently looks like:

```java
.csrf(AbstractHttpConfigurer::disable)
```

Replace it with:

```java
.csrf(csrf -> {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(csrfHandler);
})
```

Update the imports at the top of the file. Remove this import (no longer used):

```java
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
```

Add these two imports:

```java
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
```

A few details worth understanding:

- `CookieCsrfTokenRepository.withHttpOnlyFalse()` does two things at once. It selects the cookie as the storage location for the CSRF token (instead of the HTTP session), and it marks the cookie as readable by JavaScript.
- `CsrfTokenRequestAttributeHandler` with `setCsrfRequestAttributeName(null)` switches off the default XOR encoding. Without this line, the cookie value and the header value would be different XOR-encoded representations of the same underlying token, and your SPA would need to do extra work to handle that.
- The cookie name (`XSRF-TOKEN`) and the header name (`X-XSRF-TOKEN`) are conventions that match what most SPA frameworks already understand. You do not need to configure them; they are the defaults.

### Task 1.2 -- Restart the BFF

In IntelliJ, stop the BFF (red Stop button). Start it again (green Run button). Wait for `Started BankbffApplication` in the console.

> **Important:** Do a full Stop and Run, not just a hot-reload. Spring Boot's hot-reload handles most code changes cleanly, but security filter chain changes sometimes leave the application in an inconsistent state. If you see a 500 error when clicking Sign in (with no useful output in the auth server console), a full BFF restart is the most likely fix.

### Verify Exercise 1

Open a fresh browser window. Navigate to `http://localhost:5173`. Sign in as alice (the same flow as Lab 4.7).

After login, open DevTools (F12) and go to **Application** (Chrome) or **Storage** (Firefox) -> **Cookies** -> `http://localhost:5173`. You should now see two cookies, not one:

- `BFF_SESSION` (HttpOnly)
- `XSRF-TOKEN` (not HttpOnly, value visible)

The accounts list still loads correctly (GET requests do not require a CSRF token).

Now try a transfer. The transfer should **fail** with a 403 Forbidden response. Check the Console tab; you will see an error message about the failed POST. Check the Network tab and inspect the failed request: there is no `X-XSRF-TOKEN` header on the request, and the BFF rejects it.

The CSRF protection is now active. The next exercise teaches the React app to send the token.

---

## Exercise 2 -- Send the CSRF Token from the React App

**Estimated time:** 20 minutes
**Topics covered:** Reading cookies from JavaScript, modifying request headers, defensive programming for header construction
**Files modified:** `banking-ui/src/utils/cookies.ts` (new), `banking-ui/src/api/client.ts`

### Context

The React app needs to do three things on every state-changing request (POST, PUT, DELETE):

1. Read the `XSRF-TOKEN` cookie value from `document.cookie`
2. Add a `X-XSRF-TOKEN` header to the outgoing request with that same value
3. Skip both steps if the cookie is not present (which is the case before login completes)

You will build a small helper for reading cookies, then update the API client to use it on the state-changing call (`postTransfer`). The `logout` function also needs to send a CSRF token; you will update that in Exercise 4 as part of the larger logout rewrite.

### Task 2.1 -- Create a cookie helper

Create a new file `banking-ui/src/utils/cookies.ts`. The `utils` folder will not exist yet; create it as part of the file creation.

```ts
/**
 * Read a cookie value by name.
 *
 * Browsers expose cookies as a single semicolon-separated string in
 * document.cookie. This helper splits the string and returns the
 * value of the named cookie (URL-decoded), or null if not present.
 */

export function readCookie(name: string): string | null {
  const cookies = document.cookie.split(';');
  for (const cookie of cookies) {
    const [key, value] = cookie.trim().split('=');
    if (key === name) {
      return decodeURIComponent(value);
    }
  }
  return null;
}
```

The helper is small but worth understanding line by line:

- `document.cookie` returns all cookies for the current origin as one string, formatted like `name1=value1; name2=value2; name3=value3`.
- Splitting on `;` produces a list of name-value pairs, with leading whitespace on all but the first.
- Splitting each pair on `=` (just the first occurrence is what we want; for cookie values with `=` characters this approach loses everything after the first `=`, which is rare in practice and not an issue for our XSRF-TOKEN).
- `decodeURIComponent` reverses any encoding the server applied. CSRF token values typically do not contain characters that need encoding, but being defensive here costs nothing.

### Task 2.2 -- Update the API client to send the CSRF header

Open `banking-ui/src/api/client.ts`. Add an import at the top:

```ts
import { readCookie } from '../utils/cookies';
```

At the bottom of the file (after `safeReadErrorMessage`), add a helper that returns the CSRF header object:

```ts
function csrfHeader(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token } : {};
}
```

This helper returns an object suitable for spreading into a `headers` object. When the cookie is missing (before login, for example), it returns an empty object so spreading it adds no headers.

Now update `postTransfer`. Find the current `headers` object inside the fetch call:

```ts
headers: {
  'Accept': 'application/json',
  'Content-Type': 'application/json',
},
```

Change it to spread in the CSRF header:

```ts
headers: {
  'Accept': 'application/json',
  'Content-Type': 'application/json',
  ...csrfHeader(),
},
```

The two GET functions (`getCurrentUser` and `getAccounts`) do not need the CSRF header because Spring Security only requires it for state-changing requests (POST, PUT, PATCH, DELETE). GET requests pass through without a CSRF check.

The `logout` function also needs to send the CSRF token, but it has a deeper problem that the same fix will not solve. You will rewrite `logout` entirely in Exercise 4. For now, leave it as it is and ignore the fact that Sign out will fail with a 403. This is expected and will be fixed shortly.

### Verify Exercise 2

Reload the React app in the browser (sign in again if needed). Try a transfer of $50 from ACC-001 to ACC-002.

The transfer should now succeed. The accounts list updates with the new balances.

In DevTools, open the Network tab and inspect the `POST /api/transfers` request. The Request Headers section should now show:

```
X-XSRF-TOKEN: <some-uuid-looking-value>
```

The value should match the value of the `XSRF-TOKEN` cookie shown in the Application tab.

Sign out will fail at this point because we have not updated `logout`. That is expected and Exercise 4 fixes it.

---

## Exercise 3 -- End-to-End CSRF Verification

**Estimated time:** 15 minutes
**Topics covered:** Black-box testing of CSRF protection, defensive verification
**Files modified:** none (verification using the IntelliJ HTTP client)

### Context

A useful exercise after enabling any security control is to verify it actually does what you think it does. CSRF protection in particular has a reputation for being misconfigured (people enable it but accidentally exempt the routes that needed protection most). Two simple tests confirm the protection is working.

The IntelliJ HTTP client lets you send raw HTTP requests from the IDE. Either create a new `.http` scratch file (Ctrl+Shift+Alt+Insert on Windows/Linux, Cmd+Shift+Alt+Insert on Mac, then choose **HTTP Request**) or right-click any folder in the BFF project and choose **New** -> **HTTP Request**.

### Task 3.1 -- Verify the protection is active

Add this request to your `.http` file:

```http
### Try a transfer without a CSRF token. Should fail with 403.
# @no-cookie-jar
POST http://localhost:8080/api/transfers
Accept: application/json
Content-Type: application/json
Cookie: BFF_SESSION=PASTE_YOUR_BFF_SESSION_VALUE_HERE

{
  "fromAccountNumber": "ACC-001",
  "toAccountNumber": "ACC-002",
  "amount": 25.00
}
```

> **About `# @no-cookie-jar`:** IntelliJ's HTTP Client maintains its own cookie jar. When you run requests against `localhost:8080`, IntelliJ remembers any `Set-Cookie` responses and automatically attaches those cookies to subsequent requests, even when you also write a `Cookie:` header manually. The result is that your typed cookies get merged with IntelliJ's stored cookies, often with a different XSRF-TOKEN than you intended. The `# @no-cookie-jar` directive tells IntelliJ to use only the cookies you typed for this specific request. This makes the test predictable and is essential for verifying CSRF behavior, where the precise cookies sent matter.

To populate `BFF_SESSION`:

1. In the browser, log in via the React app
2. Open DevTools -> Application -> Cookies -> `http://localhost:5173`
3. Copy the `BFF_SESSION` value
4. Paste it into the request above (replacing `PASTE_YOUR_BFF_SESSION_VALUE_HERE`)

Run the request. Expected response: **403 Forbidden**.

This confirms that even with a valid session cookie, the BFF rejects state-changing requests that lack the CSRF token. This is the protection working as designed.

### Task 3.2 -- Verify the protection allows valid requests

Update the request to include the CSRF header. You will need both the `XSRF-TOKEN` cookie value and the `X-XSRF-TOKEN` header to match.

```http
### Try a transfer WITH a CSRF token. Should succeed.
# @no-cookie-jar
POST http://localhost:8080/api/transfers
Accept: application/json
Content-Type: application/json
Cookie: BFF_SESSION=PASTE_BFF_SESSION_HERE; XSRF-TOKEN=PASTE_XSRF_TOKEN_HERE
X-XSRF-TOKEN: PASTE_THE_SAME_XSRF_TOKEN_VALUE

{
  "fromAccountNumber": "ACC-001",
  "toAccountNumber": "ACC-002",
  "amount": 25.00
}
```

Copy `XSRF-TOKEN` from DevTools the same way you copied `BFF_SESSION`. Paste the same value in two places: into the Cookie line (after `XSRF-TOKEN=`), and into the `X-XSRF-TOKEN` header.

> **Common mistakes to watch for:**
>
> - Forgetting `XSRF-TOKEN=` before the value in the Cookie header. The cookie must be a proper `name=value` pair. A bare UUID is not a valid cookie.
> - The header value and the cookie value being different UUIDs. They must match exactly. CSRF protection compares them directly.
> - Re-using a token after signing out and back in. The XSRF-TOKEN value rotates per session; refresh both values from DevTools after any login change.

Run the request. Expected response: **200 OK** with a transaction ID.

If the test passes, the CSRF protection is correctly configured to reject invalid requests and accept valid ones.

### Verify Exercise 3

Both tests above are themselves the verification:

| Test | Expected response |
|---|---|
| POST /api/transfers without `X-XSRF-TOKEN` header | 403 Forbidden |
| POST /api/transfers with valid `X-XSRF-TOKEN` matching the cookie | 200 OK |

If both behave as described, your CSRF configuration is working.

If Test 1 returns 200, CSRF is not enforced. Most likely cause: the `csrf(AbstractHttpConfigurer::disable)` line is still present somewhere in SecurityConfig.

If Test 2 returns 403 even when you think the values match, the most common causes are:

- The `# @no-cookie-jar` directive is missing, so IntelliJ is silently merging your typed cookies with cookies it remembers from earlier requests. The actual XSRF-TOKEN sent may not match the one you typed. To check what was actually sent, expand the **Request** section in the response panel and read the `Cookie:` header. If you see an XSRF-TOKEN value that differs from what you typed, the cookie jar is the culprit.
- The Cookie header is missing `XSRF-TOKEN=` before the UUID. Cookies are name-value pairs; a bare UUID is not a valid cookie.
- The header value and the cookie value are different UUIDs (they must match exactly).
- The session changed between copying values (any sign-out and sign-in rotates the XSRF-TOKEN; copy fresh values).
- The XOR encoding is still active because `setCsrfRequestAttributeName(null)` was not added or was set to a non-null value.

---

## Exercise 4 -- Configure RP-Initiated Logout

**Estimated time:** 25 minutes
**Topics covered:** OIDC RP-Initiated Logout, multi-server session coordination, OAuth client logout flow, browser navigation vs. fetch
**Files modified:** `auth-server/src/main/java/com/example/authserver/config/AuthorizationServerConfig.java`, `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java`, `banking-ui/src/api/client.ts`, `banking-ui/src/components/Header.tsx`

### Context

Test the current Sign out behavior before reading further. Sign in as alice. Click Sign out. The Welcome screen appears, which suggests the logout worked. Click Sign in. You are immediately back in the app, no login form, no credentials prompt. That is the bug.

The reason is that two servers each have their own session, and they do not coordinate by default:

- The BFF (port 8080) has a session, identified by the `BFF_SESSION` cookie scoped to `localhost:5173`.
- The auth server (port 9000) has its own session, identified by its own session cookie scoped to `localhost:9000`.

When the user clicks Sign out, the React app calls `POST /logout` on the BFF. The BFF clears its session and the `BFF_SESSION` cookie disappears. Spring Security's default logout handler stops there. The auth server is not notified.

When the user clicks Sign in again, the OAuth flow redirects to the auth server. The auth server still has its own session cookie for `localhost:9000`. It recognizes the user without asking for credentials. It issues a fresh authorization code immediately and the browser comes back to the BFF, which creates a new session. The user appears logged in again with no credential challenge.

The fix is **OIDC RP-Initiated Logout**, a standardized extension to OpenID Connect. The flow is:

1. User clicks Sign out
2. BFF clears its local session
3. BFF redirects the browser to the auth server's `end_session_endpoint`
4. Auth server clears ITS session
5. Auth server redirects the browser back to a configured `post_logout_redirect_uri`
6. The browser arrives at that URL with no active sessions on either server
7. Next Sign in requires real credentials on the auth server's login form

There is one important wrinkle. The current React `logout` function uses `fetch()`, but `fetch()` cannot drive the browser through cross-origin HTML redirects. The redirect chain in step 2-5 above involves the browser navigating across origins (`localhost:5173` -> `localhost:9000` -> `localhost:5173`), and `fetch()` cannot follow that. Instead, we will rewrite `logout` to submit a real HTML form, which causes the browser to navigate naturally through the redirect chain. This is the same reason the Sign-in button is an `<a>` tag rather than a fetch call.

### Task 4.1 -- Register the post-logout redirect URI with the auth server

**File:** `auth-server/src/main/java/com/example/authserver/config/AuthorizationServerConfig.java`

Just like the OAuth login redirect URI, the post-logout redirect URI must be pre-registered on the auth server. Otherwise the auth server refuses to redirect the browser back after sign-out.

Find the `bankClientBff` `RegisteredClient` registration. Right after the two `.redirectUri(...)` lines, add two `.postLogoutRedirectUri(...)` lines:

```java
// Direct access (Lab 4.6 standalone testing)
.redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
// Through Vite proxy (Lab 4.7 React integration)
.redirectUri("http://localhost:5173/login/oauth2/code/bank-auth")
// Direct access post-logout (Lab 4.6)
.postLogoutRedirectUri("http://localhost:8080/")
// Through Vite proxy post-logout (Lab 4.7+)
.postLogoutRedirectUri("http://localhost:5173/")
```

Two URIs again, for the same reason as the login redirect URIs: one for direct testing on port 8080, one for the React app proxied flow on port 5173.

Restart the auth server (red Stop, green Run). Wait for `Started AuthServerApplication` in the console.

### Task 4.2 -- Update SecurityConfig.java in the BFF

**File:** `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java`

You need to add a new bean (a `LogoutSuccessHandler`) and wire it into the existing `logout` configuration.

Add these three imports at the top:

```java
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
```

Add a new `@Bean` method inside the `SecurityConfig` class. Put it right after the closing brace of the `securityFilterChain(...)` method:

```java
@Bean
public LogoutSuccessHandler oidcLogoutSuccessHandler(
        ClientRegistrationRepository clientRegistrationRepository) {

    OidcClientInitiatedLogoutSuccessHandler handler =
            new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

    // After the auth server completes logout, send the browser back here.
    // Must match a postLogoutRedirectUri registered on the auth server.
    handler.setPostLogoutRedirectUri("http://localhost:5173/");

    return handler;
}
```

Now update the `logout` configuration in `securityFilterChain` to use this handler. Find the current line:

```java
.logout(Customizer.withDefaults());
```

Replace it with:

```java
.logout(logout -> logout
        .logoutSuccessHandler(oidcLogoutSuccessHandler(
                http.getSharedObject(ClientRegistrationRepository.class))));
```

The `http.getSharedObject(ClientRegistrationRepository.class)` call retrieves the same `ClientRegistrationRepository` bean Spring uses elsewhere in the security configuration. The handler needs it to look up the auth server's `end_session_endpoint`.

Restart the BFF (red Stop, green Run). Wait for `Started BankbffApplication`.

### Task 4.3 -- Rewrite the React logout function

**File:** `banking-ui/src/api/client.ts`

The current `logout` function uses `fetch()`. As explained above, `fetch()` cannot drive the browser through the cross-origin redirect chain that RP-Initiated Logout requires; the browser blocks it as a CORS error. The fix is to submit a real HTML form, which lets the browser navigate naturally.

Find the `logout` function in `client.ts`:

```ts
export async function logout(): Promise<void> {
  const response = await fetch('/logout', {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      ...csrfHeader(),
    },
  });
  if (!response.ok && response.status !== 302) {
    throw new Error(`Logout failed: ${response.status}`);
  }
}
```

Replace it with this form-submission version:

```ts
export function logout(): void {
  const token = readCookie('XSRF-TOKEN');

  // Build and submit a form so the browser navigates through the
  // logout redirect chain (BFF -> auth server -> back to /).
  // fetch() cannot follow cross-origin HTML redirects, which is why
  // we use a real form submission here instead.
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  form.style.display = 'none';

  if (token) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = '_csrf';
    input.value = token;
    form.appendChild(input);
  }

  document.body.appendChild(form);
  form.submit();
}
```

Key changes from the original:

- Return type is `void`, not `Promise<void>`. The function is synchronous and never returns because the browser navigates away mid-call.
- No `fetch` call. The function builds a hidden HTML form and submits it.
- The CSRF token goes in a hidden form field named `_csrf` (Spring Security's standard form-based CSRF parameter), not in the `X-XSRF-TOKEN` header. Form submissions can't set custom headers, so Spring Security accepts the token in this alternate location.

### Task 4.4 -- Update the Header to handle the new logout

**File:** `banking-ui/src/components/Header.tsx`

The current `handleSignOut` is async and calls `refresh()` after the logout call completes. With form-based logout, the page navigates to a new URL, which triggers a full page reload. The reload runs the AuthProvider afresh, which calls `/api/me`, gets a 401, and renders the Welcome screen. No explicit refresh is needed.

Find the current `handleSignOut`:

```ts
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
```

Replace it with:

```ts
function handleSignOut() {
  logoutApi();
  // No refresh needed: the form submission causes a full page navigation,
  // and the app reloads with no session, which renders the Welcome screen.
}
```

Note that `handleSignOut` is no longer `async`, has no `try/catch`, and no longer calls `refresh()`. The form submission is fire-and-forget; the browser takes over and reloads the page when the redirect chain completes.

The `refresh` destructuring on the line `const { user, refresh } = useAuth();` can also be cleaned up since `refresh` is no longer called:

```ts
const { user } = useAuth();
```

### Verify Exercise 4

Open a fresh browser window (or clear cookies for both `localhost:5173` and `localhost:9000`). Then run through this sequence:

1. Open `http://localhost:5173`. You should see the Welcome screen.
2. Click Sign in. You should be redirected to the auth server's login page.
3. Log in as alice / password. You arrive at the React app, accounts visible.
4. Click Sign out. **Watch the address bar carefully.** You should see a brief redirect to `http://localhost:9000/connect/logout?...` (the auth server's logout endpoint) and then back to `http://localhost:5173/`. The Welcome screen appears.
5. Click Sign in again. **You should now see the auth server's login page**, asking for credentials. The auth server has no session for the user any more.
6. Log in as alice / password. You arrive back in the app.

Step 5 is the key change. Before this exercise, step 5 silently logged the user back in. After this exercise, step 5 prompts for credentials. The auth server's session is being properly cleared.

> **If the address bar does not bounce through `localhost:9000` during Sign out:** the form submission may be failing silently. Open DevTools -> Network tab, check **Preserve log**, then click Sign out. You should see a `POST /logout` request, followed by a redirect to `localhost:9000/connect/logout`, followed by a redirect back to `localhost:5173/`. If the redirect chain breaks at any point, the response status code on that request will indicate where (a 4xx points at a registration mismatch on the auth server; a 5xx points at the BFF).

### Why this matters in production

Banking apps in particular cannot leave residual sessions on the identity provider after a user has signed out. Consider a shared workstation: alice signs in, does her banking, signs out, walks away. bob sits down at the same machine. Without RP-Initiated Logout, bob clicks Sign in, the auth server still has alice's session, and bob is suddenly authenticated as alice with no challenge. This is the kind of finding that turns a routine pen test into a serious incident report.

Production deployments often go further and configure session timeouts on the auth server to expire idle sessions automatically, but RP-Initiated Logout is the foundational fix for the user-initiated case.

---

## What You Have Built

The system is now meaningfully more production-ready. Specifically:

- **The BFF rejects POST/PUT/DELETE requests that do not include a valid CSRF token.** A malicious website cannot trigger transfers on your bank's behalf, even if it can convince the user's browser to send a request, because it cannot read the XSRF-TOKEN cookie to set a matching header.
- **The React app reads the XSRF-TOKEN cookie and sends it on every state-changing request.** This is transparent to users; they never see the token themselves.
- **The two cookies serve distinct purposes.** BFF_SESSION is HttpOnly and identifies the user's session; XSRF-TOKEN is JavaScript-readable and acts as a per-session challenge value. Each is the right cookie for its job.
- **The `withHttpOnlyFalse()` configuration choice was deliberate.** The XSRF-TOKEN cookie must be readable by JavaScript for the SPA to perform the double-submit pattern. This is safe because the XSRF-TOKEN value is not itself a credential; it is only useful when paired with the matching session cookie, and an attacker on a different origin cannot read either cookie.
- **Sign out clears both sessions.** RP-Initiated Logout coordinates the BFF and the auth server so a user who signs out is genuinely signed out, not just locally signed out with a residual identity provider session.
- **The Sign out flow uses real browser navigation.** Unlike most actions in the app (which use `fetch`), Sign out submits a hidden HTML form so the browser can follow the cross-origin redirect chain through the auth server. This pattern (form submission instead of fetch) appears anywhere an OAuth-related action requires the browser to navigate across origins.

### A summary of how the protection works

1. User logs in. The BFF sets two cookies: BFF_SESSION (HttpOnly) and XSRF-TOKEN (readable).
2. User makes a transfer in the React app. The app reads XSRF-TOKEN from the cookie and adds the value to the X-XSRF-TOKEN header on the POST request.
3. Spring Security on the BFF sees the cookie and the header. It compares the values. They match, so the request is allowed.
4. Now imagine an attacker. The user visits evil.com in another tab. evil.com tries to POST to `localhost:8080/api/transfers`. The browser automatically attaches the BFF_SESSION cookie (cookies attach by hostname, not by initiator). But evil.com cannot read the XSRF-TOKEN cookie because of the same-origin policy. So evil.com cannot set a matching X-XSRF-TOKEN header. Spring Security sees the missing or mismatched header and rejects the request with 403.

The same-origin policy is what makes the entire pattern work. Without it, JavaScript on any website could read cookies from any other website, and CSRF protection would not function.

---

## Reflection Questions

In a new file `lab-4.8-notes.md` in the `banking-ui` project root, answer these:

1. The lab uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`. Why does the cookie need to be readable by JavaScript? What would break if it were HttpOnly like the session cookie?

2. Imagine deploying this app to production where the React build is served from `app.examplebank.com` and the BFF runs at `api.examplebank.com`. What changes to the CSRF configuration, the cookie attributes, and the React fetch calls would be required? (Reference the SameSite, Secure, and credentials topics.)

3. Walk through how a CSRF attack from `evil.com` against this app would fail step by step. At which exact step does the attack break, and why?

4. The `XSRF-TOKEN` cookie is readable by JavaScript. How is this not a credential leak? What makes the XSRF token safe to expose, when the access token is not?

5. What does CSRF protection NOT defend against? Specifically, if an attacker can run JavaScript on the same origin as the SPA (through XSS), what can they still do? This is the principle of "defense in depth" -- CSRF is one control, not the only control.

6. The `csrfHeader()` helper returns an empty object when no cookie is present. Why is that the right choice? What would happen if you returned `{ 'X-XSRF-TOKEN': '' }` instead, or if you threw an error?

7. Before Exercise 4, clicking Sign out and then Sign in immediately put the user back in the app with no credential prompt. Walk through what was actually happening. Why did the user appear to be logged out (the Welcome screen appeared) but then re-authenticate silently? What state was preserved, and where?

8. RP-Initiated Logout requires the auth server's post-logout redirect URI to be pre-registered, similar to the OAuth login redirect URI. Why is this pre-registration necessary? What attack would be possible if the auth server accepted any URL as a post-logout redirect target?

9. The original `logout` function used `fetch()`. The corrected version submits an HTML form. Why does `fetch()` not work for this case? What other places in a typical OAuth-aware application require real browser navigation (not `fetch`) for the same reason?

---

## What's Next

The BFF is now hardened against CSRF. The combined system (Lab 4.6 + 4.7 + 4.8) is a reasonable starting point for a production deployment. There is still more to do in a real deployment:

- HTTPS-only cookies (the `Secure` flag) and HTTPS-everywhere infrastructure
- Strict Transport Security headers
- Content Security Policy headers to limit what JavaScript can run
- Rate limiting on authentication and state-changing endpoints
- Audit logging of security-relevant events
- Penetration testing before launch

These topics are covered in Module 10 (Testing, SAST, and Security Hardening). For Module 8 you have completed the OAuth-and-CSRF hardening sequence and are ready for the capstone project.
