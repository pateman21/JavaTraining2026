# Module 8: React SPA Development with Secure OAuth
## Lab 4.8 -- Solutions and Checkpoint Answers

> **Course:** MD282 - Java Full-Stack Development
> **Purpose:** This file contains completed code for every modified file in Lab 4.8
> and written answers to every Reflection question. Use it to verify your work
> after attempting each task yourself. Reading the solution before attempting
> the task defeats the purpose of the exercise.

---

## How to Use This File

Each section below corresponds to an exercise in the lab. The complete final
contents of each modified file are shown so you can confirm your version
matches. The reflection-question answers serve as discussion prompts for the
class debrief.

### Files modified in this lab

| Project | File | Exercise |
|---|---|---|
| `bankbff` | `src/main/java/com/example/bankbff/config/SecurityConfig.java` | 1, 4 |
| `banking-ui` | `src/utils/cookies.ts` (new file) | 2 |
| `banking-ui` | `src/api/client.ts` | 2, 4 |
| `banking-ui` | `src/components/Header.tsx` | 4 |
| `auth-server` | `src/main/java/com/example/authserver/config/AuthorizationServerConfig.java` | 4 |

---

## Exercise 1 -- Enable CSRF Protection in the BFF

**File:** `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java`

### Complete SecurityConfig.java (after Exercise 1)

This is the state at the end of Exercise 1. Exercise 4 will further modify
this file to add the OIDC logout success handler. The Exercise 4 final state
appears later in this document.

```java
package com.example.bankbff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> {
                    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
                    csrfHandler.setCsrfRequestAttributeName(null);

                    csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler);
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**", "/error", "/").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> {
                    RequestMatcher apiRequest =
                            new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
                    ex.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            apiRequest);
                })
                .oauth2Login(Customizer.withDefaults())
                .logout(Customizer.withDefaults());

        return http.build();
    }
}
```

The import that was removed (no longer needed because we replaced the
`csrf(AbstractHttpConfigurer::disable)` line):

```java
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
```

The two new imports:

```java
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
```

### Why CookieCsrfTokenRepository

Spring Security has three built-in CsrfTokenRepository implementations:

- `HttpSessionCsrfTokenRepository` (the default): stores the token in the HTTP
  session. The SPA cannot easily access the session attribute, so this does not
  work for an SPA without server-side rendering of the token into a hidden form
  field.
- `CookieCsrfTokenRepository`: stores the token in a cookie. The SPA can read
  the cookie value with JavaScript and include it in a request header. This is
  the SPA-friendly pattern.
- `LazyCsrfTokenRepository`: a wrapper around another repository that defers
  token generation until first use. Rarely used directly.

For an SPA, `CookieCsrfTokenRepository.withHttpOnlyFalse()` is the correct
choice. The static factory method `withHttpOnlyFalse()` exists specifically for
this purpose.

### Why CsrfTokenRequestAttributeHandler with setCsrfRequestAttributeName(null)

Spring Security 6 introduced XOR-encoded CSRF tokens by default to mitigate the
BREACH attack, which is a side-channel attack that observes HTTP response sizes
to extract small pieces of secret data. The XOR encoding randomizes the token
on every render, defeating the size-correlation attack.

The cost is that the SPA must perform the same XOR step before sending the token
back. Specifically, when the cookie is read, the value is the XOR-encoded form;
when the cookie is set, Spring Security generated the encoded form. The header
value the SPA sends must match the encoded form, but in many SPA framework
patterns the SPA simply copies the cookie value to the header, which would not
match because the underlying tokens (before encoding) need to be compared, not
the encoded representations.

`CsrfTokenRequestAttributeHandler` with `setCsrfRequestAttributeName(null)`
disables the XOR encoding. The cookie value is the raw token, the header value
must be the same raw token, and the comparison is direct. This is the simpler
pattern and is appropriate for SPAs in most BFF deployments because the BREACH
attack requires conditions that do not commonly apply: the response must include
attacker-controlled content alongside the secret, and HTTPS compression must be
enabled in a way that allows size analysis.

For BFFs that proxy JSON to/from APIs (no rendered HTML mixing user input with
secrets), the BREACH attack vector is essentially closed at the architectural
level, so the XOR encoding adds complexity without meaningful additional
protection.

---

## Exercise 2 -- Send the CSRF Token from the React App

**Files:** `banking-ui/src/utils/cookies.ts` (new), `banking-ui/src/api/client.ts`

