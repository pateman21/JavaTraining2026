# Module 4: Secure External API Consumption
## Solutions

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 4 - Secure External API Consumption

---

## How to Use This File

This file contains the completed solution for every TODO in the exercises. Use it to:

- Confirm your implementation is correct after completing a task
- Get unstuck when a TODO is unclear
- Compare your approach when you have a working solution but want to see an alternative

Work through the exercises yourself before consulting this file. Reading a solution without attempting the problem first significantly reduces the learning value of the exercise.

The solutions are organized in the same order as the exercises. Each section shows the complete file for that task, not just the changed lines.

---

## Shared Setup Files

These files are provided in full in the exercise sheet and do not contain TODOs. They are included here for reference.

### application.yml

```yaml
spring:
  application:
    name: api-client

logging:
  level:
    com.example.api_client: DEBUG

resilience4j:

  retry:
    instances:
      upstream-api:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        exponential-max-wait-duration: 5s
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
          - java.util.concurrent.TimeoutException
          - java.io.IOException

  circuit-breaker:
    instances:
      upstream-api:
        sliding-window-size: 10
        failure-rate-threshold: 50
        permitted-number-of-calls-in-half-open-state: 2
        wait-duration-in-open-state: 30s
        record-exceptions:
          - java.lang.Exception

upstream:
  api:
    token: "exercise-static-bearer-token"
    base-url: "http://localhost:8081"
```

### Employee.java

```java
package com.example.api_client.model;

public class Employee {

    private Long id;
    private String name;
    private String department;

    public Employee() {}

    public Employee(Long id, String name, String department) {
        this.id = id;
        this.name = name;
        this.department = department;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    @Override
    public String toString() {
        return "Employee{id=" + id + ", name='" + name + "', department='" + department + "'}";
    }
}
```

### StubServerConfig.java

```java
package com.example.api_client.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Configuration
public class StubServerConfig {

    private static final Logger log = LoggerFactory.getLogger(StubServerConfig.class);
    private static final int STUB_PORT = 8081;

    private WireMockServer wireMockServer;

    @EventListener(ContextRefreshedEvent.class)
    public void start() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            return;
        }
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig().port(STUB_PORT)
        );
        wireMockServer.start();
        WireMock.configureFor("localhost", STUB_PORT);
        registerStubs();
        log.info("Stub server started on port {}. Admin UI: http://localhost:{}/__admin/mappings",
                STUB_PORT, STUB_PORT);
    }

    @PreDestroy
    public void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            log.info("Stub server stopped.");
        }
    }

    public WireMockServer getServer() {
        return wireMockServer;
    }

    private void registerStubs() {

        stubFor(get(urlEqualTo("/api/employees"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "{\"id\":1,\"name\":\"Alice\",\"department\":\"Engineering\"}," +
                                "{\"id\":2,\"name\":\"Bob\",\"department\":\"Finance\"}" +
                                "]")));

        stubFor(get(urlEqualTo("/api/employees/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Alice\",\"department\":\"Engineering\"}")));

        stubFor(get(urlEqualTo("/api/employees/99"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not found")));

        stubFor(post(urlEqualTo("/api/employees"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Location", "http://localhost:8081/api/employees/3")
                        .withBody("{\"id\":3,\"name\":\"Charlie\",\"department\":\"HR\"}")));

        stubFor(get(urlEqualTo("/api/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000)
                        .withBody("finally!")));

        stubFor(get(urlEqualTo("/api/flaky"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        // The catch-all 401 is registered first. WireMock matches in reverse registration
        // order, so the more specific Bearer stub registered below takes priority.
        stubFor(get(urlEqualTo("/api/auth-required"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        stubFor(get(urlEqualTo("/api/auth-required"))
                .withHeader("Authorization", containing("Bearer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Authenticated successfully\"}")));
    }
}
```

---

## Exercise 1 Solutions

### TODO 1 and 7 - RestTemplateConfig.java

TODOs completed: configure root URI and timeouts (TODO 1), register the logging interceptor (TODO 7).

```java
package com.example.api_client.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri("http://localhost:8081")
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .additionalInterceptors(new LoggingInterceptor())
                .build();
    }
}
```

**Key points:**

- `rootUri` sets the base URL so every call site only needs to supply the path segment
- `connectTimeout` and `readTimeout` are the correct method names in Spring Boot 3.4 - the older `setConnectTimeout` and `setReadTimeout` forms are deprecated
- `additionalInterceptors` registers the logging interceptor globally so it runs on every request

