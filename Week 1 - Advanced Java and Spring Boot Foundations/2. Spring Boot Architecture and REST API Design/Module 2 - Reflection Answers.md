# Module 2: Spring Boot Architecture & REST API Design
## Checkpoint & Reflection Answers

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 2 - Spring Boot Architecture & REST API Design

> **How to use this document:** Compare your answers after completing each exercise's checkpoints. The goal is not to match the wording exactly but to verify that the core reasoning is the same. If your answer reaches the same conclusion by a different route, that is a good answer. If your answer and the one below reach different conclusions, re-read the relevant section of the lecture slides before moving on.

---

## Exercise 1 – The Layered Architecture in Practice

**Checkpoint 1:** The controller imports nothing from `java.sql` and the service imports nothing from `org.springframework.web`. Why is maintaining this separation important as the application grows?

> Each layer can change without affecting the others. If the data access technology changes from an in-memory map to a database, only the service layer needs to change. The controller is unaffected because it never imported anything related to data storage. If the HTTP framework changes (for example, adding a GraphQL endpoint alongside the REST API), the service is unaffected because it never knew HTTP existed. The separation is the mechanism that makes the application maintainable at scale. A controller that directly queries a database, or a service that constructs `ResponseEntity` objects, has two reasons to change instead of one. In a large codebase, classes with multiple reasons to change are the primary source of regression bugs.

---

**Checkpoint 2:** Both `@Service` and `@RestController` are specializations of `@Component`. What does this annotation tell the IoC container to do at startup?

> `@Component` (and its specializations) tells the IoC container to register the class as a bean definition during the classpath scan at startup. The container then instantiates the class, resolves its constructor dependencies by looking them up in the bean registry, injects them, and stores the live instance in the registry so that any other class that depends on it can access it. `@Service` and `@RestController` are semantically specialized versions of `@Component` in that they share the same registration behavior but communicate the class's different roles to developers and the framework. `@RestController` additionally activates Spring MVC's request mapping machinery, which `@Service` does not.

---

**Checkpoint 3:** The controller receives a `UriComponentsBuilder` parameter but does not declare it as a Spring dependency. How does it get there?

> Spring MVC injects it automatically. When the `DispatcherServlet` routes an incoming request to a controller method, it inspects every parameter of that method and resolves each one from a set of registered `HandlerMethodArgumentResolver` implementations. `UriComponentsBuilder` has a dedicated resolver that constructs an instance from the current request's scheme, host, port, and context path. It is not a Spring bean and does not appear in the IoC container registry. It is a request-scoped object constructed fresh for each invocation by the argument resolution mechanism. The developer declares the need for it by adding it as a parameter; Spring MVC supplies it transparently.

---

**Checkpoint 4:** Why does the POST endpoint return `201 Created` rather than `200 OK`? What does the `Location` header communicate to the caller?

> `201 Created` has a precise meaning defined in RFC 9110: the request succeeded, and a new resource was created as a result. `200 OK` means the request succeeded but carries no implication about resource creation. Returning `200` for a creation operation is technically inaccurate and removes a useful signal because any infrastructure layer or client that distinguishes between these codes (for caching, auditing, or idempotency tracking) receives wrong information.
>
> The `Location` header carries the URI of the newly created resource. Without it, the client that just created a product has no way to retrieve, update, or delete it without making a second request to search for it. The `Location` header gives the client the address of the newly created resource in the same response, eliminating the round-trip. This is part of the `201` contract: a `201` response without a `Location` header is incomplete.

---

## Exercise 2 – Configuration, Profiles, and the Environment Abstraction


**Checkpoint 1:** The `application-dev.yml` file only declares two properties. What happens to the third (`page-size-max`)? Where does its value come from at runtime?

> Spring Boot's configuration model is additive and overlay-based. The base `application.yml` is always loaded first. When a profile is active, the corresponding profile-specific file is loaded on top of it. Properties declared in the profile file override their counterparts in the base file. Properties not declared in the profile file are left unchanged and retain their values from the base file. So `page-size-max` is not absent at runtime; it is present with its base value of `100` because `application-dev.yml` simply did not override it. The profile file does not need to redeclare properties it does not intend to change.

