# Setup Exercise: Building the Local Authorization Server
## Spring Boot 3.2.x

> **Course:** MD282:Java Full-Stack Development
> **Purpose:** Standalone setup exercise: complete this before starting the Module 3 exercises
> **Estimated time:** 30–45 minutes

---

## Overview

This exercise shows you how to set up fully working OAuth2 Authorization Server that runs on your local machine. All Module 3 exercises depend on it. Complete this first and keep the IntelliJ window the AS is running in open throughout the lab session.

When complete you will have:

- An Authorization Server running on port 9000
- Two registered clients: one for interactive user login (Authorization Code with PKCE) and one for service-to-service calls (Client Credentials)
- Two test users with different roles
- A JWKS endpoint that Resource Servers use to verify token signatures
- Custom claims (roles, department) added to user tokens

> **Important:** The Module 3 exercises and this Authorization Server must both use **Spring Boot 3.2.x**. Using different versions between the two projects will cause token validation failures.

---

## Step 1: Create the Project

1. Open [https://start.spring.io](https://start.spring.io) in a browser
2. Configure the project as follows:

| Setting | Value         |
|---|---------------|
| Project | Maven         |
| Language | Java          |
| Spring Boot | **3.5**       |
| Group | `com.example` |
| Artifact | `auth-server` |
| Packaging | Jar           |
| Java | 17            |

3. Add the following dependencies using the search box:
   - **Spring Web**
   - **OAuth2 Authorization Server**

4. Click **Generate**, unzip the archive, and open the folder in IntelliJ IDEA using **File → Open → New Window**

5. When IntelliJ finishes indexing, open the Maven tool window (**View → Tool Windows → Maven**) and confirm there are no download errors

---

## Step 2: Verify the Dependencies

Open `pom.xml` and confirm the `<dependencies>` block contains the following. If the content differs, replace it with this exactly:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-oauth2-authorization-server</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Also, change the `<parent>` block so that it specifies Spring Boot 3.2.5. The Initialzr set it to 3.5, which is not compatible with the Module 3 exercises. The correct block is:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
    <relativePath/>
</parent>
```

After making any changes, right-click `pom.xml` and choose **Maven → Reload Project**.

Verify the correct version resolved by running the following in the IntelliJ terminal:

```bash
mvn dependency:tree | grep authorization-server
```

You should see a version beginning with `1.2`:

```
\- org.springframework.security:spring-security-oauth2-authorization-server:jar:1.2.x
```

If you see `7.0.x` the Spring Boot version is wrong. Correct the `<parent>` version and reload again.

---

## Step 3: Check Your Package Name

Open the main application class (it will be named something like `AuthServerApplication.java`) and look at the first line. It will be one of:

```java
package com.example.authserver;     // no separator
```
```java
package com.example.auth_server;    // underscore
```

Note which one it is. Every class you create in this exercise must use the same base package. The instructions below use `com.example.authserver` — if your package uses an underscore, adjust accordingly.

---

## Step 4: Configure the Server

Delete `src/main/resources/application.properties` if it exists and create `src/main/resources/application.yml`:

```yaml
server:
  port: 9000

logging:
  level:
    org.springframework.security: DEBUG
```

The DEBUG logging is intentional. It lets you see every security decision the Authorization Server makes in the console, which helps you understand the OAuth2 flow as it runs.

---

## Step 5: Create the Configuration Class

Create the package `com.example.authserver.config` (adjust for your actual package name from Step 3).

Inside it, create `AuthorizationServerConfig.java` with the following content. Read every comment. Each section maps directly to a concept covered in the module lectures:

```java
package com.example.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    // -------------------------------------------------------------------------
    // Filter Chain 1: Authorization Server protocol endpoints
    //
    // This chain handles the OAuth2 protocol endpoints:
    //   /oauth2/authorize  -- the authorization endpoint (redirects to login)
    //   /oauth2/token      -- the token endpoint (issues JWTs)
    //   /oauth2/jwks       -- the public key endpoint (Resource Servers fetch from here)
    //   /.well-known/...   -- discovery endpoints
    //
    // @Order(1) ensures this chain is evaluated before the login form chain below.
    // -------------------------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {

        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                // Enable OpenID Connect 1.0 -- adds the /userinfo endpoint
                // and id_token support alongside the standard access token.
                .oidc(Customizer.withDefaults());

        http
                // When an unauthenticated browser request arrives at the
                // authorization endpoint, redirect to the login form.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                // Accept Bearer tokens for the /userinfo endpoint.
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));

        return http.build();
    }

    // -------------------------------------------------------------------------
    // Filter Chain 2: Default security for the login form
    //
    // @Order(2) means this chain is checked after the Authorization Server chain.
    // It provides the login form that users see when authenticating interactively.
    // -------------------------------------------------------------------------
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Users
    //
    // These are the human users who can authenticate interactively
    // via the Authorization Code flow.
    //
    // In production these would come from a database or enterprise identity
    // provider. For this exercise, in-memory users are sufficient.
    //
    // alice -- HR_MANAGER role, can read and write employee data
    // bob   -- VIEWER role, read-only access
    // -------------------------------------------------------------------------
    @Bean
    public UserDetailsService userDetailsService() {
        var alice = User.withDefaultPasswordEncoder()
                .username("alice")
                .password("password")
                .roles("HR_MANAGER")
                .build();

        var bob = User.withDefaultPasswordEncoder()
                .username("bob")
                .password("password")
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(alice, bob);
    }

    // -------------------------------------------------------------------------
    // Registered Clients
    //
    // A registered client is an application that has permission to request
    // tokens from this Authorization Server.
    //
    // workforce-spa: a public client using Authorization Code + PKCE.
    //   Represents a browser-based application where a secret cannot be stored.
    //   PKCE provides the security guarantee that a client secret would give.
    //
    // workforce-service: a confidential client using Client Credentials.
    //   Represents a backend service authenticating with its own identity.
    //   No user is involved -- the service authenticates directly.
    // -------------------------------------------------------------------------
    @Bean
    public RegisteredClientRepository registeredClientRepository() {

        RegisteredClient workforceSpa = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("workforce-spa")
                // Public clients have no secret. PKCE takes its place.
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:9000/authorized")
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/workforce-spa")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("read:employees")
                .scope("write:employees")
                .clientSettings(ClientSettings.builder()
                        // Require PKCE. The Authorization Server rejects any
                        // authorization request that does not include a code_challenge.
                        .requireProofKey(true)
                        // Show the consent screen so students can see scope approval.
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        // 5-minute access token lifetime. Intentionally short so the
                        // stale permissions scenario in Exercise 1 is observable
                        // within a single lab session.
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .build())
                .build();

        RegisteredClient workforceService = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("workforce-service")
                // {noop} tells Spring not to hash this value.
                // Development only -- never use {noop} in production.
                .clientSecret("{noop}workforce-service-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read:employees")
                .tokenSettings(TokenSettings.builder()
                        // Service tokens can have longer lifetimes because the
                        // service can re-authenticate silently at any time.
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(workforceSpa, workforceService);
    }

    // -------------------------------------------------------------------------
    // Token Customizer
    //
    // Adds application-specific claims beyond the standard registered claims.
    // The Resource Server exercises depend on these two custom claims:
    //
    //   roles      -- the user's application roles
    //   department -- a custom claim simulating a user profile attribute
    //
    // The customizer only runs when a user is present (Authorization Code flow).
    // Service tokens (Client Credentials) have no user, so this condition
    // is false and no user-specific claims are added. This is why the service
    // token will not contain "roles" or "department" -- a difference the
    // exercises ask you to observe and explain directly.
    // -------------------------------------------------------------------------
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            var principal = context.getPrincipal();

            if (principal != null &&
                    principal.getPrincipal() instanceof
                            org.springframework.security.core.userdetails.UserDetails user) {

                // Strip the ROLE_ prefix Spring Security adds internally.
                // Stored as "HR_MANAGER" so the Resource Server can check
                // hasRole('HR_MANAGER') in @PreAuthorize expressions.
                List<String> roles = user.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .toList();
                context.getClaims().claim("roles", roles);

                // Simulate a department attribute from a user profile store.
                String department = "alice".equals(user.getUsername())
                        ? "Human Resources"
                        : "Engineering";
                context.getClaims().claim("department", department);
            }
        };
    }

    // -------------------------------------------------------------------------
    // RSA Key Pair
    //
    // The Authorization Server signs JWTs with the private key.
    // Resource Servers verify JWTs using the public key, fetched from:
    //   http://localhost:9000/oauth2/jwks
    //
    // A new key pair is generated in memory on each startup. Any token issued
    // before a restart is immediately invalid because the new public key will
    // not match the old signature. This is expected in development and is
    // something Exercise 1 asks you to observe directly.
    //
    // In production the key pair is generated once, stored securely, and
    // rotated deliberately with advance notice to all Resource Servers.
    // -------------------------------------------------------------------------
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                // The kid (Key ID) appears in every JWT header.
                // Resource Servers use it to select the correct verification
                // key from the JWKS when multiple keys are present.
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    // -------------------------------------------------------------------------
    // Authorization Server Settings
    //
    // The issuer URI is embedded in the "iss" claim of every token this
    // server issues. Resource Servers are configured with this URI and reject
    // any token where "iss" does not match. This prevents a token issued for
    // one system from being replayed against a different system.
    // -------------------------------------------------------------------------
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9000")
                .build();
    }
}
```

---

## Step 6: Start the Server

Run the main application class. You should see it start on port 9000. The DEBUG logging will produce a large amount of filter chain output. This is normal.

Look for this line near the end of the startup output:

```
Started AuthServerApplication in X.XXX seconds
```

---

## Step 7: Verify the Key Endpoints

Open the following URLs in your browser:

**JWKS endpoint** — the public keys Resource Servers use to verify signatures:

```
http://localhost:9000/oauth2/jwks
```

Expected response:

```json
{
  "keys": [{
    "kty": "RSA",
    "e": "AQAB",
    "kid": "some-generated-uuid",
    "n": "a-very-long-base64url-encoded-number..."
  }]
}
```

**Discovery endpoint** — the document that clients and Resource Servers use to auto-configure:

```
http://localhost:9000/.well-known/oauth-authorization-server
```

Note the `issuer`, `token_endpoint`, and `jwks_uri` fields in the response. These are the values you will configure in the Workforce API's `application.yml` during the Module 3 exercises.

---

## Step 8: Obtain Your First Token

Create `auth-requests.http` in the project root:

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

Execute the Client Credentials request. You should receive:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read:employees"
}
```

