# Module 2: Spring Boot Architecture and REST API Design
## Hands-On Exercises

> **Course:** MD282 - Java Full-Stack Development
> **Module:** 2 - Spring Boot Architecture & REST API Design
> **Estimated time per exercise:** 30–60 minutes

---

## How to Use These Exercises

Each exercise is self-contained and builds directly on the concepts introduced in the module lectures. They are designed to be completed in IntelliJ IDEA using a Spring Boot project generated from Spring Initializr.

- **Context**:  why this concept matters and what problem it solves
- **Setup**:  how to structure the project and files before you start
- **Tasks**:  step-by-step work to complete, including starter code with `TODO` markers
- **Checkpoints**: questions that test conceptual understanding, not just code completion
- **Extension tasks**: optional challenges for faster learners

> **Note** - The extension exercises are not required to complete the module, but they provide an opportunity to deepen your understanding and practice applying the concepts in a more complex scenario. They are designed to be more challenging and may require additional research or experimentation.
> 
> Suggested solution to the extension exercises will be provided at the end of the module to remove the temptation to cut and paste the solution before trying it yourself.`
> 

> **Tip:** Work through the exercises in order. Each one builds on the project structure established in the first. If you get stuck on a `TODO`, re-read the relevant section of the lecture slides before looking at the solution file.

---

## Starter Project

All exercises in this module share a single Spring Boot project. Create it once and use it throughout.

### Create the project with Spring Initializr

1. Open [https://start.spring.io](https://start.spring.io) in a browser
2. Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 3.2.x (latest stable) |
| Group | `com.example` |
| Artifact | `catalogue` |
| Packaging | Jar |
| Java | 17 |

3. Add the following dependencies using the search box on the right:
   - **Spring Web**: the Spring MVC framework and embedded Tomcat server
   - **Spring Boot DevTools**: automatic restart on file changes during development
   - **Validation**: the Bean Validation (JSR-380) integration

4. Click **Generate**, unzip the downloaded archive, and open the folder in IntelliJ IDEA using **File → Open**

5. When IntelliJ finishes indexing, open the Maven tool window (View → Tool Windows → Maven) and confirm there are no download errors

### Verify the project starts

Open `CatalogueApplication.java` in `src/main/java/com/example/catalogue` and run it (click the green play button in the gutter, or press `Shift+F10`). You should see output ending with:

```
Started CatalogueApplication in X.XXX seconds
```

If Tomcat starts on port 8080 you are ready to begin.

### Package structure to create before Exercise 1

Right-click `src/main/java/com/example/catalogue` in the Project tool window and create the following sub-packages now. Creating them up front avoids context-switching later:

```
com.example.catalogue
├── controller
├── service
├── model
└── exception
```

---

## Exercise 1 – The Layered Architecture in Practice

**Estimated time:** 30–40 minutes
**Topics covered:** Layered architecture, stereotype annotations (`@RestController`, `@Service`), constructor injection, the role of each layer

### Context

Spring Boot's layered architecture is the mechanism by which the framework separates three fundamentally different concerns: HTTP communication, business logic, and data access. Each layer has a single, well-defined responsibility, and the annotations that mark each class tell Spring's IoC container exactly which role that class plays.

The architecture you will build in this exercise is the same structure used throughout the rest of this course and in production Spring Boot services. Understanding *why* each class lives in its own layer and what happens when that boundary is violated is more important than the mechanics of writing the code itself.

The key principle: controllers know about HTTP, services know about business logic, and neither knows about the internals of the other. The controller calls the service; the service does not call the controller. Dependencies flow in one direction only.

### Task 1.1 – The Model

Create a `Product` record in the `model` package. This is a Java record. It generates its constructor, accessors, `equals`, `hashCode`, and `toString` automatically.

```java
package com.example.catalogue.model;

// A record is Java's concise immutable data carrier.
// The parenthesized list is both the field declaration and the constructor.
// Accessors use the field name directly: product.id(), product.name() -- no "get" prefix.
public record Product(
        String id,
        String name,
        String category,
        double price
) {}
```

### Task 1.2 – The Service Layer

Create `ProductService.java` in the `service` package. The service owns the business logic and the in-memory data store. Notice that it has no awareness of HTTP. It cannot see `@RequestMapping`, or `HttpServletRequest`, or `ResponseEntity`.

```java
package com.example.catalogue.service;

import com.example.catalogue.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// @Service marks this class as a Spring-managed bean in the service layer.
// It is functionally identical to @Component but communicates intent:
// this class contains business logic, not HTTP handling or data access.
@Service
public class ProductService {

    // In-memory store keyed by product ID.
    // ConcurrentHashMap is used here because Tomcat is multithreaded and may
    // handle concurrent requests. This is not a concern you would manage manually
    // with a real database.
    private final Map<String, Product> store = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    public ProductService() {
        // Seed with sample data so the GET endpoints return something immediately
        save(new Product(null, "Laptop Pro", "ELECTRONICS", 1299.99));
        save(new Product(null, "Wireless Mouse", "ACCESSORIES", 49.99));
        save(new Product(null, "Standing Desk", "FURNITURE", 649.00));
    }

    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Product save(Product product) {
        // TODO 1: Generate an ID if the product does not already have one.
        // Use the counter field: "P" + counter.getAndIncrement() formatted with String.format
        // to produce IDs like P001, P002, ...
        // Then store the product in the map using the ID as the key.
        // Return the stored product (the one with the generated ID, not the input).
        String id = product.id() != null ? product.id() :
                String.format("P%03d", counter.getAndIncrement());
        Product toStore = new Product(id, product.name(), product.category(), product.price());
        store.put(id, toStore);
        return toStore;
    }