### Complete src/utils/cookies.ts

```ts
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

### Complete src/api/client.ts (state after Exercise 2)

This is the state at the end of Exercise 2. The `logout` function still uses
`fetch()` at this point and will fail when called. Exercise 4 rewrites it to
use form submission. The Exercise 4 final state of `client.ts` appears later
in this document.

```ts
import type {
  Account,
  TransferRequest,
  TransferResponse,
  User,
} from './types';
import { readCookie } from '../utils/cookies';

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
      ...csrfHeader(),
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const message = await safeReadErrorMessage(response);
    throw new Error(message || `Transfer failed: ${response.status}`);
  }
  return response.json();
}

// NOTE: Exercise 4 rewrites this entire function. At the end of Exercise 2,
// logout still uses fetch and will fail because fetch cannot follow the
// cross-origin redirect chain that RP-Initiated Logout requires. Sign out
// will not work end-to-end until Exercise 4 is complete.
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

function csrfHeader(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token } : {};
}
```

### Why the helper returns an empty object when the cookie is absent

`readCookie('XSRF-TOKEN')` returns `null` when the cookie is not present. This
happens in two situations:

1. Before the user logs in (no session, no CSRF cookie).
2. After logout (the BFF clears both cookies).

Returning an empty object when the cookie is absent has two benefits:

- Spreading `{}` into the headers object adds nothing. The fetch call still
  works correctly for endpoints that do not require CSRF (the GET endpoints).
- For endpoints that do require CSRF, the missing header naturally produces a
  403 from the BFF, which the SPA can interpret as "you need to log in" and
  trigger a re-authentication flow.

The alternative (returning `{ 'X-XSRF-TOKEN': '' }` or throwing an error) would
make every pre-login fetch fail in a confusing way. The empty-object return is
the most permissive option that still produces correct behavior.

---

## Exercise 3 -- End-to-End CSRF Verification

The two tests in the lab are themselves the verification. Successful results:

| Test | Expected response | What it proves |
|---|---|---|
| POST /api/transfers without `X-XSRF-TOKEN` header | 403 Forbidden | The BFF actively rejects CSRF-missing requests |
| POST /api/transfers with valid `X-XSRF-TOKEN` matching the cookie | 200 OK | The BFF accepts properly tokenized requests |

If Test 1 returns 200, CSRF is not enforced. Most likely cause: the
`csrf(AbstractHttpConfigurer::disable)` line is still present somewhere in
SecurityConfig.

If Test 2 returns 403 even when you think you have a matching token, the most
likely causes are, in order of frequency:

- **IntelliJ's HTTP Client cookie jar is overriding your typed cookies.** Without
  the `# @no-cookie-jar` directive, IntelliJ merges remembered cookies with what
  you typed, so the actual XSRF-TOKEN sent may differ from the one you wrote.
  To diagnose, expand the **Request** section in IntelliJ's response panel and
  read the actual `Cookie:` header that was sent. If the XSRF-TOKEN there
  differs from what you wrote, the cookie jar is the cause.
- **Missing `XSRF-TOKEN=` prefix** in the Cookie header. Cookies are
  name-value pairs; a bare UUID is not a valid cookie and gets ignored.
- **Header and cookie values differ.** Spring Security compares them byte by
  byte. They must be identical UUIDs.
- **Stale token after a session change.** Signing out and back in rotates the
  XSRF-TOKEN. After any session change, copy fresh values from DevTools.
- The XOR encoding is still active because `setCsrfRequestAttributeName(null)`
  was not added or was set to a non-null value.
- The header name is misspelled (e.g., `XSRF-TOKEN` instead of `X-XSRF-TOKEN`).

---

## Exercise 4 -- Configure RP-Initiated Logout

**Files modified:**
- `auth-server/src/main/java/com/example/authserver/config/AuthorizationServerConfig.java`
- `bankbff/src/main/java/com/example/bankbff/config/SecurityConfig.java` (final state)
- `banking-ui/src/api/client.ts` (final state)
- `banking-ui/src/components/Header.tsx`

### Complete bankClientBff registration in AuthorizationServerConfig.java

The registration should now have two `.redirectUri(...)` lines and two
`.postLogoutRedirectUri(...)` lines:

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
        // Direct access post-logout (Lab 4.6)
        .postLogoutRedirectUri("http://localhost:8080/")
        // Through Vite proxy post-logout (Lab 4.7+)
        .postLogoutRedirectUri("http://localhost:5173/")
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

