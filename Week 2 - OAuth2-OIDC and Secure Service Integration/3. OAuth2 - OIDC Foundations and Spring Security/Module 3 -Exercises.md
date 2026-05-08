# Module 3: OAuth2, OIDC, and Spring Security
## Hands-On Exercises

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 3 - OAuth2 and OIDC Foundations and Spring Security
> **Estimated time per exercise:** 30–60 minutes

---

## How to Use These Exercises

Each exercise is self-contained and builds directly on the concepts introduced in the module lectures. They are designed to be completed in IntelliJ IDEA using a Spring Boot project generated from Spring Initializr.

- **Context:** why this concept matters and what problem it solves
- **Setup:** how to structure the project and files before you start
- **Tasks:** step-by-step work to complete, including starter code with `TODO` markers
- **Checkpoints:** questions that test conceptual understanding, not just code completion
- **Extension tasks:** optional challenges for faster learners

> **Note:** The extension exercises are not required to complete the module, but they provide an opportunity to deepen your understanding. They are designed to be more challenging and may require additional research or experimentation.
>
> Suggested solutions to the extension exercises will be provided at the end of the module.

> **Tip:** Work through the exercises in order. Each one builds on the project structure established in the first. If you get stuck on a `TODO`, re-read the relevant section of the lecture slides before looking at the solution file.

---

## Before You Start: The Authorization Server

These exercises require the local Authorization Server to be running. You built and verified it in the setup exercise. If it is not currently running, start it now before continuing.

Confirm it is ready by opening this URL in your browser:

```
http://localhost:9000/oauth2/jwks
```

You should see a JSON response containing an RSA public key. If you see an error, start the `auth-server` project and wait for it to finish starting before proceeding.

**Authorization Server reference — values you will use throughout these exercises:**

| Item | Value |
|---|---|
| JWKS endpoint | `http://localhost:9000/oauth2/jwks` |
| Issuer URI | `http://localhost:9000` |
| Token endpoint | `http://localhost:9000/oauth2/token` |
| User: alice | password: `password`, role: `HR_MANAGER` |
| User: bob | password: `password`, role: `VIEWER` |
| Service client ID | `workforce-service` |
| Service client secret | `workforce-service-secret` |

---

## Tools Used in These Exercises

