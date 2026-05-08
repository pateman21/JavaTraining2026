# Module 3: OAuth2, OIDC, and Spring Security
## Lab 5 -- Input Validation and Consistent Error Handling in a Secured Banking API

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 3 - OAuth2 and OIDC Foundations and Spring Security
> **Estimated time:** 60-90 minutes

---

## Overview

This lab introduces two cross-cutting concerns that every production API must address alongside security: input validation and consistent error responses. You will build a small Banking API protected by OAuth2 Client Credentials tokens. Every endpoint is stubbed -- persistence is in-memory -- so the focus stays entirely on the security and validation layers.

By the end of this lab you will be able to:

- Configure a Spring Boot Resource Server that accepts Client Credentials tokens
- Apply `@PreAuthorize` and other method security annotations to service methods
- Annotate request bodies and path variables with Bean Validation constraints
- Write a `@ControllerAdvice` class that returns a uniform JSON error envelope for validation failures, authorization denials, and unhandled exceptions
- Explain the difference between 401 Unauthorized and 403 Forbidden and when Spring Security produces each

---

## Before You Start

The local Authorization Server must be running on port 9000. Confirm it is ready:

```
http://localhost:9000/oauth2/jwks
```

You should see a JSON document containing an RSA public key. If you see an error, start the `auth-server` project and wait for it to finish initializing.

**Authorization Server reference values for this lab:**

| Item | Value |
|---|---|
| JWKS endpoint | `http://localhost:9000/oauth2/jwks` |
| Issuer URI | `http://localhost:9000` |
| Token endpoint | `http://localhost:9000/oauth2/token` |
| Client ID (read scope) | `bank-client-read` |
| Client secret | `bank-client-read-secret` |
| Client ID (full scope) | `bank-client-full` |
| Client secret | `bank-client-full-secret` |

> **Note:** You will register these two clients in the Authorization Server in Task 1.1. Having two clients with different scopes lets you test both the authorized and the scope-denied paths without restarting anything.

---

## Starter Project

### Create the project with Spring Initializr