### Final SecurityConfig.java (combining Exercise 1 and Exercise 4 changes)

This is the complete final state of SecurityConfig.java with both CSRF
configuration and the OIDC logout handler:

```java
package com.example.bankbff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> {
                    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
                    csrfHandler.setCsrfRequestAttributeName(null);

                    csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler);
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**", "/error", "/").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> {
                    RequestMatcher apiRequest =
                            new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
                    ex.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            apiRequest);
                })
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(
                                http.getSharedObject(ClientRegistrationRepository.class))));

        return http.build();
    }

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
}
```

### Final src/api/client.ts (combining Exercise 2 and Exercise 4 changes)

```ts
import type {
  Account,
  TransferRequest,
  TransferResponse,
  User,
} from './types';
import { readCookie } from '../utils/cookies';

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
      ...csrfHeader(),
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const message = await safeReadErrorMessage(response);
    throw new Error(message || `Transfer failed: ${response.status}`);
  }
  return response.json();
}

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

function csrfHeader(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token } : {};
}
```

### Final src/components/Header.tsx

```tsx
import { useAuth } from '../auth/AuthContext';
import { logout as logoutApi } from '../api/client';

export function Header() {
  const { user } = useAuth();

  function handleSignOut() {
    logoutApi();
    // No refresh needed: the form submission causes a full page navigation,
    // and the app reloads with no session, which renders the Welcome screen.
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

### Why this fix matters

Before this exercise, the system had two independent sessions:

- The BFF session, identified by the `BFF_SESSION` cookie scoped to `localhost:5173`
- The auth server session, identified by the auth server's session cookie scoped to `localhost:9000`

These sessions are stored on different servers, identified by different cookies,
scoped to different hostnames. Browsers do not share cookies across hostnames, so
neither server has visibility into the other's session.

When the BFF cleared its session on Sign out, the auth server's session continued
to exist. The next OAuth flow contacted the auth server with the auth server's
own session cookie still present, so the auth server recognized the user without
asking for credentials.

OIDC RP-Initiated Logout fixes this by adding a redirect step. After the BFF
clears its session, the browser is sent to the auth server's `end_session_endpoint`,
which clears the auth server's session and then redirects the browser back to a
configured post-logout URL. Both sessions are now cleared and the user must
re-authenticate from scratch on the next Sign in.

### Why fetch() does not work for logout

The original `logout` function used `fetch('/logout', { method: 'POST' })`. With
the `OidcClientInitiatedLogoutSuccessHandler` wired in, that POST returns a 302
redirect to `http://localhost:9000/connect/logout?...`. The browser tries to
follow the redirect on behalf of the fetch call, but two issues stop it:

1. **Cross-origin HTML redirect.** The auth server is on a different origin
   (`localhost:9000` vs. `localhost:5173`). Browsers refuse to let `fetch()`
   follow cross-origin redirects to HTML pages, because doing so would let
   JavaScript inspect the cross-origin response in violation of the same-origin
   policy. The browser typically reports this as a CORS error in the console.
2. **The auth server's logout flow involves more redirects.** Even if the first
   cross-origin redirect were allowed, the auth server's logout handler
   typically performs additional redirects and possibly form posts to clear
   sessions and confirm the logout. None of this can be driven by a fetch call;
   it requires the browser to be in control of navigation.

The form-submission approach sidesteps both issues. When the browser submits a
form, it navigates the user's tab through the redirect chain naturally, the same
way clicking a link works. The browser updates its address bar through each
step, accepts cookies from each origin, and arrives at the configured
post-logout URL with all sessions properly cleared.

This is the same reason the Sign-in element uses an `<a href>` tag rather than
a fetch call: any OAuth-related action that requires browser navigation across
origins must be a real navigation, not a fetch.

### Why the post-logout redirect URI must be pre-registered

The post-logout redirect URI follows the same pattern as the OAuth login redirect
URI: it is registered with the auth server in advance, and the auth server only
accepts URIs from that registered list.

This is a security boundary. If the auth server accepted any URL as a post-logout
redirect target, an attacker could craft a logout URL like:

```
http://localhost:9000/connect/logout?post_logout_redirect_uri=http://evil.com/
```

The user, expecting to log out, clicks the link. The auth server clears the
session, then redirects the browser to evil.com. The attacker's site can now
present a fake "you have been logged out, please sign in again" page that
captures credentials.