---

### TODO 2, 3, 4, 5 - EmployeeRestTemplateService.java

TODOs completed: fetchAll with getForObject (TODO 2), fetchById with getForEntity (TODO 3), fetchAllWithExchange with ParameterizedTypeReference (TODO 4), createEmployee with postForEntity (TODO 5).

```java
package com.example.api_client.service;

import com.example.api_client.model.Employee;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class EmployeeRestTemplateService {

    private final RestTemplate restTemplate;

    public EmployeeRestTemplateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<Employee> fetchAll() {
        Employee[] employees = restTemplate.getForObject("/api/employees", Employee[].class);
        return employees != null ? Arrays.asList(employees) : List.of();
    }

    public Employee fetchById(Long id) {
        ResponseEntity<Employee> response = restTemplate.getForEntity(
                "/api/employees/{id}", Employee.class, id);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    public List<Employee> fetchAllWithExchange() {
        var typeRef = new ParameterizedTypeReference<List<Employee>>() {};
        ResponseEntity<List<Employee>> response = restTemplate.exchange(
                "/api/employees", HttpMethod.GET, HttpEntity.EMPTY, typeRef);
        return response.getBody();
    }

    public String createEmployee(Employee employee) {
        ResponseEntity<Employee> response = restTemplate.postForEntity(
                "/api/employees", employee, Employee.class);
        return response.getHeaders().getLocation().toString();
    }
}
```

**Key points:**

- `getForObject` requires an array type (`Employee[].class`) because Java cannot deserialize into a generic `List<Employee>` directly without type information at runtime
- `getForEntity` gives access to the status code so you can distinguish a 200 from a 404 before touching the body
- `ParameterizedTypeReference` solves the type erasure problem for `exchange` - at runtime `List<Employee>.class` does not exist, but the anonymous subclass captures the full generic type
- `postForEntity` returns the full response including the `Location` header that points to the newly created resource

---

### TODO 6 - LoggingInterceptor.java

TODO completed: log request method and URI before the call, log response status after.

```java
package com.example.api_client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        log.info(">>> {} {}", request.getMethod(), request.getURI());

        ClientHttpResponse response = execution.execute(request, body);

        log.info("<<< {}", response.getStatusCode());

        return response;
    }
}
```

**Key points:**

- The interceptor must call `execution.execute(request, body)` and return the result - skipping this would prevent the request from being sent
- Logging happens before and after the call so you can correlate request and response in the log output
- The log output appears in the **Run** tool window in IntelliJ, not in the HTTP response panel

---

### EmployeeRestTemplateController.java

No TODOs - provided in full for reference.

```java
package com.example.api_client.controller;

import com.example.api_client.model.Employee;
import com.example.api_client.service.EmployeeRestTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rt/employees")
public class EmployeeRestTemplateController {

    private final EmployeeRestTemplateService service;

    public EmployeeRestTemplateController(EmployeeRestTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<Employee> getAll() {
        return service.fetchAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable Long id) {
        var employee = service.fetchById(id);
        return employee != null ? ResponseEntity.ok(employee) : ResponseEntity.notFound().build();
    }

    @GetMapping("/exchange")
    public List<Employee> getAllWithExchange() {
        return service.fetchAllWithExchange();
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody Employee employee) {
        var location = service.createEmployee(employee);
        return ResponseEntity.ok("Created at: " + location);
    }
}
```

---

## Exercise 2 Solutions

### TODO 8 - WebClientConfig.java (initial version)

TODO completed: configure the Reactor Netty HttpClient with connection, read, and response timeouts.

```java
package com.example.api_client.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    @Primary
    public WebClient employeeWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl("http://localhost:8081")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

**Key points:**

- Without `clientConnector(new ReactorClientHttpConnector(httpClient))`, the `WebClient` uses Netty defaults and your timeout configuration has no effect
- `CONNECT_TIMEOUT_MILLIS` limits how long to wait for the TCP connection to be established
- `responseTimeout` limits how long to wait for the upstream to begin sending a response after the request is sent
- `ReadTimeoutHandler` limits how long to wait between data packets once the response has started arriving
- `@Primary` is required here because Exercise 4 adds a second `WebClient` bean - without it Spring cannot determine which bean to inject by default

---

### TODO 9, 10, 11 - EmployeeWebClientService.java (initial version)

TODOs completed: fetchAll with bodyToFlux (TODO 9), fetchById with URI template (TODO 10), createEmployee with POST and Location header (TODO 11).

```java
package com.example.api_client.service;