    public boolean delete(String id) {
        // TODO 2: Remove the product from the store.
        // Return true if the product existed and was removed, false if it was not found.
        return store.remove(id) != null;
    }
}
```

### Task 1.3 – The Controller Layer

Create `ProductController.java` in the `controller` package. This class is the HTTP boundary of the application. Its only job is to translate HTTP into service calls and service results back into HTTP.

Read the comments carefully because they explain the design decisions, not just the syntax.

```java
package com.example.catalogue.controller;

import com.example.catalogue.model.Product;
import com.example.catalogue.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

// @RestController combines @Controller and @ResponseBody.
// Every method return value is serialized directly to JSON.
// Without @ResponseBody, Spring MVC would treat the return value as a view name.
@RestController
// @RequestMapping sets the base path for all endpoints in this class.
// /api/v1 is the version prefix -- it is set at the class level so every
// method inherits it. No version logic goes inside a method body.
@RequestMapping("/api/v1/products")
public class ProductController {

    // Constructor injection is the only correct way to inject dependencies in Spring.
    // Spring instantiates this controller and injects the service at startup.
    // The field is final, which means this object cannot be created without it.
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/v1/products
    // Returns all products. Uses 200 OK implicitly (the default for a non-void return).
    @GetMapping
    public List<Product> getAll() {
        return productService.findAll();
    }

