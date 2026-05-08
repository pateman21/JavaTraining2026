# Module 3: OAuth2, OIDC, and Spring Security
## Lab 5 -- Solutions and Checkpoint Answers

> **Course:** MD282 - Java Full-Stack Development
> **Purpose:** This file contains completed code for every TODO item and written answers
> for every Checkpoint and Reflection question. Use it to verify your work after
> attempting each task yourself. Reading the solution before attempting the task
> defeats the purpose of the exercise.

---

## How to Use This File

Each section below corresponds to an exercise in the lab. Within each exercise, the
completed TODO items appear first, followed by written answers to the Checkpoint
questions. The TODO numbers match exactly the numbers in the lab file.

---

## Exercise 1 -- Authorization Server Setup and Token Acquisition

### Task 1.2 -- Inspection questions (lab6-notes.md answers)

**Question 1: What is the value of `sub` in each token? Why is it the client ID rather than a username?**

The `sub` claim in both tokens is the client ID: `bank-client-read` in the read-only token
and `bank-client-full` in the full-access token.

In the Client Credentials flow there is no user involved at all. The client application
authenticates directly to the Authorization Server using its own credentials. The resulting
token represents the service identity, not a human identity. The `sub` claim identifies
the principal that authenticated, which in this flow is the client application itself.
This is why the value is the client ID and not a username: there is no username to record.

**Question 2: What is the value of `scp` or `scope`?**

The read-only token contains `scope: "read:accounts read:transactions"`.
The full-access token contains `scope: "read:accounts read:transactions write:accounts write:transactions admin:users"`.

The two tokens carry different scopes because each registered client was configured with
a different set of allowed scopes. The Authorization Server only issues scopes that are
both requested and registered for that client.

**Question 3: What would happen if you requested `admin:users` using `bank-client-read`?**

The Authorization Server would return an error response, not a token. Specifically it
returns `{"error": "invalid_scope"}` because `admin:users` is not registered as an
allowed scope for `bank-client-read`. The token request fails entirely; the Authorization
Server does not silently drop the disallowed scope and issue a partial token. This is an
important safety guarantee: a client cannot self-escalate by requesting scopes it was
not granted at registration time.

---

## Exercise 2 -- Resource Server Configuration and URL Security

### TODO 3 -- Authorization rules in SecurityConfig

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/health").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/**").hasAuthority("SCOPE_read:accounts")
        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_write:accounts")
        .requestMatchers(HttpMethod.GET, "/api/v1/transactions/**").hasAuthority("SCOPE_read:transactions")
        .requestMatchers(HttpMethod.POST, "/api/v1/transactions").hasAuthority("SCOPE_write:transactions")
        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAuthority("SCOPE_admin:users")
        .anyRequest().authenticated()
)
```

### TODO 4 -- JWT Resource Server configuration

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

This single call activates the full JWT validation pipeline: extracting the Bearer token
from the `Authorization` header, fetching the public key from `jwks-uri`, verifying the
RS256 signature, checking the `exp`, `iss`, and `aud` claims, and mapping each value in
the `scope` claim to a `SCOPE_`-prefixed granted authority.

### TODO 5 -- Stateless session management

```java
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

### TODO 6 -- Disable CSRF

```java
.csrf(csrf -> csrf.disable())
```

### Complete SecurityConfig.java

```java
package com.example.banking.config;

import com.example.banking.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/accounts/**").hasAuthority("SCOPE_read:accounts")
                    .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_write:accounts")
                    .requestMatchers(HttpMethod.GET, "/api/v1/transactions/**").hasAuthority("SCOPE_read:transactions")
                    .requestMatchers(HttpMethod.POST, "/api/v1/transactions").hasAuthority("SCOPE_write:transactions")
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAuthority("SCOPE_admin:users")
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(ex -> ex
                    .accessDeniedHandler(accessDeniedHandler())
                    .authenticationEntryPoint(authenticationEntryPoint()));

        return http.build();
    }

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

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            ErrorResponse body = ErrorResponse.of(
                    "AUTHENTICATION_REQUIRED",
                    401,
                    "A valid Bearer token is required."
            );

            response.getWriter().write(mapper.writeValueAsString(body));
        };
    }
}
```

### TODO 1 -- POST endpoint in AccountController