- **jwt.io**: paste any JWT at [https://jwt.io](https://jwt.io) to decode the header and payload immediately
- **IntelliJ HTTP Client**: `.http` files work the same way as in Module 2

---

## Starter Project

All exercises share a single Spring Boot project for the Workforce API (the Resource Server). Create it now.

### Create the project with Spring Initializr

1. Open [https://start.spring.io](https://start.spring.io) in a browser
2. Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | Latest stable (3.x or higher) |
| Group | `com.example` |
| Artifact | `workforce` |
| Packaging | Jar |
| Java | 17 |

3. Add the following dependencies:
   - **Spring Web**
   - **Spring Security**
   - **OAuth2 Resource Server**
   - **Spring Boot DevTools**
   - **Validation**

4. Click **Generate**, unzip, and open in IntelliJ using **File → Open → New Window**

5. Confirm there are no Maven download errors in the Maven tool window

### Verify the project starts and notice what Spring Security does

Run `WorkforceApplication.java`. You should see:

```
Using generated security password: 8f3a1b9c-4d2e-4f7a-9c3e-1b2d3e4f5a6b
Started WorkforceApplication in X.XXX seconds
```

However, you may not depend on the logging level. If you don't see it, don't worry, you don't really need the credentials.

Open `http://localhost:8080/` in your browser. Spring Security has locked everything and redirected you to a login form. This is the **secure by default** behaviour described in the lecture. You have not written a single line of security configuration yet.

### Package structure to create before Exercise 1

Right-click `src/main/java/com/example/workforce` and create the following sub-packages:

```
com.example.workforce
├── config
├── controller
├── model
└── service
```

---

## Exercise 1 - Decoding JWTs and Understanding the Token Structure

**Estimated time:** 30–40 minutes
**Topics covered:** JWT structure (header, payload, signature), registered claims (`sub`, `iss`, `aud`, `exp`, `iat`), scopes, roles, what a valid signature proves and what it does not

### Context

Before configuring Spring Security to validate tokens you need to understand what a JWT actually contains. This exercise is primarily analytical. You will obtain a real token from the Authorization Server, decode it, read the claims, and reason about the security implications.

A valid signature proves the token was issued by a trusted Authorization Server and has not been tampered with. A valid signature does not prove the token has not been stolen, and does not prove the user's permissions are still current. Understanding this distinction shapes how you design token lifetimes and scope granularity.

### Task 1.1 - Obtain a service token

Create `auth-requests.http` in the **workforce** project root. This file gives you a convenient way to obtain tokens from the Authorization Server throughout the exercises:

```http
### Client Credentials token -- service identity, no user involved.
### The Authorization header is Base64("workforce-service:workforce-service-secret").
POST http://localhost:9000/oauth2/token
Authorization: Basic d29ya2ZvcmNlLXNlcnZpY2U6d29ya2ZvcmNlLXNlcnZpY2Utc2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=read:employees

###

### Authorization Code flow -- Step 1.
### Open the URL below manually in your browser (do not run it as an HTTP request).
### Log in as alice / password and approve the scopes on the consent screen.
### The browser redirects to http://127.0.0.1:9000/authorized?code=XXXX
### Copy the "code" query parameter value from the URL bar before it expires.
###
### URL to open in browser:
### http://localhost:9000/oauth2/authorize?response_type=code&client_id=workforce-spa&redirect_uri=http://127.0.0.1:9000/authorized&scope=openid%20profile%20read:employees%20write:employees&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&state=abc123

###

### Authorization Code flow -- Step 2.
### Replace CODE_FROM_BROWSER with the value from the browser URL bar.
### The code_verifier matches the code_challenge in the URL above.
POST http://localhost:9000/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=CODE_FROM_BROWSER
&redirect_uri=http://127.0.0.1:9000/authorized
&client_id=workforce-spa
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

Execute the Client Credentials request. Copy the `access_token` value from the response.

### Task 1.2 - Decode the token and answer the following questions

Navigate to [https://jwt.io](https://jwt.io) and paste the token into the **Encoded** panel. Create a file called `token-analysis.md` in the project root and write your answers there.

**Header questions:**

1. What is the value of `alg`? What does this tell the Resource Server about how to verify the signature?
2. What is the value of `kid`? Navigate to `http://localhost:9000/oauth2/jwks`. Find the key whose `kid` matches the token header. This is the specific public key the Resource Server will use to verify this token.
3. Why does the Authorization Server use RS256 rather than HS256? What problem does the asymmetric approach solve in a multi-service architecture?

**Payload questions:**

4. What is the `sub` claim in this service token? How would it differ in a token issued to alice after she logs in interactively?
5. What is the `iss` claim? Compare it to the `issuer` value in `http://localhost:9000/.well-known/oauth-authorization-server`. Where is this value configured in the Authorization Server?
6. What is the `aud` claim? Describe the attack that audience validation prevents.
7. Calculate the token lifetime by subtracting `iat` from `exp`. Both are Unix timestamps (seconds since 1 January 1970). Does this match the `accessTokenTimeToLive` configured for `workforce-service` in the Authorization Server?
8. What scopes are present? Compare them to the scopes requested in the token request.

**Signature questions:**

9. Copy the value of `n` from `http://localhost:9000/oauth2/jwks` and paste it into the public key field at jwt.io. Does the signature now verify? What does a successful verification confirm?
10. Restart the Authorization Server. Obtain a new token. Try to verify the new token using the public key value you copied in question 9. Does it verify? Explain why not, and what this implies for production key rotation.

### Task 1.3 - Reason about the stale permissions problem

Write a short paragraph (3–5 sentences) in `token-analysis.md` describing the following scenario:

> A user is granted the `HR_MANAGER` role at 09:00. They authenticate and receive a token with a 5-minute lifetime. At 04:00 into the token's life their role is revoked by an administrator. At 04:30 they make a request using the token issued at 09:00.

Will the Resource Server accept or reject this request? What claim protects against this problem and what is its limitation? What is the significance of the 5-minute lifetime, and what is the trade-off of making it shorter?

### Checkpoints

1. You observed in question 10 that restarting the Authorization Server invalidates all existing tokens because a new RSA key pair is generated. In a production system, how is the key pair managed to avoid this problem?
2. The service token does not contain `roles` or `department` claims. Looking at the `tokenCustomizer` in the Authorization Server, identify exactly which condition prevents these claims from being added to service tokens.
3. A colleague suggests caching JWTs by their `jti` value to detect replay attacks. What additional infrastructure does this require, and what availability risk does it introduce?

---

## Exercise 2 - Configuring Spring Security as a Resource Server

**Estimated time:** 40–50 minutes
**Topics covered:** `SecurityFilterChain`, `HttpSecurity` DSL, `oauth2ResourceServer` configuration, `SessionCreationPolicy.STATELESS`, CSRF, `authorizeHttpRequests`, `issuer-uri` and `jwks-uri`

### Context

In the previous exercise you analysed a JWT manually. Now you will configure Spring Security to validate JWTs automatically on every incoming request. This is the Resource Server role: your service trusts nothing except a cryptographically valid token from the Authorization Server on port 9000.

The lecture emphasised that adding `spring-boot-starter-security` locks everything down immediately. Your job is to replace that default with a configuration that matches the security contract of your API: JWT authentication, stateless sessions, and URL-level access rules tied to OAuth2 scopes.

### Task 2.1 - Create the Employee model and controller

Create `Employee.java` in the `model` package:

```java
package com.example.workforce.model;

public record Employee(
        String id,
        String name,
        String department,
        String role
) {}
```

Create `EmployeeController.java` in the `controller` package:

```java
package com.example.workforce.controller;

import com.example.workforce.model.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee("E001", "Alice Chen", "Engineering", "ENGINEER"),
            new Employee("E002", "Bob Okafor", "Engineering", "SENIOR_ENGINEER"),
            new Employee("E003", "Carol Diaz", "Human Resources", "HR_MANAGER")
    );

    @GetMapping
    public List<Employee> getAll() {
        return EMPLOYEES;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable String id) {
        return EMPLOYEES.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // TODO 1: Add a POST endpoint that accepts an Employee in the request body
    // and returns 201 Created with the employee in the response body.
    // The endpoint does not need to persist the employee -- this is a stub.
    // Annotate the parameter with @RequestBody.
    // Use ResponseEntity.status(HttpStatus.CREATED).body(employee) as the return value.
}
```

Create `HealthController.java` in the `controller` package:

```java
package com.example.workforce.controller;

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

### Task 2.2 - Configure the Resource Server

Create `SecurityConfig.java` in the `config` package:

```java
package com.example.workforce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // TODO 2: Configure authorization rules using authorizeHttpRequests().
            // Rules:
            //   - GET /health                   -- permitAll()
            //   - GET /api/v1/employees/**       -- hasAuthority("SCOPE_read:employees")
            //   - POST /api/v1/employees         -- hasAuthority("SCOPE_write:employees")
            //   - anyRequest()                   -- authenticated()
            // Rules are evaluated in order. Specific rules must come before general ones.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    // Add your rules here
                    .anyRequest().authenticated()
            )

            // TODO 3: Configure the OAuth2 Resource Server to validate JWT Bearer tokens.
            // Use: .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            // This activates JWT extraction, JWKS key fetching, signature verification,
            // and claims-to-authorities mapping. Spring Security calls the jwks-uri
            // configured in application.yml to fetch the verification keys.

            // TODO 4: Configure session management to STATELESS.
            // Use: .sessionManagement(session -> session
            //          .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // A REST API using Bearer tokens has no need for HTTP sessions.

            // TODO 5: Disable CSRF protection.
            // Use: .csrf(csrf -> csrf.disable())
            // CSRF attacks rely on browsers automatically sending session cookies.
            // Bearer tokens are not sent automatically, so CSRF does not apply here.
            ;

        return http.build();
    }
}
```

### Task 2.3 - Point the Resource Server at the Authorization Server

Add the following to `src/main/resources/application.yml` in the **workforce** project:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Spring Security fetches the public keys from this endpoint on startup
          # and caches them. The key whose "kid" matches the incoming token's
          # "kid" header claim is used to verify the signature.
          jwks-uri: http://localhost:9000/oauth2/jwks

          # The expected value of the "iss" claim in every incoming token.
          # Tokens where "iss" does not equal this value are rejected with 401.
          issuer-uri: http://localhost:9000
```