1. Open [https://start.spring.io](https://start.spring.io) in a browser
2. Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | Latest stable (3.x or higher) |
| Group | `com.example` |
| Artifact | `banking` |
| Packaging | Jar |
| Java | 17 |

3. Add the following dependencies:
   - **Spring Web**
   - **Spring Security**
   - **OAuth2 Resource Server**
   - **Spring Boot DevTools**
   - **Validation**

4. Click **Generate**, unzip, and open in IntelliJ using **File -> Open -> New Window**

### Package structure to create before Exercise 1

Right-click `src/main/java/com/example/banking` and create the following sub-packages:

```
com.example.banking
├── config
├── controller
├── dto
├── exception
├── model
└── service
```

---

## Exercise 1 -- Authorization Server Setup and Token Acquisition

**Estimated time:** 15-20 minutes
**Topics covered:** Client Credentials grant, scope design, obtaining tokens, decoding claims

### Context

Client Credentials is the correct OAuth2 grant type when no user is involved -- for example, a backend service calling a Banking API on its own behalf. The client authenticates directly with the Authorization Server using its `client_id` and `client_secret`. No browser redirect or user consent is needed. The resulting token identifies the service, not a person.

This lab uses two clients deliberately. `bank-client-read` receives only `read:accounts` and `read:transactions`, while `bank-client-full` also receives `write:accounts` and `write:transactions`. Using both clients in your HTTP test file lets you hit the same endpoint with different scopes and observe exactly where Spring Security denies the request.

### Task 1.1 -- Register the clients in the Authorization Server

Open the Authorization Server project and open `AuthorizationServerConfig.java`. Locate the `registeredClientRepository()` bean method. It contains the existing client definitions (`workforceSpa`, `workforceService`) and ends with a `return` statement that passes those clients to `InMemoryRegisteredClientRepository`.

**Step 1: Declare the two new client variables** inside the method body, before the `return` statement:

```java
RegisteredClient bankClientRead = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("bank-client-read")
        .clientSecret("{noop}bank-client-read-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("read:accounts")
        .scope("read:transactions")
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build())
        .build();

RegisteredClient bankClientFull = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("bank-client-full")
        .clientSecret("{noop}bank-client-full-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("read:accounts")
        .scope("read:transactions")
        .scope("write:accounts")
        .scope("write:transactions")
        .scope("admin:users")
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build())
        .build();
```

**Step 2: Update the `return` statement** to include the two new clients alongside the existing ones. This is the step that actually registers the clients with the Authorization Server. Declaring the variables above but omitting them from the constructor means the Authorization Server has no knowledge they exist and will return `invalid_client` on every token request.

The `return` statement must name all four clients:

```java
return new InMemoryRegisteredClientRepository(
        workforceSpa,
        workforceService,
        bankClientRead,
        bankClientFull
);
```

**Step 3: Restart the Authorization Server.** The in-memory repository is populated at startup. Changes to the client list do not take effect until the application restarts.

> **Common mistake:** Java does not warn you if you declare a variable but forget to pass it to the constructor. If you see `{"error": "invalid_client"}` when requesting a token, the first thing to check is whether the client variable appears in the `InMemoryRegisteredClientRepository` constructor call.

### Task 1.2 -- Obtain tokens and inspect them

Create `bank-requests.http` in the **banking** project root. This file will hold every test request for the lab.

```http
### Obtain a read-only token.
### Authorization header is Base64("bank-client-read:bank-client-read-secret").
POST http://localhost:9000/oauth2/token
Authorization: Basic YmFuay1jbGllbnQtcmVhZDpiYW5rLWNsaWVudC1yZWFkLXNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=read:accounts read:transactions

###

### Obtain a full-access token.
### Authorization header is Base64("bank-client-full:bank-client-full-secret").
POST http://localhost:9000/oauth2/token
Authorization: Basic YmFuay1jbGllbnQtZnVsbDpiYW5rLWNsaWVudC1mdWxsLXNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=read:accounts read:transactions write:accounts write:transactions admin:users
```

Execute both requests. Paste each `access_token` into [https://jwt.io](https://jwt.io) and answer the following in a new file called `lab6-notes.md`:

1. What is the value of `sub` in each token? Why is it the client ID rather than a username?
2. What is the value of `scp` or `scope`? Confirm the two tokens carry different scopes.
3. What would happen if you requested a scope that was not registered on the client (for example, `admin:users` using `bank-client-read`)?

---

## Exercise 2 -- Resource Server Configuration and URL Security

**Estimated time:** 20-25 minutes
**Topics covered:** `SecurityFilterChain`, `oauth2ResourceServer`, `@EnableMethodSecurity`, URL-level scope rules, 401 vs 403

### Context

A Resource Server validates the Bearer token on every inbound request before your controller code runs. The validation pipeline that Spring Security executes automatically includes: extracting the `Authorization` header, base64-decoding the JWT, fetching the public key from the JWKS endpoint, verifying the signature, checking `exp`, `iss`, and `aud` claims, and mapping `scope` values to granted authorities with a `SCOPE_` prefix.

Your job is to declare which endpoints need which authorities. You do not write the validation logic.

The 401/403 distinction is important. Spring Security returns 401 Unauthorized when no valid token is present at all -- the caller must authenticate. It returns 403 Forbidden when the token is valid but the caller lacks the required authority -- the caller is known but not permitted. You will observe both in this exercise.

### Task 2.1 -- Create the stub models

Create `Account.java` in the `model` package:

```java
package com.example.banking.model;

public record Account(
        String accountId,
        String ownerId,
        String accountType,
        double balance
) {}
```

Create `Transaction.java` in the `model` package:

```java
package com.example.banking.model;

public record Transaction(
        String transactionId,
        String accountId,
        String type,
        double amount
) {}
```

Create `BankUser.java` in the `model` package:

```java
package com.example.banking.model;

public record BankUser(
        String userId,
        String username,
        String email,
        String role
) {}
```

### Task 2.2 -- Create stub controllers

These controllers contain only the minimum structure needed to exercise security and validation. Business logic is intentionally absent.

Create `AccountController.java` in the `controller` package:

```java
package com.example.banking.controller;

import com.example.banking.model.Account;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final List<Account> ACCOUNTS = List.of(
            new Account("ACC-001", "user-1", "CHECKING", 1500.00),
            new Account("ACC-002", "user-2", "SAVINGS", 8200.50),
            new Account("ACC-003", "user-1", "SAVINGS", 3100.75)
    );

    @GetMapping
    public List<Account> getAllAccounts() {
        return ACCOUNTS;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Account> getAccountById(@PathVariable String accountId) {
        return ACCOUNTS.stream()
                .filter(a -> a.accountId().equals(accountId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // TODO 1: Add a POST endpoint that accepts a request body and returns 201 Created.
    // The request body type will be CreateAccountRequest (you will create this DTO in Exercise 3).
    // Annotate the parameter with @RequestBody and @Valid.
    // Use ResponseEntity.status(HttpStatus.CREATED).body(request) as a stub return value.
    // Leave a comment noting that persistence is not implemented.
}
```

Create `TransactionController.java` in the `controller` package:

```java
package com.example.banking.controller;

import com.example.banking.model.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final List<Transaction> TRANSACTIONS = List.of(
            new Transaction("TXN-001", "ACC-001", "DEBIT", 200.00),
            new Transaction("TXN-002", "ACC-001", "CREDIT", 500.00),
            new Transaction("TXN-003", "ACC-002", "CREDIT", 1000.00)
    );

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return TRANSACTIONS;
    }

    @GetMapping("/account/{accountId}")
    public List<Transaction> getTransactionsByAccount(@PathVariable String accountId) {
        return TRANSACTIONS.stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();
    }

    // TODO 2: Add a POST endpoint at the mapping root that accepts a request body
    // of type CreateTransactionRequest (you will create this DTO in Exercise 3).
    // Annotate the parameter with @RequestBody and @Valid.
    // Return 201 Created as a stub.
}
```

Create `UserController.java` in the `controller` package:

```java
package com.example.banking.controller;

import com.example.banking.model.BankUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final List<BankUser> USERS = List.of(
            new BankUser("user-1", "alice", "alice@bank.example", "CUSTOMER"),
            new BankUser("user-2", "bob", "bob@bank.example", "CUSTOMER"),
            new BankUser("user-3", "carol", "carol@bank.example", "ADMIN")
    );

    @GetMapping
    public List<BankUser> getAllUsers() {
        return USERS;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BankUser> getUserById(@PathVariable String userId) {
        return USERS.stream()
                .filter(u -> u.userId().equals(userId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

Create `HealthController.java` in the `controller` package:

```java
package com.example.banking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

### Task 2.3 -- Configure the Resource Server

Create `SecurityConfig.java` in the `config` package:

```java
package com.example.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // activates @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // TODO 3: Configure authorization rules with authorizeHttpRequests().
            // Rules must be declared in order from most specific to least specific.
            // Required rules:
            //   GET  /health                          permitAll()
            //   GET  /api/v1/accounts/**              hasAuthority("SCOPE_read:accounts")
            //   POST /api/v1/accounts                 hasAuthority("SCOPE_write:accounts")
            //   GET  /api/v1/transactions/**           hasAuthority("SCOPE_read:transactions")
            //   POST /api/v1/transactions             hasAuthority("SCOPE_write:transactions")
            //   GET  /api/v1/users/**                 hasAuthority("SCOPE_admin:users")
            //   anyRequest()                          authenticated()
            //
            // The SCOPE_ prefix is added automatically by Spring Security when it
            // maps the "scope" claim from the JWT to granted authorities.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    // TODO: add the remaining rules here
                    .anyRequest().authenticated()
            )

            // TODO 4: Configure JWT validation.
            // Use: .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            // Spring Security fetches the public key from jwks-uri, verifies the signature,
            // checks exp/iss/aud, and maps scopes to SCOPE_-prefixed authorities.

            // TODO 5: Set session management to STATELESS.
            // A REST API using Bearer tokens does not use HTTP sessions.
            // Use: .sessionManagement(session ->
            //          session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // TODO 6: Disable CSRF.
            // CSRF attacks depend on browsers sending session cookies automatically.
            // Bearer tokens are not sent automatically, so CSRF does not apply.
            // Use: .csrf(csrf -> csrf.disable())
            ;

        return http.build();
    }
}
```

### Task 2.4 -- Configure the JWKS endpoint

Add the following to `src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwks-uri: http://localhost:9000/oauth2/jwks
          issuer-uri: http://localhost:9000

server:
  port: 8081
```

> **Note:** Port 8081 avoids a conflict if the Workforce API from previous exercises is still running on 8080.

### Task 2.5 -- Test the URL-level rules

Add the following test requests to `bank-requests.http`. Replace the placeholder tokens with real values from Task 1.2.

```http
### Health endpoint -- no token required, should return 200.
GET http://localhost:8081/health

###

### GET accounts with no token -- should return 401.
GET http://localhost:8081/api/v1/accounts

###

### GET accounts with the read-only token -- should return 200.
GET http://localhost:8081/api/v1/accounts
Authorization: Bearer <read-only-token>

###

### GET accounts with the full-access token -- should return 200.
GET http://localhost:8081/api/v1/accounts
Authorization: Bearer <full-access-token>

###

### GET users with the read-only token -- should return 403.
### The read-only token has no admin:users scope.
GET http://localhost:8081/api/v1/users
Authorization: Bearer <read-only-token>

###

### GET users with the full-access token -- should return 200.
GET http://localhost:8081/api/v1/users
Authorization: Bearer <full-access-token>
```

Execute each request and record the status codes in `lab6-notes.md`.

### Checkpoints

1. The unauthenticated request to `/api/v1/accounts` returns 401. The request with a valid read-only token to `/api/v1/users` returns 403. Describe precisely what Spring Security examines to decide which status to use.
2. The `SCOPE_` prefix on authorities like `SCOPE_read:accounts` does not appear in the token's `scope` claim. Which Spring Security component adds it and where in the filter chain does this happen?
3. You configured `SessionCreationPolicy.STATELESS`. What would go wrong if you left the default session policy in place for a stateless JWT Resource Server?

---

## Exercise 3 -- Input Validation with @Valid and Bean Validation Constraints

**Estimated time:** 20-25 minutes
**Topics covered:** Bean Validation annotations, `@Valid` on controller parameters, `MethodArgumentNotValidException`, field-level and class-level constraints

### Context

Spring Boot includes the Bean Validation API (`jakarta.validation`) and its Hibernate Validator implementation through the `Validation` starter. Placing `@Valid` on a `@RequestBody` parameter instructs Spring MVC to run all annotations on that object before the controller method body executes. If any constraint fails, Spring throws `MethodArgumentNotValidException` before your code runs.

Without a `@ControllerAdvice` handler, Spring maps this exception to a 400 response with a verbose default body that exposes internal field paths and implementation details. Exercise 4 replaces that default with a controlled error envelope. In this exercise you focus only on placing the annotations correctly.

### Task 3.1 -- Create the CreateAccountRequest DTO

Create `CreateAccountRequest.java` in the `dto` package:

```java
package com.example.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(

        // TODO 7: Annotate ownerId so it cannot be blank.
        // Use @NotBlank with message = "Owner ID is required"
        String ownerId,

        // TODO 8: Annotate accountType so it cannot be blank and must match
        // exactly one of: CHECKING, SAVINGS, INVESTMENT.
        // Use @NotBlank and @Pattern(regexp = "CHECKING|SAVINGS|INVESTMENT",
        //     message = "Account type must be CHECKING, SAVINGS, or INVESTMENT")
        String accountType,

        // TODO 9: Annotate initialDeposit so it must be a positive number.
        // Use @Positive with message = "Initial deposit must be greater than zero"
        double initialDeposit,

        // TODO 10: Annotate description so its length, if provided, does not
        // exceed 255 characters.
        // Use @Size(max = 255, message = "Description must not exceed 255 characters")
        // Note: @Size on a String allows null -- only @NotBlank rejects null.
        String description
) {}
```

Return to `AccountController` and complete TODO 1: add the POST endpoint, annotate the parameter with `@RequestBody @Valid CreateAccountRequest request`.

### Task 3.2 -- Create the CreateTransactionRequest DTO

Create `CreateTransactionRequest.java` in the `dto` package:

```java
package com.example.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateTransactionRequest(

        // TODO 11: Annotate accountId -- must not be blank.
        String accountId,

        // TODO 12: Annotate type -- must be DEBIT or CREDIT.
        // Use @Pattern(regexp = "DEBIT|CREDIT",
        //     message = "Transaction type must be DEBIT or CREDIT")
        String type,

        // TODO 13: Annotate amount -- must be a positive value.
        // Use @Positive with message = "Amount must be greater than zero"
        double amount,

        // TODO 14: Annotate reference -- must not be blank, length 3 to 50 characters.
        // Use @NotBlank and @Size(min = 3, max = 50)
        String reference
) {}
```

Return to `TransactionController` and complete TODO 2.

### Task 3.3 -- Observe the raw validation error

Start the Banking API. Add a POST request to `bank-requests.http` that intentionally omits required fields:

```http
### POST an invalid account -- should return 400.
### Observe the default Spring error body before adding @ControllerAdvice.
POST http://localhost:8081/api/v1/accounts
Authorization: Bearer <full-access-token>
Content-Type: application/json

{
  "ownerId": "",
  "accountType": "MORTGAGE",
  "initialDeposit": -500,
  "description": ""
}
```

Execute the request. Record the exact response body in `lab6-notes.md`. Note which fields are reported and how the error messages are structured. You will compare this with the controlled response after Exercise 4.

### Checkpoints

1. `@NotBlank` rejects null, empty strings, and whitespace-only strings. `@NotNull` only rejects null. `@NotEmpty` rejects null and empty strings but accepts whitespace. Describe a scenario where choosing the wrong annotation would create a security or data quality problem.
2. If you remove `@Valid` from the controller parameter but leave all the field annotations in place, what happens when an invalid request arrives?
3. The `initialDeposit` field uses `double`. What additional validation concern does a floating-point type introduce that `@Positive` alone does not address?

---

## Exercise 4 -- Centralized Error Handling with @ControllerAdvice

**Estimated time:** 25-30 minutes
**Topics covered:** `@ControllerAdvice`, `@ExceptionHandler`, `MethodArgumentNotValidException`, `AccessDeniedException`, consistent error envelopes, HTTP status mapping

### Context

The naive approach to error handling scatters `try-catch` blocks across every controller. When the handling logic needs to change -- for example, adding a request ID to every error response -- every controller must be updated. The same divergence problem that motivated centralizing security configuration applies to error handling.

`@ControllerAdvice` is a Spring stereotype that registers a class as a cross-cutting exception handler. Spring MVC's `ExceptionHandlerExceptionResolver` intercepts any exception thrown by a controller or service, finds the matching `@ExceptionHandler` method in any registered advice class, and delegates response writing to it. The controller never sees the exception again.

Three exception types matter most for this lab:

- `MethodArgumentNotValidException` -- thrown by Spring MVC when `@Valid` fails
- `AccessDeniedException` -- thrown by Spring Security when `@PreAuthorize` denies access
- `Exception` -- a catch-all for anything not handled more specifically

### Task 4.1 -- Define the error envelope

Create `ErrorResponse.java` in the `exception` package:

```java
package com.example.banking.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String status,
        int code,
        String message,
        List<FieldError> errors,
        Instant timestamp
) {
    public record FieldError(String field, String message) {}

    // Convenience factory for single-message errors with no field list.
    public static ErrorResponse of(String status, int code, String message) {
        return new ErrorResponse(status, code, message, List.of(), Instant.now());
    }
}
```

### Task 4.2 -- Write the @ControllerAdvice class

Create `GlobalExceptionHandler.java` in the `exception` package:

```java
package com.example.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

// @RestControllerAdvice is a composed annotation: @ControllerAdvice + @ResponseBody.
// Every method in this class writes directly to the response body as JSON.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // TODO 15: Handle MethodArgumentNotValidException.
    // This exception is thrown when @Valid fails on a @RequestBody parameter.
    // Steps:
    //   1. Extract all field errors from ex.getBindingResult().getFieldErrors()
    //   2. Map each to an ErrorResponse.FieldError using field.getField() and
    //      field.getDefaultMessage()
    //   3. Build and return a ResponseEntity with status 400 and an ErrorResponse
    //      whose message is "Validation failed" and whose errors list contains the
    //      field errors from step 2.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();

        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                fieldErrors,
                Instant.now()
        );

        return ResponseEntity.badRequest().body(body);
    }

    // TODO 16: Handle AccessDeniedException.
    // This exception is thrown by Spring Security when @PreAuthorize denies access.
    // Return 403 Forbidden with status "ACCESS_DENIED" and message
    // "You do not have permission to perform this action."
    // Use ErrorResponse.of() for the body.
    //
    // IMPORTANT: For this handler to intercept AccessDeniedException, you must
    // configure Spring Security not to handle it first. You will do this in Task 4.3.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        // TODO: implement this handler
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", 403,
                        "You do not have permission to perform this action."));
    }

    // TODO 17: Handle the base Exception type as a catch-all.
    // Return 500 Internal Server Error with status "INTERNAL_ERROR" and message
    // "An unexpected error occurred."
    // Log the exception to the console using System.err.println or a logger.
    // Never expose ex.getMessage() directly in the response body.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // TODO: log ex and return a 500 body
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", 500,
                        "An unexpected error occurred."));
    }
}
```

### Task 4.3 -- Let @ControllerAdvice handle AccessDeniedException

By default Spring Security catches `AccessDeniedException` in its own filter chain and returns a plain-text 403 before the request ever reaches your `@ControllerAdvice`. To override this, register a custom `AccessDeniedHandler` in `SecurityConfig` that re-throws or delegates to let the normal dispatcher handle it.

The simpler approach is to register a handler that writes the JSON directly. Add the following bean to `SecurityConfig`:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.banking.exception.ErrorResponse;
import org.springframework.security.web.access.AccessDeniedHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

// Add this method inside SecurityConfig.

@Bean
public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, ex) -> {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        ErrorResponse body = ErrorResponse.of(
                "ACCESS_DENIED",
                403,
                "You do not have permission to perform this action."
        );

        response.getWriter().write(mapper.writeValueAsString(body));
    };
}
```

Wire it into the security configuration by adding the following inside `securityFilterChain`, after the existing configuration:

```java
// TODO 18: Register the custom AccessDeniedHandler.
// Use: .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
// Without this, Spring Security writes its own plain-text 403 before your
// @ControllerAdvice has a chance to handle the exception.
```

Similarly, register an `AuthenticationEntryPoint` that returns a JSON 401 body:

```java
// TODO 19: Add a custom AuthenticationEntryPoint bean that returns:
// {"status":"AUTHENTICATION_REQUIRED","code":401,
//  "message":"A valid Bearer token is required","errors":[],"timestamp":"..."}
// Wire it with: .exceptionHandling(ex -> ex
//       .accessDeniedHandler(accessDeniedHandler())
//       .authenticationEntryPoint(authenticationEntryPoint()))
```

### Task 4.4 -- Verify the controlled error responses

Add the following requests to `bank-requests.http`:

```http
### POST invalid account -- should now return a controlled 400 body.
POST http://localhost:8081/api/v1/accounts
Authorization: Bearer <full-access-token>
Content-Type: application/json

