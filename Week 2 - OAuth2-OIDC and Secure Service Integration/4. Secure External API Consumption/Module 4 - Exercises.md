# Module 4: Secure External API Consumption
## Hands-On Exercises

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 4 - Secure External API Consumption
> **Estimated time per exercise:** 30-60 minutes

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

## Before You Start: The Upstream API

These exercises call a local stub API that simulates an upstream service. The stub server runs inside the same Spring Boot application you are building, so there is nothing external to install or start.

The stub is powered by **WireMock**, a widely used HTTP mocking library. It starts on port `8081` automatically when you run the application and registers all the stub responses your exercises need. You do not need to configure anything separately - just start the application and the stubs are ready.

To verify the stub server is running after you complete the setup below, open this URL in your browser:

```
http://localhost:8081/__admin/mappings
```

You should see a JSON list of registered stubs. If you see a connection error, check that the application started without errors before proceeding.

**Upstream API reference:**

| Stub URL | Method | Behaviour |
|---|---|---|
| `http://localhost:8081/api/employees` | GET | Returns a list of two employees |
| `http://localhost:8081/api/employees/1` | GET | Returns a single employee |
| `http://localhost:8081/api/employees/99` | GET | Returns 404 |
| `http://localhost:8081/api/employees` | POST | Returns 201 Created with Location header |
| `http://localhost:8081/api/slow` | GET | Delays 5 seconds then responds |
| `http://localhost:8081/api/flaky` | GET | Returns 503 (Exercise 3 changes this to 200) |
| `http://localhost:8081/api/auth-required` | GET | Returns 200 only when Authorization header contains "Bearer" |

---

## Starter Project

All exercises share a single Spring Boot project. Follow these steps exactly - there are a few specific choices you need to make to ensure the project is configured correctly.

### Create the project with Spring Initializr

