# Module 3: OAuth2, OIDC, and Spring Security
## Solutions

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 3 - OAuth2 and OIDC Foundations and Spring Security

---

## How to Use This File

This file contains the completed solution for every TODO in the Module 3 exercises. It also includes suggested answers to the checkpoint questions.

Use this file to check your work after attempting each exercise, not before. The understanding you develop by working through the problems yourself is the point of the exercise. Comparing your solution to this one after the fact is a normal and productive part of learning.

Where your solution differs from the one shown here, the question to ask is: does mine produce the same result, or does the difference matter? Often there is more than one correct approach.

---

## Exercise 1 Solutions

### Checkpoint Answers

**Question 1:** In production the key pair is generated once and stored in a secure keystore such as a hardware security module, AWS KMS, or HashiCorp Vault. The key is loaded at startup rather than generated. Key rotation is performed deliberately: the new public key is published to the JWKS endpoint before any tokens are signed with the new private key, giving Resource Servers time to fetch it. During the overlap period both the old and new keys are present in the JWKS response, identified by different `kid` values.

**Question 2:** The `tokenCustomizer` bean checks whether the principal is an instance of `UserDetails`. For service tokens obtained via the Client Credentials flow, the principal is the client registration itself, not a `UserDetails` object. The `instanceof` check evaluates to false, so the `roles` and `department` claims are never added.

**Question 3:** Caching JWTs by `jti` requires a shared, distributed store (Redis, for example) that every Resource Server instance can read and write. Every token validation becomes a network call to that store. If the store is unavailable, token validation fails. This introduces a synchronous dependency on external infrastructure that undermines the stateless, scalable nature of JWT validation.

---

## Exercise 2 Solutions

### TODO 1: POST endpoint in EmployeeController

```java
@PostMapping
public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
    return ResponseEntity.status(HttpStatus.CREATED).body(employee);
}
```

### TODO 2 through TODO 5: Completed SecurityConfig

```java
package com.example.workforce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/employees/**").hasAuthority("SCOPE_read:employees")
                    .requestMatchers(HttpMethod.POST, "/api/v1/employees").hasAuthority("SCOPE_write:employees")
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
```

### Checkpoint Answers

**Question 1:** When `@EnableWebSecurity` is detected, Spring Boot's security autoconfiguration backs off. Without this annotation, Spring Boot provides a default `SecurityFilterChain` that uses form login and HTTP Basic. Your `@Bean` method replaces that default entirely.

**Question 2:** `permitAll()` allows any request regardless of authentication state. `anonymous()` only matches requests where the user is not authenticated. A request that carries a valid token but is rejected by a later rule would not match `anonymous()`, leading to unexpected behavior. `permitAll()` is the correct and unambiguous choice for endpoints that must be open to everyone.

**Question 3:** CSRF protection can safely be disabled when the application uses stateless Bearer token authentication rather than session cookies. Browsers do not automatically attach Authorization headers to cross-site requests the way they do with cookies, so the CSRF attack vector does not apply.

**Question 4:** Spring Security's `JwtGrantedAuthoritiesConverter` reads the `scope` claim from the JWT and creates a `GrantedAuthority` for each scope value, prefixing it with `SCOPE_`. So `read:employees` in the token becomes `SCOPE_read:employees` as a granted authority in the `SecurityContext`.

---

## Exercise 3 Solutions

### TODO 6 and TODO 7: Completed getCurrentUser method

```java
@GetMapping("/me")
public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {

    List<String> roles = jwt.getClaimAsStringList("roles");
    String department = jwt.getClaimAsString("department");

    Map<String, Object> result = new HashMap<>();
    result.put("subject",     jwt.getSubject());
    result.put("issuer",      jwt.getIssuer().toString());
    result.put("scopes",      jwt.getClaimAsString("scope"));
    result.put("tokenExpiry", jwt.getExpiresAt().toString());
    result.put("roles",       roles != null ? roles : List.of());
    result.put("department",  department != null ? department : "not present");

    return result;
}
```