{
  "ownerId": "",
  "accountType": "MORTGAGE",
  "initialDeposit": -500
}

###

### POST invalid transaction -- observe multiple field errors in one response.
POST http://localhost:8081/api/v1/transactions
Authorization: Bearer <full-access-token>
Content-Type: application/json

{
  "accountId": "",
  "type": "WIRE",
  "amount": 0,
  "reference": "X"
}

###

### GET users with read-only token -- should return the controlled 403 body.
GET http://localhost:8081/api/v1/users
Authorization: Bearer <read-only-token>

###

### GET accounts with no token -- should return the controlled 401 body.
GET http://localhost:8081/api/v1/accounts
```

Compare the response bodies to what you recorded in Task 3.3. In `lab6-notes.md`, describe how the controlled error envelope improves the API contract for consumers.

### Checkpoints

1. The `@ExceptionHandler(AccessDeniedException.class)` method in `GlobalExceptionHandler` would never be reached without the custom `AccessDeniedHandler` in `SecurityConfig`. Explain the exact point in the filter chain where Spring Security intercepts `AccessDeniedException` by default, and why it can act before `DispatcherServlet` is involved.
2. The catch-all `@ExceptionHandler(Exception.class)` handler does not expose `ex.getMessage()`. Give a concrete example of a message an exception might carry that would create a security problem if included in the response body.
3. Every `ErrorResponse` includes a `timestamp` field. Describe one operational benefit and one potential privacy concern this field introduces.

---

## Exercise 5 -- Method-Level Security with @PreAuthorize

**Estimated time:** 20-25 minutes
**Topics covered:** `@PreAuthorize`, SpEL expressions, scope and role combinations, `@PostAuthorize`, testing method security with `@WithMockUser`

### Context

URL-level rules in `SecurityFilterChain` enforce authorization at the HTTP boundary. They work for simple patterns like "only callers with scope X can call this path." Method-level security enforces authorization at the service boundary and works regardless of how the method is called -- through an HTTP controller, a scheduled job, or internal application code.

`@EnableMethodSecurity` in `SecurityConfig` activates the AOP infrastructure that wraps every Spring-managed bean in a proxy. Every call to a method annotated with `@PreAuthorize` goes through the proxy, which evaluates the SpEL expression before delegating to the real method. If the expression is false, `AccessDeniedException` is thrown and the method body never runs.

### Task 5.1 -- Create the AccountService with method-level security

Create `AccountService.java` in the `service` package:

```java
package com.example.banking.service;

