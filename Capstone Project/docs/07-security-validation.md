# 07 — Security Validation

Day 2 afternoon. ~90 minutes. You will run the hardening checks the rubric
grades you on, plus one Checkmarx scan, one DAST baseline, and one custom
banking payload class.

The Testing & Security Validation slice is 15% of your grade. The rubric
calls out: a SAST scan triaged in writing (fix / accept / defer per
finding, with rescan results for fixes), a DAST run with at least one
custom banking payload class, and a written security-decisions
document.

## Task 7.1 — Hardening grep sweep (~10 min)

Run these checks on your repo and fix anything that fails:

```bash
# No secrets in source
grep -rn "client_secret\|password=\|Bearer eyJ" backend frontend/src

# No tokens logged
grep -rn "log\.\(info\|debug\|trace\)(.*token" backend
grep -rn "console\.log(.*token" frontend/src

# No OAuth library in the SPA
grep -rn "oidc-client-ts\|react-oidc-context" frontend/src

# No leftover TODOs blocking grading
grep -rn "TODO" backend/*/src frontend/src
```

The first three should produce **zero** matches. The last one should only
match TODOs that are explicitly future-work.

If a `grep` finds something, fix it. The most common offenders are
`log.info("processing token=" + token)` from a Copilot session and
secrets accidentally committed in `.env` or `application-local.yml`. If
the latter happened, **rotate those credentials** — git history makes them
permanent unless you rewrite history.

## Task 7.2 — Run Checkmarx (~30 min)

Your instructor will give you access credentials and a one-page run-book.
Schedule the scan as soon as you can — the report can take 15-30 minutes,
and you need time to triage.

For each finding, decide:

- **Fix** — change the code, re-run, confirm the finding is closed.
- **Accept** — finding is real but the risk is acceptable for the capstone
  (e.g., `dev-only-not-secret` API key in `.env.example`). Justify in writing.
- **Defer** — out of scope (e.g., production-grade session store). Justify
  and note as "future work."

Document your triage in `docs/sast-findings.md` using the table from the
spec:

| Finding ID | Severity | Category | File:line | Decision | Rationale | Rescan |
|---|---|---|---|---|---|---|

Aim to fix every **high-severity** finding. Mediums and lows can be Accept
or Defer with a sentence each.

### Common findings to expect

- Hardcoded credentials in test fixtures or `application.yml`.
- Logging sensitive data (`log.info("token={}", token)`).
- `permitAll()` configured too broadly.
- Information exposure via raw `e.getMessage()` in error responses.
- `double` used for currency anywhere (you used `BigDecimal` everywhere — but
  Copilot may have regressed that).
- SQL injection via string concatenation. Should be zero findings if you
  used Spring Data — verify.

## Task 7.3 — DAST baseline scan (~15 min)

Run an OWASP ZAP baseline (passive only — no active probes) against the
running BFF. ZAP will crawl, inspect headers and cookies, and report
configuration-level issues without trying to break the app.

```bash
# Docker form (works on any machine with Docker)
docker run --rm -v "$(pwd):/zap/wrk:rw" \
    --network host \
    -t zaproxy/zap-stable zap-baseline.py \
    -t http://localhost:8080 \
    -r dast-baseline.html
```

(Or the Windows installer / standalone JAR with the same `zap-baseline`
command — your instructor may have a preferred path.)

The scan finishes in 1-5 minutes. Findings to expect:

- `X-Frame-Options` / `X-Content-Type-Options` headers (missing → fix or document)
- Cookie `SameSite` / `Secure` flags
- Verbose `Server:` headers
- Redirect-to-HTTPS configuration (production-only — accept for the
  dev environment)

Record what ZAP flagged in `docs/dast-payloads.md` with the same
fix / accept / defer columns as SAST. Most findings here are about
hardening headers — easy to fix, easy to write up.

## Task 7.4 — One custom payload class (~30 min)

The rubric's DAST line specifically grades **custom banking payloads**.
Pick the easiest of the three classes from the spec — the
**authorization probes** — and document each. The integration tests in
chapters 04 and 05 already exercise most of these paths; this task is
about *demonstrating* them with documentation, not re-implementing them.

For each probe below, send the request via `http-tests/banking.http` (or
`curl`), record what came back, and write one sentence in
`docs/dast-payloads.md` explaining why the response is correct.

| Probe | Expected | Why correct |
|---|---|---|
| Submit a transaction with `accountId` you don't own | **404** | Not 403 — 403 would confirm the account exists |
| `GET /api/v1/admin/users` as a `CUSTOMER` token | **403** | URL filter + `@PreAuthorize` both fire |
| Replay a JWT with a tampered signature (flip one byte) | **401** | RS validates signature on every request |
| Replay an expired JWT (manually craft one with `exp` in the past) | **401** | RS validates `exp` claim |
| `POST /api/v1/transactions` to the BFF with no `X-XSRF-TOKEN` | **403** | CSRF protection on mutating verbs |

To get a valid token to mutate, sign in as alice, then in DevTools →
Application → Cookies, copy the `JSESSIONID`. The BFF holds the access
token; you'll need to call the RS directly with a Bearer to test
JWT-tampering paths. Your instructor can walk you through extracting one
if needed.

If you finish early, run ZAP **active** against `/api/v1` (not baseline)
or design a second class (bulk-transfer abuse — submit 50 withdrawals in
a tight loop, document what landed in the DB).

## Task 7.5 — Hardening checklist (~10 min)

Run through these manually. They are the items the rubric grades during
the demo:

| # | Check | How to verify |
|---|---|---|
| 1 | DevTools after sign-in shows only `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. Storage is empty. | Sign in, open DevTools → Application → Cookies + Storage |
| 2 | `curl http://localhost:8081/api/v1/accounts` direct to RS → 401 | Bearer-less request |
| 3 | POST without `X-XSRF-TOKEN` → 403 | Use curl/Postman without the header |
| 4 | Customer hitting `/api/v1/admin/users` → 403 | Sign in as alice |
| 5 | Customer hitting another customer's `/api/v1/accounts/{id}` → **404** (not 403) | Use a known account ID owned by another user |
| 6 | Sign out → next API call → 401 | Click Sign out, check Network tab |
| 7 | External transfer with WireMock 503 → 502, balance unchanged | Amount > 10000 |

Any row that fails is a **must-fix** before the demo.

## Task 7.6 — Security write-up (~15 min)

Update `docs/security-decisions.md` to cover, briefly:

- Why BFF over pure-SPA tokens (one paragraph).
- Where tokens live in your build (server-side, never browser).
- How CSRF is handled (cookie token, mutations echo it).
- How the BFF authenticates to the Resource Server (the WebClient OIDC
  filter — read it in the scaffolding so you can describe it).
- How RBAC is enforced (URL filter + `@PreAuthorize` + service-layer
  ownership returning 404).
- What you would do differently in production (Redis session store, HTTPS,
  rate limiting, an outbox pattern for Kafka).

One page is plenty. The grader wants to see you understood the model,
not a thesis.

## Done when

- [ ] All four `grep` sweeps in 7.1 are clean.
- [ ] One Checkmarx scan run; `docs/sast-findings.md` has a row per finding.
- [ ] One ZAP baseline run; `docs/dast-payloads.md` has the baseline section
      populated.
- [ ] All five authorization-probe rows in 7.4 documented with response +
      one-line rationale.
- [ ] All seven hardening checklist rows in 7.5 behave correctly.
- [ ] `docs/security-decisions.md` reflects the actual implementation.

Next: [08-deliverables-and-demo.md](./08-deliverables-and-demo.md).