---

**Checkpoint 2:** Why is `@ConfigurationProperties` preferred over `@Value` for a group of related properties?

> `@Value` injects a single property into a single field. For fifteen related properties, that means fifteen `@Value` annotations scattered across one or more classes, no central place to see the complete configuration surface of the application, no IDE autocompletion for property names, and no type-safe validation at startup. `@ConfigurationProperties` binds the entire group to a single typed class. The property names are validated against the class's fields at startup, so that a typo in `application.yml` results in a startup failure rather than a silent `null` at runtime. The class is a navigable, testable Java object. The IDE provides autocompletion for property names in the YAML file when the Spring Boot configuration processor annotation processor is on the classpath. As the number of properties grows, the difference in maintainability becomes significant.

---

**Checkpoint 3:** In a containerized deployment, how would you activate the `prod` profile without modifying any file inside the JAR?

> Set the `SPRING_PROFILES_ACTIVE` environment variable in the container's environment to `prod`. Spring Boot maps environment variables to property names by converting underscores to dots and lowercasing, so `SPRING_PROFILES_ACTIVE=prod` is equivalent to setting `spring.profiles.active=prod`. The container platform (Kubernetes, ECS, Docker Compose) sets this variable when launching the container. The JAR file itself is unchanged. The same artifact that ran in development runs in production, with the environment controlling which profile is active. This is the production-standard approach: the running environment configures itself, the application does not need to know where it is deployed.

---

**Checkpoint 4:** The `environmentLabel` property has a default value set directly on the field in `CatalogueProperties`. When would that default actually be used?

> The field default (`"unknown"`) is used when the `catalogue.environment-label` property is absent from every configuration source, like the base `application.yml`, any active profile file, environment variables, and command-line arguments. In the exercises, this situation does not arise because the base `application.yml` always declares the property. In practice, field defaults in `@ConfigurationProperties` classes serve as a safety net for optional properties that a deployment may legitimately omit, and as self-documenting fallback values that make the class readable without consulting external configuration files.


---

## Exercise 3 – Resource-Oriented URL Design and HTTP Method Semantics

**Checkpoint 1:** Why does the path start with `/products/{productId}/reviews` rather than `/reviews?productId=P001`?

The hierarchical path expresses a domain-ownership relationship: reviews belong to a product, and their existence depends on the product's existence. A GET to `/products/INVALID/reviews` correctly returns `404` because the parent does not exist — the collection itself has no meaning without the parent. A query parameter like `?productId=P001` treats the product relationship as a filter on an independent resource, which is semantically wrong because reviews are not an independent resource that happens to be associated with a product by a filter. The hierarchy also has infrastructure benefits: a CDN or API gateway can apply caching and routing rules to the `/products/{id}/reviews` path pattern as a whole, understanding that it represents a product's sub-collection.

---

**Checkpoint 2:** The `POST /reviews` endpoint ignores the `productId` in the request body and uses the path variable instead. Why?

> The URL is the authoritative identity of the resource. The client is posting to `/products/P001/reviews`, which unambiguously states that the review belongs to product P001. If the client also sends `"productId": "P002"` in the body, there is an identity conflict between the two sources. Trusting the body over the URL would allow a client to create a review for one product while posting to a different product's URL, resulting in a data integrity violation. The path variable is the server-controlled, canonical identity. The body carries the review's content (author, rating, comment). Mixing identity and content in the body, and then trusting the body for identity, is a design mistake that creates inconsistency and makes the API harder to reason about.

---

**Checkpoint 3:** `GET`, `PUT`, and `DELETE` are idempotent. `POST` is not. What does this mean for retry logic when a network timeout occurs?

> When a network call times out, the client cannot know whether the request reached the server and was processed, or never arrived. For an idempotent method, this ambiguity does not matter because retrying produces the same server state as not retrying. A GET returns the same data. A DELETE of an already-deleted resource returns `404`, which is the same end state. A PUT replaces the resource with the same content it already has.
>
> For a non-idempotent POST, the ambiguity is critical. If the server processed the first request and the client retries, the operation executes twice. In this exercise, that means two identical reviews are created. In a payment system, it means two payments are processed. A client that calls a POST endpoint and receives a timeout must not retry automatically — it must either accept the uncertainty, check the server state before retrying, or use the idempotency-key pattern to make the POST safe to retry.