`CreateAccountRequest` does not exist yet when you are working through Exercise 2, which
is why the lab asks you to leave this as a stub and return to it in Exercise 3. You
cannot write the final implementation until the DTO and its validation annotations are
in place.

When you reach Exercise 3 and have created `CreateAccountRequest`, add the following
method to `AccountController`:

```java
@PostMapping
public ResponseEntity<CreateAccountRequest> createAccount(
        @RequestBody @Valid CreateAccountRequest request) {
    // Persistence not implemented -- this is a stub.
    // In a real application, accountService.create(request) would be called here.
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
}
```

After completing Exercise 5 and wiring in `AccountService`, replace the stub with:

```java
@PostMapping
public ResponseEntity<Account> createAccount(
        @RequestBody @Valid CreateAccountRequest request) {
    Account created = accountService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

### TODO 2 -- POST endpoint in TransactionController

The same applies to `TransactionController`. `CreateTransactionRequest` is created in
Exercise 3. Add the following once that DTO exists:

```java
@PostMapping
public ResponseEntity<CreateTransactionRequest> createTransaction(
        @RequestBody @Valid CreateTransactionRequest request) {
    // Persistence not implemented -- this is a stub.
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
}
```

After completing Exercise 5 and wiring in `TransactionService`, replace the stub with:

```java
@PostMapping
public ResponseEntity<Transaction> createTransaction(
        @RequestBody @Valid CreateTransactionRequest request) {
    Transaction created = transactionService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

### Exercise 2 Checkpoint Answers

**Checkpoint 1: What does Spring Security examine to decide between 401 and 403?**

Spring Security makes the decision in its exception handling filter, which sits at the
outer edge of the filter chain. It examines the `SecurityContext` after the request has
been processed.

If no `Authentication` object is present in the `SecurityContext`, or if the
`Authentication` is anonymous (that is, no credentials were provided at all), Spring
Security treats the caller as unauthenticated and returns 401. The semantics of 401 are
"I do not know who you are; please identify yourself."

If a valid `Authentication` object is present -- meaning the token was cryptographically
valid and passed all claim checks -- but the caller lacks the required authority for the
requested resource, Spring Security returns 403. The semantics of 403 are "I know who
you are, but you are not allowed to do this."

In practice: a missing or expired token produces 401. A valid token with insufficient
scope produces 403.

**Checkpoint 2: Where does the SCOPE_ prefix come from?**

The `SCOPE_` prefix is added by `JwtGrantedAuthoritiesConverter`, which is the default
component Spring Security uses to map JWT claims to `GrantedAuthority` objects. It reads
the `scope` or `scp` claim from the JWT payload -- which contains values like
`read:accounts` -- and converts each one to a `SimpleGrantedAuthority` with the value
`SCOPE_read:accounts`. This happens inside the `JwtAuthenticationConverter`, which is
invoked by the `BearerTokenAuthenticationFilter` after the signature is verified and
before the `Authentication` object is placed in the `SecurityContext`.

**Checkpoint 3: What would go wrong with the default session policy?**

The default `SessionCreationPolicy` is `IF_REQUIRED`, which tells Spring Security to
create an `HttpSession` if one does not already exist. In a stateless JWT Resource
Server, this would cause a session to be created on the first authenticated request.
Subsequent requests that include the session cookie would be treated as authenticated
even without a Bearer token, because the `SecurityContext` would be restored from the
session rather than re-validated from a JWT. This silently bypasses JWT validation for
any client that receives and resends the session cookie. Setting `STATELESS` prevents
session creation entirely, ensuring every request is independently authenticated from
its token.

---

## Exercise 3 -- Input Validation

### TODO 7 through TODO 10 -- Complete CreateAccountRequest.java

```java
package com.example.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(

        @NotBlank(message = "Owner ID is required")
        String ownerId,

        @NotBlank(message = "Account type is required")
        @Pattern(
                regexp = "CHECKING|SAVINGS|INVESTMENT",
                message = "Account type must be CHECKING, SAVINGS, or INVESTMENT"
        )
        String accountType,

        @Positive(message = "Initial deposit must be greater than zero")
        double initialDeposit,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description
) {}
```

### TODO 11 through TODO 14 -- Complete CreateTransactionRequest.java

```java
package com.example.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransactionRequest(

        @NotBlank(message = "Account ID is required")
        String accountId,

        @NotBlank(message = "Transaction type is required")
        @Pattern(
                regexp = "DEBIT|CREDIT",
                message = "Transaction type must be DEBIT or CREDIT"
        )
        String type,

        @Positive(message = "Amount must be greater than zero")
        double amount,

        @NotBlank(message = "Reference is required")
        @Size(min = 3, max = 50, message = "Reference must be between 3 and 50 characters")
        String reference
) {}
```

### Exercise 3 Checkpoint Answers

**Checkpoint 1: What problem does choosing the wrong annotation create?**

Consider a `username` field where the developer uses `@NotNull` instead of `@NotBlank`.
A client can send `{"username": "   "}` (all whitespace) and the constraint passes
because the value is not null. The application stores a whitespace-only username, which
can break lookups, display logic, and uniqueness checks downstream. In an authentication
context this could mean two accounts with superficially different but functionally
identical usernames coexist, creating an account enumeration or impersonation
opportunity.

Conversely, using `@NotBlank` on a nullable optional field means clients cannot omit it
at all, which breaks backward compatibility if the field is added to an existing API. The
choice between `@NotNull`, `@NotEmpty`, and `@NotBlank` must match the domain semantics
of the field precisely.

**Checkpoint 2: What happens if @Valid is removed from the parameter?**

Nothing. The Bean Validation annotations on the DTO fields are pure metadata. They have
no effect at runtime unless a validation framework is instructed to evaluate them.
`@Valid` is the instruction to Spring MVC to run the validator before invoking the
controller method. Without `@Valid`, all field constraints are silently ignored and the
method receives the object regardless of its contents. This is one of the most common
sources of "validation is not working" bugs in Spring applications: the annotations are
present on the DTO but `@Valid` is missing from the controller parameter.

**Checkpoint 3: What additional concern does `double` introduce that @Positive does not address?**

`@Positive` ensures the value is greater than zero, but it does not prevent values like
`NaN` (Not a Number), `Infinity`, or `-Infinity`, which are all valid IEEE 754 `double`
values. A client can send `{"amount": "Infinity"}` and, depending on the JSON parser
configuration, it may deserialize without error and pass `@Positive` (since `Infinity`
is considered greater than zero). Storing or computing with these values produces
undefined results.

The safer approach is to use `BigDecimal` for monetary amounts, which avoids
floating-point representation errors entirely, and to add an explicit `@DecimalMax`
constraint to cap reasonable transaction amounts. This also eliminates the floating-point
precision problem where values like `0.1 + 0.2` do not equal `0.3` exactly.

---

## Exercise 4 -- Centralized Error Handling with @ControllerAdvice

### TODO 15 -- Handle MethodArgumentNotValidException (completed)

The lab file already provides the completed implementation. The key points to understand
are:

- `ex.getBindingResult().getFieldErrors()` returns one `FieldError` per violated
  constraint. If three fields fail, three entries appear in the list.
- `f.getField()` returns the Java field name as declared in the record (for example,
  `"ownerId"`).
- `f.getDefaultMessage()` returns the `message` attribute from the constraint annotation
  (for example, `"Owner ID is required"`).
- The response status is always 400 for this exception type.

```java
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
```

### TODO 16 -- Handle AccessDeniedException (completed)

The lab file already provides the completed implementation. The important concept is that
this handler is only reached if Spring Security has been configured to delegate
`AccessDeniedException` handling rather than writing the response itself. This is
accomplished by registering the custom `AccessDeniedHandler` bean in `SecurityConfig`
(TODO 18).

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("ACCESS_DENIED", 403,
                    "You do not have permission to perform this action."));
}
```

### TODO 17 -- Handle generic Exception (with logging)

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    // Log the full exception internally so it is available in application logs
    // and monitoring tools, but never expose it to the caller.
    System.err.println("Unhandled exception: " + ex.getMessage());
    ex.printStackTrace();

    return ResponseEntity.internalServerError()
            .body(ErrorResponse.of("INTERNAL_ERROR", 500,
                    "An unexpected error occurred."));
}
```