import com.example.api_client.model.Employee;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class EmployeeWebClientService {

    private final WebClient employeeWebClient;

    public EmployeeWebClientService(WebClient employeeWebClient) {
        this.employeeWebClient = employeeWebClient;
    }

    public List<Employee> fetchAll() {
        return employeeWebClient.get()
                .uri("/api/employees")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .block();
    }

    public Employee fetchById(Long id) {
        return employeeWebClient.get()
                .uri("/api/employees/{id}", id)
                .retrieve()
                .bodyToMono(Employee.class)
                .block();
    }

    public String createEmployee(Employee employee) {
        return employeeWebClient.post()
                .uri("/api/employees")
                .bodyValue(employee)
                .retrieve()
                .toBodilessEntity()
                .block()
                .getHeaders()
                .getLocation()
                .toString();
    }
}
```

**Key points:**

- `bodyToFlux` is used for JSON arrays - each element becomes one emission in the reactive stream, and `collectList` gathers them into a `List`
- `bodyToMono` is used for a single JSON object
- `toBodilessEntity` is used when the response body is empty or irrelevant and you only need the headers - the 201 Created response carries the new resource URL in the `Location` header
- `.block()` is the synchronously waiting subscription - acceptable in a Spring MVC application

---

### TODO 12, 13 - EmployeeWebClientService.java and EmployeeWebClientController.java (with exception mapping)

TODOs completed: map 404 to EmployeeNotFoundException using onStatus (TODO 12), add exception handler to controller (TODO 13).

**EmployeeNotFoundException.java**

```java
package com.example.api_client.exception;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(Long id) {
        super("Employee not found: " + id);
    }
}
```

**EmployeeWebClientService.java** (updated fetchById only - other methods unchanged)

```java
package com.example.api_client.service;

import com.example.api_client.exception.EmployeeNotFoundException;
import com.example.api_client.model.Employee;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class EmployeeWebClientService {

    private final WebClient employeeWebClient;

    public EmployeeWebClientService(WebClient employeeWebClient) {
        this.employeeWebClient = employeeWebClient;
    }

    public List<Employee> fetchAll() {
        return employeeWebClient.get()
                .uri("/api/employees")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .block();
    }

    public Employee fetchById(Long id) {
        return employeeWebClient.get()
                .uri("/api/employees/{id}", id)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response -> Mono.error(new EmployeeNotFoundException(id)))
                .bodyToMono(Employee.class)
                .block();
    }

    public String createEmployee(Employee employee) {
        return employeeWebClient.post()
                .uri("/api/employees")
                .bodyValue(employee)
                .retrieve()
                .toBodilessEntity()
                .block()
                .getHeaders()
                .getLocation()
                .toString();
    }
}
```

**EmployeeWebClientController.java** (with exception handler added)

```java
package com.example.api_client.controller;

import com.example.api_client.exception.EmployeeNotFoundException;
import com.example.api_client.model.Employee;
import com.example.api_client.service.EmployeeWebClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wc/employees")
public class EmployeeWebClientController {

    private final EmployeeWebClientService service;

    public EmployeeWebClientController(EmployeeWebClientService service) {
        this.service = service;
    }

    @GetMapping
    public List<Employee> getAll() {
        return service.fetchAll();
    }

    @GetMapping("/{id}")
    public Employee getById(@PathVariable Long id) {
        return service.fetchById(id);
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody Employee employee) {
        var location = service.createEmployee(employee);
        return ResponseEntity.ok("Created at: " + location);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EmployeeNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }
}
```

**Key points:**

- `onStatus` intercepts the response before `bodyToMono` attempts deserialization - when the predicate matches, the error signal replaces the body
- The `@ExceptionHandler` converts the domain exception into a 404 HTTP response so the caller receives a clean status code rather than a 500 with a stack trace
- Without the `@ExceptionHandler`, the `EmployeeNotFoundException` would propagate up and Spring would return a 500

---

### TODO 14 - EmployeeWebClientService.java (with reactive timeout)

TODO completed: add `.timeout(Duration.ofSeconds(2))` between `collectList()` and `block()`.

```java
package com.example.api_client.service;

