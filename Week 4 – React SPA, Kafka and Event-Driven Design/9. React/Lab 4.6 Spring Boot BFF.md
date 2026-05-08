# Module 8: React SPA Development with Secure OAuth
## Lab 4.6 -- Building a Spring Boot BFF with OAuth2

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 8 - React SPA Development with Secure OAuth
> **Estimated time:** 90-120 minutes

---

## Overview

In Lab 4.5 you built a React SPA that talks to a mock API in the browser. In Lab 4.7 you will wire that same SPA to a real backend. This lab is the bridge: you will build the **Backend-for-Frontend (BFF)** that sits between the SPA and the protected banking API.

The BFF is a Spring Boot application that:

1. Authenticates users by performing the OAuth2 Authorization Code flow against the mock authorization server you have used in earlier modules
2. Holds the access and refresh tokens server-side, in the user's HTTP session
3. Issues an HttpOnly session cookie to the browser, which is the only credential the browser ever sees
4. Proxies API calls to the downstream banking resource server, attaching the bearer token automatically

This pattern is the current best-practice architecture for browser-based applications handling sensitive data. It eliminates the XSS-token-theft exposure that comes with the simpler "tokens in JavaScript" approach.

By the end of this lab you will be able to:

- Register a new OAuth client on the authorization server for the BFF
- Configure `spring-boot-starter-oauth2-client` declaratively in `application.yml`
- Configure Spring Security's filter chain to enable OAuth2 login and protect API endpoints
- Build a `WebClient` that automatically attaches the user's access token to outbound calls
- Test the full flow end to end with a browser and an HTTP test file

---

## Before You Start

The local Authorization Server must be running on port 9000. Confirm it is ready:

```
http://localhost:9000/.well-known/openid-configuration
```

You should see a JSON document describing the auth server's OAuth and OIDC endpoints. If you see an error, open the `auth-server` project in IntelliJ and run it from the green Run button (or by right-clicking the main application class and selecting **Run**). Wait for it to finish initializing.

**Authorization Server reference values for this lab:**

| Item | Value |
|---|---|
| Issuer URI | `http://localhost:9000` |
| Discovery endpoint | `http://localhost:9000/.well-known/openid-configuration` |
| Existing test users | `alice` / `password`, `bob` / `password` |
| Client ID (will be added in Exercise 1) | `bank-client-bff` |
| Client secret | `bank-client-bff-secret` |

> **A note on the existing users.** The auth server has two user accounts already configured: `alice` (HR_MANAGER role) and `bob` (VIEWER role). The role names are leftover from an earlier "workforce" example, but they will work fine for our banking application. Throughout this lab you can log in as either user.

---

## Architecture

You will be running three services side by side during this lab:

```
   ┌─────────────────────┐         ┌─────────────────────┐         ┌──────────────────┐
   │   Browser / .http   │         │   Banking BFF       │         │  Resource Server │
   │                     │◄───────►│   (Spring Boot)     │◄───────►│   (Spring Boot)  │
   │   (You, testing)    │ session │   Port 8080         │ bearer  │   Port 8081      │
   └─────────────────────┘  cookie │                     │ token   │                  │
                                   │   Built in          │         │   Built in       │
                                   │   Exercises 3-7.    │         │   Exercise 2.    │
                                   └──────────┬──────────┘         └──────────────────┘
                                              │
                                              │ OAuth flow
                                              ▼
                                   ┌─────────────────────┐
                                   │  Authorization      │
                                   │  Server             │
                                   │  Port 9000          │
                                   │                     │
                                   │  From earlier       │
                                   │  modules.           │
                                   └─────────────────────┘
```

- **Authorization Server (port 9000):** Issues tokens after authenticating users. You have used this in earlier modules. You will make a small addition to its configuration in Exercise 1.
- **Banking Resource Server (port 8081):** A small Spring Boot application that exposes account data behind OAuth2. You will build a stub version in Exercise 2.
- **Banking BFF (port 8080):** The main work of this lab. Built across Exercises 3 through 7.

---

## Exercise 1 -- Register the BFF as an OAuth Client

**Estimated time:** 10-15 minutes
**Topics covered:** OAuth client registration, Authorization Code grant, confidential clients

### Context

The authorization server already has two bank clients configured: `bank-client-read` and `bank-client-full`. Both use the **Client Credentials** grant. They are designed for service-to-service authentication where there is no user involved.

The BFF's job is different. The BFF authenticates **users**. When alice clicks "Sign in" in the SPA, the BFF needs to redirect her browser to the auth server, let her enter her credentials, then receive an authorization code back to exchange for tokens. That is the **Authorization Code** grant.

So the BFF needs a different kind of client registration. This first exercise adds it.

> **Why a separate client?** A single registration cannot do both grants the way the Spring Authorization Server is configured here. More fundamentally, the two clients have different security profiles: a service client uses its own identity, while a user-facing client always acts on behalf of someone. Keeping them separate makes the security model clear.

### Task 1.1 -- Add the BFF client registration