    // GET /api/v1/products/{id}
    // @PathVariable binds the {id} placeholder in the URL to the method parameter.
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable String id) {
        // TODO 3: Call productService.findById(id).
        // If the product is found, return ResponseEntity.ok(product) -- that produces 200.
        // If it is not found, return ResponseEntity.notFound().build() -- that produces 404.
        // Use the Optional's map/orElse pattern, not an if statement.
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/v1/products
    // @RequestBody tells Spring to deserialize the JSON request body into a Product object.
    // UriComponentsBuilder is injected by Spring MVC -- it knows the current request's
    // base URL and lets you construct the Location header without hardcoding a host.
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product,
                                          UriComponentsBuilder ucb) {
        // TODO 4: Call productService.save(product) to persist the new product.
        // Build a Location URI pointing to /api/v1/products/{id} using UriComponentsBuilder:
        //   URI location = ucb.path("/api/v1/products/{id}").buildAndExpand(saved.id()).toUri();
        // Return ResponseEntity.created(location).body(saved)
        // This produces a 201 Created response with the Location header set.
        // 201, not 200 -- 201 signals resource creation and carries the new resource's URI.
        Product saved = productService.save(product);
        URI location = ucb.path("/api/v1/products/{id}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    // DELETE /api/v1/products/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        // TODO 5: Call productService.delete(id).
        // If it returns true (product existed and was deleted), return 204 No Content.
        // If it returns false (product not found), return 404 Not Found.
        // 204 means the operation succeeded but there is no body to return.
        boolean deleted = productService.delete(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
```

### Task 1.4 – Verify with curl or a REST client

Start the application and verify each endpoint. Use curl, the IntelliJ HTTP client (a `.http` file), or any REST tool you prefer.

Create a file `requests.http` in the project root with the following content to use IntelliJ's built-in HTTP client:

```http
### List all products
GET http://localhost:8080/api/v1/products

### Get a single product (replace P001 with an actual ID from the list above)
GET http://localhost:8080/api/v1/products/P001

### Create a product
POST http://localhost:8080/api/v1/products
Content-Type: application/json

{
  "name": "Mechanical Keyboard",
  "category": "ACCESSORIES",
  "price": 129.99
}

### Delete a product
DELETE http://localhost:8080/api/v1/products/P001
```

Click the green play button next to each request in IntelliJ to execute it. Verify:
- The list returns 3 products with generated IDs
- A GET for a valid ID returns `200` with a JSON body
- A GET for a non-existent ID returns `404`
- A POST returns `201` with a `Location` header pointing to the new product's URL
- A DELETE of a valid ID returns `204` with no body
- A DELETE of an invalid ID returns `404`

### Checkpoints

1. The controller imports nothing from `java.sql` and the service imports nothing from `org.springframework.web`. Why is maintaining this separation important as the application grows?
2. The `ProductService` is annotated `@Service` and the `ProductController` is annotated `@RestController`. Both are specializations of `@Component`. What does this annotation tell the IoC container to do at startup?
3. The controller receives a `UriComponentsBuilder` parameter without declaring it as a dependency. How does it get there?
4. Why does the POST endpoint return `201 Created` rather than `200 OK`? What does the `Location` header communicate to the caller?

### Extension Task

Add a `GET /api/v1/products?category=ELECTRONICS` endpoint that filters the product list by category. The query parameter should be optional; if absent, all products are returned. Use `@RequestParam(required = false)` for the parameter and a Stream filter in the service layer.

---

## Exercise 2 – Configuration, Profiles, and the Environment Abstraction

**Estimated time:** 30–45 minutes
**Topics covered:** `application.yml`, typed configuration with `@ConfigurationProperties`, Spring Profiles, environment-specific overrides

### Context

A production Spring Boot service must run in multiple environments: local development, a shared integration environment, and production, each with different configuration values. Hard-coding configuration inside a class is not an option because it forces a recompile and redeploy for every environment change.

A production Spring Boot service must run in multiple environments: local development, a shared integration environment, and production, with different configuration values in each. Hard-coding configuration inside a class is not an option because it forces a recompile and redeploy for every environment change.

Spring Boot solves this with a layered configuration model. The base `application.yml` holds defaults. Profile-specific files (`application-dev.yml`, `application-prod.yml`) overlay those defaults with environment-specific values. Environment variables override both. This hierarchy means that a developer can run the application locally with a development configuration while the same JAR file, with no changes to its code, runs in production with production values.

In this exercise, you will also use `@ConfigurationProperties`, which is the idiomatic Spring approach to reading configuration. It binds a group of related properties to a typed Java class, ensuring configuration values have compile-time safety, are easy to test, and are navigable in the IDE.

### Task 2.1 – Base Configuration

Open `src/main/resources/application.yml`. Replace its contents with:

```yaml
# application.yml -- base configuration, loaded in every environment
spring:
  application:
    name: catalogue-service

server:
  port: 8080

catalogue:
  # Pagination defaults -- these will be read via @ConfigurationProperties
  page-size-default: 20
  page-size-max: 100
  # A label identifying which environment this service is running in
  environment-label: local
```

### Task 2.2 – Profile-Specific Overrides

Create two new files in `src/main/resources`:

**`application-dev.yml`**

```yaml
# Overlays application.yml when the 'dev' profile is active
catalogue:
  environment-label: development
  page-size-default: 5   # smaller pages for easier inspection during development
```

**`application-prod.yml`**

```yaml
# Overlays application.yml when the 'prod' profile is active
catalogue:
  environment-label: production
  page-size-max: 50  # tighter ceiling in production to protect the database
```

### Task 2.3 – Typed Configuration with `@ConfigurationProperties`

Create `CatalogueProperties.java` in a new `config` sub-package:

```java
package com.example.catalogue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// @ConfigurationProperties binds all properties under the "catalogue" prefix
// to the fields of this class at application startup.
// The field names use camelCase in Java; Spring Boot automatically maps
// kebab-case property names (page-size-default) to camelCase (pageSizeDefault).
@ConfigurationProperties(prefix = "catalogue")
public class CatalogueProperties {

    private int pageSizeDefault = 20;  // default value if the property is absent
    private int pageSizeMax = 100;
    private String environmentLabel = "unknown";

    // TODO 6: Generate getters and setters for all three fields.
    // Spring Boot's binding mechanism requires standard JavaBean setters to
    // write the values, and getters for other classes to read them.
    // Use IntelliJ's generator: Alt+Insert (Windows/Linux) or Cmd+N (Mac)
    // → "Getter and Setter" → select all fields.

    public int getPageSizeDefault() { return pageSizeDefault; }
    public void setPageSizeDefault(int pageSizeDefault) { this.pageSizeDefault = pageSizeDefault; }

    public int getPageSizeMax() { return pageSizeMax; }
    public void setPageSizeMax(int pageSizeMax) { this.pageSizeMax = pageSizeMax; }

    public String getEnvironmentLabel() { return environmentLabel; }
    public void setEnvironmentLabel(String environmentLabel) { this.environmentLabel = environmentLabel; }
}
```

### Task 2.4 – Enable and Inject the Properties

Open `CatalogueApplication.java` and add the `@EnableConfigurationProperties` annotation:

```java
package com.example.catalogue;

import com.example.catalogue.config.CatalogueProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// @EnableConfigurationProperties registers CatalogueProperties as a Spring bean
// and tells the binding mechanism to populate it from the environment.
@SpringBootApplication
@EnableConfigurationProperties(CatalogueProperties.class)
public class CatalogueApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogueApplication.class, args);
    }
}
```

Now inject `CatalogueProperties` into the controller and expose the environment label via a diagnostic endpoint:

```java
// Add to ProductController -- new field and constructor parameter

private final CatalogueProperties properties;

// TODO 7: Update the constructor to accept and store CatalogueProperties.
// Spring will inject it automatically because it is a registered bean.
// The constructor should look like:
//
//   public ProductController(ProductService productService,
//                            CatalogueProperties properties) {
//       this.productService = productService;
//       this.properties = properties;
//   }

// Add this new endpoint to ProductController:
@GetMapping("/info")
public java.util.Map<String, Object> info() {
    return java.util.Map.of(
            "environment", properties.getEnvironmentLabel(),
            "defaultPageSize", properties.getPageSizeDefault(),
            "maxPageSize", properties.getPageSizeMax()
    );
}
```

### Task 2.5 – Activate a Profile and Observe the Difference

Run the application twice with different active profiles and compare the `/api/v1/products/info` response each time.

**Without a profile (uses base `application.yml`):**

Run the application normally. Call `GET http://localhost:8080/api/v1/products/info`.