In a production application replace `System.err.println` with an SLF4J logger:

```java
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

// Inside the handler:
log.error("Unhandled exception processing request", ex);
```

### TODO 18 -- Register the custom AccessDeniedHandler

```java
.exceptionHandling(ex -> ex
        .accessDeniedHandler(accessDeniedHandler())
        .authenticationEntryPoint(authenticationEntryPoint()))
```

This belongs inside `securityFilterChain`, chained after `.csrf(csrf -> csrf.disable())`.

### TODO 19 -- AuthenticationEntryPoint bean

```java
@Bean
public AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, ex) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        ErrorResponse body = ErrorResponse.of(
                "AUTHENTICATION_REQUIRED",
                401,
                "A valid Bearer token is required."
        );

        response.getWriter().write(mapper.writeValueAsString(body));
    };
}
```

### Complete GlobalExceptionHandler.java

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

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", 403,
                        "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        System.err.println("Unhandled exception: " + ex.getMessage());
        ex.printStackTrace();

        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", 500,
                        "An unexpected error occurred."));
    }
}
```

### Exercise 4 Checkpoint Answers

**Checkpoint 1: Where does Spring Security intercept AccessDeniedException by default?**

Spring Security processes `AccessDeniedException` in `ExceptionTranslationFilter`, which
is placed in the filter chain after authentication filters but before the request reaches
`DispatcherServlet`. When an `AccessDeniedException` propagates up from either a
`SecurityFilterChain` authorization check or a method security AOP proxy, it is caught
by `ExceptionTranslationFilter`, which calls the registered `AccessDeniedHandler`
directly on the raw `HttpServletResponse`.

Because this happens in the filter chain before `DispatcherServlet` is invoked, the
exception never enters Spring MVC's exception handling infrastructure at all. The
`@ControllerAdvice` class is a Spring MVC construct and only handles exceptions that
originate inside `DispatcherServlet`'s request processing pipeline. To intercept the
exception with a `@ControllerAdvice` handler you must replace the default
`AccessDeniedHandler` with one that either writes the response directly (as done in this
lab) or re-dispatches the error to a controller endpoint using
`RequestDispatcher.forward()`.

**Checkpoint 2: Give a concrete example of a dangerous exception message.**

`java.sql.SQLException: ORA-00942: table or view 'BANK_ACCOUNTS' does not exist`

If this message were included in a 500 response body it would tell a caller:
(1) the application uses Oracle, (2) the exact table name for accounts, and (3) that
this table is currently missing -- which may indicate a deployment error or a successful
drop-table injection. An attacker can use this information to craft targeted SQL
injection payloads or to map the database schema.

Other dangerous examples include messages from `ClassNotFoundException` (reveals
package structure), `FileNotFoundException` (reveals file system paths), and
`AuthenticationException` subtypes that reveal whether a user account exists.

**Checkpoint 3: Operational benefit and privacy concern of the timestamp field.**

The operational benefit is correlation. When a client reports an error, the timestamp
in the response body can be used to find the corresponding log entry, distributed trace,
or metric spike without requiring the client to know the server's log format or time
zone. This is particularly useful in microservice environments where the same logical
request touches multiple services.

The privacy concern is that timestamps reveal server activity patterns. A sequence of
error responses with timestamps can tell an external observer roughly how many requests
the server is receiving and at what rate, which may be considered operational
intelligence. In regulated environments (certain financial and healthcare contexts),
response body content including timestamps may fall under data minimization requirements.
The standard mitigation is to return a correlation ID instead of a timestamp, log the
timestamp internally, and allow the client to reference the correlation ID when
reporting issues.

---

## Exercise 5 -- Method-Level Security with @PreAuthorize

### TODO 20 -- @PreAuthorize on AccountService.findAll()

```java
@PreAuthorize("hasAuthority('SCOPE_read:accounts')")
public List<Account> findAll() {
    return List.copyOf(store.values());
}
```

### TODO 21 and TODO 22 -- @PreAuthorize and @PostAuthorize on AccountService.findById()

```java
@PreAuthorize("hasAuthority('SCOPE_read:accounts')")
@PostAuthorize("returnObject.isEmpty() || " +
               "returnObject.get().ownerId() == authentication.name || " +
               "hasAuthority('SCOPE_admin:users')")
