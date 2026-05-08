# 05 — Transfers

Day 2 morning. You will implement `applyTransferOut`, the most complex piece
of the banking domain, then write the unit and integration tests that prove
both branches behave under success and failure.

## Contract for `TRANSFER_OUT` (same endpoint as DEPOSIT/WITHDRAWAL)

**Request body:**
```json
{
  "accountId": "acc_001",
  "type": "TRANSFER_OUT",
  "amount": 200.00,
  "counterparty": "acc_002",
  "description": "moving savings"
}
```

- `counterparty` — **required** for TRANSFER_OUT. If it matches one of
  the caller's own account IDs → internal transfer. Otherwise → external
  (Payment Processor call).
- All other validation rules same as DEPOSIT/WITHDRAWAL (chapter 03).

**Status codes (mapped by `GlobalExceptionHandler`):**

| Result | HTTP | `code` |
|---|---|---|
| Internal transfer success | 201 | — (response body has **two** rows) |
| External transfer success | 201 | — (response body has one row) |
| Insufficient balance | 422 | `INSUFFICIENT_FUNDS` |
| Counterparty missing/blank | 422 | `BUSINESS_RULE_VIOLATION` |
| Payment Processor 4xx/5xx | 502 | `PAYMENT_PROCESSOR_ERROR` |
| Account not owned | 404 | `NOT_FOUND` |

## The shape of the problem

A `TRANSFER_OUT` has two paths that look superficially similar but differ
significantly in their failure model:

- **Internal transfer** — the counterparty is one of the caller's own
  accounts. Two transaction rows are created (one `TRANSFER_OUT` on the
  source, one `TRANSFER_IN` on the destination), they share a single
  `transferGroupId`, and both rows commit in the same DB transaction.
- **External transfer** — the counterparty is anything else. The Payment
  Processor (WireMock) is called. On success, you debit the account and
  persist a single `TRANSFER_OUT` row with status `COMPLETED`. On
  failure, you persist a `FAILED` row but **do not debit** — and the
  exception bubbles up so the global handler returns 502.

The "do not debit on failure" rule is the single most important safety
invariant in the codebase. Money must never leave an account if the
processor did not confirm success.

## Task 5.1 — Implement applyTransferOut

**File:** `resource-server/src/main/java/com/example/banking/service/TransactionService.java`
**Method:** `applyTransferOut(AccountEntity source, NewTransactionRequest req, String callerUserId)`

The Javadoc above the TODO walks through the algorithm. The big steps:

1. **Shared validation:** counterparty must be non-null and non-blank;
   `requireFunds(source, amount)` to guard against overdraft.
2. **Determine internal vs external:** load `accounts.findByOwnerId(callerUserId)`.
   If any of those owned accounts has an ID equal to `req.counterparty()`,
   it's an internal transfer. Otherwise external.
3. **Internal branch:**
   - Generate `transferGroupId = "grp_" + UUID.randomUUID()`.
   - Debit source, save it, persist a `TRANSFER_OUT` row.
   - Credit destination, save it, persist a `TRANSFER_IN` row with the same
     `transferGroupId`.
   - Both rows have status `COMPLETED`. Return both DTOs.
4. **External branch:**
   - Generate a fresh `idempotencyKey = UUID.randomUUID().toString()`.
   - Wrap the `paymentService.submitExternalTransfer(...)` call in a try/catch
     for `PaymentProcessorException`.
   - On success: debit, save, persist `COMPLETED` row, return.
   - On failure: persist a `FAILED` row (do **not** touch the balance), then
     `throw e`. The global exception handler maps it to 502.

`@Transactional` on `submit(...)` ensures both internal-branch rows commit
atomically. If your implementation throws between the debit and the credit,
JPA rolls both back.

**Hints:**

- Use the `persistRow(...)` helper for every row. Don't save `TransactionEntity`
  manually.
- The internal branch must produce **exactly two rows**. The two-row response
  is what the SPA renders.
- The external branch's `transferGroupId` is `null` — only internal transfers
  share a group.
- Don't catch any exception other than `PaymentProcessorException`. Bubbling
  up `RuntimeException`s would mask bugs.

## Task 5.2 — Smoke test in the running app

Start WireMock if it isn't running:

```bash
scripts/start-wiremock.bat
```

Then:

| Request | Expected |
|---|---|
| Internal transfer between two of alice's accounts | **201**, two rows, balances net to zero |
| External transfer to `EXT-ACCT-001`, amount 50.00 | **201**, balance debited 50.00 |
| External transfer to `EXT-ACCT-001`, amount 20000 | **502**, balance unchanged, FAILED row persisted |
| External transfer with insufficient balance | **422**, no DB write |

