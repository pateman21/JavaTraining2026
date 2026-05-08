# Banking Capstone — Scaffolding

A full-stack banking application demonstrating enterprise Spring Boot patterns,
OAuth2 / JWT security, Kafka event publishing, WireMock integration testing,
and a React 18 single-page application.

> **Students:** the workbook that drives what you build is provided seperately
> This README documents the scaffolding's architecture and how to run it.

## Architecture overview

```
Browser (React 18 — Vite, port 5173)
         | JSESSIONID cookie  | CSRF header
         v
BFF  (Spring Boot, port 8080)
  OAuth2 client - PKCE or Authorization Code
  Session-based; proxies REST calls to Resource Server
         | Bearer JWT
         v
Resource Server  (Spring Boot, port 8081)
  Stateless JWT; two-issuer decoder (mock-auth + Google)
  JwtAuthConverter -> upserts BANK_USERS row on first login
  Kafka producer -> transactions.completed topic
         |
         v
Oracle DB (XEPDB1)  +  Kafka (localhost:9092)

Mock Auth Server  (Spring Authorization Server, port 9000)
  In-memory users: alice/password (CUSTOMER), admin/password (ADMIN)
  Confidential client: bank-client-bff
  Custom login page at /login
  Adds "role" claim to issued JWTs
```

## Quick start

**TL;DR — open four Command Prompt windows:**

```bat
REM 1. Mock Auth Server
scripts\start-mock-auth.bat

REM 2. Resource Server
scripts\start-resource-server.bat

REM 3. BFF
scripts\start-bff.bat

REM 4. Frontend
scripts\start-frontend.bat
```

Then open **http://localhost:5173** and sign in with `alice / password` (demo) or `admin / password`.

To use WireMock (payment-processor stub):

```bat
scripts\start-wiremock.bat
```

## Module map

| Module | Port | Description |
|---|---|---|
| `mock-auth` | 9000 | Spring Authorization Server |
| `bff` | 8080 | OAuth2 client + reverse proxy |
| `resource-server` | 8081 | JWT-secured REST API |
| frontend | 5173 | React 18 (Vite) |

## Running tests

```bat
REM All backend tests
cd backend
mvn test

REM Frontend unit tests (Vitest)
cd frontend
npm install
npm test
```

## Project structure

```
scaffolding\
+-- backend\                          Multi-module Maven project
|   +-- mock-auth\                    Spring Authorization Server (port 9000)
|   +-- bff\                          OAuth2 client + reverse proxy (port 8080)
|   +-- resource-server\              JWT-secured REST API (port 8081)
|       +-- src\main\java\com\example\banking\
|           +-- config\               Security, CORS, JWT decoder, properties
|           +-- controller\           Health, User, Account, Transaction
|           +-- dto\                  Request/response records
|           +-- exception\            Domain exceptions + GlobalExceptionHandler
|           +-- kafka\                TransactionEvent + Publisher
|           +-- model\                JPA entities + enums
|           +-- repository\           Spring Data interfaces
|           +-- security\             JwtAuthConverter (sub -> local user + role)
|           +-- service\              AccountService, TransactionService, PaymentService
+-- frontend\                         React 18 + Vite SPA
|   +-- src\
|       +-- api\                      apiFetch + per-resource modules
|       +-- hooks\                    useMe hook
|       +-- components\               AccountCard, TransactionList, TransactionForm, ErrorBanner
|       +-- routes\                   Page components
+-- docs\                             Architecture, security decisions, demo script, etc.
+-- scripts\                          Startup .bat scripts (one per service)
+-- wiremock-stubs\                   Payment Processor stubs
+-- http-tests\                       IntelliJ HTTP client test file
```

## Common gotchas

- **Flyway error: "Found non-empty schema(s) without baseline"** — re-run
  `scripts\setup-oracle.sql` to drop and recreate the `bankapp` user.
- **Oracle "ORA-12514"** — check the PDB name with `lsnrctl status` and update
  `ORACLE_URL` in `.env`.
- **WireMock JAR missing** — `start-wiremock.bat` downloads it automatically on
  first run via PowerShell. If the VM has no internet access, manually place
  `wiremock-standalone-3.6.0.jar` in `scripts\.cache\`.