Pre-registration prevents this. evil.com is not on the registered list, so the
auth server refuses the logout request entirely.

---

## Reflection Questions (lab-4.8-notes.md answers)

### Question 1: The lab uses CookieCsrfTokenRepository.withHttpOnlyFalse(). Why does the cookie need to be readable by JavaScript? What would break if it were HttpOnly like the session cookie?

The whole point of the double-submit cookie pattern is that the SPA reads the
cookie value and copies it into a request header. If the cookie were HttpOnly,
JavaScript could not read its value, so the SPA could not set the matching
header, so every state-changing request would fail.

The session cookie (`BFF_SESSION`) is correctly HttpOnly because it is the
authentication credential. JavaScript should never need to read it: the
browser sends it automatically with every request, and an attacker getting hold
of it could impersonate the user.

The CSRF token has different security properties. It is not a credential. It
is a per-session challenge value that proves the request originated from
JavaScript that could read the cookie (which means JavaScript on the same
origin, because the same-origin policy prevents cross-origin reading of cookies).
Letting JavaScript read it does not enable an attack; it enables the defense
itself.

### Question 2: Imagine deploying this app to production where the React build is served from `app.examplebank.com` and the BFF runs at `api.examplebank.com`. What changes to the CSRF configuration, the cookie attributes, and the React fetch calls would be required?

Several things need to change because the SPA and the BFF are now on different
origins. The Vite proxy disappears in production, so requests genuinely go
cross-origin.

**Cookie attributes:**

- `Secure` flag: cookies must only be sent over HTTPS. Cookies must be
  Secure in production.
- `SameSite=None`: cookies set by `api.examplebank.com` must be sent on requests
  initiated from `app.examplebank.com`. The default `SameSite=Lax` blocks this.
  Setting `SameSite=None` enables it, but then `Secure` is required (modern
  browsers enforce this combination).
- `Domain=examplebank.com` or specific subdomain: the cookie's domain attribute
  must allow it to be sent to `api.examplebank.com` from a request initiated on
  `app.examplebank.com`. This typically means setting `Domain=.examplebank.com`
  so the cookie is shared across both subdomains.

**CORS configuration on the BFF:**

The BFF must include a CORS configuration that allows requests from
`https://app.examplebank.com`, including credentials:

```java
.cors(cors -> cors.configurationSource(...corsConfigurationSource that allows
  app.examplebank.com origin with allowCredentials=true...))
```

**React fetch calls:**

Every fetch call must include `credentials: 'include'`. Without this, the
browser will not send cookies on cross-origin requests, even if the cookies
have the right attributes.

```ts
const response = await fetch('https://api.examplebank.com/api/me', {
  credentials: 'include',
  headers: { 'Accept': 'application/json' },
});
```

The base URLs in the API client also need to point at `api.examplebank.com`
instead of `/api` (which only worked because of the Vite proxy in development).

**One subtle detail:** the CSRF cookie's `XSRF-TOKEN` value must be readable
by JavaScript on `app.examplebank.com`, not on `api.examplebank.com`. With
`Domain=.examplebank.com` the cookie is visible on both, which works. Without
that domain attribute, the cookie would be scoped only to `api.examplebank.com`
and `document.cookie` on `app.examplebank.com` would not see it.

In short, production deployment is significantly more involved than the
development-with-Vite-proxy setup. Many teams choose to keep the SPA and BFF
on the same origin in production (serving the React build from the BFF as
static files) to avoid these complications entirely.

### Question 3: Walk through how a CSRF attack from `evil.com` against this app would fail step by step. At which exact step does the attack break, and why?

Setup: alice is logged into the bank (BFF_SESSION cookie set, XSRF-TOKEN cookie
set, both scoped to localhost:5173). alice opens evil.com in another tab.

evil.com tries to POST to `localhost:5173/api/transfers` with a forged transfer
request.

Step-by-step:

1. **Attacker's HTML loads in alice's browser.** evil.com is allowed to embed
   any JavaScript or to use HTML forms with target URLs anywhere on the web.
   This step succeeds.

2. **Attacker's JavaScript tries to read the XSRF-TOKEN cookie.** This step
   fails. `document.cookie` on evil.com only returns evil.com's cookies, not
   localhost:5173's cookies. The same-origin policy explicitly prevents this
   read. **THIS IS WHERE THE ATTACK BREAKS.**

