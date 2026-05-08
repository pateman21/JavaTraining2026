# Module 8: React SPA Development with Secure OAuth
## Lab 4.6 -- Solutions and Checkpoint Answers

> **Course:** MD282 - Java Full-Stack Development
> **Purpose:** This file contains completed code for every TODO item and written answers
> for every Reflection question. Use it to verify your work after attempting each
> task yourself. Reading the solution before attempting the task defeats the
> purpose of the exercise.

---

## How to Use This File

Each section below corresponds to an exercise in the lab. Within each exercise, the
completed TODO items appear first, followed by written answers to the Reflection
questions at the end. The TODO numbers match exactly the numbers in the lab file.

---

## Exercise 1 -- Register the BFF as an OAuth Client

There are no fill-in TODOs in Exercise 1; the code in the lab is a direct copy-paste
into the auth server's `RegisteredClientRepository` bean. Verify your edits match
the lab file exactly.

The complete `bankClientBff` registration:

```java
RegisteredClient bankClientBff = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("bank-client-bff")
        .clientSecret("{noop}bank-client-bff-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
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

The updated `return` statement:

```java
return new InMemoryRegisteredClientRepository(
        workforceSpa, workforceService, bankClientRead, bankClientFull, bankClientBff);
```

---

## Exercise 2 -- Build the Resource Server Stub

Exercise 2 has no fill-in TODOs; all code is copy-paste. Verify your project structure
matches the lab and your code compiles. The complete project should have these files:

```
bankserver/
├── pom.xml                                                       (auto-generated)
├── src/main/resources/application.yml                            (Task 2.3)
└── src/main/java/com/example/bankserver/
    ├── BankingResourceServerApplication.java                     (auto-generated)
    ├── config/SecurityConfig.java                                (Task 2.5)
    ├── controller/BankingController.java                         (Task 2.7)
    ├── model/Account.java                                        (Task 2.4)
    ├── model/AccountStatus.java                                  (Task 2.4)
    ├── model/AccountType.java                                    (Task 2.4)
    ├── model/TransactionStatus.java                              (Task 2.4)
    ├── model/TransferRequest.java                                (Task 2.4)
    ├── model/TransferResponse.java                               (Task 2.4)
    └── service/BankingService.java                               (Task 2.6)