### Task 2.4 - Test the security configuration

Ensure both the Authorization Server (port 9000) and the Workforce API (port 8080) are running. Add the following to `auth-requests.http`:

```http
### Step 1: Obtain a service token (run this first).
POST http://localhost:9000/oauth2/token
Authorization: Basic d29ya2ZvcmNlLXNlcnZpY2U6d29ya2ZvcmNlLXNlcnZpY2Utc2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=read:employees

###

### Step 2: Health endpoint -- no token required, should return 200.
GET http://localhost:8080/health

###

### Step 3: Employees with no token -- should return 401.
GET http://localhost:8080/api/v1/employees

###

### Step 4: Employees with a valid token -- should return 200.
### Paste the access_token from Step 1.
GET http://localhost:8080/api/v1/employees
Authorization: Bearer <paste-access-token-here>

###

### Step 5: POST with read scope only -- should return 403.
### The service token has read:employees but NOT write:employees.
POST http://localhost:8080/api/v1/employees
Authorization: Bearer <paste-access-token-here>
Content-Type: application/json

{
  "id": "E004",
  "name": "Dana Park",
  "department": "Finance",
  "role": "ANALYST"
}
```

Execute each request in order and record the HTTP status code for each in `token-analysis.md`. The 403 on Step 5 confirms scope-based URL authorization is working correctly.

