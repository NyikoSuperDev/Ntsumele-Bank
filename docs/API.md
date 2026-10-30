# HTTP API

All endpoints are same-origin. Authenticate using the session cookie, not a supplied customer or owner ID. Financial values are read from the authorized ledger projection. JSON property names are strict; unknown mutation fields return 400.

## Session and CSRF

1. `GET /api/csrf` returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` and establishes a cookie-backed session.
2. `POST /api/login` with `application/x-www-form-urlencoded` fields `username` and `password`, the session cookie, and the returned CSRF header. Success is 200 with no body. Login rotates the session and CSRF state.
3. `GET /api/me` returns `{ "username": "amy", "displayName": "...", "role": "CUSTOMER" }`.
4. Fetch a new `/api/csrf` token before every mutation, retaining cookies. Do not cache a pre-login token across login.
5. `POST /api/logout` with CSRF invalidates the session and returns 204.

The Angular client implements these steps. Missing authentication returns 401; missing/expired CSRF or a prohibited role returns 403. The default limiter returns 429 and `Retry-After` after 15 login attempts from the direct peer IP in a minute. A production gateway must provide distributed throttling.

## Customer endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/accounts` | Authorized accounts with masked numbers and balances |
| GET | `/api/transactions` | Page `{content,totalElements,totalPages,number,size}` |
| GET | `/api/disputes` | Authorized page of case summaries, without full timelines |
| GET | `/api/disputes/summary` | Authorized aggregate counts: total, active, awaitingInformation, resolved |
| GET | `/api/disputes/{id}` | Authorized case and timeline |
| POST | `/api/disputes` | Create a separate case; 201 and Location header |
| POST | `/api/disputes/{id}/information` | Add requested information and return case |

Transactions accept optional `accountId`, `page` (zero-based), `size` (1–100), `search` (merchant/reference, max 120 characters), `status` (`BOOKED`, `PENDING`, or `REVERSED`), and `disputeStatus` (`NOT_DISPUTED`, `DISPUTED`, or `DISPUTE_RESOLVED`). The two statuses are independent and their filters can be combined; invalid values return 400. Totals and pagination use the same account-authorized filters. Sorting is stable by booking time then transaction ID. Dispute lists accept `page` (zero-based), `size` (default 10, 1–100), and `status` (empty/all, `ACTIVE`, or an exact case status). Their response is `{content,totalElements,totalPages,number,size}` rather than the previous array. Rows contain `id`, `caseReference`, `transaction`, `reason`, `status`, timestamps, `version` and `historyCount`; description, resolution and full history are fetched from the detail endpoint. Ordering is `createdAt DESC, id DESC`. A nonempty page uses four SQL statements regardless of page size: count, bounded case rows, a batch of transaction projections and grouped history counts. Count/rows use repeatable-read consistency. Empty or deep pages remain bounded.

Create-dispute request:

```http
POST /api/disputes
Content-Type: application/json
Idempotency-Key: 6f5f919a-b7c1-4cb1-b87e-5c662e7f1c80
X-CSRF-TOKEN: <fresh token>

{
  "transactionId": "tx-1001",
  "reason": "UNRECOGNIZED",
  "description": "I do not recognize this card purchase."
}
```

Reasons: `UNRECOGNIZED`, `DUPLICATE`, `INCORRECT_AMOUNT`, `GOODS_NOT_RECEIVED`. Description length is 10–2000 characters. No amount, reference, account owner, or case status may be submitted. The idempotency key is 8–100 characters from letters, digits, `.`, `_`, `:`, and `-`. Retain it after an uncertain response; retry the identical payload with the same key. Keys are scoped to the signed-in principal. Active-case keys are retained; closed-case keys become cleanup-eligible only after both their creation and the case's last update are older than the configured retention window (default 90 days). While a replay record is retained, retrying returns the current representation of the same case, including after closure; the response is also 201. After legitimate expiry/purge, that old key has no replay guarantee and may represent a new eligible case. Cases, histories and audit events are retained independently.