public Optional<Account> findById(String accountId) {
    return Optional.ofNullable(store.get(accountId));
}
```

### TODO 23 -- @PreAuthorize on AccountService.create()

```java
@PreAuthorize("hasAuthority('SCOPE_write:accounts') && hasAuthority('SCOPE_read:accounts')")
public Account create(CreateAccountRequest request) {
    String newId = "ACC-" + (store.size() + 1);
    Account account = new Account(newId, request.ownerId(),
            request.accountType(), request.initialDeposit());
    store.put(newId, account);
    return account;
}
```

### TODO 24 -- @PreAuthorize on TransactionService.findAll()

```java
@PreAuthorize("hasAuthority('SCOPE_read:transactions')")
public List<Transaction> findAll() {
    return List.copyOf(store.values());
}
```

### TODO 25 -- @PreAuthorize on TransactionService.findByAccountId()

```java
@PreAuthorize("hasAuthority('SCOPE_read:transactions')")
public List<Transaction> findByAccountId(String accountId) {
    return store.values().stream()
            .filter(t -> t.accountId().equals(accountId))
            .toList();
}
```

### TODO 26 -- @PreAuthorize on TransactionService.create()

```java
@PreAuthorize("hasAuthority('SCOPE_write:transactions')")
public Transaction create(CreateTransactionRequest request) {
    String newId = "TXN-" + (store.size() + 1);
    Transaction txn = new Transaction(newId, request.accountId(),
            request.type(), request.amount());
    store.put(newId, txn);
    return txn;
}
```

### TODO 27 -- @PreAuthorize on UserService.findAll()

```java
@PreAuthorize("hasAuthority('SCOPE_admin:users')")
public List<BankUser> findAll() {
    return List.copyOf(store.values());
}
```

### TODO 28 -- @PreAuthorize on UserService.findById()

```java
@PreAuthorize("hasAuthority('SCOPE_admin:users')")
public Optional<BankUser> findById(String userId) {
    return Optional.ofNullable(store.get(userId));
}
```

### Complete AccountService.java

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

    @PreAuthorize("hasAuthority('SCOPE_read:accounts')")
    public List<Account> findAll() {
        return List.copyOf(store.values());
    }

    @PreAuthorize("hasAuthority('SCOPE_read:accounts')")
    @PostAuthorize("returnObject.isEmpty() || " +
                   "returnObject.get().ownerId() == authentication.name || " +
                   "hasAuthority('SCOPE_admin:users')")
    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(store.get(accountId));
    }

    @PreAuthorize("hasAuthority('SCOPE_write:accounts') && hasAuthority('SCOPE_read:accounts')")
    public Account create(CreateAccountRequest request) {
        String newId = "ACC-" + (store.size() + 1);
        Account account = new Account(newId, request.ownerId(),
                request.accountType(), request.initialDeposit());
        store.put(newId, account);
        return account;
    }
}
```