Expected:
```json
{
  "environment": "local",
  "defaultPageSize": 20,
  "maxPageSize": 100
}
```

**With the `dev` profile active:**

In IntelliJ, open **Run → Edit Configurations**, find your Spring Boot run configuration, and add `dev` to the **Active profiles** field. Restart and call the same endpoint.

Expected:
```json
{
  "environment": "development",
  "defaultPageSize": 5,
  "maxPageSize": 100
}
```

Notice that `pageSizeMax` remains `100` from the base file because `application-dev.yml` does not override it. This is the overlay mechanic at work.

### Checkpoints

1. The `application-dev.yml` file only declares two properties. What happens to the third (`page-size-max`)? Where does its value come from at runtime?
2. Why is `@ConfigurationProperties` preferred over injecting individual values with `@Value("${catalogue.page-size-default}")`? Think about what happens when you have 15 related properties.
3. In a containerized deployment, how would you activate the `prod` profile without modifying any file inside the JAR? Which Spring Boot property controls the active profile?
4. The `environmentLabel` property has a default value set directly on the field in `CatalogueProperties`. When would that default actually be used?

### Extension Task

Add a `catalogue.allowed-categories` property that holds a `List<String>` of valid product categories (e.g., `ELECTRONICS`, `ACCESSORIES`, `FURNITURE`). Update `CatalogueProperties` to include a `List<String> allowedCategories` field. Inject it into `ProductService` and validate that a product's category is in the allowed list during `save()`. Throw an `IllegalArgumentException` if it is not. (You will add proper HTTP error mapping for this in Exercise 4.)

---

## Exercise 3 – Resource-Oriented URL Design and HTTP Method Semantics

**Estimated time:** 30–45 minutes
**Topics covered:** Resource-oriented URLs, sub-resources, HTTP method semantics (GET/POST/PUT/PATCH/DELETE), query parameters vs path variables, idempotency

### Context

REST is a set of constraints on how distributed systems communicate. The most important of those constraints, the uniform interface, requires that URLs identify resources and that HTTP methods carry the semantics of the operation being performed. When this separation is maintained, every layer of the infrastructure (caches, load balancers, API gateways, monitoring tools) can reason about requests without reading the body.

The most common REST design mistake is treating the URL as a command. `/createProduct`, `/getProductById`, `/deleteProduct` are all antipatterns. They move intent from the HTTP method (where it belongs) into the URL (where it does not belong). The correct design is `/products` with `GET`, `POST`, `PUT`, or `DELETE` to express what you want to do with that resource.

In this exercise, you will extend the product API with sub-resources (product reviews) and correct use of query parameters, and you will reason about the idempotency properties of the endpoints you build.

### Task 3.1 – Add a Review Sub-Resource Model

Create `Review.java` in the `model` package:

```java
package com.example.catalogue.model;

// Review is a sub-resource: its identity is only meaningful in the context
// of a parent Product. A review without a product has no domain meaning.
// This is the correct signal that a hierarchical URL (/products/{id}/reviews)
// is appropriate here.
public record Review(
        String reviewId,
        String productId,  // the parent's identity is part of the review's state
        String author,
        int rating,        // 1 to 5
        String comment
) {}
```

### Task 3.2 – Add a Review Service

Create `ReviewService.java` in the `service` package:

```java
package com.example.catalogue.service;

import com.example.catalogue.model.Review;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final Map<String, Review> store = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    public ReviewService() {
        // Seed data -- these reviews reference P001 which is seeded in ProductService
        add(new Review(null, "P001", "alice", 5, "Excellent performance"));
        add(new Review(null, "P001", "bob", 4, "Good value for the price"));
        add(new Review(null, "P002", "carol", 3, "Works as expected"));
    }

    public List<Review> findByProductId(String productId) {
        // Convenience method -- delegates to the rated version with no minimum.
        // Keeps any existing callers working without changes.
        return findByProductIdAndRating(productId, 1);
    }

    public Optional<Review> findById(String reviewId) {
        return Optional.ofNullable(store.get(reviewId));
    }

    public Review add(Review review) {
        String id = review.reviewId() != null ? review.reviewId() :
                String.format("R%03d", counter.getAndIncrement());
        Review toStore = new Review(id, review.productId(), review.author(),
                review.rating(), review.comment());
        store.put(id, toStore);
        return toStore;
    }

    // TODO 8: Implement findByProductIdAndRating(String productId, int minRating).
    // Return all reviews for the given product with rating >= minRating.
    // This will be used by the GET /reviews?minRating= endpoint in Task 3.3.
    public List<Review> findByProductIdAndRating(String productId, int minRating) {
        return store.values().stream()
                .filter(r -> r.productId().equals(productId) && r.rating() >= minRating)
                .collect(Collectors.toList());
    }
}
```

### Task 3.3 – A Sub-Resource Controller

Create `ReviewController.java` in the `controller` package. Notice the URL structure: `/api/v1/products/{productId}/reviews`. The `productId` in the parent path is a path variable, not a query parameter, because it is part of the resource's identity.

The GET endpoint handles both the unfiltered and filtered cases through a single method. When `minRating` is absent from the URL it defaults to `1`, returning all reviews. When it is present it filters by that minimum rating. Both cases hit the same URL and only the query parameter differs. This is the correct REST design: the URL identifies the collection, and the query parameter shapes the representation returned.