3. **Attacker constructs a POST request without the X-XSRF-TOKEN header.**
   Either the attacker omits the header entirely, or sets it to a guessed value.
   The header will not match the actual XSRF-TOKEN cookie value.

4. **Browser sends the POST.** The browser automatically attaches the
   BFF_SESSION cookie (cookies attach by hostname, not by initiator). The
   browser also automatically attaches the XSRF-TOKEN cookie (same reason).

5. **BFF receives the request.** Spring Security's CSRF filter compares the
   X-XSRF-TOKEN header value to the XSRF-TOKEN cookie value. They do not match
   (or the header is missing). Spring Security returns 403 Forbidden.

6. **The attack fails.** No transfer happens, no state change occurs, alice's
   account is safe.

The crucial step is step 2: the same-origin policy is the foundation of the
defense. Without it, evil.com could read the cookie and forge a matching header,
and the entire CSRF defense would be useless.

### Question 4: The XSRF-TOKEN cookie is readable by JavaScript. How is this not a credential leak? What makes the XSRF token safe to expose, when the access token is not?

A credential is something that, when presented, proves identity to the system.
Exposing a credential to attackers is bad because attackers can replay it.

The XSRF token is not a credential. By itself, it does nothing. To use the
XSRF token to make a request, an attacker would also need:

1. A valid BFF_SESSION cookie (the actual credential)
2. To send the request from JavaScript that the browser will let read the
   XSRF-TOKEN cookie (i.e., from the same origin)

If an attacker has both of those, they did not need the XSRF token in the
first place. Their JavaScript on the same origin can just make any request it
wants.

The XSRF token's purpose is not to be secret. Its purpose is to be **unforgeable
from a different origin**. It is safe to expose to JavaScript on the same origin
because that JavaScript could already make any request it wanted. It is
useless to JavaScript on a different origin because the same-origin policy
prevents reading it.

The access token, by contrast, IS a credential. Anyone holding it can use it
from any IP, any browser, any tool. Exposing it to JavaScript means exposing
it to any XSS attack and any malicious browser extension. The BFF pattern keeps
access tokens off the browser entirely for exactly this reason.

The two cookies enforce different boundaries:

- BFF_SESSION (HttpOnly, the credential) is hidden from JavaScript entirely. No
  XSS attack can exfiltrate it.
- XSRF-TOKEN (readable, the challenge) is exposed to JavaScript but bound to
  the origin. Cross-origin attacks cannot use it.

### Question 5: What does CSRF protection NOT defend against? Specifically, if an attacker can run JavaScript on the same origin as the SPA (through XSS), what can they still do?

CSRF protection does not defend against same-origin attacks. If an attacker
gets JavaScript running on `localhost:5173` (or `app.examplebank.com` in
production), they have everything they need to bypass the CSRF check:

- They can read the XSRF-TOKEN cookie (it is not HttpOnly)
- They can construct fetch requests with the matching X-XSRF-TOKEN header
- The BFF_SESSION cookie is attached automatically

In other words, an XSS attack defeats CSRF protection. CSRF protection assumes
the attacker is on a different origin; XSS gives the attacker the same origin.

What an XSS attacker can still do despite the BFF pattern:

- Make API calls as the user (transfers, account changes, anything the API
  allows)
- Read the responses to those calls (account balances, transaction history,
  personal data)
- Modify the page to phish the user (capture passwords, MFA codes, etc.)
- Persist malicious code by storing it in the user's data if the API allows
  user content

The BFF pattern does prevent XSS from doing some specific things:

- The attacker cannot exfiltrate the access token to use later from a different
  machine. When the user closes the tab, the attack stops.
- The attacker cannot impersonate the user from any other device or browser.
- The attack window is limited to the duration of the session.

But "limited window during which arbitrary actions can happen" is still a major
breach. **Defense in depth** means having other controls so that an XSS bug
does not become a complete compromise:

- Strict Content Security Policy headers to prevent XSS in the first place
- Input validation and output encoding throughout the application
- Per-action confirmation (transaction signing, MFA on high-value operations)
- Rate limiting and anomaly detection on the BFF
- Audit logging so that an attack can be detected and contained

CSRF protection is one layer. XSS prevention is another. Output encoding is
another. None of them alone is enough; together they make a real defense.

### Question 6: The csrfHeader() helper returns an empty object when no cookie is present. Why is that the right choice? What would happen if you returned `{ 'X-XSRF-TOKEN': '' }` instead, or if you threw an error?