### Complete TransactionService.java

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

    @PreAuthorize("hasAuthority('SCOPE_read:transactions')")
    public List<Transaction> findAll() {
        return List.copyOf(store.values());
    }

    @PreAuthorize("hasAuthority('SCOPE_read:transactions')")
    public List<Transaction> findByAccountId(String accountId) {
        return store.values().stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();
    }

    @PreAuthorize("hasAuthority('SCOPE_write:transactions')")
    public Transaction create(CreateTransactionRequest request) {
        String newId = "TXN-" + (store.size() + 1);
        Transaction txn = new Transaction(newId, request.accountId(),
                request.type(), request.amount());
        store.put(newId, txn);
        return txn;
    }
}
```

### Complete UserService.java

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

    @PreAuthorize("hasAuthority('SCOPE_admin:users')")
    public List<BankUser> findAll() {
        return List.copyOf(store.values());
    }

    @PreAuthorize("hasAuthority('SCOPE_admin:users')")
    public Optional<BankUser> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }
}
```

### TODO 29 -- findAll_withoutReadScope test body

```java
@Test
@WithMockUser
void findAll_withoutReadScope_throwsAccessDeniedException() {
    assertThatThrownBy(() -> accountService.findAll())
            .isInstanceOf(AccessDeniedException.class);
}
```

### TODO 30 -- findAll_withNoAuthentication test body

```java
@Test
void findAll_withNoAuthentication_throwsAccessDeniedException() {
    // No @WithMockUser -- the SecurityContext is empty (unauthenticated).
    // Spring Security evaluates hasAuthority(...) against an empty context
    // and throws AccessDeniedException rather than returning null or an empty list.
    assertThatThrownBy(() -> accountService.findAll())
            .isInstanceOf(AccessDeniedException.class);
}
```