import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.model.Account;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private final Map<String, Account> store = new ConcurrentHashMap<>();

    public AccountService() {
        store.put("ACC-001", new Account("ACC-001", "user-1", "CHECKING", 1500.00));
        store.put("ACC-002", new Account("ACC-002", "user-2", "SAVINGS", 8200.50));
        store.put("ACC-003", new Account("ACC-003", "user-1", "SAVINGS", 3100.75));
    }

    // TODO 20: Add @PreAuthorize to require SCOPE_read:accounts.
    // Any caller without this scope must be denied before the method body runs.
    public List<Account> findAll() {
        return List.copyOf(store.values());
    }

    // TODO 21: Add @PreAuthorize to require SCOPE_read:accounts.
    //
    // TODO 22: Add @PostAuthorize that runs after the method returns.
    // The expression should enforce that the caller can only see accounts
    // they own, unless they also hold SCOPE_admin:users.
    // Expression: "returnObject.isEmpty() ||
    //              returnObject.get().ownerId() == authentication.name ||
    //              hasAuthority('SCOPE_admin:users')"
    //
    // @PostAuthorize is evaluated with the return value available as returnObject.
    // The method body runs first. If the expression is false, AccessDeniedException
    // is thrown and the return value is discarded. This enforces ownership at the
    // object level, not just at the URL level.
    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(store.get(accountId));
    }

    // TODO 23: Add @PreAuthorize that requires BOTH:
    //   hasAuthority('SCOPE_write:accounts') AND hasAuthority('SCOPE_read:accounts')
    // Combine them with &&.
    // A client with write scope but no read scope should be denied.
    public Account create(CreateAccountRequest request) {
        String newId = "ACC-" + (store.size() + 1);
        Account account = new Account(newId, request.ownerId(),
                request.accountType(), request.initialDeposit());
        store.put(newId, account);
        return account;
    }
}
```

### Task 5.2 -- Create the TransactionService with method-level security

Create `TransactionService.java` in the `service` package:

```java
package com.example.banking.service;