### TODO 8 and TODO 9: Completed AuditService

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = "anonymous";

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            subject = jwt.getSubject();
        }

        System.out.printf("[AUDIT] %s | action=%s | resource=%s | caller=%s%n",
                Instant.now(), action, resourceId, subject);
    }
}
```

### Completed EmployeeController (after Exercise 3)

```java
package com.example.workforce.controller;

import com.example.workforce.model.Employee;
import com.example.workforce.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee("E001", "Alice Chen", "Engineering", "ENGINEER"),
            new Employee("E002", "Bob Okafor", "Engineering", "SENIOR_ENGINEER"),
            new Employee("E003", "Carol Diaz", "Human Resources", "HR_MANAGER")
    );

    private final AuditService auditService;

    public EmployeeController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<Employee> getAll() {
        return EMPLOYEES;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable String id) {
        auditService.logEvent("READ_EMPLOYEE", id);
        return EMPLOYEES.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String department = jwt.getClaimAsString("department");

        Map<String, Object> result = new HashMap<>();
        result.put("subject",     jwt.getSubject());
        result.put("issuer",      jwt.getIssuer().toString());
        result.put("scopes",      jwt.getClaimAsString("scope"));
        result.put("tokenExpiry", jwt.getExpiresAt().toString());
        result.put("roles",       roles != null ? roles : List.of());
        result.put("department",  department != null ? department : "not present");

        return result;
    }
}
```

### Checkpoint Answers

**Question 1:** `@AuthenticationPrincipal` is the preferred approach in controller methods because it is concise, type-safe, and expresses intent clearly. `SecurityContextHolder.getContext().getAuthentication()` is necessary in service classes, scheduled tasks, or any code that is not a Spring MVC controller method, because `@AuthenticationPrincipal` is a Spring MVC feature and only works in controller method parameters.

**Question 2:** `SecurityContextHolder` stores the authentication in a `ThreadLocal`. If the method is executed on a different thread, that thread has a different (empty) `ThreadLocal` and the authentication will be null. Spring Security provides `DelegatingSecurityContextExecutor` and `SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)` to propagate the context to child threads.

**Question 3:** The `tokenCustomizer` checks `auth.getPrincipal() instanceof UserDetails`. For service tokens, the principal is the OAuth2 client identity, not a `UserDetails` object. The `instanceof` check fails, the `if` block does not execute, and no `roles` or `department` claims are added.

---

## Exercise 4 Solutions

### TODO 10 through TODO 14: Completed EmployeeService

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

    @PreAuthorize("hasAuthority('SCOPE_read:employees')")
    public List<Employee> findAll() {
        return new ArrayList<>(store.values());
    }

    @PreAuthorize("hasAuthority('SCOPE_read:employees')")
    public Optional<Employee> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @PreAuthorize("hasAuthority('SCOPE_write:employees') && hasRole('HR_MANAGER')")
    public Employee save(Employee employee) {
        store.put(employee.id(), employee);
        return employee;
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostAuthorize("returnObject.isPresent() && (returnObject.get().department() == 'Human Resources' || hasRole('ADMIN'))")
    public Optional<Employee> findByIdForHr(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @PreAuthorize("hasRole('ADMIN') || #department == authentication.name")
    public List<Employee> findByDepartment(String department) {
        return store.values().stream()
                .filter(e -> e.department().equals(department))
                .toList();
    }

    public List<Employee> findAllInternal() {
        return this.findAll();
    }
}
```

### TODO 15 through TODO 18: Completed EmployeeServiceSecurityTest