import com.example.api_client.exception.EmployeeNotFoundException;
import com.example.api_client.model.Employee;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class EmployeeWebClientService {

    private final WebClient employeeWebClient;

    public EmployeeWebClientService(WebClient employeeWebClient) {
        this.employeeWebClient = employeeWebClient;
    }

    public List<Employee> fetchAll() {
        return employeeWebClient.get()
                .uri("/api/employees")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .timeout(Duration.ofSeconds(2))
                .block();
    }

    public Employee fetchById(Long id) {
        return employeeWebClient.get()
                .uri("/api/employees/{id}", id)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response -> Mono.error(new EmployeeNotFoundException(id)))
                .bodyToMono(Employee.class)
                .block();
    }

    public String createEmployee(Employee employee) {
        return employeeWebClient.post()
                .uri("/api/employees")
                .bodyValue(employee)
                .retrieve()
                .toBodilessEntity()
                .block()
                .getHeaders()
                .getLocation()
                .toString();
    }
}
```

**Key points:**

- `.timeout()` is placed between `collectList()` and `block()` - it operates on the `Mono<List<Employee>>` produced by `collectList()`
- The reactive timeout fires after 2 seconds and throws a `TimeoutException`, regardless of what the HTTP engine timeout is set to
- To observe this: temporarily change the URI to `/api/slow`, run `GET http://localhost:8080/wc/employees`, and watch the Run console - the `TimeoutException` fires after 2 seconds rather than waiting for the 5-second stub delay

---

## Exercise 3 Solutions

### TODO 15 and 16 - ResilientEmployeeService.java

TODOs completed: add `@Retry` and `@CircuitBreaker` annotations (TODO 15), implement the fallback method (TODO 16).

```java
package com.example.api_client.service;

import com.example.api_client.model.Employee;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class ResilientEmployeeService {

    private static final Logger log = LoggerFactory.getLogger(ResilientEmployeeService.class);

    private final WebClient employeeWebClient;

    public ResilientEmployeeService(WebClient employeeWebClient) {
        this.employeeWebClient = employeeWebClient;
    }

    @Retry(name = "upstream-api")
    @CircuitBreaker(name = "upstream-api", fallbackMethod = "fetchAllFallback")
    public List<Employee> fetchAll() {
        log.info("Calling upstream /api/flaky");
        return employeeWebClient.get()
                .uri("/api/flaky")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .block();
    }

    public List<Employee> fetchAllFallback(Throwable ex) {
        log.warn("Fallback invoked: {}", ex.getMessage());
        return List.of();
    }
}
```

**Key points:**

- `@Retry` must be listed above `@CircuitBreaker` (closer to the method declaration). Spring AOP applies annotations from the outside in, so `@CircuitBreaker` is the outermost wrapper and evaluates first on every call
- The circuit breaker checks its state before the retry runs. If the circuit is open, it fails immediately without making any network calls
- The fallback method must have the same return type as `fetchAll()` and must accept a `Throwable` as its last parameter - the name passed to `fallbackMethod` must match exactly
- Returning `List.of()` from the fallback produces a degraded but valid response so the caller receives a 200 with an empty list rather than a 500

---

### ResilientController.java

No TODOs - provided in full for reference.

```java
package com.example.api_client.controller;

import com.example.api_client.model.Employee;
import com.example.api_client.service.ResilientEmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/resilient")
public class ResilientController {

    private final ResilientEmployeeService service;

    public ResilientController(ResilientEmployeeService service) {
        this.service = service;
    }

    @GetMapping("/employees")
    public List<Employee> getAll() {
        return service.fetchAll();
    }
}
```

---

### StubManagementController.java

No TODOs - provided in full for reference.

```java
package com.example.api_client.controller;

import com.example.api_client.config.StubServerConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@RestController
@RequestMapping("/stubs")
public class StubManagementController {

    private final StubServerConfig stubServerConfig;

    public StubManagementController(StubServerConfig stubServerConfig) {
        this.stubServerConfig = stubServerConfig;
    }

    @PostMapping("/flaky/recover")
    public ResponseEntity<String> makeFlakyRecover() {
        stubServerConfig.getServer().stubFor(
                get(urlEqualTo("/api/flaky"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[{\"id\":1,\"name\":\"Alice\",\"department\":\"Engineering\"}]"))
        );
        return ResponseEntity.ok("Stub updated: /api/flaky now returns 200");
    }

    @PostMapping("/flaky/fail")
    public ResponseEntity<String> makeFlakyFail() {
        stubServerConfig.getServer().stubFor(
                get(urlEqualTo("/api/flaky"))
                        .willReturn(aResponse()
                                .withStatus(503)
                                .withBody("Service Unavailable"))
        );
        return ResponseEntity.ok("Stub updated: /api/flaky now returns 503");
    }
}
```