Open the Authorization Server project. Locate the `RegisteredClientRepository` bean in `AuthorizationServerConfig.java`. Add the BFF client after the `bankClientFull` registration:

```java
RegisteredClient bankClientBff = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("bank-client-bff")
        // {noop} tells Spring not to hash this value.
        // Development only; never use {noop} in production.
        .clientSecret("{noop}bank-client-bff-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        // Authorization Code flow for user-facing login,
        // plus refresh tokens so the BFF can refresh access tokens silently.
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        // Where the auth server is allowed to redirect the user after login.
        // Spring Security on the BFF auto-exposes this URL pattern.
        .redirectUri("http://localhost:8080/login/oauth2/code/bank-auth")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("read:accounts")
        .scope("read:transactions")
        .scope("write:accounts")
        .scope("write:transactions")
        .clientSettings(ClientSettings.builder()
                // Skip the consent screen for this lab to keep the
                // login flow short. In a real customer-facing app
                // you would typically require consent.
                .requireAuthorizationConsent(false)
                .build())
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(60))
                .refreshTokenTimeToLive(Duration.ofDays(1))
                .build())
        .build();
```

Update the `return` statement at the end of the method to include the new client:

```java
return new InMemoryRegisteredClientRepository(
        workforceSpa, workforceService, bankClientRead, bankClientFull, bankClientBff);
```

Restart the Authorization Server. In IntelliJ, click the **Stop** button in the Run panel, then click the green **Run** button to start it again. Wait for the application to finish starting up.

### Task 1.2 -- Verify the registration

Visit `http://localhost:9000/.well-known/openid-configuration` in a browser. You should see a JSON document describing the auth server's endpoints. The new `bank-client-bff` registration is now active in memory; you cannot directly inspect it without writing code, but you will exercise it through the BFF later in this lab.

---

## Exercise 2 -- Build the Resource Server Stub

**Estimated time:** 15-20 minutes
**Topics covered:** OAuth2 Resource Server, JWT validation, in-memory data

### Context

The BFF needs a downstream API to call. In a real banking application this would be a substantial service backed by databases, message queues, and other infrastructure. For this lab a small stub is enough. The stub validates JWT bearer tokens against the auth server, then returns canned account data.

You will not modify this project after building it. Run it on port 8081 alongside the auth server (9000) and the BFF (8080).

### Task 2.1 -- Create the project with Spring Initializr