```java
package com.example.workforce.service;

import com.example.workforce.model.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EmployeeServiceSecurityTest {

    @Autowired
    private EmployeeService employeeService;

    @MockitoBean
    private DownstreamEmployeeService downstreamEmployeeService;

    @Test
    @WithMockUser(authorities = {"SCOPE_read:employees"})
    void findAll_withReadScope_returnsEmployees() {
        var result = employeeService.findAll();
        assertThat(result).isNotEmpty();
    }

    @Test
    @WithMockUser
    void findAll_withoutReadScope_throwsAccessDeniedException() {
        assertThatThrownBy(() -> employeeService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_write:employees", "ROLE_HR_MANAGER"})
    void save_withWriteScopeAndHrManagerRole_succeeds() {
        Employee employee = new Employee("E004", "Dana Park", "Human Resources", "HR_COORDINATOR");
        Employee saved = employeeService.save(employee);
        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo("E004");
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_write:employees"})
    void save_withWriteScopeButNoHrManagerRole_throwsAccessDeniedException() {
        Employee employee = new Employee("E005", "Eve Russo", "Engineering", "ENGINEER");
        assertThatThrownBy(() -> employeeService.save(employee))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findAllInternal_bypassesMethodSecurity() {
        // No @WithMockUser -- the SecurityContext is empty.
        // findAllInternal() calls this.findAll() which bypasses the AOP proxy.
        // @PreAuthorize on findAll() is never evaluated.
        // The result comes back without any AccessDeniedException being thrown.
        //
        // Why this happens: Spring wraps the EmployeeService bean in an AOP proxy.
        // External callers go through the proxy, which enforces @PreAuthorize.
        // Internal calls via 'this' skip the proxy and reach the real method directly.
        //
        // Production risk: a developer believes a method is protected by @PreAuthorize
        // but an internal caller bypasses it silently with no error or warning.
        //
        // Correct solutions:
        //   1. Inject the bean into itself and call self.findAll() instead of this.findAll().
        //      The self-injected reference goes through the proxy.
        //   2. Move findAllInternal() to a separate Spring bean.
        //      Any call from one bean to another goes through the proxy.
        var result = employeeService.findAllInternal();
        assertThat(result).isNotEmpty();
    }
}
```

### Checkpoint Answers

**Question 1:** Combining both conditions closes two independent gaps. The scope check ensures the client application has been granted write permission. The role check ensures the authenticated user has the organizational authority to perform the operation. A service account might have `write:employees` scope but must not be able to create records without a human HR Manager identity behind it. A user might have the HR_MANAGER role but must not be able to write if the client application they are using has not been granted write scope.

**Question 2:** `@PostAuthorize` is appropriate when the authorization decision depends on the content of the returned object. If you need to check whether the specific record belongs to the caller's department, you cannot know that until after the query runs. The trade-off is that the database query always executes even if the result will be rejected. This is acceptable when the query is not expensive and the data is not sensitive to load.

**Question 3:** `@WithMockUser` creates a `UsernamePasswordAuthenticationToken` in the `SecurityContext`. The `@PreAuthorize` SpEL expressions such as `hasAuthority()` and `hasRole()` evaluate against the `GrantedAuthority` list on that token. They do not care whether the token came from a JWT or from `@WithMockUser`. The authority names are what matter, not the token type.

**Question 4:** The two correct solutions are self-injection (inject the bean into itself with `@Autowired` so internal calls go through the proxy) and bean separation (move the internal method to a different Spring bean so the call crosses a bean boundary and goes through the proxy). Self-injection is simpler but unusual and can confuse readers. Bean separation is cleaner architecturally and makes the dependency explicit, but requires restructuring the code.

---

## Exercise 5 Solutions

### Completed DownstreamClientConfig

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

    @Bean
    public WebClient downstreamApiClient(OAuth2AuthorizedClientManager authorizedClientManager) {

        var oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("downstream-api");

        return WebClient.builder()
                .baseUrl(downstreamBaseUrl)
                .apply(oauth2Filter.oauth2Configuration())
                .build();
    }
}
```

### TODO 20: Completed DownstreamEmployeeService

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

    public List<Employee> fetchAllFromDownstream() {
        return downstreamApiClient
                .get()
                .uri("/api/v1/employees")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .block();
    }
}
```

### TODO 21: Completed EmployeeController (final version)