Returning an empty object is the most permissive option that still produces
correct behavior. There are three states to consider:

**State 1: User is logged in. CSRF cookie is present.** The helper returns
`{ 'X-XSRF-TOKEN': '<actual-value>' }`. The fetch call sends the header. The
BFF accepts the request.

**State 2: User is not logged in. CSRF cookie is absent.** The helper returns
`{}`. The fetch call sends no extra header. The BFF rejects the request with
401 (because authentication is missing) or 403 (because CSRF is missing). The
SPA sees the error and triggers a re-authentication flow.

**State 3: Logout has just completed. CSRF cookie is absent.** Same as state 2.

Returning `{ 'X-XSRF-TOKEN': '' }` would send an empty header. Some servers
treat an empty header the same as a missing one, but Spring Security might
treat it as an explicit (empty) value that fails comparison differently. The
behavior is unpredictable and fragile.

Throwing an error would crash any pre-login fetch call in a confusing way. The
SPA legitimately wants to call `getCurrentUser()` (a GET) before login to check
auth state; this should not crash. Throwing in the helper would break that
basic flow.

The empty-object return ensures that:

- GET calls (which do not need CSRF) work in all states
- POST calls work when logged in
- POST calls fail predictably when not logged in (with a normal HTTP error
  the SPA can handle)

This is a small example of a general principle: defensive helpers should
degrade gracefully, returning sensible defaults for each input rather than
throwing for any "unusual" input. Throwing is appropriate when the function
cannot make a meaningful response; an empty cookie state is not unusual or
unrecoverable.

### Question 7: Before Exercise 4, clicking Sign out and then Sign in immediately put the user back in the app with no credential prompt. Walk through what was actually happening. Why did the user appear to be logged out (the Welcome screen appeared) but then re-authenticate silently? What state was preserved, and where?

Two independent servers each held a session, and the original logout flow only
cleared one of them. Specifically:

**Two sessions exist:**

- The BFF's session: stored on the BFF (port 8080), identified by the
  `BFF_SESSION` cookie scoped to `localhost:5173`.
- The auth server's session: stored on the auth server (port 9000), identified
  by the auth server's own session cookie scoped to `localhost:9000`.

These cookies are scoped to different hostnames, so the browser stores them
separately and does not share them. Neither server knows about the other's
session.

**What clicking Sign out did before Exercise 4:**

1. React app calls `POST /logout` on the BFF.
2. BFF's logout handler runs: it invalidates the HTTP session and clears the
   `BFF_SESSION` cookie.
3. BFF responds with success.
4. React app updates its UI: AuthContext re-checks authentication, gets a 401
   from `/api/me` (since the BFF session is gone), and renders the Welcome
   screen.

This created the appearance of a successful logout. From the React app's
perspective, the user was no longer authenticated.

**What was preserved:**

The auth server's session cookie was untouched. The auth server (running on
`localhost:9000`) still had a session for alice. The BFF could not have cleared
this even if it tried, because cookies on `localhost:9000` are not visible to
code running on the BFF.

**What clicking Sign in then did:**

1. User clicks Sign in. The browser navigates to
   `http://localhost:5173/oauth2/authorization/bank-auth`.
2. Vite proxies to BFF. BFF starts the OAuth flow and redirects to the auth
   server.
3. Browser hits the auth server with the auth server's session cookie still
   attached (because the cookie is scoped to `localhost:9000` and was never
   cleared).
4. Auth server recognizes alice from the session cookie. No login prompt.
5. Auth server immediately issues an authorization code and redirects back.
6. BFF exchanges the code for tokens, creates a new session, sets a new
   `BFF_SESSION` cookie.
7. User arrives back in the app, freshly authenticated, with no credential
   prompt.

The user perceived this as "I clicked Sign out, then I clicked Sign in, and I'm
back in." The auth server perceived it as "alice was logged in, I gave her a
fresh authorization code as requested." Both were correct from their local
viewpoint, but the combined behavior was a security gap.

The fix in Exercise 4 explicitly clears the auth server's session as part of
the logout flow, so step 3 above no longer succeeds without re-authentication.

### Question 8: RP-Initiated Logout requires the auth server's post-logout redirect URI to be pre-registered, similar to the OAuth login redirect URI. Why is this pre-registration necessary? What attack would be possible if the auth server accepted any URL as a post-logout redirect target?

