# 06 — Frontend Tour & Extra Backend Tests

Day 2 mid-day. Two activities, ~90 minutes total:

1. **Frontend tour** (~30 min) — read the React SPA, run it, prepare to demo
   it. You write no JavaScript.
2. **Extra backend unit tests** (~60 min) — fill in two small test classes
   the rubric expects (`PaymentServiceTest`, `JwtAuthConverterTest`).

## Why a "tour" instead of building the frontend

The instructional intent of this capstone is to **make a React SPA secure**,
not to teach you React. The bootcamp covered React conceptually plus one
introductory lab — not enough to expect you to author the SPA from scratch.
The scaffold ships a fully working React 18 + Vite + react-router SPA. You
will demo it; you will be questioned on it; you will not modify it.

The frontend uses Vite + React 18 + react-router-dom v6. Same-origin in
dev — Vite proxies `/api`, `/login`, `/logout`, `/oauth2` to the BFF on
port 8080. There is **no OAuth library in the SPA** — Spring on the BFF
handles the entire OAuth flow at the network layer.

---

## Part 1 — Frontend tour

### Task 6.1 — Run the SPA end-to-end

With the full stack up (mock-auth, resource-server, bff, frontend), confirm
each of these works in your browser. If any step fails, the issue is almost
always a backend bug from chapters 03–05; fix it before continuing.

1. Open `http://localhost:5173`. The SPA detects "no session" via
   `useMe`'s 401, and renders the sign-in page.
2. Click **Sign in**. The browser navigates to `http://localhost:9000/login`
   on the mock-auth server.
3. Sign in as `alice` / `password`.
4. Land back at `http://localhost:5173/`. AccountsPage shows alice's seeded
   accounts.
5. Click an account. AccountDetailPage shows the balance and any
   transactions.
6. Click **New transaction**. Submit a deposit of 25.00. You return to the
   account detail page; the new row is at the top.
7. Submit an internal transfer between two of alice's accounts. Both
   account detail pages show the matching `TRANSFER_OUT` / `TRANSFER_IN`
   rows.
8. Sign out. The next API call returns 401; the SPA renders the sign-in
   page again.
9. Sign in as `admin` / `password`. The admin nav link appears; the
   `/admin/users` page lists users.
10. Sign back in as `alice`. The admin nav link is hidden, and a direct
    request to `/api/v1/admin/users` returns 403.

If all ten steps work, the BFF pattern is alive on your machine. The demo
walks roughly this same script.

### Task 6.2 — Read the four files you need to explain

You will be asked about these in the demo. Read each one with a teammate;
agree on a one-sentence answer to the corresponding question.

| File | Question you must answer |
|---|---|
| `frontend/src/api/apiClient.js` | How does a POST attach the CSRF token? What happens on 401? |
| `frontend/src/hooks/useMe.js` | When does this run, and what does the rest of the app do with `user === null`? |
| `frontend/src/routes/AccountsPage.jsx` | How does it render a different UI for loading, empty, error, and loaded? |
| `frontend/src/components/TransactionForm.jsx` | What client-side validation runs before the POST hits the BFF? |

Each file is ~30 lines. Five minutes per file is enough.

### Task 6.3 — Verify the BFF pattern from the browser

Open DevTools while signed in. Confirm:

- **Cookies tab:** only `JSESSIONID` (HttpOnly = true) and `XSRF-TOKEN`
  (HttpOnly = false). No third cookie. No `access_token` cookie.
- **Local Storage** and **Session Storage:** both empty.
- **Network tab → any `/api/v1/...` request:** the request has a
  `Cookie:` header but **no `Authorization:` header**. The Bearer is
  attached server-side by the BFF; the browser never sees it.

If anything is off — for example, an `access_token` value sitting in
session storage — the BFF pattern is broken on your build. The most likely
cause is a backend bug from chapters 04 or 05; fix it before continuing.

This is the rubric's headline check. Practice walking through it — the
demo grader will ask.

---

## Part 2 — Extra backend unit tests

The capstone rubric (chapter 08) grades "reasonable coverage of service
+ controller logic" under Testing & Security Validation (15%). The
classes worth covering are `TransactionService`, `AccountService` (via
integration), `JwtAuthConverter`, `PaymentService`,
`GlobalExceptionHandler`, and `TransactionEventPublisher`. Previous
chapters covered the first two, and the global exception handler +
event publisher are pre-implemented. Two classes still have empty test
files: `PaymentService` and `JwtAuthConverter`.

### Task 6.4 — `PaymentServiceTest`

**File:** `resource-server/src/test/java/com/example/banking/service/PaymentServiceTest.java`

Fill in two `@Test` methods. The class has a `@MockBean` `RestClient`-style
collaborator already wired up; you stub its responses.

| Test | What it proves |
|---|---|
| `submit_external_transfer_calls_processor_with_idempotency_header` | The payment processor receives the request with the `Idempotency-Key` header and the API key |
| `submit_external_transfer_5xx_throws_payment_processor_exception` | A 5xx response from the stub causes `PaymentProcessorException`; balance is untouched |

Hints:

- WireMock is your collaborator pattern. The same patterns from Module 4 /
  Lab 4.4 apply: stub a `POST /payments` mapping that returns 200 or 503,
  invoke the service, assert on what the service returned or threw.
- The scaffolding's `PaymentService` class has a Javadoc explaining how
  it builds the request. Read it before writing the test.
- Don't test what the framework does (HTTP plumbing). Test the contract
  of *your* method: input shape → outbound call → output shape, and the
  exception mapping on failure.

### Task 6.5 — `JwtAuthConverterTest`

**File:** `resource-server/src/test/java/com/example/banking/security/JwtAuthConverterTest.java`

You're testing the pre-built `JwtAuthConverter` from chapter 04 — same
class you read and answered questions about. The tests force you to walk
through the converter's behaviour from the outside, which is the second
half of understanding it.

Fill in two `@Test` methods.

| Test | What it proves |
|---|---|
| `first_login_creates_bank_user_row_with_role_from_claim` | A JWT with `sub: "new-sub"` and `role: "ADMIN"` triggers a new `BANK_USERS` row with `ROLE_ADMIN` |
| `subsequent_login_reuses_existing_row_and_role` | A JWT with the same `sub` returns the existing user; the role is taken from the **stored row**, not the JWT (so an in-DB demotion sticks) |

Hints:

- `BankUserRepository` is the only collaborator. Mock it.
- Build a `Jwt` with `Jwt.withTokenValue("test").header("alg","none")
  .claim("sub","...").claim("role","ADMIN").claim("email","x@y.com").build()`.
- Assert on the returned `Authentication`'s `getName()` (must be local
  userId, not the JWT subject) and its `GrantedAuthority` list.

Run:

```bash
mvn -pl backend/resource-server test
```

Whole suite (yours + pre-implemented) should be green.

---

## Done when

- [ ] All ten frontend smoke-test steps pass.
- [ ] DevTools confirms cookies-only, no tokens in storage, no `Authorization`
      header in `/api/v1/...` requests.
- [ ] Each of the four "you must explain" files has an agreed one-sentence
      answer per question.
- [ ] `PaymentServiceTest` has two passing tests.
- [ ] `JwtAuthConverterTest` has two passing tests.

Next: [07-security-validation.md](./07-security-validation.md).