### TODO 31 -- create_withWriteAndReadScope_succeeds test body

```java
@Test
@WithMockUser(authorities = {"SCOPE_write:accounts", "SCOPE_read:accounts"})
void create_withWriteAndReadScope_succeeds() {
    CreateAccountRequest request = new CreateAccountRequest(
            "user-1",
            "SAVINGS",
            500.00,
            "Test account"
    );

    Account result = accountService.create(request);

    assertThat(result).isNotNull();
    assertThat(result.accountId()).isNotBlank();
    assertThat(result.ownerId()).isEqualTo("user-1");
    assertThat(result.accountType()).isEqualTo("SAVINGS");
}
```

### TODO 32 -- create_withWriteScopeButNoReadScope test body

```java
@Test
@WithMockUser(authorities = {"SCOPE_write:accounts"})
void create_withWriteScopeButNoReadScope_throwsAccessDeniedException() {
    CreateAccountRequest request = new CreateAccountRequest(
            "user-1",
            "SAVINGS",
            500.00,
            "Test account"
    );

    assertThatThrownBy(() -> accountService.create(request))
            .isInstanceOf(AccessDeniedException.class);
}
```

### Complete AccountServiceSecurityTest.java

```java
package com.example.banking.service;

import com.example.banking.dto.CreateAccountRequest;
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

    @Test
    @WithMockUser(authorities = {"SCOPE_read:accounts"})
    void findAll_withReadScope_returnsAccounts() {
        var result = accountService.findAll();
        assertThat(result).isNotEmpty();
    }

    @Test
    @WithMockUser
    void findAll_withoutReadScope_throwsAccessDeniedException() {
        assertThatThrownBy(() -> accountService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findAll_withNoAuthentication_throwsAccessDeniedException() {
        assertThatThrownBy(() -> accountService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_write:accounts", "SCOPE_read:accounts"})
    void create_withWriteAndReadScope_succeeds() {
        CreateAccountRequest request = new CreateAccountRequest(
                "user-1", "SAVINGS", 500.00, "Test account");

        Account result = accountService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isNotBlank();
        assertThat(result.ownerId()).isEqualTo("user-1");
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_write:accounts"})
    void create_withWriteScopeButNoReadScope_throwsAccessDeniedException() {
        CreateAccountRequest request = new CreateAccountRequest(
                "user-1", "SAVINGS", 500.00, "Test account");

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
```

### Complete AccountController.java

This is the final state of the controller after all exercises are complete: the service
is wired in via constructor injection, both GET endpoints delegate to the service, and
the POST endpoint accepts a validated request body and returns the created account.

```java
package com.example.banking.controller;

import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.model.Account;
import com.example.banking.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

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

    @PostMapping
    public ResponseEntity<Account> createAccount(
            @RequestBody @Valid CreateAccountRequest request) {
        Account created = accountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

### Complete TransactionController.java

```java
package com.example.banking.controller;