Copy the `access_token` value. You will decode it at [https://jwt.io](https://jwt.io) in Exercise 1.

---

## Reference: What This Server Provides

| Item | Value |
|---|---|
| Authorization Server URL | `http://localhost:9000` |
| JWKS endpoint | `http://localhost:9000/oauth2/jwks` |
| Issuer URI | `http://localhost:9000` |
| Token endpoint | `http://localhost:9000/oauth2/token` |
| User: alice | password: `password`, role: `HR_MANAGER` |
| User: bob | password: `password`, role: `VIEWER` |
| Public client ID | `workforce-spa` |
| Service client ID | `workforce-service` |
| Service client secret | `workforce-service-secret` |
| User token lifetime | 300 seconds (5 minutes) |
| Service token lifetime | 3600 seconds (1 hour) |

---

## Troubleshooting

**Port 9000 is already in use**

Change `server.port` in `application.yml` to another port, update `issuer` in `AuthorizationServerSettings` to match, and use the new port when configuring the Workforce API in Module 3.

**The application fails to start with a `NoSuchBeanDefinitionException`**

The most common cause is a missing `@EnableWebSecurity` annotation on `AuthorizationServerConfig`. Verify it is present.

**Tokens become invalid after restarting the server**

This is expected. A new RSA key pair is generated on each startup. Re-execute the Client Credentials request in `auth-requests.http` to get a fresh token.

**Authorization code exchange fails with `invalid_grant`**

Authorization codes expire in approximately 5 minutes and are single-use. Open the browser URL again to get a fresh code and exchange it immediately.

---

*Once the JWKS endpoint responds correctly and you have a service token in hand, proceed to the Module 3 exercises.*