import com.example.banking.dto.CreateTransactionRequest;
import com.example.banking.model.Transaction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionService {

    private final Map<String, Transaction> store = new ConcurrentHashMap<>();

    public TransactionService() {
        store.put("TXN-001", new Transaction("TXN-001", "ACC-001", "DEBIT", 200.00));
        store.put("TXN-002", new Transaction("TXN-002", "ACC-001", "CREDIT", 500.00));
        store.put("TXN-003", new Transaction("TXN-003", "ACC-002", "CREDIT", 1000.00));
    }

    // TODO 24: Add @PreAuthorize to require SCOPE_read:transactions.
    public List<Transaction> findAll() {
        return List.copyOf(store.values());
    }

    // TODO 25: Add @PreAuthorize to require SCOPE_read:transactions.
    public List<Transaction> findByAccountId(String accountId) {
        return store.values().stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();
    }

    // TODO 26: Add @PreAuthorize that requires SCOPE_write:transactions.
    public Transaction create(CreateTransactionRequest request) {
        String newId = "TXN-" + (store.size() + 1);
        Transaction txn = new Transaction(newId, request.accountId(),
                request.type(), request.amount());
        store.put(newId, txn);
        return txn;
    }
}
```

### Task 5.3 -- Create the UserService with admin-only access

Create `UserService.java` in the `service` package:

```java
package com.example.banking.service;