WireMock returns 503 for any `POST /payments` with `amount > 10000`. That's
how you exercise the failure path.

After the failure case, query the database:

```sql
SELECT TRANSACTION_ID, STATUS FROM TRANSACTIONS
 WHERE ACCOUNT_ID = '<your-acc>' ORDER BY CREATED_AT DESC FETCH FIRST 1 ROWS ONLY;
```

Status should be `FAILED`. If it's `COMPLETED`, your error path is wrong.

## Task 5.3 — Unit tests (Day 2 set)

**File:** `resource-server/src/test/java/com/example/banking/service/TransactionServiceTest.java`

Implement these two. The `external_transfer_success_completes_and_debits`
case is pre-implemented — read it; it shows the Mockito pattern for the
success path.

| Test | What it proves |
|---|---|
| `internal_transfer_creates_two_rows_with_same_transfer_group_id` | Two rows linked, source debited, destination credited |
| `external_transfer_failure_persists_failed_row_rethrows_and_does_not_debit` | Balance unchanged; `PaymentProcessorException` re-thrown |

**Mockito patterns specific to this set:**

- `when(accounts.findByOwnerId("usr_1")).thenReturn(List.of(source, dest))` —
  controls whether the transfer is internal.
- `doThrow(new PaymentProcessorException("503"))
   .when(paymentService).submitExternalTransfer(any(), any(), any(), any(), any())`
  — drives the failure path.

The failure-path test is the most important one in the entire test suite.
It enforces the "no debit on failure" invariant. If it ever goes red in CI,
that's a money-loss bug.

Run:

```bash
mvn -pl backend/resource-server test -Dtest=TransactionServiceTest
```

The whole suite should be green (your three Day-1 + your two Day-2 + the
pre-implemented ones).

## Task 5.4 — Integration tests for transfers

**File:** `resource-server/src/test/java/com/example/banking/controller/AccountControllerIntegrationTest.java`

Implement these two. The other transfer-related cases (admin 403, health,
internal-transfer two-row, processor-503) are pre-implemented — read them
to see the patterns.

| Test | What it proves |
|---|---|
| `deposit_happy_path_returns_201_and_publishes_kafka_event` | 201 + `publishEvent` called once |
| `internal_transfer_returns_201_with_two_transaction_rows` | Response has 2 rows with the same `transferGroupId` |

Wait — `internal_transfer_returns_201_with_two_transaction_rows` is on the
"pre-implemented" list above. Read the rest of this section carefully:
**you only implement `deposit_happy_path_returns_201_and_publishes_kafka_event`.**
The other tests are reference patterns.

**Patterns for the deposit test:**

- Stub the service with `MockBean` (already declared at the top of the class).
- Drive the response by stubbing `transactionService.submit(...)` to return
  the DTO list you want — the controller's logic does not run a real
  `TransactionService`, so you control its output entirely.
- Stub `transactionService.toEvent(...)` to return a `TransactionEvent`.
- Assert `status().isCreated()` and the JSON shape via `jsonPath`.
- Verify the controller called `transactionService.publishEvent(any(TransactionEvent.class))`
  exactly once.

Run:

```bash
mvn -pl backend/resource-server test -Dtest=AccountControllerIntegrationTest
```

Full suite should be green.

## Task 5.5 — Verify Kafka is publishing

Open a terminal and start a console consumer:

```bash
kafka-console-consumer.bat ^
  --bootstrap-server localhost:9092 ^
  --topic transactions.completed ^
  --from-beginning ^
  --property print.key=true ^
  --property key.separator=" | "
```

Submit a deposit through the SPA. You should see one event print, keyed by
the account ID. Submit an internal transfer; you should see two events.

If the consumer doesn't see anything, check:

- The topic name in `application.yml` matches `transactions.completed`.
- The publish in `TransactionController` happens **after** `transactionService.submit(...)`
  returns, not inside `@Transactional`.
- The broker is reachable on the configured port.

## Done when

- [ ] All four manual smoke tests in 5.2 behave correctly.
- [ ] Two new unit tests pass; suite is fully green.
- [ ] One new integration test passes; suite is fully green.
- [ ] Kafka events appear on the console consumer for every transaction.
- [ ] Failed external transfers leave the balance untouched (verify in Oracle).

Next: [06-frontend.md](./06-frontend.md).
