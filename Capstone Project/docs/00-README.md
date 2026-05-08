# Capstone — Student Workbook

Welcome to the Banking Capstone. The scaffolding under `Capstone spec/scaffolding/`
is a working skeleton: builds compile, services start, the database schema migrates,
the React SPA is **fully implemented**, and the **security plumbing** (BFF
SecurityConfig, WebClient OIDC filter, Resource Server filter chain, OAuth login
flow) is wired up for you.

Your job in two days is to fill in the backend domain logic and security
converter, write the tests that prove they behave, and demonstrate the BFF
pattern end-to-end. **You will not write any React or TypeScript** — the
frontend is provided. You will, however, need to read it well enough to demo
it and answer questions about it.

## How to use these docs

Each chapter maps to one phase of the build. Work them in order — later
chapters assume the code you wrote in earlier ones is in place.

| # | Chapter | When |
|---|---|---|
| 01 | [Environment Check](./01-environment-check.md) | Day 1, first hour |
| 02 | [Codebase Tour](./02-codebase-tour.md) | Day 1, second hour |
| 03 | [Deposit & Withdrawal](./03-deposit-and-withdrawal.md) | Day 1 morning |
| 04 | [Resource Server Security](./04-resource-server-security.md) | Day 1 afternoon |
| 05 | [Transfers](./05-transfers.md) | Day 2 morning |
| 06 | [Frontend Tour](./06-frontend.md) | Day 2 mid-day |
| 07 | [Security Validation](./07-security-validation.md) | Day 2 afternoon |
| 08 | [Deliverables & Demo](./08-deliverables-and-demo.md) | Day 2 final block |

## Day-by-day plan

**Day 1 (~8 hours):**

| Block | Activity |
|---|---|
| 1 hr | Environment check (chapter 01) |
| 1 hr | Codebase tour (chapter 02) |
| 3 hrs | Deposit, Withdrawal, three unit tests (chapter 03) |
| 1.5 hrs | Read JwtAuthConverter + two integration tests (chapter 04) |
| 1.5 hrs | Buffer — catch up on chapter 03 work or get a head start on chapter 05 reading |

**Day 2 (~8 hours):**

| Block | Activity |
|---|---|
| 4 hrs | TRANSFER_OUT (internal + external) + tests (chapter 05) |
| 30 min | Frontend tour — read, run, prepare to demo (chapter 06) |
| 1 hr | PaymentService + JwtAuthConverter unit tests (chapter 06) |
| 1 hr | Hardening sweep + Checkmarx scan + DAST baseline (chapter 07) |
| 30 min | One custom DAST payload class (chapter 07) |
| 1 hr | Demo dry-run + final polish (chapter 08) |

Day 2 is full but achievable. If you fall behind on Day 1, push the
WITHDRAWAL unit tests into Day 2 morning. The chapter 04 read on
`JwtAuthConverter` is short — don't skip it; the whole RBAC story
depends on understanding what that class does.

## What's pre-built (don't reinvent)

The following are **already implemented** in the scaffolding. Read them
during the codebase tour; do not rewrite them.

- Resource Server `SecurityConfig` filter chain
- Resource Server `JwtAuthConverter` (JWT → local user mapping with
  first-login auto-provisioning)
- BFF `SecurityConfig` (session + CSRF + OAuth2 login)
- BFF `WebClientConfig` (the OIDC bearer-token filter that makes BFF work)
- All proxy controllers, DTOs, entities, repositories, exceptions, Kafka
  publisher, Flyway migrations, mock-auth users + client
- **The entire React SPA** — apiClient, hooks, page components, shells,
  tests. You read it; you don't write it.

You will still **read** every one of these as part of chapter 02 — the demo
expects you to explain why they look the way they do, and the demo Q&A may
include "walk us through how the SPA submits a transaction" or "what happens
in the SPA when an API call returns 401?"

## What "done" looks like

The capstone is graded against a 7-section rubric (weights below). The
full Definition of Done checklist is in chapter 08 — read it on Day 1
morning, tick boxes only when you've **observed** the behaviour, not
when you believe the code is right.

| Section | Weight |
|---|---|
| Backend Implementation | 25% |
| Frontend Implementation | 15% |
| Security Integration | 20% |
| Testing & Security Validation | 15% |
| AI-Assisted Development | 10% |
| Code Quality | 10% |
| Collaboration & Presentation | 5% |

## Ground rules

- **Do not delete the `// TODO` blocks until the matching code passes.**
- **Do not move or rename files** the spec calls out — tests import them by name.
- **Use the spec docs as the contract.** If your code disagrees with
  `03-api-contract.md` or `04-security.md`, the spec wins.
- **Use Copilot, but read what it produces.** "Copilot wrote it" is the wrong
  answer in the demo Q&A.
- **Never commit secrets.** `.env` is gitignored — keep it that way.

## Working agreement

You're graded as a team but you should still split the surface so you don't
trip over each other. Agree on this before writing code (write it down in
`docs/team-plan.md`):

| Lead role | Owns |
|---|---|
| Resource Server | TransactionService, RS integration tests, Kafka verification, reading + explaining JwtAuthConverter |
| Frontend | apiFetch, useMe, page components, smoke tests |
| Quality / Security | Hardening sweep, SAST, demo script |

Leads, not silos. Everyone touches everything; the lead owns the final review.

Good luck. Start with [01-environment-check.md](./01-environment-check.md).