### Checkpoints

1. You added `@EnableWebSecurity` to `SecurityConfig`. What does Spring Boot's auto-configuration do when it detects this annotation? What default behaviour does your `SecurityFilterChain` bean replace?
2. The `/health` endpoint uses `permitAll()`. Explain why `anonymous()` would be a less correct choice even though both allow unauthenticated access.
3. You disabled CSRF. Describe precisely the condition that must be true for it to be safe to disable CSRF on a REST API endpoint.
4. Your authorization rules check for `SCOPE_read:employees`. The token's `scope` claim contains `read:employees` without a prefix. Where does the `SCOPE_` prefix come from, and which Spring Security component adds it?

### Extension task

Add a custom `AuthenticationEntryPoint` and `AccessDeniedHandler` that return JSON error responses instead of Spring Security's default plain-text responses:

```json
{"error": "authentication_required", "message": "A valid Bearer token is required"}
```

Wire them into the security configuration using `.exceptionHandling(ex -> ex.authenticationEntryPoint(...).accessDeniedHandler(...))`. Re-run Steps 3 and 5 and verify the response body is now JSON.

---

## Exercise 3 - Accessing JWT Claims in Application Code

**Estimated time:** 30–40 minutes
**Topics covered:** `SecurityContextHolder`, the `Authentication` object, `JwtAuthenticationToken`, extracting claims from the JWT, audit logging, ownership checks

### Context

Once Spring Security has validated a token and populated the `SecurityContext`, your application code can read the authenticated identity and its claims. This is how you implement ownership checks and audit logging.

The `Authentication` object is the central data structure that flows through both the filter chain and method security. In a JWT Resource Server application the concrete type is `JwtAuthenticationToken`, which exposes the original JWT and all its claims.

### Task 3.1 - Read the current identity in a controller

Add the following method to `EmployeeController`:

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.HashMap;
import java.util.Map;