---

**Checkpoint 4:** A colleague suggests a `/api/v1/products/search` endpoint that accepts a JSON body with filter criteria. What REST constraint does this violate?

> It violates the uniform interface constraint, specifically the requirement that GET is safe and stateless. Search criteria belong in the URL as query parameters, not in the request body. A GET request with a body is technically permitted by HTTP, but widely unsupported: many proxies, load balancers, and caching layers strip or ignore the body of a GET request. The operation becomes uncacheable because the cache key is the URL, and two requests to the same URL with different bodies look identical to any caching layer.
>
> The correct design is `GET /api/v1/products?name=laptop&category=ELECTRONICS&maxPrice=1500`. Query parameters are part of the URL, are fully cacheable, appear in access logs, work in every HTTP client without special handling, and correctly express that they are shaping the representation of the `/products` collection rather than defining a new resource.

---

**Task 3.5 – Idempotency Reasoning**

- `GET /api/v1/products/P001/reviews` sent twice → **Idempotent.** Both calls return the same collection. No server state changes.
- `POST /api/v1/products/P001/reviews` sent twice with identical body → **Not idempotent.** The first call creates one review. The second call creates a second, distinct review with a different ID, even if the content is identical. The server state after two calls differs from the server state after one.
- `DELETE /api/v1/products/P001` sent twice → **Idempotent by outcome, but the response differs.** The first call removes the product and returns `204`. The second call finds nothing to remove and returns `404`. The server state is the same after both calls (the product does not exist), but the HTTP response code differs. RFC 9110 defines idempotency in terms of server state, not response code — so `DELETE` is correctly classified as idempotent even though the second call returns a different status.

---

## Exercise 4 – HTTP Status Code Discipline and the Error Contract

**Checkpoint 1:** What made it possible to simplify `getById` from `ResponseEntity<Product>` with inline error handling to a plain `Product` return?

> `GlobalExceptionHandler`. Before Exercise 4, the controller had to handle the not-found case itself because there was nothing else to catch it. If the service returned `Optional.empty()`, the controller had to translate that into a `404` response inline. After Exercise 4, `ProductService.findById` throws `ResourceNotFoundException` when the product is not found, and `GlobalExceptionHandler` intercepts that exception and produces the `404` response. The controller never sees the error case at all. It calls the service, receives a valid `Product` if one exists, and returns it. The happy path and the error path are now handled in completely separate classes, each with a single responsibility.

---

**Checkpoint 2:** The catch-all `Exception` handler omits the exception message from the response body. Why? What risk does including it create?

> Exception messages frequently contain internal implementation details: database error messages with table and column names, SQL fragments, internal class names and package paths, file system paths, connection strings, or stack frame information. Each of these is reconnaissance data for an attacker. A `NullPointerException` message that includes a class name tells an attacker which class failed. A database error that includes a table name tells an attacker something about the data model. A connection refused message that includes a hostname or port reveals internal network topology.
>
> The full exception is logged server-side, associated with a trace ID, and available to the engineering team. The client receives the trace ID so they can file a support request that can be correlated with the internal log. This gives developers everything they need to diagnose the problem while giving an attacker nothing useful.

---

**Checkpoint 3:** What is the semantic difference between `400 Bad Request` and `422 Unprocessable Entity`?

> `400 Bad Request` means the server cannot understand the request at all. Either the syntax is malformed, a required field is missing, a field has the wrong type, or the JSON cannot be parsed. The problem is at the structural level: the request is not a valid message. The client needs to fix the request format.
>
> `422 Unprocessable Entity` means the server understood the request perfectly. It is syntactically valid and structurally correct, but the content violates a business rule or domain constraint. The data makes sense as a message, but the domain rejects it. Examples: creating a product in a non-existent category, assigning a review rating outside the 1–5 range, or submitting a date range where the end date is before the start date.
>
> The distinction matters to the client because the remediation is different. A `400` tells the client to fix the structure of the request. A `422` tells the client the structure is fine, but the content violates a rule, and the client may need to show a domain-level error to the user rather than a generic formatting error.