1. Open [https://start.spring.io](https://start.spring.io) in a browser
2. Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | Latest stable (3.3.x or higher) |
| Group | `com.example` |
| Artifact | `bankserver` |
| Packaging | Jar |
| Java | 21 |

3. Add the following dependencies:
   - **Spring Web**
   - **OAuth2 Resource Server**
   - **Validation**

4. Click **Generate**, unzip, and open in IntelliJ using **File -> Open -> New Window**

### Task 2.2 -- Create the package structure

Right-click `src/main/java/com/example/bankserver` and create the following sub-packages:

```
com.example.bankserver
├── config
├── controller
├── model
└── service
```

### Task 2.3 -- Configure the application

Delete the auto-generated `src/main/resources/application.properties` and create `application.yml` in the same location:

```yaml
server:
  port: 8081

spring:
  application:
    name: bankserver
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000

logging:
  level:
    org.springframework.security: INFO
```

The `issuer-uri` lets Spring Security auto-discover the JWKS endpoint and other OAuth metadata.

### Task 2.4 -- Create the model classes

Create `Account.java` in the `model` package:

```java
package com.example.bankserver.model;

public record Account(
        String accountNumber,
        AccountStatus status,
        double balance,
        AccountType type
) {
}
```

Create `AccountStatus.java`:

```java
package com.example.bankserver.model;

public enum AccountStatus {
    ACTIVE,
    INACTIVE
}
```

Create `AccountType.java`:

```java
package com.example.bankserver.model;

public enum AccountType {
    SAVINGS,
    CHECKING
}
```

Create `TransferRequest.java`:

```java
package com.example.bankserver.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotBlank String fromAccountNumber,
        @NotBlank String toAccountNumber,
        @Positive double amount
) {
}
```

Create `TransferResponse.java`:

```java
package com.example.bankserver.model;

public record TransferResponse(
        String transactionId,
        TransactionStatus status
) {
}
```

Create `TransactionStatus.java`:

```java
package com.example.bankserver.model;

public enum TransactionStatus {
    COMPLETE,
    FAILED
}
```

### Task 2.5 -- Create the security configuration

Create `SecurityConfig.java` in the `config` package:

```java
package com.example.bankserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the resource server.
 *
 * Validates JWT bearer tokens on every request. Tokens are verified
 * against the mock authorization server's JWKS endpoint, configured
 * via spring.security.oauth2.resourceserver.jwt.issuer-uri in
 * application.yml.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

This configuration is small but does meaningful work. The `oauth2ResourceServer.jwt()` call activates the JWT validation filter. Every request must carry a valid bearer token. Sessions are disabled because resource servers are stateless: each request stands on its own.

### Task 2.6 -- Create the banking service

Create `BankingService.java` in the `service` package:

```java
package com.example.bankserver.service;

import com.example.bankserver.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory banking service.
 *
 * Holds account state and processes transfers. The same accounts are returned
 * that Lab 4.5's MSW mock returned, so the data feels continuous when the SPA
 * is wired up in Lab 4.7.
 */
@Service
public class BankingService {

    private final List<Account> accounts = new ArrayList<>(List.of(
            new Account("ACC-001", AccountStatus.ACTIVE,   1500.00, AccountType.CHECKING),
            new Account("ACC-002", AccountStatus.ACTIVE,   8200.50, AccountType.SAVINGS),
            new Account("ACC-003", AccountStatus.ACTIVE,   3100.75, AccountType.SAVINGS),
            new Account("ACC-004", AccountStatus.INACTIVE, 0.00,    AccountType.CHECKING)
    ));

    private final ReentrantLock lock = new ReentrantLock();

    public List<Account> listAccounts() {
        lock.lock();
        try {
            return List.copyOf(accounts);
        } finally {
            lock.unlock();
        }
    }

    public TransferResponse transfer(TransferRequest request) {
        lock.lock();
        try {
            int fromIndex = findIndexByAccountNumber(request.fromAccountNumber());
            if (fromIndex == -1) {
                return new TransferResponse(null, TransactionStatus.FAILED);
            }
            Account fromAccount = accounts.get(fromIndex);

            if (fromAccount.status() != AccountStatus.ACTIVE) {
                return new TransferResponse(null, TransactionStatus.FAILED);
            }

            if (request.amount() <= 0 || request.amount() > fromAccount.balance()) {
                return new TransferResponse(null, TransactionStatus.FAILED);
            }

            Account debited = new Account(
                    fromAccount.accountNumber(),
                    fromAccount.status(),
                    fromAccount.balance() - request.amount(),
                    fromAccount.type()
            );
            accounts.set(fromIndex, debited);

            int toIndex = findIndexByAccountNumber(request.toAccountNumber());
            if (toIndex != -1) {
                Account toAccount = accounts.get(toIndex);
                Account credited = new Account(
                        toAccount.accountNumber(),
                        toAccount.status(),
                        toAccount.balance() + request.amount(),
                        toAccount.type()
                );
                accounts.set(toIndex, credited);
            }

            String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return new TransferResponse(transactionId, TransactionStatus.COMPLETE);
        } finally {
            lock.unlock();
        }
    }

    private int findIndexByAccountNumber(String accountNumber) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).accountNumber().equals(accountNumber)) {
                return i;
            }
        }
        return -1;
    }
}
```

### Task 2.7 -- Create the controller

Create `BankingController.java` in the `controller` package:

```java
package com.example.bankserver.controller;

import com.example.bankserver.model.Account;
import com.example.bankserver.model.TransferRequest;
import com.example.bankserver.model.TransferResponse;
import com.example.bankserver.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Banking REST API controller.
 *
 * All endpoints require a valid JWT bearer token, enforced by SecurityConfig.
 */
@RestController
public class BankingController {

    private final BankingService bankingService;

    public BankingController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    @GetMapping("/accounts")
    public List<Account> listAccounts() {
        return bankingService.listAccounts();
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return bankingService.transfer(request);
    }
}
```

### Task 2.8 -- Run the resource server

In IntelliJ, locate the main application class `BankserverApplication` in `src/main/java/com/example/bankserver/`. Right-click it and select **Run 'BankserverApplication.main()'**. The application will start on port 8081.

Alternatively, if IntelliJ has already created a run configuration after opening the project, click the green **Run** button at the top of the IDE.

You should see logs indicating Spring Security configured the JWT decoder by reading `http://localhost:9000/.well-known/openid-configuration`.

### Verify Exercise 2

Test that the resource server is rejecting unauthenticated requests. In IntelliJ, create a scratch HTTP file (**File -> New -> Scratch File -> HTTP Request**) and add this request:

```http
### Unauthenticated request -- expect 401
GET http://localhost:8081/accounts
```

Click the green arrow next to the request to run it. You should see `HTTP/1.1 401 Unauthorized` in the IntelliJ Services panel below. The resource server is correctly rejecting unauthenticated requests. You will not be able to test the success path until the BFF is built.

Leave this server running for the rest of the lab.

---

## Exercise 3 -- Create the BFF Project and Configure OAuth Client

**Estimated time:** 15-20 minutes
**Topics covered:** Spring Initializr, `spring-boot-starter-oauth2-client`, `application.yml`

### Context

Now you build the main application of the lab. With `spring-boot-starter-oauth2-client` on the classpath, most of the OAuth client behavior is configured declaratively in `application.yml`. Spring Security reads the configuration on startup and wires up the entire flow.

### Task 3.1 -- Create the BFF project with Spring Initializr