```

If running the application from IntelliJ succeeds and the verification request returns 401, you have
completed Exercise 2 correctly.

---

## Exercise 3 -- Configure the OAuth Client

### TODO 3.1 -- client-id

```yaml
client-id: bank-client-bff
```

### TODO 3.2 -- client-secret

```yaml
client-secret: bank-client-bff-secret
```

### TODO 3.3 -- authorization-grant-type

```yaml
authorization-grant-type: authorization_code
```

### TODO 3.4 -- redirect-uri

```yaml
redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
```

The double quotes are important. Without them, YAML may try to interpret the `{`
characters specially. The quoted string preserves the placeholder syntax intact for
Spring to process at runtime.

### TODO 3.5 -- scope

```yaml
scope:
  - openid
  - profile
  - read:accounts
  - read:transactions
  - write:accounts
  - write:transactions
```

### TODO 3.6 -- issuer-uri

```yaml
issuer-uri: http://localhost:9000
```

### Complete `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: bankbff

  security:
    oauth2:
      client:
        registration:
          bank-auth:
            client-id: bank-client-bff
            client-secret: bank-client-bff-secret
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
              - read:accounts
              - read:transactions
              - write:accounts
              - write:transactions

        provider:
          bank-auth:
            issuer-uri: http://localhost:9000

banking:
  resource-server:
    base-url: http://localhost:8081

logging:
  level:
    org.springframework.security: INFO
    org.springframework.security.oauth2: DEBUG
    com.example.bankbff: DEBUG
```

---

## Exercise 4 -- Configure Spring Security in the BFF

### Complete SecurityConfig.java

```java
package com.example.bankbff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // TODO 4.1
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**", "/error", "/").permitAll()
                        .anyRequest().authenticated())
                // TODO 4.2
                .exceptionHandling(ex -> {
                    RequestMatcher apiRequest =
                            new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
                    ex.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            apiRequest);
                })
                // TODO 4.3
                .oauth2Login(Customizer.withDefaults())
                // TODO 4.4
                .logout(Customizer.withDefaults());

        return http.build();
    }
}
```

The order of the configuration calls does not matter for correctness; Spring builds
up the filter chain from the cumulative configuration. Most teams group related
calls together (auth rules, login, logout) for readability.

### Why TODO 4.2 matters: the dual-audience problem

A BFF has two kinds of clients sharing the same URL space:

1. **Browser navigation requests** carrying `Accept: text/html`. The user typed a URL
   or clicked a link. If they are not logged in, redirecting them to the auth server's
   login page is the correct behavior.
2. **SPA API requests** (JavaScript `fetch` calls) carrying `Accept: application/json`.
   The SPA expects JSON. If the user is not logged in, returning a 302 redirect to
   an HTML login page would just confuse the SPA's JSON parser.

`oauth2Login()` registers an `AuthenticationEntryPoint` that redirects every
unauthenticated request to the login flow. That works for browsers but breaks API
calls.

The fix is to register a *higher-priority* entry point for requests that prefer
JSON. `defaultAuthenticationEntryPointFor()` lets us provide a `RequestMatcher`
that decides which entry point applies. We use `MediaTypeRequestMatcher` to match
on the `Accept` header.

The result:

- Request without an `Accept` header, or with `Accept: text/html` -> redirected to login (oauth2Login default).
- Request with `Accept: application/json` -> 401 Unauthorized (our entry point).

This is why every request in `bank-requests.http` includes `Accept: application/json`.
Without it, the IntelliJ HTTP client follows the redirect chain and you end up looking
at the auth server's login page (HTTP 200 with HTML content) instead of seeing the
401 you expected.

### Why TODO 4.1 permits both `/oauth2/**` and `/login/**`

Spring Security's OAuth2 client uses two URL prefixes that both must be reachable
without authentication:

- **`/oauth2/authorization/{registrationId}`** -- where the OAuth flow **starts**.
  When the user clicks "Sign in", the BFF receives a request at this URL,
  `OAuth2AuthorizationRequestRedirectFilter` builds the authorization request, and
  redirects the user's browser to the auth server.

- **`/login/oauth2/code/{registrationId}`** -- where the OAuth flow **ends**.
  After the user authenticates with the auth server, the auth server redirects the
  user's browser back to this URL with an authorization code. `OAuth2LoginAuthenticationFilter`
  receives the code and exchanges it for tokens.

Both URLs must be reachable by an unauthenticated user, otherwise the user would
need to be authenticated to start the authentication flow -- a chicken-and-egg
problem. That is why we permit both `/oauth2/**` (start) and `/login/**` (end).

A common point of confusion: older Spring Security documentation and tutorials
mention `/login/oauth2/authorization/{registrationId}` as the start URL. That URL
worked in older Spring Security versions but does not work in Spring Security 6.x.
The current path is `/oauth2/authorization/{registrationId}`. Always check the
version of your dependencies when troubleshooting OAuth login URLs.

---

## Exercise 5 -- Build the WebClient with OAuth Filter

### Complete WebClientConfig.java

```java
package com.example.bankbff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient bankingWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${banking.resource-server.base-url}") String baseUrl) {

        // TODO 5.1
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        // TODO 5.2
        oauth2.setDefaultClientRegistrationId("bank-auth");

        // TODO 5.3
        return WebClient.builder()
                .baseUrl(baseUrl)
                .apply(oauth2.oauth2Configuration())
                .build();
    }
}
```

---

## Exercise 6 -- Build the Resource Server Client

### Complete BankingApiClient.java

```java
package com.example.bankbff.client;

import com.example.bankbff.dto.AccountDto;
import com.example.bankbff.dto.TransferRequestDto;
import com.example.bankbff.dto.TransferResponseDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class BankingApiClient {

    private final WebClient bankingWebClient;

    public BankingApiClient(WebClient bankingWebClient) {
        this.bankingWebClient = bankingWebClient;
    }

    public List<AccountDto> getAccounts() {
        // TODO 6.1
        return bankingWebClient.get()
                .uri("/accounts")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AccountDto>>() {})
                .block();
    }

    public TransferResponseDto postTransfer(TransferRequestDto request) {
        // TODO 6.2
        return bankingWebClient.post()
                .uri("/transfers")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TransferResponseDto.class)
                .block();
    }
}
```

The `ParameterizedTypeReference<List<AccountDto>>() {}` is needed because of Java
type erasure. Without it, WebClient cannot determine the element type of the list
at runtime. The empty `{}` makes it an anonymous subclass; the type information is
preserved in the class metadata.

---

## Exercise 7 -- Build the User and Account Controllers

### Complete UserController.java (TODO 7.1)

```java
package com.example.bankbff.controller;

import com.example.bankbff.dto.UserInfoDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public UserInfoDto me(@AuthenticationPrincipal OidcUser user) {
        // TODO 7.1
        String username = user.getPreferredUsername();
        if (username == null) {
            username = user.getSubject();
        }

        String name = user.getFullName();
        if (name == null) {
            name = username;
        }

        List<String> roles = user.getClaimAsStringList("roles");
        if (roles == null) {
            roles = List.of();
        }

        return new UserInfoDto(username, name, roles);
    }
}
```

For `alice` logging in with the auth server in this course, the response will look
like this:

```json
{
  "username": "alice",
  "name": "alice",
  "roles": ["HR_MANAGER"]
}
```

`name` and `username` are the same because the auth server's `UserDetailsService`
does not record a separate display name for the user. In a production identity
provider, the user's profile would have richer data and the two fields would differ.

### Complete AccountController.java (TODO 7.2 and 7.3)

```java
package com.example.bankbff.controller;

import com.example.bankbff.client.BankingApiClient;
import com.example.bankbff.dto.AccountDto;
import com.example.bankbff.dto.TransferRequestDto;
import com.example.bankbff.dto.TransferResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final BankingApiClient bankingApiClient;

    public AccountController(BankingApiClient bankingApiClient) {
        this.bankingApiClient = bankingApiClient;
    }

    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        // TODO 7.2
        return bankingApiClient.getAccounts();
    }

    @PostMapping("/transfers")
    public TransferResponseDto transfer(@RequestBody TransferRequestDto request) {
        // TODO 7.3
        return bankingApiClient.postTransfer(request);
    }
}
```

These two controller methods are deliberately trivial. The interesting work happens
in the `BankingApiClient` and the OAuth filter on the `WebClient`. The controller's
job is to expose HTTP endpoints; the client's job is to make outbound calls; the
filter's job is to attach tokens. Each layer has one responsibility.

---

## Exercise 8 -- Test with bank-requests.http

Exercise 8 has no fill-in TODOs; the `.http` file is copy-paste. The expected
results are:

| Step | Expected response |
|---|---|
| Unauthenticated `/api/me` | 401 |
| Unauthenticated `/api/accounts` | 401 |
| Authenticated `/api/me` | 200 with user info JSON |
| Authenticated `/api/accounts` | 200 with the list of four accounts |
| Authenticated `/api/transfers` (ACC-001 to ACC-002, $100) | 200 with `transactionId` and `status: COMPLETE` |
| Re-fetch `/api/accounts` | 200; ACC-001 balance is `1400.00`, ACC-002 balance is `8300.50` |
| `POST /logout` | 200 (or 302 redirect to `/`) |
| `/api/me` after logout | 401 |

If any step fails, check the BFF logs first. Common issues:

- Wrong `JSESSIONID` value pasted in the `.http` file. Re-copy from DevTools.
- Browser was logged out (closed tab, session expired). Re-log in via the browser.
- Resource server is not running or returning errors. Check port 8081 logs.
- Auth server is not running. Check port 9000 logs.

---

## Reflection Questions (lab-b-notes.md answers)

### Question 1: The BFF uses the `authorization_code` grant. The other bank clients use `client_credentials`. What is the practical difference? When would you use each?

The two grants represent two fundamentally different scenarios:

**Authorization Code grant** is used when there is a real human user logging in. The
flow involves a redirect to the auth server, where the user authenticates with their
own credentials (such as username and password). The resulting tokens represent the
user's identity and permissions. The BFF uses this grant because it acts on behalf
of users like alice and bob.

**Client Credentials grant** is used when one service authenticates as itself to
call another service. There is no user involved at all. The token represents the
service identity (the client ID), not a human. This is appropriate for backend jobs,
scheduled tasks, or service-to-service calls where the action is not initiated by
any specific user.

The practical difference shows up in the `sub` claim of the token. With Authorization
Code, `sub` is the user's identifier. With Client Credentials, `sub` is the client ID.

Use Authorization Code when the action is "on behalf of" a user. Use Client Credentials
when the action is "by the service itself". Some operations need both: a user-initiated
action might trigger a backend job that runs later, where the job authenticates with
its own client credentials. They are not interchangeable.

### Question 2: After logging in, the browser holds only a `JSESSIONID` cookie. What threats does this protect against compared to storing the access token in browser JavaScript?

The big one is XSS-based token theft.

If the access token were stored in browser JavaScript (in `localStorage`, `sessionStorage`,
or even just an in-memory variable), any JavaScript code running on the same origin
could read it. That includes attacker-injected JavaScript via XSS. With the token in
hand, the attacker can:

- Send the token to their own server and impersonate the user from anywhere
- Make API calls as the user from any tab, any browser, any machine
- Continue using the token until it expires (potentially hours)

With the BFF pattern, the browser holds only an `HttpOnly` session cookie. JavaScript
cannot read it. An XSS attack can still make calls to the API while the page is open
(the cookie is sent automatically), but it cannot exfiltrate the credential to use
elsewhere. When the user closes the tab or the session expires, the attack stops.

Other threats the BFF reduces:

- **Token leakage through logging**: tokens never appear in browser history, console
  logs, or analytics events because they are never in browser code.
- **Token persistence on disk**: `localStorage` is written to disk and survives browser
  restarts; a session cookie generally does not.
- **Cross-device leakage**: browser sync features may replicate `localStorage` to the
  user's other devices; session cookies are not synced.

### Question 3: The `WebClient` has the OAuth2 filter applied. What would happen if you used a different `WebClient` (for example, the default `WebClient.builder().build()`) to call the resource server? Why?

A WebClient without the OAuth2 filter sends requests without an `Authorization` header.
The resource server expects a JWT bearer token on every request, so it would respond
with 401 Unauthorized. The filter is what makes the BFF's calls succeed.

Specifically, the filter does three things on every outbound request:

1. Looks up the current user's `OAuth2AuthorizedClient` (a record holding the access
   and refresh tokens, keyed by registration ID and the user's session)
2. Refreshes the access token if it is near or past expiry, using the refresh token
3. Adds the access token to the request as `Authorization: Bearer <token>`

Without the filter, none of this happens. The request goes out with no token, the
resource server rejects it, and the BFF passes the 401 back to the SPA.

This is also why you should not create a separate WebClient bean for resource server
calls. There is one configured WebClient, and all banking calls go through it.

### Question 4: If the access token expires during a long user session, what does the BFF do? What does the user see?

The BFF refreshes the access token transparently. The user sees nothing.

The chain of events when an access token is near expiry:

1. The user makes a request to the BFF (for example, `GET /api/accounts`).
2. The BFF calls the resource server through the WebClient.
3. The OAuth2 filter notices the access token is about to expire (or has expired).
4. The filter calls the auth server's token endpoint with the refresh token to get
   a new access token (and possibly a new refresh token).
5. The filter updates the user's `OAuth2AuthorizedClient` with the new tokens.
6. The filter attaches the new access token to the resource server request.
7. The resource server returns the data.
8. The BFF returns the data to the user.

All of this happens in milliseconds, on a single request. The user sees a normal
response with no extra latency they would notice. They are never sent back to the
auth server's login page during their session, even though access tokens are
typically short-lived (minutes).

If the refresh token itself is expired or revoked, the refresh fails. The user's
next request returns 401, the SPA detects this, and redirects to the login flow.
That is a session-end event, not a transparent renewal.

### Question 5: CSRF protection is disabled in this lab. What kind of attack does CSRF protect against, and why is it more relevant for cookie-authenticated APIs than for bearer-token APIs? You will turn CSRF on in Lab 4.7; what changes?

CSRF (Cross-Site Request Forgery) protects against an attack where a malicious
website tricks a user's browser into making a state-changing request to an API the
user is logged into.

The classic example: alice is logged into bank.com in one tab. In another tab she
visits evil.com. evil.com has a hidden form that submits to `bank.com/transfers`.
Because alice's browser holds a session cookie for bank.com, the browser
automatically attaches the cookie to the request. The bank's server cannot tell
the request was unwanted.

CSRF is more relevant for cookie-authenticated APIs because cookies are attached
automatically by the browser. The application has no opportunity to opt out. With
bearer tokens, the application code has to deliberately add the `Authorization`
header on each request; a malicious site cannot make the user's browser add it
automatically.

CSRF is disabled in this lab because we are testing through the IntelliJ HTTP client
and a browser, and we want to keep the testing surface small. In Lab 4.7 the React
SPA needs to make state-changing calls (`POST /api/transfers`), and CSRF protection
is required to prevent the attack described above.

When CSRF is turned on:

- Spring Security generates a CSRF token per session
- The token is stored in a cookie that JavaScript can read (`XSRF-TOKEN`)
- The SPA reads the cookie and includes the token in a request header
  (`X-XSRF-TOKEN`) on every state-changing request
- Spring Security validates that the header matches the cookie before allowing
  the request

This is the "double-submit cookie" pattern. It works because evil.com cannot read
the cookie value; the same-origin policy prevents that. So evil.com cannot include
the matching header in its forged request.

### Question 6: Coming from C#, which Spring concept in this lab was the most surprising? How does it compare to the equivalent in ASP.NET Core?

Open-ended question; sample answer:

The single most surprising concept is how much OAuth client behavior is configured
in `application.yml` rather than written as code. In ASP.NET Core, OAuth client
configuration is typically done in `Program.cs` with method calls:
`AddAuthentication().AddOAuth(...)`. Spring's approach is to put almost all of it
in YAML under `spring.security.oauth2.client.*`, with Spring Security reading the
configuration at startup and wiring everything up by convention.

The trade-off is similar to other declarative-vs-programmatic comparisons:

- **Declarative (Spring style)**: less code, easier to understand at a glance,
  externalized so it can be changed without recompilation. But it can feel "magical"
  because the connections between configuration and behavior are not obvious.
- **Programmatic (ASP.NET style)**: more code, but the wiring is explicit. You can
  see exactly which method is called when. Easier to step through with a debugger.

Both approaches work and most enterprises end up with a mix. Spring also supports
programmatic OAuth client configuration if you need it; YAML is just the default
path that handles the common case.

The auto-exposed endpoints (`/oauth2/authorization/{registrationId}`) are also
worth mentioning. In ASP.NET Core you typically write a controller action that calls
`Challenge()` to start the OAuth flow. Spring Security registers the URL pattern
automatically as soon as `oauth2Login()` is called in the security config; you do
not write a controller for it. Less code, more magic.