// TODO 6: Complete this endpoint.
// @AuthenticationPrincipal instructs Spring Security to inject the validated Jwt
// from the SecurityContext directly as a method parameter.
// This is cleaner than calling SecurityContextHolder.getContext() manually.
@GetMapping("/me")
public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    // TODO 7: Return a Map containing:
    //   "subject"     -- jwt.getSubject()
    //   "issuer"      -- jwt.getIssuer().toString()
    //   "scopes"      -- jwt.getClaimAsString("scope")
    //   "tokenExpiry" -- jwt.getExpiresAt().toString()
    //   "roles"       -- jwt.getClaimAsStringList("roles"), or an empty list if null
    //   "department"  -- jwt.getClaimAsString("department"), or "not present" if null
    //
    // The service token will not have "roles" or "department" because the
    // token customizer in the Authorization Server only adds those for
    // user-context tokens. This difference is the main thing to observe here.
    return Map.of(); // Replace with your implementation
}
```

Add a test request to `auth-requests.http`:

```http
### Current identity -- observe which claims are present for a service token.
GET http://localhost:8080/api/v1/employees/me
Authorization: Bearer <paste-service-token-here>
```

Execute the request. Confirm that `roles` is absent or empty and `department` shows "not present". This directly demonstrates the difference between service-identity tokens and user-identity tokens.

### Task 3.2 - Access the SecurityContext in a service class

`@AuthenticationPrincipal` only works in controller methods. Inside a service class you must read from the `SecurityContextHolder` directly. Create `AuditService.java` in the `service` package:

```java
package com.example.workforce.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class AuditService {

    public void logEvent(String action, String resourceId) {
        // TODO 8: Retrieve the Authentication from the SecurityContextHolder.
        // Use SecurityContextHolder.getContext().getAuthentication()
        // Then use pattern matching to check if the principal is a Jwt:
        //   if (auth != null && auth.getPrincipal() instanceof Jwt jwt) { ... }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = "anonymous";

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            // TODO 9: Assign the subject from jwt.getSubject() to the subject variable.
        }

        System.out.printf("[AUDIT] %s | action=%s | resource=%s | caller=%s%n",
                Instant.now(), action, resourceId, subject);
    }
}
```

Inject `AuditService` into `EmployeeController` via constructor injection and call `auditService.logEvent("READ_EMPLOYEE", id)` inside `getById`. Restart the Workforce API, call `GET /api/v1/employees/E001` with a valid token, and verify the audit line appears in the console.

### Task 3.3 - Obtain a user token and compare the claims

Add the Authorization Code flow requests to `auth-requests.http` (already added in Task 1.1). Complete the flow for alice by opening the authorization URL in your browser, logging in, approving the scopes, and exchanging the code for tokens.

Paste alice's `access_token` into jwt.io. In `token-analysis.md`, record which claims are present in alice's user token that were absent in the service token. Then call `GET /api/v1/employees/me` with alice's token and observe the difference in the response.

### Checkpoints

1. `@AuthenticationPrincipal` injects the JWT directly into controller methods. When is `SecurityContextHolder.getContext().getAuthentication()` necessary instead?
2. The `AuditService` reads from the `SecurityContext` on the calling thread. What problem would occur if the service method were executed on a different thread (for example, using `@Async`)? What does Spring Security provide to address this?
3. Looking at the `tokenCustomizer` in the Authorization Server, explain exactly which condition prevents `roles` and `department` from appearing in service tokens.

---

## Exercise 4 - Method-Level Security with @PreAuthorize

**Estimated time:** 40–50 minutes
**Topics covered:** `@EnableMethodSecurity`, `@PreAuthorize`, SpEL expressions, role-based and scope-based method authorization, the AOP proxy model, testing method security

### Context

URL-level rules in `SecurityFilterChain` provide coarse-grained access control at the HTTP boundary. Method-level security provides fine-grained control at the service layer, and enforces authorization regardless of which entry point calls the method: HTTP controller, Kafka consumer, scheduled job, or internal code.

Spring Security implements method security using AOP proxies. When Spring creates your service bean it wraps it in a proxy. Every external call goes through the proxy, which evaluates the `@PreAuthorize` expression before delegating to the real method. If the expression is false, `AccessDeniedException` is thrown and the method body never executes.

### Task 4.1 - Enable method security

Add `@EnableMethodSecurity` to `SecurityConfig`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Activates @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter
public class SecurityConfig {
    // ... existing configuration unchanged
}
```

### Task 4.2 - Create a service with method-level security

Create `EmployeeService.java` in the `service` package:

```java
package com.example.workforce.service;

import com.example.workforce.model.Employee;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmployeeService {

    private final Map<String, Employee> store = new ConcurrentHashMap<>();

    public EmployeeService() {
        store.put("E001", new Employee("E001", "Alice Chen", "Engineering", "ENGINEER"));
        store.put("E002", new Employee("E002", "Bob Okafor", "Engineering", "SENIOR_ENGINEER"));
        store.put("E003", new Employee("E003", "Carol Diaz", "Human Resources", "HR_MANAGER"));
    }

    // TODO 10: Add @PreAuthorize("hasAuthority('SCOPE_read:employees')")
    // Any caller -- HTTP, Kafka, scheduled job -- must have this scope.
    public List<Employee> findAll() {
        return new ArrayList<>(store.values());
    }

    // TODO 11: Add @PreAuthorize("hasAuthority('SCOPE_read:employees')")
    public Optional<Employee> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    // TODO 12: Add @PreAuthorize that requires BOTH:
    //   hasAuthority('SCOPE_write:employees') AND hasRole('HR_MANAGER')
    // Combine them with &&.
    // A service account with write scope but no HR_MANAGER role will be denied.
    public Employee save(Employee employee) {
        store.put(employee.id(), employee);
        return employee;
    }

    // TODO 13: Add @PreAuthorize("hasRole('HR_MANAGER')")
    // Also add @PostAuthorize to check the return value after the method runs:
    //   "returnObject.isPresent() && (returnObject.get().department() == 'Human Resources'
    //    || hasRole('ADMIN'))"
    public Optional<Employee> findByIdForHr(String id) {
        return Optional.ofNullable(store.get(id));
    }

    // TODO 14: After completing the other TODOs, add @PreAuthorize here
    // that references the method argument using the # prefix:
    //   "hasRole('ADMIN') || #department == authentication.name"
    public List<Employee> findByDepartment(String department) {
        return store.values().stream()
                .filter(e -> e.department().equals(department))
                .toList();
    }
}
```