---

**Checkpoint 4:** Why is the machine-readable `errorCode` field necessary when the human-readable `message` field already describes the problem?

> The `message` field is for humans and intended to be displayed in a UI or read by a developer. It can change between releases (rewording for clarity, localization, tone), and client code must never switch on its value. The `errorCode` field is for machines because it is a stable, versioned string that client code can switch on to implement specific error-handling behavior. A client that handles `PRODUCT_NOT_FOUND` differently from `CATEGORY_NOT_FOUND` cannot do so reliably by parsing the `message` string. If the message text changes in a future release, the client's string matching breaks silently. The `errorCode` is a contract: once published, it changes only with a version increment, and clients can depend on it with the same stability expectations as a field in the success response.

---

## Exercise 5 – API Versioning Strategy

**Checkpoint 1:** Why is the v2 controller a new class rather than a modified v1 controller with version-branching logic?

> A controller with version-branching logic inside its methods (`if (version == 2) { ... } else { ... }`) has multiple reasons to change: any v1 requirement, any v2 requirement, and any logic that determines which branch to execute. As more versions accumulate, the branching grows in complexity, the methods become harder to read and test, and a change to v2 behavior risks accidentally affecting v1 behavior through shared code paths.
>
> A separate v2 controller class has exactly one reason to change: a v2 requirement. The v1 controller is frozen. Its behavior cannot be accidentally broken by v2 changes because there is no shared code. Each version is an independent, testable unit. The version is expressed in the URL (an address), not in a conditional statement (a runtime decision).

---

**Checkpoint 2:** The `Sunset` header communicates a specific date. What obligation does setting this header create?

> Setting the `Sunset` header is a public commitment that the endpoint will stop functioning on that date. Once published to clients, that date is part of the API contract. If the team removes the endpoint before the sunset date, clients who have not yet migrated will be broken earlier than they were told to expect. If the team extends the endpoint past the sunset date without updating the header, the header becomes misleading and erodes client trust in the API's lifecycle signals. The obligation is to honor the announced date. This means the sunset date must be realistic when it is set, must give clients sufficient time to migrate (three to six months for internal APIs, twelve months for external ones), and must be formally updated if circumstances require a change.

---

**Checkpoint 3:** Under what condition does adding a new field to a response become a breaking change?

> Adding a new field is breaking when any existing client uses strict deserialization that rejects unknown fields — that is, the client's deserializer throws an error if it encounters a field it does not recognize. This is an incorrect client implementation (well-written clients are designed to ignore unknown fields to support forward compatibility), but it does occur in practice and must be considered before deploying. The governance principle is to treat the breaking-change question as a consumer-impact question: ask which clients call the endpoint and whether any of them would behave incorrectly after the change. If the answer is yes for any client, the change is breaking regardless of its theoretical classification. The implication for client authors is that JSON deserializers should always be configured to ignore unknown fields; in Jackson, this is `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`, which is the default in Spring Boot.

---

**Checkpoint 4:** Why must the error code contract be versioned alongside the success response contract?

> Clients that handle specific error codes programmatically depend on those codes with the same stability expectation as any field in the success response. A client that catches `PRODUCT_NOT_FOUND` and shows a specific message to the user will silently break if that code is renamed to `RESOURCE_NOT_FOUND` in a patch release, then the error is no longer specifically handled, falls through to a generic handler, and the user receives a less informative experience or an incorrect one. The error contract is as much a part of the versioned API surface as the success response schema. When a v2 endpoint is introduced with different error codes, both the v1 and v2 error code sets must remain stable for their respective versions until the version is retired.

---

**Task 5.4 – The Version Increment Decision**

