# 04 — Resource Server Security

Day 1 afternoon. ~1.5 hours. The Resource Server's security plumbing is
**already built for you** — both the filter chain (`SecurityConfig.java`)
and the JWT-to-user converter (`JwtAuthConverter.java`). What you do here
is **read** that code carefully, run a manual smoke test to confirm it
works on your machine, then write the integration tests that prove the
boundary behaves under HTTP traffic.

The Resource Server is the **stateless** half of the BFF design — no
cookies, no CSRF, no sessions, no CORS. Only Bearer JWTs. The pre-built
`SecurityConfig` reflects that: stateless session policy, CSRF disabled,
CORS disabled, JWT validation via `oauth2ResourceServer.jwt(...)`, and
a two-layer admin gate (URL filter + `@PreAuthorize` on controller
methods for defence in depth).

## Task 4.1 — Read JwtAuthConverter and answer five questions (~20 min)

**File:** `resource-server/src/main/java/com/example/banking/security/JwtAuthConverter.java`

Open the file. It's about 35 lines of code with comments. Read top to
bottom — the Javadoc explains the design choices the converter makes.

This is the single most important security component in the Resource
Server. Every `/api/v1/**` request flows through it. When Spring validates
a Bearer token, it calls `convert(Jwt jwt)` to build the `Authentication`
object that lands in the security context — the principal name from this
object is what every service-layer ownership check compares against.

As a team, agree on a one-sentence answer to each question. The demo
graders may ask any of these:

1. Why does the converter look up `BankUserEntity` by **subject** (the JWT
   `sub` claim) instead of by email?
2. Why is `Authentication.getName()` set to the **local userId**, not the
   JWT's `sub` value?
3. What happens the **very first time** a brand-new user signs in, before
   any `BANK_USERS` row exists for them?
4. Why is the role taken from the **stored row's** role, not from the
   JWT's `role` claim directly?
5. What would break if we returned the two-arg `JwtAuthenticationToken(jwt, authorities)`
   constructor instead of the three-arg version that includes a name?

If any answer isn't obvious from reading the code, find the line that
explains it. The comments are there on purpose.

> **Why is this pre-built?** Custom `JwtAuthenticationConverter`
> implementations weren't covered in the bootcamp. Module 3 taught you to
> *read* JWT claims (`@AuthenticationPrincipal Jwt jwt`); writing a
> `Converter<Jwt, AbstractAuthenticationToken>` that builds a
> `JwtAuthenticationToken` with a custom principal name and authorities —
> while also auto-provisioning a database row — is several concept jumps
> beyond that. You'll write *tests against* this code in chapter 06; that
> exercises your understanding without asking you to compose it from
> scratch.

## Task 4.2 — Smoke test the security boundary (~15 min)

Restart the Resource Server, log in via the SPA as `alice` / `password`,
and confirm:

| Request | Expected |
|---|---|
| `GET http://localhost:8081/health` | **200** |
| `GET http://localhost:8081/api/v1/accounts` (no token, direct to RS) | **401** |
| `GET http://localhost:8080/api/v1/accounts` (via BFF, signed in) | **200**, alice's accounts |
| `GET http://localhost:8080/api/v1/admin/users` (alice = CUSTOMER) | **403** |

If alice's accounts come back empty, your seed data is wrong (verify the
demo accounts seed migration ran and the `OWNER_ID` matches the
auto-provisioned `usr_*` row in `BANK_USERS`).

If the admin endpoint returns 200 to alice, something is wrong with the
two-layer admin gate — either the URL filter, the `@PreAuthorize`, or
the role mapping. Check the resource server's startup logs.

## Task 4.3 — Integration tests for the security boundary (~50 min)

**File:** `resource-server/src/test/java/com/example/banking/controller/AccountControllerIntegrationTest.java`

The class is already wired with `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@EmbeddedKafka`, and `@MockBean`s for the service layer. Implement these
two `@Test` methods (the others — admin 403, health 200, deposit happy,
internal transfer, processor 503 — are pre-implemented for you to read):

| Test | What it proves |
|---|---|
| `get_accounts_without_token_returns_401` | Default `authenticated()` gate works |
| `customer_hitting_other_users_account_returns_404` | Ownership rule returns 404, not 403 |

**Patterns you will use:**

- A bare `mockMvc.perform(get("/api/v1/accounts"))` simulates an
  unauthenticated request.
- `.with(jwt().jwt(j -> j.subject("...").claim("email","...").claim("role","CUSTOMER")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))`
  simulates a JWT-authenticated request without spinning up the auth server.
  This is the same `.with(jwt())` post-processor from Module 3.
- `when(accountService.loadOwned(eq("acc_other"), any())).thenThrow(new ResourceNotFoundException("account", "acc_other"))`
  drives the 404 path through the controller's exception handling.

**Why 404 and not 403 for non-owned resources?**
A 403 confirms that the resource exists. A 404 doesn't. Returning 404
prevents account-ID enumeration. Read the pre-implemented
`customer_hitting_admin_endpoint_returns_403` test for the contrast — admin
endpoints openly reject with 403 because the URL itself is public knowledge.

Run:

```bash
mvn -pl backend/resource-server test -Dtest=AccountControllerIntegrationTest
```

The full suite (your two + the pre-implemented ones) should be green.

## Done when

- [ ] Your team has agreed on one-sentence answers to all five questions
      in Task 4.1.
- [ ] All four smoke-test rows in 4.2 behave as expected.
- [ ] Both integration tests you wrote are green; the pre-implemented
      ones also pass.

If you finish chapter 04 with time remaining, get a head start on
chapter 05's reading — TRANSFER_OUT is the genuinely hard piece of this
capstone, and an early pass on the algorithm pays off Day 2 morning.

Next: [05-transfers.md](./05-transfers.md).