```java
package com.example.catalogue.controller;

import com.example.catalogue.model.Review;
import com.example.catalogue.service.ProductService;
import com.example.catalogue.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
// The base path encodes the ownership relationship:
// a review is a child of a product, so the product's ID appears in the path.
// This is the correct design when a child resource's identity is only
// meaningful in the context of its parent.
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

    private final ProductService productService;
    private final ReviewService reviewService;

    public ReviewController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    // GET /api/v1/products/{productId}/reviews
    // GET /api/v1/products/{productId}/reviews?minRating=4
    //
    // Both URLs are handled by this single method.
    // minRating is a query parameter -- it refines the collection, it does not
    // identify a different resource. The URL /reviews?minRating=4 and /reviews
    // refer to the same collection, shaped differently.
    // When minRating is absent, defaultValue = "1" means all reviews are returned.
    //
    // First verifies the parent product exists -- a 404 here is semantically correct
    // because the sub-resource collection itself does not exist without the parent.
    @GetMapping
    public ResponseEntity<List<Review>> getReviews(
            @PathVariable String productId,
            // required = false means the parameter is optional.
            // defaultValue provides the fallback so the method always receives a value.
            @RequestParam(required = false, defaultValue = "1") int minRating) {

        // TODO 9: Check that the product with productId exists (use productService.findById).
        // If it does not exist, return 404.
        // If it does, call reviewService.findByProductIdAndRating(productId, minRating)
        // and return 200 with the result.
        // Because minRating defaults to 1, this returns all reviews when no filter is supplied.
        return productService.findById(productId)
                .map(p -> ResponseEntity.ok(
                        reviewService.findByProductIdAndRating(productId, minRating)))
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/v1/products/{productId}/reviews
    @PostMapping
    public ResponseEntity<Review> addReview(@PathVariable String productId,
                                            @RequestBody Review review,
                                            UriComponentsBuilder ucb) {
        // TODO 10: Verify the product exists. Return 404 if not.
        // If the product exists, call reviewService.add(), binding the productId from the
        // path variable into the Review (not the request body -- the client should not
        // set the productId; it comes from the URL).
        // Return 201 Created with a Location header:
        //   /api/v1/products/{productId}/reviews/{reviewId}
        if (productService.findById(productId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Review toSave = new Review(null, productId, review.author(),
                review.rating(), review.comment());
        Review saved = reviewService.add(toSave);
        URI location = ucb.path("/api/v1/products/{productId}/reviews/{reviewId}")
                .buildAndExpand(productId, saved.reviewId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }
}
```

### Task 3.4 – Verify Filtering Behaviour

Add the following requests to `requests.http` and verify each one produces the expected result:

```http
### All reviews for P001 (no filter -- minRating defaults to 1)
GET http://localhost:8080/api/v1/products/P001/reviews

### Only reviews rated 5 or above
GET http://localhost:8080/api/v1/products/P001/reviews?minRating=5

### Only reviews rated 4 or above
GET http://localhost:8080/api/v1/products/P001/reviews?minRating=4

### Product does not exist -- expect 404
GET http://localhost:8080/api/v1/products/INVALID/reviews
```

Expected results with the seeded data (alice=5, bob=4 for P001):
- No filter → both reviews returned
- `minRating=5` → alice's review only
- `minRating=4` → both reviews returned
- Invalid product → 404

### Task 3.5 – Reason About Idempotency

Answer these questions *before* looking at the checkpoints below. Write your answers in a comment block at the top of `ReviewController.java`.

Which of the endpoints you have built are idempotent, and which are not? For each endpoint, state what would happen if the client sent the same request twice.

Consider:
- `GET /api/v1/products/P001/reviews` sent twice
- `POST /api/v1/products/P001/reviews` sent twice with identical body
- `DELETE /api/v1/products/P001` sent twice (from Exercise 1)

### Checkpoints

1. Why does the path for reviews start with `/products/{productId}/reviews` rather than simply `/reviews?productId=P001`? What does the hierarchical structure communicate to the client and to the infrastructure?
2. The `POST /reviews` endpoint ignores the `productId` field in the request body and uses the one from the path variable instead. Why is this the correct design?
3. `GET`, `PUT`, and `DELETE` are idempotent. `POST` is not. What does this mean for retry logic in a client that calls your API and receives a network timeout before the response arrives?
4. A colleague suggests adding a `/api/v1/products/search` endpoint that accepts a JSON body containing filter criteria. What REST constraint does this violate, and what is the correct design?

### Extension Task

Add a `PUT /api/v1/products/{productId}/reviews/{reviewId}` endpoint that replaces an existing review completely. Implement it as a true PUT: if the review does not exist, create it (this is the correct PUT semantics for a resource with a client-provided identity). Return `200` if the resource was replaced and `201` if it was created. Note that this requires a minor addition to `ReviewService`.

---

## Exercise 4 – HTTP Status Code Discipline and the Error Contract

**Estimated time:** 40–60 minutes
**Topics covered:** HTTP status code semantics, `@ControllerAdvice`, `@ExceptionHandler`, structured error payloads aligned with RFC 7807, the 200-with-error antipattern

### Context