Information response body: `{ "message": "Requested supporting details...", "version": 2 }`. It is accepted only for an authorized case in `AWAITING_INFORMATION`, with a 5–2000 character message and the current version. It appends history and returns the case to `UNDER_REVIEW`.

A case includes `id`, `caseReference`, `transaction`, `reason`, `description`, `status`, `createdAt`, `updatedAt`, `resolution`, `version`, and `history`. Each history event includes `id`, `fromStatus`, `toStatus`, `actor`, `occurredAt`, and `note`. Timestamps are UTC ISO 8601. Currency is an ISO code; the demo uses ZAR. Amounts are signed: debits are negative. The response includes `observedAt` for ledger freshness and `disputable`/`activeDisputeId` for the current eligibility view; creation always checks eligibility again server-side. Transaction responses also include `disputeStatus` and nullable `latestDisputeId`: an active case yields `DISPUTED`, earlier cases without an active case yield `DISPUTE_RESOLVED`, and no case ever yields `NOT_DISPUTED`. `latestDisputeId` selects the active case first, otherwise the newest created case (ID breaks ties), allowing historical navigation after resolution. These fields also appear on the transaction nested in a case response and reflect the current transaction classification. Ledger `status` remains unchanged. See [status rules](TRANSACTION-STATUS.md).

## Staff endpoints

`GET /api/staff/disputes` returns the same paginated summary shape and status/page/size filters for the staff queue. `GET /api/staff/disputes/summary` returns global staff aggregate counts. Neither list hydrates full case histories. `GET /api/staff/disputes/{id}` retrieves a case. `POST /api/staff/disputes/{id}/status` takes:

```json
{ "status": "UNDER_REVIEW", "note": "Assigned for investigation.", "version": 0 }
```

The note is 5–2000 characters. Staff role is required. Valid transitions are documented in the README; a resolved case is terminal. A resolution note records the case outcome, not a ledger posting. The demo grants all case reviewers global case access; real staff need institution-approved assignment, least-privilege roles, and monitoring.

## Errors and retries

Errors use `{ "code": "ACTIVE_DISPUTE_EXISTS", "message": "..." }` without internal SQL or stack traces.

| HTTP | Meaning / client action |
| --- | --- |
| 400 | Invalid fields, query, enum, or idempotency key; correct the request |
| 401 | Sign in again; keep the user's draft in memory only |
| 403 | Prohibited role or invalid CSRF; refresh token/session as appropriate |
| 404 | Object missing or outside the customer's account access |
| 409 | Active case, ineligible/stale transaction, invalid transition, stale version, or conflicting key; refresh and inspect the code |
| 413 | JSON mutation body exceeds 16 KiB, including requests without a content length; shorten the request |
| 429 | Login throttled; observe `Retry-After` |
| 503 | Database contention/statement timeout (`BUSY`, Retry-After: 1) or connection/resource failure (`DATABASE_UNAVAILABLE`, Retry-After: 5); retry the same creation payload/key with bounded backoff |

Never blindly retry a mutation with a new key after a timeout. First reload the case list, or retry creation with its original key. Lifecycle writes use a case version rather than an idempotency key: reload after an uncertain result to determine whether the transition committed. PostgreSQL retains idempotency records across application restarts. Backup/restore consistency and the approved retention horizon remain necessary for guarantees across recovery.

`GET /actuator/health` is a minimal unauthenticated liveness/readiness aid; health details and all customer data are not publicly exposed. PostgreSQL is accessible only through the configured private database connection, not an application database console.

Production transport uses the configured trusted proxy boundary: secure responses carry HSTS; plaintext reads receive a 308 redirect to `PUBLIC_ORIGIN`, and plaintext writes receive 403 `HTTPS_REQUIRED`. Forwarded headers from an untrusted socket peer cannot make a request secure. The minimal internal health endpoint remains available over HTTP.