1. Open a new browser tab to [https://start.spring.io](https://start.spring.io)
2. Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | Latest stable (3.3.x or higher) |
| Group | `com.example` |
| Artifact | `bankbff` |
| Packaging | Jar |
| Java | 21 |

3. Add the following dependencies:
   - **Spring Web**
   - **Spring Reactive Web**
   - **Spring Security**
   - **OAuth2 Client**

   The Reactive Web dependency is needed because we will use `WebClient` for outbound calls. The application as a whole stays servlet-based; only the HTTP client is reactive.

4. Click **Generate**, unzip, and open in IntelliJ using **File -> Open -> New Window**

### Task 3.2 -- Create the package structure

Right-click `src/main/java/com/example/bankbff` and create the following sub-packages:

```
com.example.bankbff
├── client
├── config
├── controller
└── dto
```

### Task 3.3 -- Configure the OAuth client

Delete `src/main/resources/application.properties` and create `application.yml` in the same location with this content. The `# TODO` comments mark places where you must fill in values.

```yaml
# Banking BFF configuration.
#
# Most of the OAuth client behavior is configured here rather than in code.
# Spring Security reads this configuration on startup and wires up the
# OAuth flow, token storage, and refresh logic automatically.

server:
  port: 8080

spring:
  application:
    name: bankbff

  security:
    oauth2:
      client:
        registration:
          # The 'bank-auth' name is a local label; you reference it in URLs
          # like /oauth2/authorization/bank-auth.
          bank-auth:
            # TODO 3.1: Set the client-id to match the registration in the
            # auth server (Exercise 1).
            client-id:

            # TODO 3.2: Set the client-secret to match the auth server.
            client-secret:

            # TODO 3.3: Set the grant type. The BFF is a confidential client
            # acting on behalf of a user, so use authorization_code.
            authorization-grant-type:

            # TODO 3.4: Set the redirect-uri using Spring's placeholder syntax.
            # The pattern is: "{baseUrl}/login/oauth2/code/{registrationId}".
            # Spring fills in the placeholders at runtime.
            redirect-uri:

            # TODO 3.5: Request these scopes:
            #   openid, profile, read:accounts, read:transactions,
            #   write:accounts, write:transactions
            scope:

        provider:
          bank-auth:
            # TODO 3.6: Set the issuer-uri. Spring will discover the rest of
            # the OAuth endpoints from the standard well-known location
            # (issuer-uri + /.well-known/openid-configuration).
            issuer-uri:

# Banking resource server (downstream API the BFF proxies to).
banking:
  resource-server:
    base-url: http://localhost:8081

logging:
  level:
    org.springframework.security: INFO
    org.springframework.security.oauth2: DEBUG
    com.example.bankbff: DEBUG
```

Fill in each TODO. The values you need are in the **Before You Start** table at the top of this lab and in Exercise 1. The redirect URI pattern is exactly as shown in the comment; Spring's placeholder syntax means you should not hardcode `localhost:8080` here.

> **A note on placeholders.** `{baseUrl}` becomes `http://localhost:8080` at runtime. `{registrationId}` becomes `bank-auth`. The resulting URL is `http://localhost:8080/login/oauth2/code/bank-auth`, which must match the redirect URI you registered with the auth server in Exercise 1. Mismatch is the most common cause of OAuth login failures.

### Verify Exercise 3

Try to start the BFF. In IntelliJ, locate `BankbffApplication` in `src/main/java/com/example/bankbff/`, right-click it, and select **Run 'BankbffApplication.main()'** (or use the green **Run** button if a run configuration already exists).

The application will start but most of the request paths will fail because the security config and beans are still empty. Look at the startup logs to confirm:

- The application started on port 8080
- No errors about the OAuth client configuration
- A log line indicating Spring discovered the OAuth provider's endpoints

Stop the BFF for now by clicking the red **Stop** button in IntelliJ's Run panel. You will start it again at the end of the next exercise.

---

## Exercise 4 -- Configure Spring Security in the BFF

**Estimated time:** 10-15 minutes
**Topics covered:** Spring Security filter chain, OAuth2 login, authorization rules, dual-audience entry points

### Context

With the OAuth client configured, you now need to tell Spring Security to:

1. Require authentication for `/api/**` endpoints
2. Allow the OAuth flow endpoints (`/oauth2/**` and `/login/**`) through without authentication
3. Enable OAuth2 login
4. Enable logout
5. Return **401 Unauthorized** to JSON API requests (instead of redirecting to the login page)

That last point is important and worth dwelling on. A BFF serves two different kinds of clients:

- **Browser navigation requests** (the user types a URL or clicks a link) -- these expect HTML responses. If the user is not logged in, redirecting them to the login page is the right behavior.
- **SPA API requests** (`fetch` calls from JavaScript) -- these expect JSON responses. If the user is not logged in, the SPA needs a clean **401 Unauthorized** so it can decide what to show (typically a "Sign in" button). Redirecting an XHR call to an HTML login page would just confuse the SPA's JSON parser.

Spring Security's default `oauth2Login()` behavior is to redirect **all** unauthenticated requests to the login page. We need to override that for API requests so they receive 401 instead. The `Accept` header tells us which kind of request it is.

This is configured by a single `SecurityFilterChain` bean.

### Task 4.1 -- Create SecurityConfig

Create `SecurityConfig.java` in the `config` package. The TODOs guide you through each part:

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

/**
 * Security configuration for the BFF.
 *
 * Configures Spring Security to:
 *  - Require authentication for /api/** endpoints.
 *  - Allow the auto-exposed OAuth endpoints (/oauth2/**, /login/**, /logout) through
 *    without explicit configuration. Spring Security registers these
 *    automatically when spring-boot-starter-oauth2-client is on the classpath.
 *  - Return 401 (instead of redirecting) for API requests that ask for JSON.
 *  - Disable CSRF for this lab (a deliberate simplification; we will turn it
 *    on in Lab 4.7 when the React SPA needs it).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is disabled for Lab 4.6 for simplicity.
                // Lab 4.7 will turn it on with a cookie-based token repository
                // appropriate for the React SPA.
                .csrf(AbstractHttpConfigurer::disable)

                // TODO 4.1: Configure authorization rules.
                //
                // Use .authorizeHttpRequests() to declare:
                //   - /oauth2/** is public (the OAuth flow starts here)
                //   - /login/** is public (the OAuth callback lands here)
                //   - /error and / are public
                //   - everything else requires authentication (.authenticated())
                //
                // Pattern:
                //   .authorizeHttpRequests(auth -> auth
                //       .requestMatchers("/oauth2/**", "/login/**", "/error", "/").permitAll()
                //       .anyRequest().authenticated())


                // TODO 4.2: Configure the dual-audience authentication entry point.
                //
                // For requests that ask for JSON (Accept: application/json),
                // return a clean 401 instead of redirecting to the login page.
                // Browser navigation requests (Accept: text/html) will still
                // be redirected to the login page by oauth2Login() below.
                //
                // Pattern:
                //   .exceptionHandling(ex -> {
                //       RequestMatcher apiRequest =
                //           new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
                //       ex.defaultAuthenticationEntryPointFor(
                //               new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                //               apiRequest);
                //   })


                // TODO 4.3: Enable OAuth2 login with default settings.
                //
                // Pattern:
                //   .oauth2Login(Customizer.withDefaults())


                // TODO 4.4: Enable logout with default settings.
                //
                // Pattern:
                //   .logout(Customizer.withDefaults())
                ;

        return http.build();
    }
}
```

After filling in the TODOs, restart the BFF. In IntelliJ, click the **Rerun** button (the circular arrow icon) in the Run panel. This stops the running application and starts it again with the latest code.

> **A note on the two OAuth URL paths.** Spring Security exposes two URL patterns for the OAuth flow: `/oauth2/authorization/{registrationId}` is where the flow **starts** (the user clicks "Sign in" and the BFF redirects them to the auth server), and `/login/oauth2/code/{registrationId}` is where the flow **ends** (the auth server redirects the user's browser back here with the authorization code). Both must be public so that an unauthenticated user can complete the login. That is why the rule above permits both `/oauth2/**` and `/login/**`.

### Verify Exercise 4

In a browser, visit `http://localhost:8080/api/me`. You should be redirected to the auth server's login page. **Do not log in yet** -- the rest of the BFF is not built. Just confirm the redirect happens. Then close the browser tab.

This redirect is the visible confirmation that Spring Security is intercepting unauthenticated requests and routing them through the OAuth flow.

---

## Exercise 5 -- Build the WebClient with OAuth Filter

**Estimated time:** 15-20 minutes
**Topics covered:** WebClient, OAuth2 token attachment, the BFF outbound pattern

### Context

The BFF needs to call the resource server on behalf of the authenticated user. The resource server requires a JWT bearer token in the `Authorization` header. Where does that token come from?

Spring Security stores the user's tokens in an `OAuth2AuthorizedClient`, keyed by the user's session and the registration ID. When the BFF wants to call the resource server, it could in principle:

1. Look up the `OAuth2AuthorizedClient` for the current user
2. Extract the access token
3. Add it to an `Authorization` header
4. Make the request
5. Handle token expiry by calling the auth server's token endpoint to refresh

That is a lot of plumbing for every endpoint. Spring Security ships a filter that does it all for you.

### Task 5.1 -- Create WebClientConfig

Create `WebClientConfig.java` in the `config` package:

```java
package com.example.bankbff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for outbound calls to the resource server.
 *
 * The WebClient is configured with Spring Security's OAuth2 filter, which:
 *
 *  - Looks up the authenticated user's OAuth2AuthorizedClient (which holds
 *    their access and refresh tokens).
 *  - If the access token is near expiry, refreshes it transparently.
 *  - Attaches the access token as an Authorization: Bearer header on every
 *    outbound request.
 *
 * Application code uses the WebClient like any normal HTTP client and never
 * touches tokens directly.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient bankingWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${banking.resource-server.base-url}") String baseUrl) {

        // TODO 5.1: Create the OAuth2 filter.
        //
        // Pattern:
        //   ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
        //       new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);


        // TODO 5.2: Tell the filter to use the 'bank-auth' registration by default.
        //
        // Pattern:
        //   oauth2.setDefaultClientRegistrationId("bank-auth");


        // TODO 5.3: Build a WebClient with the base URL set and the OAuth2 filter applied.
        //
        // Pattern:
        //   return WebClient.builder()
        //       .baseUrl(baseUrl)
        //       .apply(oauth2.oauth2Configuration())
        //       .build();

        return null;  // remove this line once you complete the TODOs above
    }
}
```

After filling in the TODOs, the WebClient bean is ready. No controller uses it yet, so the application will start but doing anything useful with API endpoints still fails.

> **What `apply(oauth2.oauth2Configuration())` does.** It installs the filter into every request the WebClient makes. From now on, the filter will look up the current user's tokens, refresh them if needed, and attach the access token automatically. Application code never sees the `Authorization` header.

### Verify Exercise 5

Restart the BFF. It should start without errors. Check the logs for any complaints about missing beans -- there should be none.

---

## Exercise 6 -- Build the Resource Server Client

**Estimated time:** 10 minutes
**Topics covered:** WebClient method calls, ParameterizedTypeReference, blocking calls

### Context

`BankingApiClient` is a thin wrapper around the WebClient with typed methods. Each method calls one resource server endpoint and converts the response. The OAuth filter is already attached, so you do not handle tokens here.

### Task 6.1 -- Create the DTO classes

Create `AccountDto.java` in the `dto` package:

```java
package com.example.bankbff.dto;

/**
 * Account DTO returned by the BFF to the SPA.
 *
 * Mirrors the shape of the Account record returned by the resource server.
 * In a more sophisticated BFF you might transform the shape here, but for
 * Lab 4.6 we just pass the data through.
 */
public record AccountDto(
        String accountNumber,
        String status,
        double balance,
        String type
) {
}
```

Create `TransferRequestDto.java`:

```java
package com.example.bankbff.dto;

public record TransferRequestDto(
        String fromAccountNumber,
        String toAccountNumber,
        double amount
) {
}
```

Create `TransferResponseDto.java`:

```java
package com.example.bankbff.dto;

public record TransferResponseDto(
        String transactionId,
        String status
) {
}
```

Create `UserInfoDto.java`:

```java
package com.example.bankbff.dto;

import java.util.List;

/**
 * Information about the authenticated user, exposed via /api/me.
 *
 * The SPA calls /api/me on load to determine whether the user is logged in
 * and to display their name in the header. If the user is not authenticated,
 * the BFF returns 401, signalling the SPA to show the login button instead.
 */
public record UserInfoDto(
        String username,
        String name,
        List<String> roles
) {
}
```

### Task 6.2 -- Create BankingApiClient

Create `BankingApiClient.java` in the `client` package:

```java
package com.example.bankbff.client;

import com.example.bankbff.dto.AccountDto;
import com.example.bankbff.dto.TransferRequestDto;
import com.example.bankbff.dto.TransferResponseDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Client for the banking resource server.
 *
 * Wraps the configured WebClient with typed methods for the resource
 * server's endpoints. The WebClient already has the OAuth2 filter applied
 * (see WebClientConfig), so bearer tokens are attached automatically.
 */
@Component
public class BankingApiClient {

    private final WebClient bankingWebClient;

    public BankingApiClient(WebClient bankingWebClient) {
        this.bankingWebClient = bankingWebClient;
    }

    public List<AccountDto> getAccounts() {
        // TODO 6.1: Call GET /accounts on the resource server and return
        // the response as a List<AccountDto>.
        //
        // Pattern:
        //   return bankingWebClient.get()
        //       .uri("/accounts")
        //       .retrieve()
        //       .bodyToMono(new ParameterizedTypeReference<List<AccountDto>>() {})
        //       .block();
        //
        // Why .block()? WebClient is reactive and returns a Mono. Since our
        // controller is synchronous Spring MVC, we block to get the value.

        return null;  // replace with the implementation
    }

    public TransferResponseDto postTransfer(TransferRequestDto request) {
        // TODO 6.2: Call POST /transfers on the resource server with the
        // given request body and return the response as a TransferResponseDto.
        //
        // Pattern:
        //   return bankingWebClient.post()
        //       .uri("/transfers")
        //       .bodyValue(request)
        //       .retrieve()
        //       .bodyToMono(TransferResponseDto.class)
        //       .block();

        return null;  // replace with the implementation
    }
}
```

### Verify Exercise 6

The client compiles but is still not exposed via any endpoint. Restart the BFF to confirm it starts cleanly.

---

## Exercise 7 -- Build the User and Account Controllers

**Estimated time:** 10-15 minutes
**Topics covered:** OidcUser, ID token claims, @AuthenticationPrincipal

### Context

The SPA needs two kinds of endpoints from the BFF:

1. `/api/me` -- "who am I?" Returns the authenticated user's info, or 401 if not logged in.
2. `/api/accounts` and `/api/transfers` -- the proxied banking endpoints.

Spring Security automatically passes the current user's `OidcUser` into our controller methods when we annotate the parameter with `@AuthenticationPrincipal`. The `OidcUser` exposes all the claims from the ID token plus those fetched from the auth server's `/userinfo` endpoint.

### Task 7.1 -- Create UserController

Create `UserController.java` in the `controller` package:

```java
package com.example.bankbff.controller;

import com.example.bankbff.dto.UserInfoDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints related to the authenticated user.
 *
 * The SPA calls /api/me on load to determine whether the user is logged in
 * and to display their name in the header. If no user is authenticated,
 * Spring Security returns 401 before this controller is invoked.
 */
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public UserInfoDto me(@AuthenticationPrincipal OidcUser user) {
        // TODO 7.1: Build and return a UserInfoDto from the OidcUser.
        //
        // The OidcUser exposes the ID token claims:
        //   user.getSubject()           -- the unique user ID (sub claim)
        //   user.getPreferredUsername() -- typically the username
        //   user.getFullName()          -- the user's display name
        //
        // For the roles list, look up the "roles" claim:
        //   user.getClaimAsStringList("roles")
        //
        // Hints:
        //   - For username, prefer getPreferredUsername(); fall back to getSubject() if null.
        //   - For name, prefer getFullName(); fall back to username if null.
        //   - If the roles claim is null, use List.of().

        return null;  // replace with the implementation
    }
}
```

### Task 7.2 -- Create AccountController

Create `AccountController.java` in the `controller` package:

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

/**
 * Banking endpoints exposed to the SPA.
 *
 * Each method delegates to BankingApiClient, which calls the downstream
 * resource server. The bearer token is attached automatically by the
 * WebClient's OAuth2 filter.
 *
 * Spring Security gates these endpoints based on the rules in SecurityConfig.
 * Unauthenticated requests are rejected with 401 before they reach this
 * controller.
 */
@RestController
@RequestMapping("/api")
public class AccountController {

    private final BankingApiClient bankingApiClient;

    public AccountController(BankingApiClient bankingApiClient) {
        this.bankingApiClient = bankingApiClient;
    }

    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        // TODO 7.2: Delegate to bankingApiClient.getAccounts() and return
        // the result.

        return null;
    }

    @PostMapping("/transfers")
    public TransferResponseDto transfer(@RequestBody TransferRequestDto request) {
        // TODO 7.3: Delegate to bankingApiClient.postTransfer(request) and
        // return the result.

        return null;
    }
}
```