| Proposed change | Breaking? | Reasoning |
|---|---|---|
| Add an optional `description` field to the v2 response | **No** (with caveat) | Well-written clients ignore unknown fields. Breaking only if any consumer uses strict deserialisation that rejects unknowns — verify before deploying. |
| Remove the `price` field from the v2 response | **Yes** | Any client that reads `price` will break. Removing a field is always breaking regardless of whether clients appear to use it. |
| Change `price` from `double` to `String` in the v2 response | **Yes** | A type change breaks any client that assigns the field to a numeric variable. This is one of the most reliably breaking changes possible. |
| Make the `name` query parameter required instead of optional | **Yes** | Any client that calls the endpoint without `name` currently receives a valid response. After the change it receives a `400`. Existing clients break without any change on their side. |
| Add a new endpoint `GET /api/v2/products/featured` | **No** | Adding a new endpoint does not affect any existing endpoint. Clients that do not call the new endpoint are unaffected. |
| Rename error code `PRODUCT_NOT_FOUND` to `RESOURCE_NOT_FOUND` | **Yes** | Any client that switches on `PRODUCT_NOT_FOUND` will no longer match. The error falls through to a generic handler. The error contract is part of the versioned API surface. |

---

## Final Reflection Questions

**Question 1:** What production problems does centralised `@ControllerAdvice` error handling prevent that inline handling does not?

> **Consistency across endpoints.** With inline handling, every developer who writes a new endpoint must independently produce a correctly formatted error response. Over time, subtle inconsistencies accumulate: one endpoint returns `{"error": "not found"}`, another returns `{"message": "Product not found", "code": 404}`, and a third returns a plain string. Clients cannot deserialize errors reliably. With `@ControllerAdvice`, the format is defined once and applied uniformly to every endpoint automatically.
>
> **Correct status codes without repetition.** Inline handling requires every controller method to know which exception maps to which status code. That mapping logic is duplicated across dozens of methods. If the mapping needs to change (for example, a decision to return `422` instead of `400` for a class of business errors), every method must be updated. With centralized handling, the mapping is defined and changed in one place.
>
> **Clean controller methods.** A controller method with inline error handling contains `try/catch` blocks, `ResponseEntity` construction for error cases, and status code decisions mixed with business logic. It has multiple reasons to change, and is harder to read and test. With `@ControllerAdvice`, a controller method expresses only the happy path. It is shorter, has one responsibility, and is easier to test because the error path is tested separately in the handler.
>
> **Unhandled exception safety.** The catch-all `Exception` handler ensures that any unexpected exception produces a safe, structured `500` response rather than Spring Boot's default error page, which may contain stack traces. Without a centralized handler, an unhandled exception anywhere in the application could leak internal details to the client.

---

**Question 2:** When the `ConcurrentHashMap` is replaced with a database, which classes change and which do not?

> Only `ProductService` and `ReviewService` change. The in-memory map, the `AtomicInteger` counter, and the manual ID-generation logic are replaced with calls to the repository. ID generation moves to the database (via a sequence or auto-increment column).
>
> `ProductController`, `ReviewController`, `ProductControllerV2`, `GlobalExceptionHandler`, `ApiError`, `CatalogueProperties`, and all model classes remain completely unchanged. They have no knowledge of how data is stored.
>
> This is the layered architecture working as designed. The controller's contract with the service is expressed through method signatures (`findById`, `save`, `delete`). The service can fulfil those signatures using a map, a database, a remote API, or a cache without the controller knowing or caring. Replacing the data access mechanism is a change that is contained entirely within the service layer. In a codebase with hundreds of classes, this containment is the difference between a safe, testable migration and a change that touches files across the entire application.

---

**Question 3:** What would you add to the API contract to allow clients to retry a POST safely?

> The idempotency-key pattern. The API contract would require clients to generate a unique key (UUID v4 is standard) before the first attempt and send it with every retry of the same logical operation in a request header; conventionally `Idempotency-Key: <uuid>`.
>
On the server side, `ProductService.save` would check whether the key has already been seen. If it has, it returns the stored result of the first execution without creating a new product. If it has not, it creates the product, stores the result associated with the key, and returns the new product.
>
> The key must be generated by the client before the first attempt, not by the server, and not freshly on each retry. A new key on each retry defeats the purpose entirely, since each retry appears as a new operation to the server. The server's key store must have a defined retention window long enough to cover any realistic retry interval. The client receives the same `201 Created` response and the same `Location` header on a duplicate request as on the original. The duplicate is not an error; it is a correctly handled retry.
---

*End of Module 2 Reflection Answers*