Status codes are the primary communication mechanism between an API and every layer that interacts with it. They are a contract. A load balancer decides whether to retry a request based on the status code. A circuit breaker decides whether to open based on the rate of 5xx responses. A monitoring dashboard raises an alert when there is a spike in 4xx codes. If the application returns `200 OK` for every response and encodes the real outcome in the body, every one of these capabilities is disabled.

The `@ControllerAdvice` + `@ExceptionHandler` pattern in Spring Boot is the mechanism for centralizing exception-to-status-code mappings. Controllers throw domain exceptions; the advice translates them to HTTP responses. Controllers contain no `try/catch` blocks and no `ResponseEntity` construction for error cases. This is a clean separation of concerns: the controller declares intent, and the advice handles outcomes.

In this exercise you will build a domain exception hierarchy, a centralized exception handler, and a structured error payload that follows RFC 7807.

### Task 4.1 – Domain Exception Classes

Create two exception classes in the `exception` package:

```java
package com.example.catalogue.exception;

// Thrown when a requested resource does not exist.
// @ResponseStatus is one way to declare the HTTP mapping.
// You will use @ControllerAdvice in this exercise instead,
// which gives centralized control over all mappings.
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
}
```

```java
package com.example.catalogue.exception;

// Thrown when a request is syntactically valid but violates a business rule.
// This maps to 422 Unprocessable Entity, not 400 Bad Request.
// 400 means the request could not be understood.
// 422 means the request was understood but the domain rejected it.
public class BusinessRuleException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
```

### Task 4.2 – A Structured Error Payload

Create `ApiError.java` in the `exception` package. This is the payload returned in the body of every error response. Its structure follows RFC 7807 Problem Details for HTTP APIs.

```java
package com.example.catalogue.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

// @JsonInclude(NON_NULL) tells Jackson not to serialize fields that are null.
// The "fieldErrors" field is only relevant for validation failures;
// it should be absent from the JSON for other error types.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String status;       // e.g. "404 NOT_FOUND"
    private final String errorCode;    // machine-readable code, e.g. "PRODUCT_NOT_FOUND"
    private final String message;      // human-readable description
    private final Instant timestamp;
    private final List<FieldError> fieldErrors;  // non-null only for 400 validation failures

    // TODO 12: Write the constructor that accepts all five fields.
    // Mark the constructor public.
    // Assign all parameters to the final fields.
    // Use Instant.now() for timestamp when you want the current time.
    // (The full constructor is provided below -- write it yourself first)
    public ApiError(String status, String errorCode, String message,
                    List<FieldError> fieldErrors) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = Instant.now();
        this.fieldErrors = fieldErrors;
    }

    // Getters -- required for Jackson serialization
    public String getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    // Nested record for per-field validation errors
    public record FieldError(String field, String message) {}
}
```

### Task 4.3 – The Global Exception Handler

Create `GlobalExceptionHandler.java` in the `exception` package. This class centralizes all exception-to-HTTP mappings. No controller or service class will reference `HttpStatus` or construct a `ResponseEntity` for error cases.

```java
package com.example.catalogue.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

// @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
// Every method in this class can return a plain object and Spring will
// serialize it to JSON, just like @RestController does for normal endpoints.
// This class applies to all controllers in the application by default.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles ResourceNotFoundException -- returns 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        // TODO 13: Build an ApiError with:
        //   status = "404 NOT_FOUND"
        //   errorCode = ex.getResourceType().toUpperCase() + "_NOT_FOUND"
        //   message = ex.getMessage()
        //   fieldErrors = null (no per-field errors for a not-found)
        // Return ResponseEntity with status HttpStatus.NOT_FOUND and the ApiError as the body.
        ApiError error = new ApiError(
                "404 NOT_FOUND",
                ex.getResourceType().toUpperCase() + "_NOT_FOUND",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Handles BusinessRuleException -- returns 422
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        // TODO 14: Build an ApiError with:
        //   status = "422 UNPROCESSABLE_ENTITY"
        //   errorCode = ex.getErrorCode()
        //   message = ex.getMessage()
        //   fieldErrors = null
        // Return ResponseEntity with status HttpStatus.UNPROCESSABLE_ENTITY.
        ApiError error = new ApiError(
                "422 UNPROCESSABLE_ENTITY",
                ex.getErrorCode(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    // Handles validation failures from @Valid -- returns 400
    // MethodArgumentNotValidException is thrown by Spring MVC when @Valid fails.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        ApiError error = new ApiError(
                "400 BAD_REQUEST",
                "VALIDATION_FAILURE",
                "One or more fields failed validation",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Catch-all for any unhandled exception -- returns 500
    // Security note: the response body intentionally contains no stack trace,
    // no exception class name, and no internal message.
    // Those details are available in the server logs, not in the HTTP response.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // TODO 15: Build an ApiError with:
        //   status = "500 INTERNAL_SERVER_ERROR"
        //   errorCode = "INTERNAL_ERROR"
        //   message = "An unexpected error occurred. Please contact support."
        //   fieldErrors = null
        // Log the real exception (use System.err.println for now -- a real app uses SLF4J).
        // Return ResponseEntity with status HttpStatus.INTERNAL_SERVER_ERROR.
        System.err.println("Unhandled exception: " + ex.getMessage());
        ApiError error = new ApiError(
                "500 INTERNAL_SERVER_ERROR",
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please contact support.",
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### Task 4.4 – Use the Exceptions in the Service Layer

Update `ProductService` to throw `ResourceNotFoundException` rather than returning `Optional.empty()`. Notice that the service never references `HttpStatus`. Instead, it throws a domain exception, and the handler decides the HTTP mapping.

```java
// Replace the findById method in ProductService with:

public Product findById(String id) {
    // TODO 16: Look up the product in the store.
    // If found, return it directly (not wrapped in Optional -- the exception handles the missing case).
    // If not found, throw new ResourceNotFoundException("Product", id).
    Product product = store.get(id);
    if (product == null) {
        throw new ResourceNotFoundException("Product", id);
    }
    return product;
}
```

> **Note:** This changes the return type from `Optional<Product>` to `Product`. Update all callers accordingly. The controller's `getById` method becomes simpler. It just calls the service and returns the result directly, because the 404 case is now handled by `GlobalExceptionHandler`.

Update `ProductController.getById`:

```java
@GetMapping("/{id}")
public Product getById(@PathVariable String id) {
    // No Optional handling here -- if the product doesn't exist,
    // ResourceNotFoundException is thrown by the service and caught
    // by GlobalExceptionHandler, which returns 404. The controller
    // only handles the happy path.
    return productService.findById(id);
}
```

### Task 4.5 – Verify the Error Contract

Add to `requests.http`:

```http
### Request a product that does not exist
GET http://localhost:8080/api/v1/products/DOES_NOT_EXIST

### Expected response: 400 Not Found
### Body should contain errorCode: "PRODUCT_NOT_FOUND"
```

Restart the application and execute the request. Verify:
- The HTTP status code is `404` (check the status line in the response, not the body)
- The body contains `errorCode`, `message`, `timestamp`, and `status`
- The body does not contain a stack trace or internal class names

### Checkpoints

1. Before this exercise, `ProductController.getById` returned `ResponseEntity<Product>` and handled the not-found case inline. After this exercise it returns `Product` and has no not-found logic at all. What made this simplification possible?
2. The catch-all `Exception` handler deliberately omits the exception message from the response body. Why? What risk does including it create?
3. `BusinessRuleException` maps to `422 Unprocessable Entity` rather than `400 Bad Request`. What is the semantic difference between these two status codes, and why does it matter to the client?
4. The `errorCode` field in `ApiError` is a machine-readable string like `PRODUCT_NOT_FOUND`. Why is this field necessary when the `message` field already describes the problem in English?

### Extension Task

Add Bean Validation to the `POST /api/v1/products` endpoint. Create a `CreateProductRequest` record (or class) with the following constraints:

- `name`: must not be blank, maximum 100 characters
- `category`: must not be blank
- `price`: must be greater than zero

Annotate the fields with `@NotBlank`, `@Size`, and `@Positive` from `jakarta.validation.constraints`. Add `@Valid` to the `@RequestBody` parameter in the controller. Verify that sending a request with a blank name produces a `400` response with a `fieldErrors` array in the body.

---

## Exercise 5 – API Versioning Strategy

**Estimated time:** 25–35 minutes
**Topics covered:** URL path versioning, breaking vs non-breaking changes, deprecation headers, the version increment decision

### Context

An API that cannot evolve without breaking its callers is an API that cannot be maintained. Versioning is a commitment made from the first line of code. The version prefix in the URL (`/api/v1/products`) is the mechanism by which a service can introduce a breaking change in v2 while v1 continues to serve existing clients unchanged.

The most important discipline in API versioning is knowing precisely which changes require a version increment and which do not. A version bump is expensive: the old version must be maintained, clients must migrate, and two code paths must coexist for the deprecation period. Understanding the decision precisely prevents both over-versioning (bumping for non-breaking changes) and under-versioning (silently breaking clients).

### Task 5.1 – Introduce a v2 Controller

Your v1 `ProductController` returns `Product` records that include all fields. A v2 contract changes the response shape: the `category` field is renamed to `productCategory` (a breaking change) and a new `lastUpdated` timestamp field is added.

Create a v2 response model in the `model` package:

```java
package com.example.catalogue.model;

import java.time.Instant;

// This is the v2 response shape. It is a separate class -- not a modified
// version of the v1 Product record. v1 and v2 coexist independently.
public record ProductV2(
        String id,
        String name,
        String productCategory,   // renamed from "category" -- this is the breaking change
        double price,
        Instant lastUpdated       // new field -- non-breaking for clients that ignore unknowns
) {}
```

Create `ProductControllerV2.java` in the `controller` package:

```java
package com.example.catalogue.controller;

import com.example.catalogue.model.ProductV2;
import com.example.catalogue.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

// The version prefix is changed to v2 at the class level.
// There is no version-branching logic inside any method.
// The version is an address, not a condition.
@RestController
@RequestMapping("/api/v2/products")
public class ProductControllerV2 {

    private final ProductService productService;