### Task 4.3 - Test method security without a real token

Spring Security's test support provides `@WithMockUser` to simulate an authenticated user in unit tests. This lets you test authorization logic without the Authorization Server running at all. Add the following test class to `src/test/java/com/example/workforce/service`:

```java
package com.example.workforce.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EmployeeServiceSecurityTest {

    @Autowired
    private EmployeeService employeeService;

    // @WithMockUser simulates an authenticated user in the SecurityContext.
    // "SCOPE_read:employees" is the authority Spring Security derives from the
    // "read:employees" scope in a JWT -- the SCOPE_ prefix is added automatically.
    @Test
    @WithMockUser(authorities = {"SCOPE_read:employees"})
    void findAll_withReadScope_returnsEmployees() {
        var result = employeeService.findAll();
        assertThat(result).isNotEmpty();
    }

    @Test
    @WithMockUser
    void findAll_withoutReadScope_throwsAccessDeniedException() {
        // TODO 15: Assert that calling employeeService.findAll() throws AccessDeniedException.
        assertThatThrownBy(() -> employeeService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    // TODO 16: Write a test verifying save() succeeds with both
    // SCOPE_write:employees AND ROLE_HR_MANAGER.
    // @WithMockUser(authorities = {"SCOPE_write:employees"}, roles = {"HR_MANAGER"})
    // The roles attribute automatically adds "ROLE_HR_MANAGER" as an authority.
    @Test
    @WithMockUser(authorities = {"SCOPE_write:employees", "ROLE_HR_MANAGER"})
    void save_withWriteScopeAndHrManagerRole_succeeds() {
        // TODO: Create an Employee and call save(). Assert the return value is not null.
    }

    // TODO 17: Write a test verifying save() throws AccessDeniedException when the
    // user has write scope but NOT the HR_MANAGER role.
    @Test
    @WithMockUser(authorities = {"SCOPE_write:employees"})
    void save_withWriteScopeButNoHrManagerRole_throwsAccessDeniedException() {
        // TODO: Assert that save() throws AccessDeniedException.
    }
}
```

Run the tests. All should pass. Notice that `@WithMockUser` bypasses the Authorization Server entirely — this is the correct approach for unit testing authorization logic.


Note: If your tests fail with the error 

**No qualifying bean of type 'org.springframework.web.reactive.function.client.WebClient' available,** it means DownstreamEmployeeService has already been created as part of Exercise 5 setup. The @SpringBootTest annotation loads the full application context, which tries to wire DownstreamEmployeeService, and that fails because the WebClient bean requires OAuth2 client configuration that is not relevant to Exercise 4.

Fix this by adding @MockitoBean to the test class so Spring uses a mock instead of the real implementation:

```java 
@SpringBootTest
class EmployeeServiceSecurityTest {

    @Autowired
    private EmployeeService employeeService;

    @MockitoBean
    private DownstreamEmployeeService downstreamEmployeeService;

    // ... all tests unchanged
}
```
@MockitoBean is available in Spring Boot 3.4 and later. If your version uses the older annotation, use @MockBean instead. Both have the same effect: Spring creates a Mockito mock for DownstreamEmployeeService and registers it as a bean, satisfying the dependency without requiring the real WebClient infrastructure.


### Task 4.4 - Observe the self-invocation limitation

Add the following method to `EmployeeService`:

```java
/**
 * Calls findAll() via 'this' -- which bypasses the AOP proxy.
 * The @PreAuthorize on findAll() is NOT enforced for this call.
 * This is the most common AOP proxy gotcha in Spring applications.
 */
public List<Employee> findAllInternal() {
    return this.findAll(); // proxy not involved -- @PreAuthorize does not run
}
```

Add a test:

```java
// TODO 18: Write a test with NO @WithMockUser (unauthenticated context).
// Call employeeService.findAllInternal() and assert that no exception is thrown
// and the result is not empty.
// Despite findAll() having @PreAuthorize, the annotation is not enforced
// because the call bypasses the proxy via self-invocation.
// Add a comment explaining: why this happens, what the correct solutions are,
// and what the production security risk is.
@Test
void findAllInternal_bypassesMethodSecurity() {
    // TODO: Assert the result is not empty and no exception is thrown.
}
```

### Checkpoints