1. Open [https://start.spring.io](https://start.spring.io) in a browser

2. Configure the left-hand panel as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | **3.4.5** (see note below) |
| Group | `com.example` |
| Artifact | `api-client` |
| Name | `api-client` |
| Packaging | Jar |
| Java | 17 |

   > **Spring Boot version:** Spring Initializr defaults to the latest available version, which may be 4.x. These exercises require **3.4.5**. Click the Spring Boot version dropdown and select `3.4.5`. If 3.4.5 is not listed, select the highest available `3.x` version. Do not use a 4.x version - it is not yet compatible with the dependencies used in these exercises.

3. Click **ADD DEPENDENCIES** and add the following. Search for each by name:

   - **Spring Web** - provides `RestTemplate`, `RestTemplateBuilder`, and the Spring MVC controller layer
   - **Spring Reactive Web** - provides `WebClient`, `Mono`, and `Flux`
   - **Spring Boot DevTools**
   - **Validation**

   > **Both Spring Web and Spring Reactive Web are required.** They serve different purposes and are not alternatives to each other. After adding dependencies, confirm that both appear in the dependency list on the right side of the Initializr page before generating.

4. Click **GENERATE**, unzip the downloaded file, and open the project in IntelliJ using **File -> Open -> New Window**

5. Wait for Maven to finish downloading dependencies. Watch the progress bar at the bottom of IntelliJ and confirm there are no red error lines in the Maven tool window before continuing.

### Verify and fix pom.xml

Before writing any code, open `pom.xml` and replace its entire contents with the following. This ensures the correct Spring Boot version and dependency artifact IDs are in place, regardless of what Initializr generated.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.4.5</version>
		<relativePath/>
	</parent>
	<groupId>com.example</groupId>
	<artifactId>api-client</artifactId>
	<version>0.0.1-SNAPSHOT</version>

	<properties>
		<java.version>17</java.version>
	</properties>

	<dependencies>
		<!-- Spring MVC: RestTemplateBuilder, @RestController, embedded Tomcat -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<!-- WebFlux: WebClient, Mono, Flux -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webflux</artifactId>
		</dependency>

		<!-- Bean validation (@Valid, @NotNull, etc.) -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>

		<!-- Hot reload during development -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>

		<!-- Embedded stub server for exercises -->
		<dependency>
			<groupId>org.wiremock</groupId>
			<artifactId>wiremock-standalone</artifactId>
			<version>3.6.0</version>
		</dependency>

		<!-- Standard Spring Boot test support (JUnit 5, MockMvc, WebTestClient) -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

After saving, reload Maven using the popup prompt that appears in IntelliJ, or by clicking the reload icon in the Maven tool window. Wait for the download to complete before continuing.

### Package structure to create before Exercise 1

Right-click `src/main/java/com/example/api_client` and create the following sub-packages:

```
com.example.api_client
├── config
├── controller
├── exception
├── model
└── service
```

### Shared model class

Create `Employee.java` in the `model` package. All exercises share this class:

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

### Embedded stub server setup

Create `StubServerConfig.java` in the `config` package. This class starts a WireMock server on port `8081` when the Spring application starts and registers all the stubs used across every exercise. Read through every stub registration - each one corresponds directly to a behaviour in the upstream API reference table above.

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

        // Point the WireMock static DSL at our server instance
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

    // Exposed so StubManagementController can update stubs at runtime
    public WireMockServer getServer() {
        return wireMockServer;
    }

    private void registerStubs() {

        // --- Employee list ---
        stubFor(get(urlEqualTo("/api/employees"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" +
                                "{\"id\":1,\"name\":\"Alice\",\"department\":\"Engineering\"}," +
                                "{\"id\":2,\"name\":\"Bob\",\"department\":\"Finance\"}" +
                                "]")));

        // --- Single employee ---
        stubFor(get(urlEqualTo("/api/employees/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Alice\",\"department\":\"Engineering\"}")));

        // --- Missing employee (404) ---
        stubFor(get(urlEqualTo("/api/employees/99"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not found")));

        // --- Create employee (201 with Location header) ---
        stubFor(post(urlEqualTo("/api/employees"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Location", "http://localhost:8081/api/employees/3")
                        .withBody("{\"id\":3,\"name\":\"Charlie\",\"department\":\"HR\"}")));

        // --- Slow response (used to demonstrate timeout behaviour in Exercise 2) ---
        stubFor(get(urlEqualTo("/api/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000)
                        .withBody("finally!")));

        // --- Flaky endpoint (starts as 503; Exercise 3 updates it to 200 at runtime) ---
        stubFor(get(urlEqualTo("/api/flaky"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        // --- Auth-required: 401 when Authorization header is missing or wrong ---
        // WireMock matches stubs in reverse registration order (last registered wins).
        // The catch-all 401 stub must be registered FIRST so that the more specific
        // Bearer stub registered below takes priority when the header is present.
        stubFor(get(urlEqualTo("/api/auth-required"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        // --- Auth-required: 200 when Authorization header contains "Bearer" ---
        // Registered AFTER the catch-all so WireMock evaluates this one first.
        stubFor(get(urlEqualTo("/api/auth-required"))
                .withHeader("Authorization", containing("Bearer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Authenticated successfully\"}")));
    }
}
```

> **What is WireMock?** WireMock is an HTTP server that returns pre-configured responses rather than processing real business logic. It is the standard tool for testing HTTP client code without depending on real external services.

> **WireMock stub matching order:** WireMock evaluates stubs in reverse registration order - the last registered stub is checked first. For the `/api/auth-required` stubs, the catch-all 401 is registered first and the more specific Bearer stub is registered second, so the Bearer stub takes priority when the Authorization header is present.

Start the application now. You should see this line near the end of the startup log:

```
Stub server started on port 8081. Admin UI: http://localhost:8081/__admin/mappings
```

Open that URL in your browser to confirm all stubs are registered before proceeding to Exercise 1.

---

## Exercise 1 - RestTemplate: Synchronous HTTP Calls

**Estimated time:** 30-40 minutes
**Topics covered:** RestTemplate, `getForObject`, `getForEntity`, `exchange`, `ParameterizedTypeReference`, interceptors, error handling

### Context

`RestTemplate` is the original Spring HTTP client and remains common in enterprise codebases. Even if you write new code using `WebClient`, you will encounter `RestTemplate` when maintaining or migrating existing applications. This exercise builds the muscle memory for reading and writing idiomatic `RestTemplate` code.

The lecture introduced a key distinction: `getForObject` returns the body directly and throws on any non-2xx response, while `getForEntity` returns the full HTTP response including status and headers. In production, `getForEntity` is usually the better choice because HTTP status codes carry meaning that your calling code often needs to inspect.

`RestTemplate` follows the same template-method pattern used elsewhere in Spring, such as `JdbcTemplate`: it manages the repetitive lifecycle work of an HTTP connection while you supply only the parts that vary - the URL, method, and expected response type.

### Task 1.1 - Create and configure a RestTemplate bean

Create `RestTemplateConfig.java` in the `config` package:

```java
package com.example.api_client.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    // TODO 1: Use the RestTemplateBuilder to create a RestTemplate bean.
    // Set the root URI to "http://localhost:8081".
    // Set a connection timeout of 3 seconds.
    // Set a read timeout of 5 seconds.
    // Hint: builder.rootUri(...).connectTimeout(...).readTimeout(...)
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build(); // Replace with your implementation
    }
}
```

> **Note:** In Spring Boot 3.4, the timeout methods on `RestTemplateBuilder` were renamed. Use `connectTimeout(Duration)` and `readTimeout(Duration)` rather than the older `setConnectTimeout` and `setReadTimeout` forms, which are deprecated and will produce compiler warnings.

**Why `rootUri` matters:** Setting the root URI on the builder means every call site only needs to supply the path segment (for example, `/api/employees`), not the full URL. This eliminates duplication and makes the base URL easy to change in one place.

### Task 1.2 - Fetch a list of employees using `getForObject`

Create `EmployeeRestTemplateService.java` in the `service` package:

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

    // TODO 2: Implement fetchAll() using getForObject.
    // The upstream URL is "/api/employees".
    // The return type must be Employee[].class (arrays are safe for getForObject).
    // Convert the array to a List before returning.
    // Hint: Arrays.asList(restTemplate.getForObject(...))
    public List<Employee> fetchAll() {
        return List.of(); // Replace with your implementation
    }

    // TODO 3: Implement fetchById() using getForEntity.
    // The upstream URL uses a URI variable: "/api/employees/{id}".
    // Return the body from the ResponseEntity.
    // Inspect the status code: if it is not 2xx, return null.
    // Hint: restTemplate.getForEntity("/api/employees/{id}", Employee.class, id)
    public Employee fetchById(Long id) {
        return null; // Replace with your implementation
    }

    // TODO 4: Implement fetchAllWithExchange() using exchange.
    // This method does the same job as fetchAll() but uses ParameterizedTypeReference
    // so the response is deserialized directly into List<Employee> rather than Employee[].
    // Hint:
    //   var typeRef = new ParameterizedTypeReference<List<Employee>>() {};
    //   ResponseEntity<List<Employee>> response = restTemplate.exchange(
    //       "/api/employees", HttpMethod.GET, HttpEntity.EMPTY, typeRef);
    //   return response.getBody();
    public List<Employee> fetchAllWithExchange() {
        return List.of(); // Replace with your implementation
    }

    // TODO 5: Implement createEmployee() using postForEntity.
    // POST to "/api/employees" with the employee object as the request body.
    // Return the Location header from the response as a String.
    // Hint: restTemplate.postForEntity("/api/employees", employee, Employee.class)
    //       response.getHeaders().getLocation().toString()
    public String createEmployee(Employee employee) {
        return null; // Replace with your implementation
    }
}
```

> **Why `ParameterizedTypeReference`?** Java erases generic type parameters at runtime due to type erasure. The expression `List<Employee>.class` does not compile. `ParameterizedTypeReference<List<Employee>>() {}` creates an anonymous subclass that captures the full generic type at compile time, giving `RestTemplate` enough information to deserialize a JSON array into `List<Employee>`.

### Task 1.3 - Expose the service through a controller

Create `EmployeeRestTemplateController.java` in the `controller` package:

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

Create `rest-template-requests.http` in the project root to test the endpoints:

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

Run each request. Confirm that the list endpoints return two employees, the single-employee endpoint returns Alice, the ID 99 request returns 404, and the POST response includes the Location URL.

### Task 1.4 - Add a logging interceptor

The lecture explained that `ClientHttpRequestInterceptor` is the correct mechanism for cross-cutting concerns on `RestTemplate`. Authentication headers, correlation IDs, and logging all belong in an interceptor rather than scattered across call sites.

Create `LoggingInterceptor.java` in the `config` package:

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

        // TODO 6: Before calling execution.execute(), log the HTTP method and URI.
        // Use: log.info(">>> {} {}", request.getMethod(), request.getURI())
        // After execution.execute() returns, log the response status code.
        // Use: log.info("<<< {}", response.getStatusCode())
        // Return the response.
        // Hint: ClientHttpResponse response = execution.execute(request, body);

        return execution.execute(request, body); // Replace with your implementation
    }
}
```

Register the interceptor in `RestTemplateConfig.java`:

```java
// TODO 7: Register the LoggingInterceptor on the RestTemplate.
// Use builder.additionalInterceptors(new LoggingInterceptor()) before calling build().
```

Run the GET all employees request again. You should see log lines in the application console:

```
>>> GET http://localhost:8081/api/employees
<<< 200 OK
```

> **Finding the log output:** The log lines appear in the **Run** tool window at the bottom of IntelliJ, not in the HTTP response panel. Open it with **Alt+4** (Windows/Linux) or **Cmd+4** (Mac). After running a request, scroll to the bottom of the console or use **Ctrl+F** to search for `>>>`.

### Checkpoints

1. The lecture stated that `getForObject` is a convenience method and `getForEntity` is usually the better production choice. Looking at your `fetchById` implementation, describe a case where knowing the HTTP status code changes what your method returns to the caller.
2. `RestTemplate` is in maintenance mode as of Spring 5. Given that, when would you choose to use it rather than `WebClient` in a real project?
3. The interceptor logs the full URI including the base URL. If a colleague argued that the base URL should be stripped from the log to save space, how would you do that using `request.getURI()`?

### Extension Task

Add a `BearerTokenInterceptor` that reads a token from a `@Value`-injected property named `upstream.api.token` and attaches it as an `Authorization: Bearer <token>` header on every request. Add `upstream.api.token=test-token-value` to `application.yml`. Confirm the header is present by calling `GET http://localhost:8081/__admin/requests` and inspecting the logged request in the WireMock admin response.

---

## Exercise 2 - WebClient: Fluent Non-Blocking HTTP

**Estimated time:** 40-50 minutes
**Topics covered:** `WebClient`, `Mono`, `Flux`, the filter chain, `retrieve()` vs `exchangeToMono()`, URI templates, per-call timeouts, domain exception mapping

### Context

`WebClient` is Spring's current HTTP client. Where `RestTemplate` is synchronous and holds a thread for the duration of every call, `WebClient` is built on reactive streams and releases the thread the moment the request is dispatched. The thread returns to the pool and is available to handle other work while the network I/O completes.

In a Spring MVC application (which this project is), you will use `.block()` to bridge from the reactive pipeline back to an imperative return value. This is perfectly valid and widely used. The benefit of `WebClient` in Spring MVC is not reactive end-to-end pipelines but rather the fluent API, the filter chain, codec control, and access to features that `RestTemplate` will never receive.

`Mono<T>` represents a single value that will arrive asynchronously - similar in concept to a `Future` or `Promise`. `Flux<T>` represents a stream of zero or more values arriving asynchronously. Neither does anything until something subscribes to it. Calling `.block()` is the subscription mechanism used in Spring MVC - it waits synchronously for the result and returns it as a plain Java object.

### Task 2.1 - Create and configure a WebClient bean

Create `WebClientConfig.java` in the `config` package:

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

    // TODO 8: Create a WebClient bean named "employeeWebClient".
    // Configure the Reactor Netty HttpClient with:
    //   - Connection timeout: 3000 milliseconds (ChannelOption.CONNECT_TIMEOUT_MILLIS)
    //   - Read timeout: 5 seconds (ReadTimeoutHandler added via doOnConnected)
    //   - Response timeout: 5 seconds (HttpClient.responseTimeout)
    // Build a WebClient using WebClient.builder()
    //   - Set the base URL to "http://localhost:8081"
    //   - Apply the ReactorClientHttpConnector wrapping your HttpClient
    //
    // Hint - HttpClient setup:
    //   HttpClient httpClient = HttpClient.create()
    //       .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
    //       .responseTimeout(Duration.ofSeconds(5))
    //       .doOnConnected(conn ->
    //           conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS)));
    @Bean
    @Primary
    public WebClient employeeWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8081")
                .build(); // Replace with your implementation
    }
}
```

> **Why configure timeouts at the HTTP engine level?** The Netty `HttpClient` timeout is a safety net at the TCP and I/O layer. It catches cases where the connection itself hangs or where the response delivery stalls at the network level. The reactive `.timeout()` you will add in Task 2.4 operates at the business logic layer and enforces end-to-end time budgets per call. Both levels are needed in production.

> **Why `@Primary`?** Exercise 4 adds a second `WebClient` bean for authenticated calls. `@Primary` tells Spring which bean to inject by default when no `@Qualifier` is specified, preventing ambiguous dependency errors.

### Task 2.2 - Fetch employees using WebClient

Create `EmployeeWebClientService.java` in the `service` package:

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

    // TODO 9: Implement fetchAll() using WebClient.
    // Chain: .get().uri("/api/employees").retrieve()
    //        .bodyToFlux(Employee.class).collectList().block()
    // bodyToFlux turns the JSON array into a reactive stream of Employee objects.
    // collectList() gathers all emitted items into a single Mono<List<Employee>>.
    // block() subscribes and waits synchronously (acceptable in Spring MVC).
    public List<Employee> fetchAll() {
        return List.of(); // Replace with your implementation
    }

    // TODO 10: Implement fetchById() using URI template variables.
    // Chain: .get().uri("/api/employees/{id}", id).retrieve()
    //        .bodyToMono(Employee.class).block()
    // URI template variables are automatically percent-encoded.
    // They are resistant to path traversal because {id} is treated as a single segment.
    public Employee fetchById(Long id) {
        return null; // Replace with your implementation
    }

    // TODO 11: Implement createEmployee() using POST.
    // Chain: .post().uri("/api/employees")
    //        .bodyValue(employee)
    //        .retrieve()
    //        .toBodilessEntity()     <- returns Mono<ResponseEntity<Void>>
    //        .block()
    // Extract the Location header: response.getHeaders().getLocation()
    // Return the Location URI as a String.
    public String createEmployee(Employee employee) {
        return null; // Replace with your implementation
    }
}
```

Create `EmployeeWebClientController.java` in the `controller` package:

```java
package com.example.api_client.controller;

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
}
```

Create `web-client-requests.http` in the project root:

```http
### Get all employees
GET http://localhost:8080/wc/employees

###

### Get employee by ID
GET http://localhost:8080/wc/employees/1

###

### Create a new employee
POST http://localhost:8080/wc/employees
Content-Type: application/json

{
  "name": "Dana",
  "department": "Legal"
}
```

Run each request and confirm the responses match what you saw from the `RestTemplate` endpoints in Exercise 1.

### Task 2.3 - Map HTTP errors to domain exceptions

The lecture explained that your service layer should speak your domain, not HTTP. A caller should handle `EmployeeNotFoundException`, not `WebClientResponseException`. This keeps your service layer independent of whichever HTTP client is in use.

Create `EmployeeNotFoundException.java` in the `exception` package:

```java
package com.example.api_client.exception;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(Long id) {
        super("Employee not found: " + id);
    }
}
```

Update `fetchById()` in `EmployeeWebClientService` to map the 404 response:

```java
// TODO 12: Replace the fetchById() implementation with one that maps a 404
// to EmployeeNotFoundException using onStatus().
//
//   return employeeWebClient.get()
//       .uri("/api/employees/{id}", id)
//       .retrieve()
//       .onStatus(
//           status -> status.value() == 404,
//           response -> Mono.error(new EmployeeNotFoundException(id)))
//       .bodyToMono(Employee.class)
//       .block();
//
// onStatus() intercepts responses matching the predicate and substitutes
// the supplied error signal instead of attempting to deserialize the body.
// Add the required import: import reactor.core.publisher.Mono;
```

Add an `@ExceptionHandler` to `EmployeeWebClientController` to return a clean 404 response:

```java
// TODO 13: Add this handler inside EmployeeWebClientController.
@ExceptionHandler(EmployeeNotFoundException.class)
public ResponseEntity<String> handleNotFound(EmployeeNotFoundException ex) {
    return ResponseEntity.notFound().build();
}
```

Add a test request to `web-client-requests.http`:

```http
### Get missing employee - should return 404, not 500
GET http://localhost:8080/wc/employees/99
```

Run the request. The response should be a clean 404 with no body, rather than a 500 with a stack trace.

### Task 2.4 - Add a per-call reactive timeout

Update `fetchAll()` to enforce a 2-second business-level timeout using the reactive `.timeout()` operator:

```java
// TODO 14: Add .timeout(Duration.ofSeconds(2)) between collectList() and block()
// in fetchAll(). Import java.time.Duration.
//
// This timeout is separate from the HTTP engine timeout in WebClientConfig.
// The HTTP engine timeout is the safety net at the network layer.
// This reactive timeout enforces an end-to-end time budget for this specific call.
```

To observe the timeout firing, temporarily change the URI in `fetchAll()` from `/api/employees` to `/api/slow` and run `GET http://localhost:8080/wc/employees`. The stub delays 5 seconds but the reactive timeout fires after 2, so you will see a `TimeoutException` in the Run console almost immediately rather than waiting. Change the URI back to `/api/employees` when you are done.

### Checkpoints

1. The lecture described `Mono` and `Flux` as "cold publishers." What does cold mean in this context, and what happens if you build a `Mono` chain but never call `.block()` or `.subscribe()`?
2. `retrieve()` and `exchangeToMono()` are both ways to process a response. The lecture stated that if you use `exchangeToMono()` you must consume the response body in every code path. What happens to the HTTP connection pool if you do not?
3. Your `WebClientConfig` sets a 5-second read timeout at the HTTP engine level. `fetchAll()` sets a 2-second reactive timeout. If the upstream takes 3 seconds to respond, which timeout fires? Why?

### Extension Task

Add a logging `ExchangeFilterFunction` to `employeeWebClient` that logs the HTTP method and URI before each request, and the status code after each response. The filter must not log the value of the `Authorization` header. Register the filter in `WebClientConfig` using `.filter(loggingFilter)` before calling `.build()`.

---

## Exercise 3 - Resilience Patterns: Retry, Backoff, and Circuit Breaker

**Estimated time:** 40-55 minutes
**Topics covered:** timeout, retry, exponential backoff with jitter, circuit breaker, Resilience4j, fallback methods, the resilience stack ordering

### Context

Every upstream service you consume will eventually fail. Networks drop packets, deployments restart services, and load spikes cause temporary throttling. Without resilience patterns, a single upstream failure becomes your failure: threads exhaust waiting on hung connections, your service degrades, and users see errors from transient conditions that would have resolved on their own.

This exercise adds Resilience4j to the project and applies the four essential patterns the lecture covered: timeout, retry, exponential backoff with jitter, and circuit breaker.

### Task 3.1 - Add Resilience4j dependencies

Add the following to `pom.xml` inside the `<dependencies>` block:

```xml
<!-- Resilience4j Spring Boot starter -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- Required for AOP-based annotations (@CircuitBreaker, @Retry) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Reload Maven when IntelliJ prompts you to do so.

### Task 3.2 - Configure resilience settings in application.yml

Rename `src/main/resources/application.properties` to `application.yml` and replace its contents with the following. Read through every comment before writing any code.

```yaml
spring:
  application:
    name: api-client

logging:
  level:
    com.example.api_client: DEBUG

resilience4j:

  # Retry: attempt failed calls up to 3 times before giving up.
  retry:
    instances:
      upstream-api:
        max-attempts: 3
        wait-duration: 500ms
        # Exponential backoff doubles the wait after each failure:
        # attempt 1 fails -> wait 500ms, attempt 2 fails -> wait 1000ms, attempt 3 fails -> propagate
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        exponential-max-wait-duration: 5s
        # Only retry on these exceptions.
        # 4xx client errors are not listed - retrying them will never help.
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
          - java.util.concurrent.TimeoutException
          - java.io.IOException

  # Circuit Breaker: stop calling a failing upstream when it is clearly struggling.
  circuit-breaker:
    instances:
      upstream-api:
        # Evaluate the last 10 calls to decide whether to open the circuit.
        sliding-window-size: 10
        # Open the circuit if 50% or more of the last 10 calls fail.
        failure-rate-threshold: 50
        # Allow 2 probe requests through the half-open state to test recovery.
        permitted-number-of-calls-in-half-open-state: 2
        # Wait 30 seconds in the open state before moving to half-open.
        wait-duration-in-open-state: 30s
        # Count all exceptions as failures toward the threshold.
        record-exceptions:
          - java.lang.Exception

upstream:
  api:
    # In a real deployment this value comes from an environment variable or a secrets manager.
    # It is in application.yml here for exercise purposes only.
    # Never commit real credentials to source control.
    token: "exercise-static-bearer-token"
    base-url: "http://localhost:8081"
```

### Task 3.3 - Apply retry and circuit breaker annotations

Create `ResilientEmployeeService.java` in the `service` package:

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

    // TODO 15: Add @Retry(name = "upstream-api") to fetchAll().
    // Add @CircuitBreaker(name = "upstream-api", fallbackMethod = "fetchAllFallback") to fetchAll().
    //
    // Annotation ordering matters. CircuitBreaker wraps Retry, which means:
    //   1. The circuit breaker checks its state first.
    //      If open, it fails immediately without making a network call.
    //   2. If closed, Retry orchestrates multiple attempts with exponential backoff.
    //   3. If all retry attempts are exhausted, the exception reaches the circuit breaker,
    //      which counts it as a failure toward the threshold.
    public List<Employee> fetchAll() {
        log.info("Calling upstream /api/flaky");
        return employeeWebClient.get()
                .uri("/api/flaky")
                .retrieve()
                .bodyToFlux(Employee.class)
                .collectList()
                .block();
    }

    // TODO 16: Implement the fallback method.
    // The fallback method must have the same return type as fetchAll() -- List<Employee>.
    // It must accept a Throwable as its last (and only) parameter.
    // Log a warning that includes ex.getMessage() so the failure is observable in logs.
    // Return List.of() to serve a degraded empty response rather than propagating the error.
    public List<Employee> fetchAllFallback(Throwable ex) {
        return List.of(); // Replace with your implementation
    }
}
```

Create `ResilientController.java` in the `controller` package:

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

### Task 3.4 - Create a stub management controller

The exercises need a way to change stub behaviour at runtime to simulate an upstream recovering or going down. Create `StubManagementController.java` in the `controller` package:

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

    // Simulates the /api/flaky upstream recovering and returning 200
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

    // Simulates the /api/flaky upstream going back down
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

### Task 3.5 - Observe the resilience stack in action

Add requests to `web-client-requests.http`:

```http
### Resilient endpoint - the /api/flaky stub currently returns 503.
### Watch the application console: you will see retry attempts before the fallback fires.
GET http://localhost:8080/resilient/employees

###

### Make the flaky stub return 200 (simulates upstream recovery)
POST http://localhost:8080/stubs/flaky/recover

###

### Make the flaky stub return 503 again (simulates upstream going down)
POST http://localhost:8080/stubs/flaky/fail
```

Run `GET /resilient/employees`. Because the `/api/flaky` stub always returns 503, the retry will exhaust all three attempts and the circuit breaker will invoke the fallback. You should see three log lines in the console:

```
INFO  Calling upstream /api/flaky
INFO  Calling upstream /api/flaky
INFO  Calling upstream /api/flaky
WARN  Fallback invoked: 503 Service Unavailable
```

The HTTP response to your client should be `200 OK` with an empty array `[]`. The fallback produced a degraded response rather than propagating the failure to the caller.

Now run `POST /stubs/flaky/recover`, then run `GET /resilient/employees` again. The response should include employees and no fallback warning should appear in the log. Run `POST /stubs/flaky/fail` to restore the 503 behaviour.

### Checkpoints

1. The lecture described the thundering herd problem. With 100 clients all retrying at the same interval after a shared failure, what happens when the upstream recovers? How does jitter address this?
2. The retry configuration lists `ServiceUnavailable` (503) as a retryable exception but does not include `WebClientResponseException.NotFound` (404). Explain the reason for this distinction.
3. The circuit breaker's `wait-duration-in-open-state` is set to 30 seconds. During those 30 seconds, what happens when a request arrives? Does it go to the upstream, to the fallback, or is an exception thrown?

### Extension Task

Add a `@TimeLimiter` from Resilience4j to `fetchAll()` with a timeout of 1 second. Add a new endpoint to `StubManagementController` that updates the `/api/flaky` stub to return 200 after a 3-second delay (using `.withFixedDelay(3000)`). Call `POST /stubs/flaky/recover-slow` and then `GET /resilient/employees`. Confirm that the time limiter fires before the HTTP engine timeout and that the fallback handles the `TimeoutException`.

---

## Exercise 4 - Bearer Token Attachment via WebClient Filter

**Estimated time:** 30-40 minutes
**Topics covered:** bearer tokens, `ExchangeFilterFunction`, token attachment via filter vs inline, secure configuration, proactive token expiry checks

### Context

A bearer token is a credential. Anyone bearing the token can use it - there is no binding to a specific client identity. This means mishandling the token is a security failure: logging its full value, sending it over plain HTTP, or scattering token retrieval logic across call sites all create vulnerabilities.

The lecture described the correct pattern: attach tokens in a `WebClient` filter, not at call sites. The filter is a single, auditable, independently testable place where all token handling lives. Call sites express only business intent.

This exercise simulates a service-to-service call where your application must acquire a token and attach it to every outbound request. You will work with a static token for now (the OAuth2 automated flow is covered in Module 3). The focus here is on the filter mechanics and the secure configuration practices.

### Task 4.1 - Confirm the token property is in application.yml

The `upstream.api.token` property should already be present in `application.yml` from Task 3.2. Confirm the following block is there:

```yaml
upstream:
  api:
    token: "exercise-static-bearer-token"
    base-url: "http://localhost:8081"
```

> **The baseline rule from the lecture:** secrets never appear in source code. In production, the token value would be injected from an environment variable using `${UPSTREAM_API_TOKEN}` as the value in `application.yml`, or sourced from a secrets manager such as AWS Secrets Manager, Azure Key Vault, or HashiCorp Vault. The `application.yml` file itself would contain only the reference, not the value.

### Task 4.2 - Create a token provider

Create `StaticTokenProvider.java` in the `config` package:

```java
package com.example.api_client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the bearer token for outbound API calls.
 *
 * In production this would contact an OAuth2 token endpoint,
 * cache the token, and refresh it before expiry.
 * For this exercise it returns a static value injected from configuration.
 */
@Component
public class StaticTokenProvider {

    private final String token;

    // TODO 17: Inject the value of upstream.api.token using @Value.
    // @Value reads from application.yml using Spring's ${...} syntax.
    // Assign the injected value to the token field.
    public StaticTokenProvider(@Value("${upstream.api.token}") String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
```

### Task 4.3 - Create a bearer token filter

Create `BearerTokenFilter.java` in the `config` package:

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

    // TODO 18: Implement filter(), returning an ExchangeFilterFunction.
    //
    // Use ExchangeFilterFunction.ofRequestProcessor(request -> { ... })
    // Inside the lambda:
    //   1. Call tokenProvider.getToken() to retrieve the token value.
    //   2. Log that a token is being attached, but log only the first 8 characters
    //      followed by "..." to avoid exposing the full token value in logs.
    //      Example: log.debug("Attaching token: {}...", token.substring(0, 8))
    //   3. Build a new request with the Authorization header:
    //        ClientRequest.from(request)
    //            .header("Authorization", "Bearer " + token)
    //            .build()
    //   4. Wrap the result in Mono.just() and return it.
    //
    // Never log the full token value. This is a security requirement, not a style choice.
    public ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            return Mono.just(request); // Replace with your implementation
        });
    }
}
```

> **Why log only the first 8 characters?** The lecture stated: never log the full token value. A full bearer token in a log file is a credential leak. An attacker with log access gains the same permissions as the token holder. The first 8 characters are enough to identify which token is in use for debugging purposes without exposing the credential itself.

### Task 4.4 - Register the filter on a new WebClient bean

Add a second `WebClient` bean to `WebClientConfig.java` that includes the bearer token filter. The complete updated `WebClientConfig` should look like this:

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

    // TODO 19: Complete this bean by applying the bearer token filter.
    // The BearerTokenFilter is already constructed below.
    // Add .filter(bearerTokenFilter.filter()) to the builder before .build().
    @Bean
    public WebClient authenticatedWebClient(StaticTokenProvider tokenProvider) {
        var bearerTokenFilter = new BearerTokenFilter(tokenProvider);
        return WebClient.builder()
                .baseUrl("http://localhost:8081")
                .build(); // Replace with your implementation
    }
}
```

> **Why `@Primary` on `employeeWebClient`?** With two `WebClient` beans in the context, Spring does not know which one to inject when a class declares `WebClient` without a `@Qualifier`. `@Primary` designates `employeeWebClient` as the default. The `authenticatedWebClient` is only injected where `@Qualifier("authenticatedWebClient")` is explicitly specified.

### Task 4.5 - Call the protected endpoint

Create `AuthenticatedEmployeeService.java` in the `service` package:

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

    // TODO 20: Implement ping() using the authenticatedWebClient.
    // Call GET "/api/auth-required".
    // Return the response body as a String.
    // Chain: .get().uri("/api/auth-required").retrieve().bodyToMono(String.class).block()
    public String ping() {
        return null; // Replace with your implementation
    }
}
```

Create `AuthController.java` in the `controller` package:

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

Add a request to `web-client-requests.http`:

```http
### Should return: {"message":"Authenticated successfully"}
GET http://localhost:8080/auth/ping
```

Run the request. You should receive `{"message":"Authenticated successfully"}`.

To confirm the filter is doing the work, temporarily comment out the `.header(...)` line inside `BearerTokenFilter.filter()` so it returns `Mono.just(request)` unchanged. Run the request again - you should receive a 401. This confirms the stub requires the `Authorization` header and the filter is what provides it. Restore the filter before continuing.

### Task 4.6 - Reflect on filter ordering

The lecture stated that filters execute in registration order and recommended: correlation ID first, token attachment before logging, error-handling filters last.

Add a second filter to `authenticatedWebClient` that logs the HTTP method and URI of every request. Register it after the bearer token filter using a second `.filter(...)` call.

Run `GET /auth/ping` again and examine the Run console. You should see:

1. The bearer token filter log line (token being attached)
2. The logging filter log line (method and URI)

Swap the registration order and run again. Both log lines still appear, but note that if the logging filter ran first it would record the request before the token was attached. A log entry without an `Authorization` header present could be misread as meaning the call was unauthenticated. Restore the original order when you are done.

### Checkpoints

1. The lecture listed three golden rules for bearer tokens. State all three from memory, then verify against the lecture notes.
2. Your `StaticTokenProvider` returns the same token on every call. In a real production system using OAuth2 Client Credentials, what would `getToken()` need to do differently to avoid sending an expired token to the upstream?
3. The filter uses `ClientRequest.from(request).header(...).build()`. Why create a new request object rather than mutating the existing one?

### Extension Task

Extend `BearerTokenFilter` to simulate proactive expiry checking. Add an `Instant tokenExpiry` field and initialize it to `Instant.now().plusSeconds(30)` in the constructor. In `filter()`, check whether `Instant.now().isAfter(tokenExpiry.minusSeconds(30))` before attaching the token. If the token is close to expiry, log a warning and reset `tokenExpiry` to `Instant.now().plusSeconds(300)` to simulate a refresh. This mirrors the proactive approach described in the lecture without requiring a real token endpoint.

---

## Summary and Reflection

After completing all four exercises you have worked through the core lifecycle of secure external API consumption: making synchronous calls with `RestTemplate`, building fluent non-blocking pipelines with `WebClient`, applying resilience patterns to protect your service from upstream failures, and attaching bearer tokens securely through the filter chain.

| Exercise | Concept | Key Insight |
|---|---|---|
| 1 - RestTemplate | `getForObject`, `getForEntity`, `exchange`, interceptors | `RestTemplate` is still widely used; interceptors are the correct place for cross-cutting concerns |
| 2 - WebClient | Fluent API, `Mono`, `Flux`, domain exception mapping | WebClient is a pipeline, not a function call; nothing executes until subscribed |
| 3 - Resilience | Retry, backoff, jitter, circuit breaker, fallback | Ordering matters: circuit breaker wraps retry; jitter prevents thundering herd |
| 4 - Bearer Tokens | `ExchangeFilterFunction`, secure config, filter ordering | Tokens are credentials; attach them in a filter, never at call sites, never log the full value |

### Final Reflection Questions

Take 10 minutes to answer these before your next session:

1. The lecture's production checklist includes "safe logging filter - no Authorization header values logged." You implemented partial token logging (first 8 characters only) in Exercise 4. Describe how you would extend the logging filter to redact the `Authorization` header from request logs entirely, while still logging a flag that indicates whether the header was present.

2. In Exercise 3, the circuit breaker fallback returns an empty list. In a real banking application, describe three different fallback strategies for a circuit-broken call to a customer account balance service. Consider the trade-offs of each: what does the user experience, what is the business risk, and when would each be appropriate?

3. Exercise 4 used a static token for simplicity. Spring Security's `OAuth2AuthorizedClientManager` (covered in Module 3) automates token acquisition, caching, and refresh. Looking at the filter mechanics you built manually in this exercise, identify which parts of your `BearerTokenFilter` would be replaced by the Spring Security integration, and which parts (such as logging and connection management) would remain your responsibility.

---

*End of Module 4 Exercises*
