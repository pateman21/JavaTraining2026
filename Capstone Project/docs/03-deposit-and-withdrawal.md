# 03 — Deposit & Withdrawal

Day 1 morning. You will implement the two simplest transaction types in
`TransactionService`, then write the unit tests that prove they behave.

## Contract for `POST /api/v1/transactions` (DEPOSIT and WITHDRAWAL)

**Request body:**
```json
{
  "accountId": "acc_001",
  "type": "DEPOSIT",
  "amount": 50.00,
  "counterparty": null,
  "description": "Paycheque"
}
```

- `type` — `DEPOSIT` or `WITHDRAWAL` for this chapter (`TRANSFER_OUT` is
  chapter 05).
- `amount` — `BigDecimal`, > 0, up to 4 decimal places.
- `counterparty` — must be `null` for DEPOSIT and WITHDRAWAL.
- `description` — optional, max 255 chars.

**Status codes (mapped by `GlobalExceptionHandler`):**

| Result | HTTP | `code` |
|---|---|---|
| Success | 201 | — |
| Bean Validation failure | 400 | `VALIDATION_FAILED` |
| Account not owned by caller | 404 | `NOT_FOUND` |
| Withdrawal would overdraft | 422 | `INSUFFICIENT_FUNDS` |
| Counterparty present (not allowed) | 422 | `BUSINESS_RULE_VIOLATION` |

## What's already wired

- `TransactionController` accepts `POST /api/v1/transactions`, validates the DTO,
  delegates to `TransactionService.submit(...)`, and publishes Kafka events
  **after** the DB transaction commits.
- `TransactionService.submit(...)` resolves the source account, enforces
  ownership (404 if you don't own it), and dispatches by type.
- `TransactionService.persistRow(...)` builds and saves a `TransactionEntity`
  for you. Use it; do not call `transactions.save(...)` directly.
- `TransactionService.requireFunds(source, amount)` throws
  `InsufficientFundsException` if the balance can't cover the amount. Use it.
- `GlobalExceptionHandler` already maps every domain exception to the right
  HTTP status and RFC 7807 envelope.

You only need to fill in two private methods plus their tests.

## Task 3.1 — Implement DEPOSIT

**File:** `resource-server/src/main/java/com/example/banking/service/TransactionService.java`
**Method:** `applyDeposit(AccountEntity source, NewTransactionRequest req)`

Read the Javadoc above the method. It lists the five steps. Implement each one
in order.

**Hints:**

- A DEPOSIT must reject any non-null `counterparty`. Throw
  `BusinessRuleException` with a clear message; `GlobalExceptionHandler` will
  map it to **422**.
- `BigDecimal` is immutable. `source.getBalance().add(req.amount())` returns a
  **new** `BigDecimal`. If you forget to call `setBalance`, the deposit silently
  vanishes.
- After updating the balance, persist the account before persisting the
  transaction row.
- Status is `COMPLETED`. There is no async deposit in this capstone.

**Gotchas:**

- Do not generate a `transferGroupId` — deposits aren't transfers.
- Do not return the entity. Return `TransactionDto.from(row)` — DTOs are the
  controller's contract.

## Task 3.2 — Implement WITHDRAWAL

**Method:** `applyWithdrawal(AccountEntity source, NewTransactionRequest req)`

Similar to DEPOSIT, with one critical extra step: **check funds before
mutating the balance.**

**Hints:**

- The Javadoc says "step 2 must happen BEFORE step 3." This is not stylistic.
  The unit test in 3.4 specifically asserts that the balance is unchanged when
  `InsufficientFundsException` is thrown.
- `requireFunds(source, amount)` does the check for you. One call is enough.
- Subtract with `source.getBalance().subtract(req.amount())`. Again, immutable —
  call `setBalance`.
- Counterparty must be null for withdrawals.

## Task 3.3 — Smoke test in the running app

With both methods implemented and the resource server restarted, use
`http-tests/banking.http` (or `curl` with a manually-issued JWT — see
`scaffolding/docs/0-student-guide.md` for the procedure):

| Request | Expected |
|---|---|
| POST DEPOSIT 50.00 to a seeded account | **201**, balance increases by 50.00 |
| GET the account | new transaction visible, status `COMPLETED` |
| POST WITHDRAWAL 10.00 | **201**, balance decreases by 10.00 |
| POST WITHDRAWAL bigger than balance | **422**, `code: INSUFFICIENT_FUNDS`, balance unchanged |
| POST DEPOSIT with `counterparty: "acc_x"` | **422**, `code: BUSINESS_RULE_VIOLATION` |

If any row is wrong, fix it before writing tests.

## Task 3.4 — Unit tests (Day 1 set)

**File:** `resource-server/src/test/java/com/example/banking/service/TransactionServiceTest.java`

Implement these three `@Test` methods. The class header already wires up
Mockito and supplies an `account(id, owner, balance)` helper. The
ownership-404 path is already covered by an integration test in chapter 04
and a pre-implemented test in this file — you only fill in the three below.

| Test | What it must prove |
|---|---|
| `deposit_credits_balance_and_returns_completed_row` | After a 50.00 deposit on a 200.00 balance, balance is 250.00, returned row has status `COMPLETED` |
| `withdrawal_within_balance_succeeds_and_debits` | Balance goes from 100.00 to 70.00, status `COMPLETED` |
| `withdrawal_below_balance_throws_insufficient_funds_and_does_not_debit` | Balance unchanged, exception is `InsufficientFundsException` |

**Mockito patterns you will need:**

- `when(accounts.findById("acc_1")).thenReturn(Optional.of(acct))` — stub a return value.
- `when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0))` — echo
  the argument back so the entity flows through unchanged.
- `assertThatThrownBy(() -> svc.submit(...)).isInstanceOf(SomeException.class)`
  — assert an exception type.
- `assertThat(acct.getBalance()).isEqualByComparingTo("70.00")` — compare
  `BigDecimal` ignoring scale (so `70.0` equals `70.00`).

**Do not** mock the class under test. **Do not** assert that a method was
called without also asserting on the result. The rubric grades whether your
assertions actually prove something.

Run them:

```bash
mvn -pl backend/resource-server test -Dtest=TransactionServiceTest
```

All four should be green before you move on.

## Definition of done for this chapter

- [ ] Both methods implemented.
- [ ] Manual smoke tests in 3.3 pass.
- [ ] All three unit tests you wrote are green (the suite has more —
      pre-implemented ones should also still pass).
- [ ] No `// TODO` comments left in the two methods you wrote.

Next: [04-resource-server-security.md](./04-resource-server-security.md).