import com.example.banking.model.BankUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, BankUser> store = new ConcurrentHashMap<>();

    public UserService() {
        store.put("user-1", new BankUser("user-1", "alice", "alice@bank.example", "CUSTOMER"));
        store.put("user-2", new BankUser("user-2", "bob", "bob@bank.example", "CUSTOMER"));
        store.put("user-3", new BankUser("user-3", "carol", "carol@bank.example", "ADMIN"));
    }

    // TODO 27: Add @PreAuthorize to require SCOPE_admin:users.
    // This is the most sensitive endpoint. Only the full-access client holds this scope.
    public List<BankUser> findAll() {
        return List.copyOf(store.values());
    }

    // TODO 28: Add @PreAuthorize to require SCOPE_admin:users.
    public Optional<BankUser> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }
}
```

### Task 5.4 -- Wire services into controllers

Inject `AccountService` into `AccountController`, `TransactionService` into `TransactionController`, and `UserService` into `UserController` using constructor injection. Replace the static `ACCOUNTS`, `TRANSACTIONS`, and `USERS` lists with service calls.

```java
// Example for AccountController -- apply the same pattern to the other two.

private final AccountService accountService;

public AccountController(AccountService accountService) {
    this.accountService = accountService;
}

@GetMapping
public List<Account> getAllAccounts() {
    return accountService.findAll();
}