---

## Exercise 4 Solutions

### TODO 17 - StaticTokenProvider.java

TODO completed: inject `upstream.api.token` using `@Value`.

```java
package com.example.api_client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticTokenProvider {

    private final String token;

    public StaticTokenProvider(@Value("${upstream.api.token}") String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
```

**Key points:**

- The `@Value` annotation is already on the constructor parameter in the exercise starter code - the TODO is already satisfied as written, provided `upstream.api.token` exists in `application.yml`
- If the property is missing from `application.yml`, Spring fails at startup with `Could not resolve placeholder 'upstream.api.token'`
- In production the value in `application.yml` would be `${UPSTREAM_API_TOKEN}`, referencing an environment variable rather than a literal string

---

### TODO 18 - BearerTokenFilter.java

TODO completed: implement `filter()` to attach the Authorization header, logging only the first 8 characters of the token.

```java
package com.example.api_client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

public class BearerTokenFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenFilter.class);

    private final StaticTokenProvider tokenProvider;

    public BearerTokenFilter(StaticTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String token = tokenProvider.getToken();
            log.debug("Attaching token: {}...", token.substring(0, 8));

            ClientRequest authenticatedRequest = ClientRequest.from(request)
                    .header("Authorization", "Bearer " + token)
                    .build();

            return Mono.just(authenticatedRequest);
        });
    }
}
```

**Key points:**

- `ofRequestProcessor` transforms the outgoing request before it is sent - it receives the original request and returns a new one
- `ClientRequest.from(request)` copies all existing headers, URI, and method from the original request so nothing is lost
- A new `ClientRequest` is built rather than mutating the existing one because `ClientRequest` is immutable - this is a deliberate design choice that makes the filter thread-safe
- Never log the full token value - the first 8 characters are enough to identify which credential is in use without creating a credential leak in the logs

---

### TODO 19 - WebClientConfig.java (final version with both beans)

TODO completed: add the `authenticatedWebClient` bean with the bearer token filter applied.

```java
package com.example.api_client.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    @Primary
    public WebClient employeeWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl("http://localhost:8081")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient authenticatedWebClient(StaticTokenProvider tokenProvider) {
        var bearerTokenFilter = new BearerTokenFilter(tokenProvider);

        return WebClient.builder()
                .baseUrl("http://localhost:8081")
                .filter(bearerTokenFilter.filter())
                .build();
    }
}
```

**Key points:**

- `employeeWebClient` keeps `@Primary` so all existing service classes that inject `WebClient` without a qualifier continue to receive the unauthenticated bean
- `authenticatedWebClient` has no `@Primary` - it is only injected where `@Qualifier("authenticatedWebClient")` is explicitly declared
- The bean method name (`authenticatedWebClient`) is what Spring uses as the default qualifier name, so `@Qualifier("authenticatedWebClient")` in `AuthenticatedEmployeeService` matches it

---

### TODO 20 - AuthenticatedEmployeeService.java

TODO completed: implement `ping()` to call `GET /api/auth-required` and return the response body.

```java
package com.example.api_client.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AuthenticatedEmployeeService {

    private final WebClient authenticatedWebClient;

    public AuthenticatedEmployeeService(
            @Qualifier("authenticatedWebClient") WebClient authenticatedWebClient) {
        this.authenticatedWebClient = authenticatedWebClient;
    }

    public String ping() {
        return authenticatedWebClient.get()
                .uri("/api/auth-required")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
```

**Key points:**

- `@Qualifier("authenticatedWebClient")` is required because there are two `WebClient` beans. Without it, Spring injects `employeeWebClient` (the `@Primary` bean), which has no token filter, and the stub returns 401
- The `authenticatedWebClient` already has `BearerTokenFilter` registered, so the `Authorization: Bearer ...` header is added automatically without any call-site code

---

### AuthController.java

