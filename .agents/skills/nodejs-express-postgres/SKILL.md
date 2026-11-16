---
name: nodejs-express-postgres
description: >-
  Backend API development skill for amaMomoApp using Node.js, Express.js, and
  PostgreSQL. Use when designing database schemas, implementing auth, 7-day
  free trial logic, transaction sync endpoints, or backend security.
---

# Node.js, Express & PostgreSQL Backend (amaMomoApp)

Build and maintain the backend API and PostgreSQL database for `amaMomoApp`.

## Stack & Conventions

- **Runtime & Framework:** Node.js with Express.js (REST JSON API).
- **Database:** PostgreSQL with parameterized queries / migrations (no raw string interpolation in SQL).
- **Auth & Trial Lifecycle:**
  - Authenticate agents securely (hashed passwords, signed tokens).
  - Activate the automatic 7-day free trial only after the backend successfully creates the user/agent account.
  - Enforce subscription/trial status server-side on protected endpoints.
- **Offline-First Sync:**
  - Use idempotency keys / client UUIDs for synced transactions so retries never duplicate financial records.
  - Validate all request payloads and timestamps.

## Security & Configuration

- Keep all secrets (`DATABASE_URL`, JWT secrets, `PAYSTACK_SECRET_KEY`) in environment variables, never committed to source control.
- Do not introduce Docker unless explicitly required.
- Log structured errors without leaking sensitive tokens, PINs, or credentials.

## Verification

- Run database migrations cleanly against PostgreSQL.
- Run backend unit/integration tests (e.g., `npm test`) and verify health/auth/sync endpoints.