@GetMapping("/{accountId}")
public ResponseEntity<Account> getAccountById(@PathVariable String accountId) {
    return accountService.findById(accountId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```

### Task 5.5 -- Test method security without a real token

Add the Spring Security test dependency to `pom.xml` if it is not already present:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Create `AccountServiceSecurityTest.java` in `src/test/java/com/example/banking/service`:

```java
package com.example.banking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountServiceSecurityTest {

    @Autowired
    private AccountService accountService;

    // @WithMockUser populates the SecurityContext with a mock Authentication.
    // No Authorization Server or real token is needed.
    // "SCOPE_read:accounts" is the authority Spring Security derives from the
    // "read:accounts" scope claim when processing a real JWT. The SCOPE_ prefix
    // is added automatically by Spring Security's JWT converter.
    @Test
    @WithMockUser(authorities = {"SCOPE_read:accounts"})
    void findAll_withReadScope_returnsAccounts() {
        var result = accountService.findAll();
        assertThat(result).isNotEmpty();
    }

    @Test
    @WithMockUser   // authenticated but no read:accounts scope
    void findAll_withoutReadScope_throwsAccessDeniedException() {
        // TODO 29: Assert that calling accountService.findAll() throws AccessDeniedException.
        assertThatThrownBy(() -> accountService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findAll_withNoAuthentication_throwsAccessDeniedException() {
        // TODO 30: Call accountService.findAll() with no @WithMockUser annotation
        // (unauthenticated SecurityContext) and assert that AccessDeniedException is thrown.
        // This verifies the method is not accidentally callable without any authentication.
    }

    // TODO 31: Write a test verifying that create() succeeds when the caller holds
    // both SCOPE_write:accounts and SCOPE_read:accounts.
    // @WithMockUser(authorities = {"SCOPE_write:accounts", "SCOPE_read:accounts"})
    @Test
    @WithMockUser(authorities = {"SCOPE_write:accounts", "SCOPE_read:accounts"})
    void create_withWriteAndReadScope_succeeds() {
        // TODO: Build a CreateAccountRequest with valid data and call accountService.create().
        // Assert the returned account is not null and has a generated accountId.
    }

    // TODO 32: Write a test verifying that create() throws AccessDeniedException
    // when the caller holds SCOPE_write:accounts but NOT SCOPE_read:accounts.
    // This validates the compound @PreAuthorize expression on the create() method.
    @Test
    @WithMockUser(authorities = {"SCOPE_write:accounts"})
    void create_withWriteScopeButNoReadScope_throwsAccessDeniedException() {
        // TODO: Assert that create() throws AccessDeniedException.
    }
}
```

Run the tests. All should pass.

### Task 5.6 -- Observe the @PostAuthorize behaviour

Add the following requests to `bank-requests.http` to exercise the `@PostAuthorize` ownership check on `AccountService.findById()`:

```http
### GET a single account with the read-only token.
### The Client Credentials token sub is "bank-client-read", not "user-1".
### The @PostAuthorize expression checks returnObject.get().ownerId() == authentication.name
### so this request should return 403 because "bank-client-read" != "user-1".
GET http://localhost:8081/api/v1/accounts/ACC-001
Authorization: Bearer <read-only-token>

###

### GET the same account with the full-access token.
### The full-access token sub is "bank-client-full", but the token holds SCOPE_admin:users.
### The @PostAuthorize expression grants access when hasAuthority('SCOPE_admin:users') is true,
### so this request should return 200.
GET http://localhost:8081/api/v1/accounts/ACC-001
Authorization: Bearer <full-access-token>
```

In `lab6-notes.md`, explain in your own words why `@PostAuthorize` is used here instead of `@PreAuthorize`, and what trade-off that introduces.

### Checkpoints

1. The `@PreAuthorize` annotations on `AccountService` methods duplicate some of the URL rules in `SecurityFilterChain`. In the lecture, the principle of layered security was described as applying URL rules for coarse-grained control and method rules for fine-grained control. Explain which of the method-level rules in this exercise could NOT be expressed as a URL rule, and why.
2. `@PostAuthorize` runs the method body before evaluating the expression. Identify the specific scenario in this exercise where that sequencing is necessary, and explain what information is unavailable at pre-authorize time.
3. You wrote both an authorized test and an unauthorized test for `findAll()`. Explain why the unauthorized test is the more important of the two from a security perspective.

---

## Summary and Reflection

After completing this lab you have applied the full security and validation stack to a banking domain: a Resource Server validated by Client Credentials tokens, URL-level rules enforcing scope at the HTTP boundary, Bean Validation constraints rejecting malformed input before controller code runs, a `@ControllerAdvice` delivering a consistent error envelope regardless of exception type, and `@PreAuthorize` and `@PostAuthorize` annotations enforcing authorization at the service layer.

| Exercise | Concept | Key Insight |
|---|---|---|
| 1 -- Token Acquisition | Client Credentials grant, scope design | The token identifies the service, not a user. Scope is the mechanism that limits what the service can do. |
| 2 -- Resource Server | `SecurityFilterChain`, URL-level scope rules, 401 vs 403 | Spring Security validates every token automatically. Your job is to declare rules, not implement verification. |
| 3 -- Input Validation | `@Valid`, Bean Validation constraints, `@NotBlank`, `@Pattern`, `@Positive` | Validation runs before controller code. A missing `@Valid` silently disables all field constraints. |
| 4 -- Error Handling | `@ControllerAdvice`, `@ExceptionHandler`, consistent error envelopes | A single handler class centralizes all error response logic. The default Spring error body leaks implementation details. |
| 5 -- Method Security | `@PreAuthorize`, `@PostAuthorize`, AOP proxies, testing with `@WithMockUser` | Method security enforces authorization at the service boundary regardless of entry point. |

### Final Reflection Questions

Take 10 minutes to write answers in `lab6-notes.md` before your next session.

1. In Exercise 5 you placed `@PreAuthorize` annotations on service methods that already had corresponding URL rules in `SecurityFilterChain`. A colleague argues this is redundant and the method annotations should be removed. Construct the argument for keeping both layers, and describe a concrete scenario where removing the method annotations would create a vulnerability.

2. The `@PostAuthorize` expression in `AccountService.findById()` executes the method body before evaluating the ownership check. This means a database query runs even for requests that will ultimately be denied. Describe the trade-off and identify a real-world scenario where this would be problematic enough to justify a different approach.

3. The `GlobalExceptionHandler` catch-all handler returns a generic 500 message and never exposes `ex.getMessage()`. A developer on your team suggests including the exception message in development environments by reading a configuration property. What logging and monitoring alternative gives you the same debugging capability without any risk of leaking information to clients?

---

*End of Lab 5*