1. The `@PreAuthorize` expression for `save()` combines a scope check and a role check with `&&`. Explain why this combination is more secure than checking either condition alone. Give a concrete example of the gap that each check independently closes.
2. `@PostAuthorize` on `findByIdForHr()` runs after the method executes, meaning data is always loaded regardless of the authorization outcome. In what scenario would you accept this trade-off rather than using `@PreAuthorize`?
3. The tests use `@WithMockUser`. Why can `@WithMockUser` be used for method security tests even though the production application uses JWT tokens? What does `@WithMockUser` put in the `SecurityContext` and why is that sufficient for evaluating `@PreAuthorize` expressions?
4. What are the two correct solutions to the self-invocation problem, and what are the trade-offs of each?

### Extension task

Add a `@PreFilter` annotation to a new `saveAll(List<Employee> employees)` method in `EmployeeService` that filters the input list to include only employees whose department equals `"Human Resources"`. Write a test that passes a mixed-department list and verifies only the matching employees are saved.

---

## Exercise 5 - Understanding the Client Credentials Flow

**Estimated time:** 30–45 minutes
**Topics covered:** Client Credentials grant type, service-to-service authentication, the distinction between user-delegated tokens and service identity tokens, `spring-boot-starter-oauth2-client`, `WebClient` with OAuth2 token attachment

### Context

The previous exercises configured your Workforce API as a Resource Server: it receives and validates tokens. Services also act as clients: they obtain tokens and present them to downstream services.

The Client Credentials flow is for service-to-service communication where no user is present. The calling service authenticates using its own identity (client ID and secret) and receives a token representing the service itself. In this exercise the Workforce API calls itself as a downstream service — a deliberate simplification that lets you observe the full flow without needing a second service.

### Setup

Add the following dependencies to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Reload Maven after saving.

### Task 5.1 - Configure the OAuth2 client registration

Add client configuration to `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwks-uri: http://localhost:9000/oauth2/jwks
          issuer-uri: http://localhost:9000
      client:
        registration:
          downstream-api:
            client-id: workforce-service
            # Matches the {noop}workforce-service-secret configured in the
            # Authorization Server. In production this comes from an
            # environment variable or secrets manager.
            client-secret: workforce-service-secret
            authorization-grant-type: client_credentials
            scope: read:employees
        provider:
          downstream-api:
            token-uri: http://localhost:9000/oauth2/token
```

### Task 5.2 - Configure a WebClient with automatic token attachment

Create `DownstreamClientConfig.java` in the `config` package:

```java
package com.example.workforce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DownstreamClientConfig {

    @Value("${downstream.api.base-url:http://localhost:8080}")
    private String downstreamBaseUrl;

    /**
     * Creates a WebClient that automatically acquires and attaches a Bearer token
     * using Client Credentials before every outbound request.
     *
     * The OAuth2AuthorizedClientManager is auto-configured by Spring Boot
     * from the spring.security.oauth2.client.* properties above.
     * It handles token acquisition, caching, and refresh automatically.
     */
    @Bean
    public WebClient downstreamApiClient(OAuth2AuthorizedClientManager authorizedClientManager) {

        // TODO 19: Create a ServletOAuth2AuthorizedClientExchangeFilterFunction,
        // passing the authorizedClientManager to its constructor.
        // Call setDefaultClientRegistrationId("downstream-api") on the filter.
        // Build and return a WebClient using WebClient.builder()
        // with the baseUrl set to downstreamBaseUrl
        // and the filter applied with .apply(oauth2Filter.oauth2Configuration()).

        var oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("downstream-api");

        return WebClient.builder()
                .baseUrl(downstreamBaseUrl)
                .apply(oauth2Filter.oauth2Configuration())
                .build();
    }
}
```

### Task 5.3 - Call a downstream service using the service token

Create `DownstreamEmployeeService.java` in the `service` package:

```java
package com.example.workforce.service;

import com.example.workforce.model.Employee;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Service
public class DownstreamEmployeeService {

    private final WebClient downstreamApiClient;

    public DownstreamEmployeeService(WebClient downstreamApiClient) {
        this.downstreamApiClient = downstreamApiClient;
    }

    /**
     * Calls the employee API using a Client Credentials token.
     * Spring Security acquires the token from the Authorization Server automatically.
     * The token represents this service's identity -- no user token is forwarded.
     */
    public List<Employee> fetchAllFromDownstream() {
        // TODO 20: Use the WebClient to call GET /api/v1/employees.
        // Chain: .get().uri("/api/v1/employees").retrieve()
        //        .bodyToFlux(Employee.class).collectList().block()
        // The OAuth2 filter calls the Authorization Server's token endpoint,
        // caches the token, and adds it to the Authorization header automatically.
        return List.of(); // Replace with your implementation
    }
}
```