### Verify Exercise 7

Restart the BFF. All three services should now be running:

- Authorization server on port 9000
- Resource server on port 8081
- BFF on port 8080

Test the full flow in a browser:

1. Open `http://localhost:8080/oauth2/authorization/bank-auth`.
2. The auth server's login page appears.
3. Log in as `alice` / `password`.
4. The browser is redirected back to `http://localhost:8080/`. You may see a 404 page since we have not built a root page; that is fine. The session cookie is set.
5. Open DevTools (F12), go to the Application tab, find the cookies for `localhost`, and confirm there is a `JSESSIONID` cookie. Note its `HttpOnly` flag.
6. In a new browser tab, visit `http://localhost:8080/api/me`. You should see a JSON response with alice's information.
7. In the same tab, visit `http://localhost:8080/api/accounts`. You should see the four bank accounts.

If all of these work, you have built a fully functional BFF.

---

## Exercise 8 -- Test with bank-requests.http

**Estimated time:** 10-15 minutes
**Topics covered:** Cookie-based session testing, the IntelliJ HTTP client

### Context

A complete BFF deserves a test file. IntelliJ's HTTP client (or the VS Code REST Client extension) lets you script HTTP requests in plain text and run them with one click. Most of this lab's HTTP testing has involved redirects through a browser, but once the session cookie is established you can exercise the API endpoints from a `.http` file.