import com.example.banking.dto.CreateTransactionRequest;
import com.example.banking.model.Transaction;
import com.example.banking.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return transactionService.findAll();
    }

    @GetMapping("/account/{accountId}")
    public List<Transaction> getTransactionsByAccount(@PathVariable String accountId) {
        return transactionService.findByAccountId(accountId);
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(
            @RequestBody @Valid CreateTransactionRequest request) {
        Transaction created = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

### Complete UserController.java

```java
package com.example.banking.controller;

import com.example.banking.model.BankUser;
import com.example.banking.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<BankUser> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BankUser> getUserById(@PathVariable String userId) {
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

### Exercise 5 Checkpoint Answers

**Checkpoint 1: Which method-level rules cannot be expressed as URL rules?**

The `@PostAuthorize` ownership check on `findById()` cannot be expressed as a URL rule.
A URL rule can only examine the request itself: the HTTP method, the path, and the
headers. It cannot examine the response body or compare values from the response against
claims from the token. The ownership check -- verifying that `account.ownerId()` matches
`authentication.name` -- requires the account object to have been fetched first. That
information only exists after the method runs, which is precisely why `@PostAuthorize`
is needed.

The compound `SCOPE_write:accounts && SCOPE_read:accounts` rule on `create()` could be
expressed at the URL level as `hasAuthority('SCOPE_write:accounts') and hasAuthority('SCOPE_read:accounts')`,
but applying it at the method level means the rule is enforced even if `create()` is
called from a scheduled job or internal service that does not go through the HTTP
controller at all. URL rules only protect the HTTP entry point.

**Checkpoint 2: Why is the sequencing of @PostAuthorize necessary here?**

The ownership check compares `returnObject.get().ownerId()` against
`authentication.name`. The `ownerId` field is a property of the `Account` object itself
and is only available after the method has fetched the account from the store. There is
no way to know whose account `ACC-001` belongs to before looking it up. A
`@PreAuthorize` expression cannot reference `returnObject` because the return value does
not exist yet. The post-authorization pattern is required whenever the authorization
decision depends on data that is a property of the returned object rather than a property
of the request.

**Checkpoint 3: Why is the unauthorized test more important?**

A security rule is only meaningful if it correctly rejects unauthorized access. The
authorized test verifies that a correctly configured caller can reach the method, but
that is also what would happen if the `@PreAuthorize` annotation were missing entirely.
A missing annotation produces a passing authorized test and a passing happy path, but
leaves the method completely unprotected.

The unauthorized test is the only test that would fail if the annotation were removed.
It is the test that provides evidence the rule is actually enforced, not just present.
This is the principle from the lecture: every `@PreAuthorize` annotation must have at
least one test that explicitly verifies unauthorized access is denied. Without that test,
a future developer who accidentally deletes the annotation will not be warned by any
failing test.

---

## Final Reflection Question Answers

**Reflection 1: The case for keeping both URL rules and method-level annotations**

The URL rules in `SecurityFilterChain` and the `@PreAuthorize` annotations on service
methods are not redundant -- they guard different entry points. URL rules only apply to
requests that arrive over HTTP through `DispatcherServlet`. They provide no protection
when a service method is called from a `@Scheduled` job, a Kafka consumer, a JMS
listener, or an internal application event handler. If the URL rule for
`POST /api/v1/accounts` is the only protection on account creation, then any code path
that calls `accountService.create()` directly bypasses authorization entirely.

The concrete vulnerability: a developer adds a `@Scheduled` task that auto-generates
accounts based on a configuration file. Because the scheduled method calls
`accountService.create()` directly and there is no HTTP request involved, no URL rule
is evaluated. If only the URL rule exists, the method runs with whatever is in the
`SecurityContext` at that moment -- which in a background thread is typically null.
With `@PreAuthorize` on the service method, the same call throws `AccessDeniedException`
regardless of how it was triggered.

**Reflection 2: The @PostAuthorize trade-off**

The trade-off is that work is performed before the authorization decision is made. In
the banking domain `findById()` queries the in-memory store, which is trivially cheap.
In a real application backed by a database, the query executes, a connection is acquired,
the result is mapped, and the object graph is populated -- all before the ownership check
runs and potentially discards everything.

This becomes problematic in scenarios with high read volume against sensitive records.
If an attacker issues thousands of requests for account IDs they do not own, each request
executes a database query, consumes a connection from the pool, and generates load, even
though every response is a 403. A pre-authorization approach requires some other
mechanism to make the ownership information available before the query -- for example,
embedding the `ownerId` in the URL path and verifying it against the token before
fetching, or performing a lightweight ownership check query first. The correct approach
depends on the sensitivity of the data and the expected volume of requests.

**Reflection 3: Logging and monitoring alternative to exposing exception messages**

The standard production pattern is structured logging with a correlation ID. When an
exception occurs, the handler generates a UUID, logs the full exception including the
message and stack trace at the ERROR level with that UUID attached, and returns the UUID
to the client in the error response body as a `correlationId` field. The client sees
only the generic message and the correlation ID. A developer investigating an incident
searches the centralized log system (Elasticsearch, Splunk, CloudWatch Logs) by the
correlation ID and finds the full exception detail there.

This pattern gives the same debugging capability -- the full exception message and stack
trace are available -- without any risk of information leakage because the sensitive
information never leaves the server boundary. It also scales better than configuration
flags because the debug information is always present in logs for any environment,
eliminating the class of incidents where a production issue cannot be reproduced because
the debug flag is not enabled.

---

*End of Lab 5 Solutions*