Add an endpoint to `EmployeeController`. Inject `DownstreamEmployeeService` via the constructor alongside `AuditService`:

```java
// TODO 21: Add this endpoint to EmployeeController.
// It is protected and requires an authenticated caller.
// The inbound request uses the caller's token.
// The outbound call to the downstream service uses the service's own token.
@GetMapping("/downstream")
public List<Employee> getFromDownstream() {
    // TODO: call downstreamEmployeeService.fetchAllFromDownstream() and return the result
    return List.of();
}
```

Add a test request to `auth-requests.http`:

```http
### The downstream endpoint.
### Inbound: the caller's token authenticates this request.
### Outbound: the Workforce API obtains its own service token automatically.
### Watch the Authorization Server console when this executes -- you will see a
### POST /oauth2/token request arrive from workforce-service.
GET http://localhost:8080/api/v1/employees/downstream
Authorization: Bearer <paste-any-valid-token-here>
```

Execute the request and watch the Authorization Server console. You should see a `POST /oauth2/token` request arrive from `workforce-service` before the response comes back.

### Task 5.4 - Compare user tokens and service tokens

Decode both alice's user token (from Exercise 3) and the service token (from Exercise 1) at jwt.io. Complete the comparison table in `token-analysis.md`:

| Claim | User Token (alice) | Service Token (workforce-service) |
|---|---|---|
| `sub` | | |
| `iss` | | |
| `aud` | | |
| `scope` | | |
| `roles` (if present) | | |
| `department` (if present) | | |
| Lifetime in seconds (`exp - iat`) | | |

Answer these questions in `token-analysis.md`:

1. In the user token `sub` identifies alice. In the service token, what does `sub` identify, and where does this value come from in the Authorization Server configuration?
2. The service token has no `roles` or `department` claims. Looking at the `tokenCustomizer` in the Authorization Server, identify exactly which condition prevents those claims from being added.
3. The service token has a 3600-second lifetime. The user token has a 300-second lifetime. Explain the security reasoning behind the different lifetimes.

### Checkpoints

1. The `OAuth2AuthorizedClientManager` caches the service token after its first acquisition. What triggers a new token request to the Authorization Server?
2. If the Authorization Server is unavailable when the Workforce API starts, the `WebClient` configuration still succeeds. The failure only occurs on the first outbound call. What resilience pattern from Module 4 would you apply to `fetchAllFromDownstream()` to handle this gracefully?
3. The client secret is in `application.yml` for this exercise. In a production deployment what mechanism would you use to supply it at runtime without committing it to source control?

---

## Summary and Reflection

After completing all five exercises you have worked through the full OAuth2 and Spring Security lifecycle: inspecting tokens, configuring a Resource Server, reading claims in application code, enforcing method-level authorization, and acting as an OAuth2 client for downstream calls.

| Exercise | Concept | Key Insight |
|---|---|---|
| 1 – JWT Structure | Header, payload, signature, registered claims | A valid signature proves authenticity and integrity, not current permissions or legitimate possession |
| 2 – Resource Server | `SecurityFilterChain`, JWT validation, URL rules | Spring Security wraps the entire application. Your job is to declare rules, not enforce them inline. |
| 3 – Claims in Code | `SecurityContextHolder`, `@AuthenticationPrincipal`, ownership checks | The `SecurityContext` is populated once by the filter chain and is available throughout the request |
| 4 – Method Security | `@PreAuthorize`, AOP proxies, self-invocation limitation | Method security enforces authorization regardless of which entry point calls the method |
| 5 – Client Credentials | Service-to-service authentication, `WebClient` with OAuth2 | The Client Credentials flow authenticates the service identity. No user is involved and no user token is forwarded. |

### Final Reflection Questions

Take 10 minutes to answer these before your next session:

1. The Authorization Server generates a new RSA key pair on every startup, immediately invalidating all previously issued tokens. You observed this in Exercise 1. Describe the production solution: where should the key pair be stored, and what does a deliberate key rotation process look like from the Resource Server's perspective?

2. Exercise 4 demonstrated the self-invocation limitation of AOP proxies. `@Transactional` uses the same mechanism with the same limitation. How would you structure a service class that needs both transactional behaviour and method-level security on the same methods?

3. In Exercise 5 the Workforce API used its own service token to call the downstream employee endpoint, even though the incoming request carried alice's user token. Describe a scenario where you would instead forward alice's token to the downstream service rather than using a service token. What are the security trade-offs of each approach?

---

*End of Module 3 Exercises*