### Task 8.1 -- Create bank-requests.http

In the `bankbff` project root, create `bank-requests.http`. Notice that every request includes an `Accept: application/json` header. This is what tells the BFF to treat the request as a programmatic API call. Without this header, the BFF assumes the request is browser navigation and may redirect to the login page (returning HTML) instead of returning a clean 401.

```http
### Banking BFF -- request examples

@bff = http://localhost:8080
@authServer = http://localhost:9000
@resourceServer = http://localhost:8081

###############################################################################
### 1. Verify the BFF is running and rejecting unauthenticated calls.
### Should return 401 Unauthorized.
###############################################################################

### Unauthenticated /api/me -- expect 401
GET {{bff}}/api/me
Accept: application/json

### Unauthenticated /api/accounts -- expect 401
GET {{bff}}/api/accounts
Accept: application/json


###############################################################################
### 2. Log in via a browser, then paste the JSESSIONID cookie value below.
###
### To get the cookie:
###   - Open http://localhost:8080/oauth2/authorization/bank-auth in a browser.
###   - Log in as alice / password.
###   - Open DevTools, go to Application > Cookies > localhost.
###   - Copy the JSESSIONID value.
###############################################################################

@sessionCookie = JSESSIONID=PASTE_THE_COOKIE_VALUE_HERE


###############################################################################
### 3. Authenticated requests
###############################################################################

### /api/me -- expect 200 with user info
GET {{bff}}/api/me
Accept: application/json
Cookie: {{sessionCookie}}

### /api/accounts -- expect 200 with the account list
GET {{bff}}/api/accounts
Accept: application/json
Cookie: {{sessionCookie}}

### /api/transfers -- expect 200 with a transaction ID
POST {{bff}}/api/transfers
Accept: application/json
Cookie: {{sessionCookie}}
Content-Type: application/json

{
  "fromAccountNumber": "ACC-001",
  "toAccountNumber": "ACC-002",
  "amount": 100.00
}

### /api/accounts again -- ACC-001 balance should be lower
GET {{bff}}/api/accounts
Accept: application/json
Cookie: {{sessionCookie}}


###############################################################################
### 4. Logout and verify the session is gone.
###############################################################################

### POST /logout -- expect 200 (or 204 No Content)
POST {{bff}}/logout
Accept: application/json
Cookie: {{sessionCookie}}

### /api/me after logout -- expect 401
GET {{bff}}/api/me
Accept: application/json
Cookie: {{sessionCookie}}
```