If the auth server accepted any URL as a post-logout redirect target, an
attacker could craft a malicious logout URL and trick the user into following
it.

**The attack scenario:**

Attacker creates a phishing email or tweet:

> "Click here to securely log out of your bank from anywhere"
>
> `http://localhost:9000/connect/logout?post_logout_redirect_uri=http://evil.com/banking-login`

The user, expecting to log out (and seeing the legitimate auth server domain),
clicks the link. What happens:

1. The auth server processes the logout. The user's session is cleared.
2. The auth server redirects the browser to `http://evil.com/banking-login`.
3. evil.com presents a page that looks identical to the bank's login page,
   complete with the bank's logo and the message "Your session has expired,
   please log in again."
4. The user, having just logged out, tries to log back in. They type their
   credentials into the fake form on evil.com.
5. evil.com captures the credentials and forwards them to the real bank for
   replay.

This is a classic phishing attack with a specific twist: the attacker uses the
auth server itself as part of the redirect chain, lending legitimacy to the
URL the user initially clicks on. The user sees a real bank domain, follows
the link, gets logged out for real, and lands on a fake site that looks
plausible.

**Why pre-registration prevents this:**

Pre-registration constrains the auth server to a known, vetted list of
post-logout URLs. The bank's developers control this list. evil.com is not on
it. When the auth server receives a logout request with
`post_logout_redirect_uri=http://evil.com/`, it rejects the request entirely
(or ignores the parameter and uses a safe default).

This is the same pattern as OAuth's pre-registered login redirect URIs. In both
cases the attacker's leverage comes from being able to control where the auth
server sends the browser; pre-registration removes that leverage by limiting
the destination to URLs the bank has explicitly approved.

A general principle worth remembering: any time your auth server accepts a
URL parameter that controls where the browser will be sent, that parameter
must be validated against a pre-registered list. This applies to login redirect
URIs, post-logout redirect URIs, password reset return URIs, account linking
callback URIs, and similar.

### Question 9: The original logout function used fetch(). The corrected version submits an HTML form. Why does fetch() not work for this case? What other places in a typical OAuth-aware application require real browser navigation (not fetch) for the same reason?

`fetch()` cannot drive the browser through cross-origin HTML redirect chains.
RP-Initiated Logout requires exactly that: the BFF's POST `/logout` returns a
302 redirect to the auth server's logout endpoint on `localhost:9000`. The
browser refuses to let `fetch()` follow that redirect, because doing so would
let JavaScript inspect a cross-origin HTML response in violation of the
same-origin policy. The browser typically reports this as a CORS error.

Even if the first hop were allowed, the auth server's logout flow involves
additional redirects and possibly form submissions to clear sessions and
confirm the logout. None of that can be driven by a `fetch()` call. It needs
the browser to be in control of navigation: updating the address bar, accepting
cookies from each origin, rendering each page, and following each redirect
naturally.

A real form submission causes the browser to navigate the user's tab, the same
way clicking a link does. The browser handles the redirect chain, the cookies,
and the final landing page.

**Other places this same constraint applies:**

- **Sign in.** The Sign-in element in the React app is `<a href="/oauth2/authorization/bank-auth">`,
  not a fetch call, for exactly this reason. The OAuth login flow involves the
  same kind of cross-origin redirect chain (BFF -> auth server -> back).

- **OAuth provider linking.** If the user wanted to link an additional account
  from another identity provider, that flow would also be a navigation, not a
  fetch.

- **Federated logout where multiple OAuth providers are involved.** If the
  application uses OAuth against several IdPs (each with their own session), a
  thorough logout might chain through several auth servers. Each step is a
  navigation.

- **Single Sign-On (SAML).** SAML's authentication flow uses POST-binding form
  submissions specifically because it relies on the browser to navigate
  through the IdP redirect chain. The same constraint applies.

- **PayPal, Stripe Checkout, and similar redirect-based payment flows.** These
  redirect the user's browser to the payment provider's hosted page, take the
  payment, and redirect back. They cannot be done with `fetch()`.

The general pattern: any time the workflow requires the browser to display
content from a third-party origin (a login form, a consent screen, a payment
form, a logout confirmation), the workflow must use real browser navigation.
`fetch()` is appropriate for same-origin API calls where the response is data
your JavaScript will process; navigation is appropriate when the workflow
crosses origins and involves UI on the other side.