No TODOs - provided in full for reference.

```java
package com.example.api_client.controller;

import com.example.api_client.service.AuthenticatedEmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticatedEmployeeService service;

    public AuthController(AuthenticatedEmployeeService service) {
        this.service = service;
    }

    @GetMapping("/ping")
    public String ping() {
        return service.ping();
    }
}
```

---

## HTTP Request Files

### rest-template-requests.http

```http
### Get all employees (getForObject)
GET http://localhost:8080/rt/employees

###

### Get employee by ID (getForEntity)
GET http://localhost:8080/rt/employees/1

###

### Get missing employee (should return 404)
GET http://localhost:8080/rt/employees/99

###

### Get all employees (exchange with ParameterizedTypeReference)
GET http://localhost:8080/rt/employees/exchange

###

### Create a new employee
POST http://localhost:8080/rt/employees
Content-Type: application/json

{
  "name": "Charlie",
  "department": "HR"
}
```

### web-client-requests.http

```http
### Get all employees
GET http://localhost:8080/wc/employees

###

### Get employee by ID
GET http://localhost:8080/wc/employees/1

###

### Get missing employee - should return 404, not 500
GET http://localhost:8080/wc/employees/99

###

### Create a new employee
POST http://localhost:8080/wc/employees
Content-Type: application/json

{
  "name": "Dana",
  "department": "Legal"
}

###

### Resilient endpoint - watch the console for retry attempts and fallback log lines
GET http://localhost:8080/resilient/employees

###

### Make the flaky stub return 200 (simulates upstream recovery)
POST http://localhost:8080/stubs/flaky/recover

###

### Make the flaky stub return 503 again (simulates upstream going down)
POST http://localhost:8080/stubs/flaky/fail

###

### Authenticated ping - should return {"message":"Authenticated successfully"}
GET http://localhost:8080/auth/ping
```

---

## Expected Responses

| Request | Expected Status | Expected Body |
|---|---|---|
| `GET /rt/employees` | 200 | Array of Alice and Bob |
| `GET /rt/employees/1` | 200 | Alice |
| `GET /rt/employees/99` | 404 | (empty) |
| `GET /rt/employees/exchange` | 200 | Array of Alice and Bob |
| `POST /rt/employees` | 200 | `Created at: http://localhost:8081/api/employees/3` |
| `GET /wc/employees` | 200 | Array of Alice and Bob |
| `GET /wc/employees/1` | 200 | Alice |
| `GET /wc/employees/99` | 404 | (empty) |
| `POST /wc/employees` | 200 | `Created at: http://localhost:8081/api/employees/3` |
| `GET /resilient/employees` (flaky=503) | 200 | `[]` (fallback) |
| `GET /resilient/employees` (flaky=200) | 200 | Array of Alice |
| `GET /auth/ping` | 200 | `{"message":"Authenticated successfully"}` |

---

## Common Errors and Fixes

**`Cannot resolve symbol 'RestTemplateBuilder'` or `No beans of 'RestTemplateBuilder' type found`**
`spring-boot-starter-web` is missing from `pom.xml`. Replace `pom.xml` with the version provided in the exercise setup section and reload Maven.

**`GET /rt/employees` returns `[]`**
The TODO methods in `EmployeeRestTemplateService` still contain the stub `return List.of()`. Implement the methods as shown in the solutions above.

**`GET /auth/ping` returns 401**
The `authenticatedWebClient` bean is not being injected. Check that `WebClientConfig` contains both the `employeeWebClient` and `authenticatedWebClient` beans, that `employeeWebClient` has `@Primary`, and that `AuthenticatedEmployeeService` has `@Qualifier("authenticatedWebClient")` on its constructor parameter.

**`Could not resolve placeholder 'upstream.api.token'`**
The `upstream.api.token` property is missing from `application.yml`. Add the `upstream.api` block shown in the Task 3.2 solution.

**Deprecation warnings on `setConnectTimeout` or `setReadTimeout`**
Use `connectTimeout` and `readTimeout` instead. These are the correct method names in Spring Boot 3.4.

**`GET /resilient/employees` returns employees instead of `[]`**
The `/api/flaky` stub was updated to return 200 in a previous run. Send `POST http://localhost:8080/stubs/flaky/fail` to restore the 503 behaviour, then restart the application to reset the circuit breaker state.

---

*End of Module 4 Solutions*