### Task 8.2 -- Run the requests in order

1. Run the unauthenticated requests. Both should return 401.
2. In a browser, log in via `http://localhost:8080/oauth2/authorization/bank-auth` as `alice` / `password`.
3. Copy the `JSESSIONID` cookie from DevTools and paste it in place of `PASTE_THE_COOKIE_VALUE_HERE`.
4. Run the authenticated requests. They should all succeed.
5. Run the transfer request. The response should contain a `transactionId` and `status: COMPLETE`.
6. Re-run `GET /api/accounts`. The balance of `ACC-001` should be 100 less than before, and the balance of `ACC-002` should be 100 more.
7. Run `POST /logout`.
8. Run `GET /api/me` again. It should now return 401.

If all of these work, the BFF is complete.

---

## What You Have Built

The BFF you have built does several things at once:

- **Authenticates users via OAuth2.** When the user clicks "Sign in" (or visits a protected URL while not authenticated), Spring Security redirects them to the auth server, receives the callback, exchanges the authorization code for tokens, and stores them in the user's HTTP session.
- **Issues an HttpOnly session cookie.** This is the only credential the browser ever sees. Tokens never reach JavaScript. An XSS vulnerability in the SPA cannot exfiltrate the tokens because they are not in the browser.
- **Refreshes tokens transparently.** The `WebClient`'s OAuth2 filter checks the access token's expiry on every outbound call and refreshes it using the refresh token if needed. The user is never bounced to the auth server during their session.
- **Proxies API calls.** The BFF forwards SPA requests to the resource server, attaching the bearer token automatically. From the SPA's perspective, the BFF is the only backend it sees.