    public ProductControllerV2(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductV2> getAll() {
        // TODO 17: Call productService.findAll() to get the list of v1 Products.
        // Map each Product to a ProductV2 using a Stream:
        //   - copy id, name, and price directly
        //   - map "category" to "productCategory"
        //   - set lastUpdated to Instant.now() (in a real service this would
        //     come from the database, but for this exercise a fixed value is fine)
        return productService.findAll().stream()
                .map(p -> new ProductV2(p.id(), p.name(), p.category(),
                        p.price(), Instant.now()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductV2 getById(@PathVariable String id) {
        // TODO 18: Call productService.findById(id) -- it throws ResourceNotFoundException
        // if not found, so no null-checking is needed.
        // Map the returned Product to ProductV2 the same way as above.
        var p = productService.findById(id);
        return new ProductV2(p.id(), p.name(), p.category(), p.price(), Instant.now());
    }
}
```

### Task 5.2 – Deprecation Headers for v1

The v1 controller should signal its deprecation to well-written clients. Add deprecation headers to all v1 responses using a `@ModelAttribute` method. This runs before every request-handling method in the controller and can add headers to the response.

```java
// Add to ProductController (the v1 controller)

import jakarta.servlet.http.HttpServletResponse;

// @ModelAttribute methods without a return value are executed before every
// request-handling method in the same controller class.
// This is the correct place to add headers that should appear on every response.
@ModelAttribute
public void addDeprecationHeaders(HttpServletResponse response) {
    // TODO 19: Add these three headers to every v1 response:
    //   Deprecation: true
    //   Sunset: Sat, 01 Nov 2025 00:00:00 GMT
    //   Link: </api/v2/products>; rel="successor-version"
    response.setHeader("Deprecation", "true");
    response.setHeader("Sunset", "Sat, 01 Nov 2025 00:00:00 GMT");
    response.setHeader("Link", "</api/v2/products>; rel=\"successor-version\"");
}
```

### Task 5.3 – Verify Both Versions

Add to `requests.http`:

```http
### v1 endpoint -- should include Deprecation and Sunset headers in the response
GET http://localhost:8080/api/v1/products

### v2 endpoint -- response body has "productCategory" instead of "category"
GET http://localhost:8080/api/v2/products
```

Restart and execute both requests. In the v1 response, expand the response headers panel and verify `Deprecation: true` is present. In the v2 response, verify the body uses `productCategory` rather than `category`.

### Task 5.4 – The Version Increment Decision

For each change listed below, decide: does this change require a new version increment (v3), or is it non-breaking and can be made to v2 without a bump? Write your reasoning as a comment in a new file `VERSION_NOTES.md` in the project root.

| Proposed change | Breaking? |
|---|---|
| Add an optional `description` field to the v2 response | ? |
| Remove the `price` field from the v2 response | ? |
| Change `price` from `double` to `String` in the v2 response | ? |
| Make the `name` query parameter on the filter endpoint required instead of optional | ? |
| Add a new endpoint `GET /api/v2/products/featured` | ? |
| Rename the error code `PRODUCT_NOT_FOUND` to `RESOURCE_NOT_FOUND` | ? |

### Checkpoints

1. The v2 controller is a new class (`ProductControllerV2`) rather than a modified version of `ProductController`. Why is this the correct design? What would be wrong with adding an `if (version == 2)` branch inside the existing controller?
2. The `Sunset` header communicates a specific date. What obligation does setting this header create for the team that owns the API?
3. Adding a new field to a response is listed as potentially non-breaking. Under what condition does it become breaking? What does this imply about how well-written clients should deserialize JSON?
4. Why must the error code contract be versioned alongside the success response contract?

---

## Summary and Reflection

After completing all five exercises, you have built a versioned, layered Spring Boot REST API with structured error handling and environment-aware configuration. The table below maps each exercise back to the core concepts from the module:

| Exercise | Concept | Key Insight |
|---|---|---|
| 1 – Layered Architecture | `@RestController`, `@Service`, constructor injection | Each layer has one responsibility; HTTP concerns do not reach the service layer |
| 2 – Configuration & Profiles | `application.yml`, `@ConfigurationProperties`, profile overlays | Configuration is externalised, typed, and environment-aware without code changes |
| 3 – URL & HTTP Method Design | Resource-oriented URLs, sub-resources, query parameters, idempotency | URLs identify resources; HTTP methods carry semantics; the uniform interface enables infrastructure intelligence |
| 4 – Status Codes & Error Contract | `@ControllerAdvice`, `@ExceptionHandler`, RFC 7807 | Status codes are a contract, not a detail; the 200-with-error pattern blinds every layer that depends on them |
| 5 – Versioning | URL path versioning, breaking changes, deprecation headers | Version from day one; a v2 controller is a new class, not a modified v1 controller |

### Final Reflection Questions

Take 10 minutes to answer these before your next session:

1. A colleague argues that `@ControllerAdvice` is over-engineering and that it is simpler for each controller method to catch its own exceptions and return the appropriate `ResponseEntity`. What production problems does the centralized approach prevent that the inline approach does not?

2. Your service currently stores data in a `ConcurrentHashMap`. A future exercise will replace this with a database. Which classes will need to change? Which will not? Does the layered architecture make this replacement easier or harder, and why?

3. A client reports that a POST request to create a product sometimes creates two products with identical data. Based on what you know about idempotency and the idempotency-key pattern, what would you add to the API contract to allow clients to retry safely?

---

*End of Module 2 Exercises*