```java
package com.example.workforce.controller;

import com.example.workforce.model.Employee;
import com.example.workforce.service.AuditService;
import com.example.workforce.service.DownstreamEmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee("E001", "Alice Chen", "Engineering", "ENGINEER"),
            new Employee("E002", "Bob Okafor", "Engineering", "SENIOR_ENGINEER"),
            new Employee("E003", "Carol Diaz", "Human Resources", "HR_MANAGER")
    );

    private final AuditService auditService;
    private final DownstreamEmployeeService downstreamEmployeeService;

    public EmployeeController(AuditService auditService,
                              DownstreamEmployeeService downstreamEmployeeService) {
        this.auditService = auditService;
        this.downstreamEmployeeService = downstreamEmployeeService;
    }

    @GetMapping
    public List<Employee> getAll() {
        return EMPLOYEES;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable String id) {
        auditService.logEvent("READ_EMPLOYEE", id);
        return EMPLOYEES.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String department = jwt.getClaimAsString("department");

        Map<String, Object> result = new HashMap<>();
        result.put("subject",     jwt.getSubject());
        result.put("issuer",      jwt.getIssuer().toString());
        result.put("scopes",      jwt.getClaimAsString("scope"));
        result.put("tokenExpiry", jwt.getExpiresAt().toString());
        result.put("roles",       roles != null ? roles : List.of());
        result.put("department",  department != null ? department : "not present");

        return result;
    }

    @GetMapping("/downstream")
    public List<Employee> getFromDownstream() {
        return downstreamEmployeeService.fetchAllFromDownstream();
    }
}
```

### Checkpoint Answers

**Question 1:** The `OAuth2AuthorizedClientManager` caches the token in an `OAuth2AuthorizedClientRepository`, which in a servlet application defaults to the HTTP session. A new token request is triggered when the cached token is absent (first request) or when the token has expired. Spring Security checks the `expires_in` value returned with the token and requests a new one proactively before it expires.

**Question 2:** The resilience pattern to apply is a circuit breaker, combined with a timeout. If the Authorization Server is unavailable, the token request will hang or fail. A timeout prevents the calling thread from blocking indefinitely. A circuit breaker opens after repeated failures and returns a fallback response immediately rather than attempting the call, giving the Authorization Server time to recover.

**Question 3:** In production the client secret is supplied via an environment variable referenced in `application.yml` using the `${VARIABLE_NAME}` syntax. The actual secret value is stored in a secrets manager such as HashiCorp Vault, AWS Secrets Manager, or a Kubernetes secret, and injected into the environment at deployment time. The `application.yml` file committed to source control contains only the reference, not the value.

---

## Final Reflection Question Answers

**Question 1:** In production the RSA key pair is generated once and stored in a secure keystore such as a hardware security module or a cloud key management service. Key rotation is a coordinated process. The new public key is added to the JWKS endpoint while the old one remains. Resource Servers cache JWKS keys and refresh them periodically. Once all Resource Servers have fetched the new key, the Authorization Server begins signing new tokens with the new private key. The old private key is retired but the old public key remains in the JWKS until all tokens signed with it have expired.

**Question 2:** `@Transactional` and `@PreAuthorize` both use AOP proxies and both suffer from the self-invocation limitation. The correct structural approach is to separate the two concerns into two beans. One bean handles the transaction boundary and calls the other bean, which handles the authorization boundary. Each call crosses a bean boundary and goes through the respective proxy. Alternatively, if a method needs both, annotate it with both and ensure all callers are external to the bean.

**Question 3:** Token forwarding is appropriate when the downstream service needs to know which user is making the request, for example to enforce row-level access control based on user identity or to write audit records that trace back to the human user. The trade-off is that the downstream service must trust the upstream service not to forward tokens it does not own, and the downstream service must validate the token itself. Using a service token is appropriate when the downstream service only needs to know which calling service is making the request and the user identity is irrelevant to the operation. Service tokens are simpler to manage and do not couple the downstream service to the upstream user authentication context.

---

*End of Module 3 Solutions*