This is the architectural pattern that the next lab (Lab 4.7) will rely on. The React app you built in Lab 4.5 will be wired up to call this BFF, replacing the mock service worker. The fetch calls in the React code will not need to change.

---

## Reflection Questions

In a new file `lab-b-notes.md` in the `bankbff` project root, answer these:

1. The BFF uses the `authorization_code` grant. The other bank clients on the auth server use `client_credentials`. What is the practical difference? When would you use each?

2. After logging in, the browser holds only a `JSESSIONID` cookie. What threats does this protect against compared to storing the access token in browser JavaScript?

3. The `WebClient` has the OAuth2 filter applied. What would happen if you used a different `WebClient` (for example, the default `WebClient.builder().build()`) to call the resource server? Why?

4. If the access token expires during a long user session, what does the BFF do? What does the user see?

5. CSRF protection is disabled in this lab. What kind of attack does CSRF protect against, and why is it more relevant for cookie-authenticated APIs than for bearer-token APIs? You will turn CSRF on in Lab 4.7; what changes?

6. Coming from C#, which Spring concept in this lab was the most surprising? How does it compare to the equivalent in ASP.NET Core?

---

## What's Next

In Lab 4.7 you will:

- Replace MSW in your Lab 4.5 React application with real calls to this BFF
- Add a "Sign in" link that redirects to the BFF's OAuth login endpoint
- Add a `/api/me` call that determines whether the user is authenticated
- Show the user's name in the SPA's header
- Add a logout button that POSTs to the BFF's `/logout` endpoint
- Turn CSRF protection on in the BFF and configure the SPA to send the CSRF token correctly

The React code will change very little. Most of the work is on the BFF side, and most of the BFF code is already done; you just unlock CSRF and tighten a few defaults.
