# Operations and recovery

This project is a single-process reference application with a real Angular client and Spring Boot API. Use synthetic data in the supplied demo. Its business controls are designed for serious review, but running the default container does not make it a live banking service.

## PostgreSQL storage and deployment boundary

PostgreSQL 17 is the application's only database engine. Flyway creates and validates its schema; the JDBC driver and PostgreSQL Flyway database module are included in the application. The H2 driver and file profile are removed.

| Configuration | Behaviour | Operational consequence |
| --- | --- | --- |
| Compose `database` | PostgreSQL 17 with named `postgres-data` volume; no host database port by default | Cases, history, audit, idempotency, accounts and seeded ledger snapshots persist across application/container restarts. Protect and back up the volume's data. |
| Default `demo` | Fictional identities and ledger snapshots seeded into an empty database | Keep private and use synthetic data. Existing records are preserved on restart; ledger observations are not refreshed by restarting. |
| `secure` | Secure session cookie; no demo profile implicitly enabled | Requires HTTPS and institution-provisioned identities, entitlements and ledger data. This flag is not a complete production configuration. |

Compose waits for `pg_isready` before starting the application. The optional `compose.local.yaml` adds only `127.0.0.1:5432` for host-run Java/tests. The app uses a separate non-superuser role (`DB_USERNAME`, `DB_PASSWORD`), and the database container alone receives bootstrap administrator credentials (`POSTGRES_USER`, `POSTGRES_PASSWORD`). The initialization script grants the application role ownership of `public` for Flyway. For production, use a separate controlled migration role and a runtime role limited to necessary table/sequence access; define grants with each migration and run migrations through the approved release process.

Copy `.env.example` to `.env`, generate different passwords, and keep credentials outside source control/build contexts. Compose initialization variables and `docker-entrypoint-initdb.d` scripts are applied **only to an empty volume**. An existing database keeps its roles, passwords and data. Changing `.env` alone will make connections fail if it no longer matches the stored password. Rotate an existing role through an authenticated administrative session (`psql`'s interactive `\password role_name` avoids putting a password in shell history), update the app secret and restart/reconnect the pool in a coordinated procedure. Never delete the volume as a password-rotation technique.

A persistent volume provides local durability, not backups, encryption, multi-zone availability or a regulatory retention policy. Production needs an institution-approved PostgreSQL service, encrypted storage/backups, protected secret distribution, network controls and TLS with certificate/hostname verification (for example `sslmode=verify-full` plus a trusted root certificate for a remote JDBC connection). Define failover, key custody and restore testing. Sessions currently belong to one JVM; multi-instance operation needs an approved session store or identity architecture. Database persistence does not persist those sessions.

### Moving from the previous H2 delivery

The PostgreSQL Flyway baseline targets a **new PostgreSQL database**. Do not copy an H2 file into the PostgreSQL volume, reuse its Flyway history as if the engines were identical, or expect a driver change to transfer rows. No data importer is bundled. Earlier in-memory H2 data is lost when its process stops; export anything needed while that instance is still running. Preserve an existing H2 file database and its matching application version before doing any cutover.

For a data-bearing migration: inventory/export source records using trusted administration tooling; map types/time zones and preserve identifiers, amounts, ownership, status/event ordering, versions and keys; load a new isolated PostgreSQL database in foreign-key order; reconcile counts, financial totals, all case histories/audit events and idempotency records; run ownership, lifecycle and concurrency checks; then cut over with an agreed writer freeze, backup and rollback plan. Do not re-seed a migrated database or discard historical records. The supplied demo initializes a new empty target only and does not perform this transfer.

## Identity, sessions and access

- Terminate HTTPS at an approved ingress and enable the secure profile. Configure trusted forwarding only for known proxies. Keep the application private until transport, identity and edge controls have been tested.
- Replace demo accounts with the bank's identity/provisioning process, preferably an approved OIDC integration with MFA, staff step-up controls and central revocation. No OIDC or MFA provider is bundled. Confirm customer/account entitlements against the authoritative access source and propagate revocation promptly.
- Preserve server-side ownership checks even when the UI hides actions. Reviewer access is a privileged cross-customer function; use individual accounts, scoped assignments, access reviews and staff activity monitoring before expanding staff use.
- Session cookies are HttpOnly and SameSite Strict; idle expiry is 15 minutes. Mutations require CSRF protection. Refresh the CSRF token after login/logout. Never move authentication credentials into URL parameters, local storage or source control.
- A local limiter permits 15 attempted logins per minute per direct peer IP, including successful attempts. Behind a reverse proxy, many customers may share that peer and quota. Add an approved gateway/identity-provider limiter keyed to trusted identity/network information, credential attack detection, account recovery controls, and broader abuse limits. Do not trust arbitrary forwarded-IP headers. Test the whole deployed path, including error responses and logout.

## Ledger integration and financial consistency

The supplied database contains **seeded ledger projections**. There is no connection to a live banking ledger or transaction provider, no payment execution, and no real account-balance reconciliation. The original transaction and its dispute are separate records. Dispute creation and resolution do not change financial amounts, owners, references or balances.

The backend boundary is read-only to dispute operations. A trusted integration should populate authoritative snapshots/events using a stable provider identifier, account mapping, currency and decimal amount, event time, observed time, source version and reversal linkage. The adapter and credentials need their own authorisation boundary; customer input must never populate these fields.

Recommended production adapter contract:

1. Import provider pages/events with a durable cursor and unique provider-event/version key. Persist the event and cursor atomically. Replayed events must be harmless; reject or quarantine conflicting duplicates and out-of-order versions.
2. Keep immutable original ledger events. Model settlement of a pending authorisation, corrections and reversals as linked events/new projection versions rather than rewriting history through the dispute service. A pending authorisation may change or disappear; it is not a final booked debit.
3. Reconcile event counts, amounts, missing pages and provider control totals on a schedule. Monitor the last successful observation and expose an accurate freshness indication. A network outage must not turn into a fabricated empty transaction list.
4. Exclude pending transactions, reversed entries and credits from this demo's case-creation path. Production eligibility must also evaluate the applicable payment-scheme rules. Recheck ownership, state and freshness on submission, even when the displayed row was eligible earlier.
5. When a delayed reversal arrives after a case was opened, link it and alert case operations. Assess the financial outcome explicitly; a reversal must not delete a case or silently declare it resolved. Credits/refunds, if authorised elsewhere, need a separate payment workflow and reconciliation.

The backend has a configurable ledger-age safety threshold (`app.ledger.max-age`, default 24 hours). It is a stale-snapshot guard, not a guarantee that a real provider is current or that a scheme deadline is met. The seed runs only when the user table is empty and there is no configured refresh job: eligibility will expire after 24 hours, including with the persistent PostgreSQL database. Restarting the JVM or container does not make those observations fresh. Supply a trusted provider refresh before real use; do not bypass staleness by accepting customer timestamps. For a fresh synthetic evaluation, start a new isolated demo database after preserving anything needed from the previous evaluation. Agree a threshold per source and service objective, monitor it and test delayed-event behaviour.

## Reliability and retry policy

Dispute creation uses a required `Idempotency-Key` scoped to the authenticated principal and checks a hash of the submitted claim. The same key must be retained for a retry of the same user intent. Reusing a key with another payload is a conflict. The database also enforces a single active case per transaction; a different key does not bypass that business rule. Keep idempotency records for an approved period at least covering the entire retry/reconciliation horizon. PostgreSQL retains those records across application restarts, subject to its storage and recovery guarantees.

For an uncertain submission result, retry the identical payload with the **same** key after a bounded backoff, or refresh the cases list. Do not generate a new key merely because the response timed out. Do not automatically repeat a rejected validation, permission, stale-version or duplicate-case request. Lifecycle updates use a version check: refresh the case after a conflict and require a deliberate review of the current state before resubmission.

Database operations use READ COMMITTED transactions, a 5-second connection/lock timeout, 15-second statement/idle-transaction limits and a 20-second driver socket timeout. Native PostgreSQL lock and statement cancellation are translated to the API's sanitized 503 BUSY response; pool/resource failures return 503 DATABASE_UNAVAILABLE. The complete PostgreSQL error-code definition in `sql-error-codes.xml` must be compared with Spring's when upgrading the framework. These protect atomicity and contention, not end-to-end availability. There is no bundled provider HTTP client, message broker, notification delivery or general-purpose retry engine. For those additions, configure connect/read/total timeouts, bounded exponential backoff with jitter, retry only safe failures, use a circuit breaker and bulkheads where justified, and route exhausted work to an operational queue. Use a transactional outbox for notifications/scheme messages so committing a case cannot lose the intended external action. Receivers need deduplication because delivery is generally at least once.

## Audit, monitoring and evidence

Case creation and state changes record actor, time and transition details in the same database transaction as the business change. Closed cases and their history are retained by the application. Inspect the schema and integration tests for the exact implemented events. Application audit append semantics do not prevent a database administrator from altering data; there is no cryptographic seal or external immutable audit service in the demo.

Before real use, forward audit events through a durable, access-controlled pipeline to approved tamper-resistant retention, synchronise clocks, and separate database administration from audit custody. Record entitlement and identity administration, sensitive reads/exports, successful and failed authentication, privileged investigations, and configuration changes according to the bank's policy. Redact credentials, CSRF tokens and customer free text from operational logs. Limit access to the audit data itself.

The health endpoint reveals no database details. Integrate it with readiness/liveness supervision as appropriate; an HTTP health check is not proof of ledger freshness, backup health or dispute queue processing. Add metrics and alerts for API errors and latency, pool/lock exhaustion, stale ledger observations, duplicate conflicts, invalid transitions, authentication anomalies, case-age/deadline breaches, backup failures, reconciliation mismatches, and audit delivery lag. Establish an on-call owner and escalation path for every alert.

## Backup and recovery procedure

A named volume is not a backup. Use a protected backup destination outside the database host, retain the application/migration version with each backup, and exclude credentials and customer backups from the source archive. The following local evaluation procedure creates a PostgreSQL custom-format dump, then copies it as a file. It avoids binary output redirection, including PowerShell's version-dependent handling of native command output.

```sh
docker compose exec database sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/ntsumele.dump'
docker compose cp database:/tmp/ntsumele.dump ./ntsumele-backup.dump
docker compose exec database rm /tmp/ntsumele.dump
```

Check exit codes and move the local dump into access-controlled, encrypted backup storage promptly. `pg_dump` produces a consistent single-database snapshot while the server is running. Roles/global objects need a separate controlled backup/re-provisioning plan; this dump does not include them. Backups contain sensitive customer data and require the same access/retention controls as the source.

Test recovery into a **new isolated database**, keeping the original intact:

```sh
docker compose cp ./ntsumele-backup.dump database:/tmp/ntsumele.dump
docker compose exec database sh -c 'createdb -U "$POSTGRES_USER" -O "$APP_DB_USERNAME" ntsumele_restore'
docker compose exec database sh -c 'pg_restore -U "$POSTGRES_USER" --exit-on-error --no-owner --role="$APP_DB_USERNAME" -d ntsumele_restore /tmp/ntsumele.dump'
docker compose exec database rm /tmp/ntsumele.dump
```

Use a unique recovery database name if it already exists; do not drop an existing one just to follow the example. Check `pg_restore` output and validate schema/Flyway history, counts, access grants and audit/case continuity before pointing an isolated application at it. Run with the matching version and a compatible profile; restore testing must not send notifications or external financial actions. After validation, follow an approved cutover procedure rather than overwriting the running database blindly.

Production owners must choose recovery point and recovery time objectives after a business-impact analysis. Configure PostgreSQL-supported consistent backups and WAL archiving/point-in-time recovery where needed; isolate backup credentials, monitor completion, keep a geographically appropriate recovery copy and test restoration against the objectives. The project makes no RPO/RTO guarantee. Do not copy a live PostgreSQL data directory as an ad hoc backup. See the PostgreSQL 17 [SQL dump guidance](https://www.postgresql.org/docs/17/backup-dump.html) and [continuous archiving procedure](https://www.postgresql.org/docs/17/continuous-archiving.html).

Recovery exercise:

1. Declare the incident and preserve evidence. Stop conflicting writers, notify the accountable operations/security owners, record the last trusted restore point and assess reporting obligations using [COMPLIANCE.md](COMPLIANCE.md).
2. Restore an isolated environment with the matching application and a compatible schema. Validate backup integrity, key availability, migration state and database consistency before enabling traffic.
3. Check account entitlements, case totals, history/audit continuity, active-case uniqueness and idempotency records. Reconcile against the provider from a safe overlapping cursor so lost/duplicated events are detected.
4. Identify any cases, external scheme messages or financial actions occurring after the restore point. Reconcile them explicitly; do not replay payment actions blindly. Keep a customer communication and manual work queue for uncertain outcomes.
5. Revoke stale sessions if needed, verify access isolation and critical case flows, then release traffic under the incident commander's approval. Record achieved data loss and restoration time, outstanding discrepancies and follow-up corrective work.

## Change and release procedure

Run the documented PostgreSQL integration profile (`-Ppostgres-integration verify`) against a disposable `_test` database and the Angular tests/production build for each release. Review schema changes, dependency advisories, generated software inventories and image scan results. Perform deployment smoke checks with synthetic users for ownership isolation, case creation, duplicate prevention, history, staff transitions, information requests and logout. Test stale-version and concurrent submissions. Review the pending compliance/release decisions in [COMPLIANCE.md](COMPLIANCE.md) with their owners. Keep a versioned, tested rollback/roll-forward plan; restoring an older application over a newer schema without compatibility checks is not a rollback strategy.


## Production transport configuration

Default forwarded-header handling is NONE. Enable `secure` only with a dedicated ingress whose socket addresses are explicitly matched by `TRUSTED_PROXY_REGEX`. It uses Tomcat NATIVE handling; the ingress must replace `X-Forwarded-For` and `X-Forwarded-Proto`, set a fixed approved Host, and discard client `Forwarded` headers. Restrict the backend port to that proxy/private host. Never use a match-all or broad private-network proxy trust rule. The host Nginx/Docker bridge socket peer may differ from the browser address; inspect the actual deployment rather than guessing it.

Set `PUBLIC_ORIGIN` to the approved `https://host[:port]` origin, without a path, credentials, query or fragment. The application redirects plaintext reads to this canonical origin, rejects plaintext mutations and keeps the minimal `/actuator/health` probe available on the private HTTP port. Native proxy tests verify that an untrusted peer cannot spoof HTTPS, even with forged forwarding headers. HSTS uses max-age 31536000 and includeSubDomains on secure responses; the supplied Nginx TLS example sets the same edge policy even for upstream failures. Validate that policy for the institution's hostname before rollout.

`DB_URL` must explicitly include `sslmode=verify-full` under `secure`, and `DB_SSL_ROOT_CERT` must identify a readable trusted CA file. The secure-profile validator rejects TLS downgrades, duplicate URL options and custom nonvalidating factories/hostname verifiers from either the URL or data-source properties. Mount the CA read-only, use its matching certificate hostname, and rotate certificates through a controlled process. Tests prove a real encrypted PostgreSQL session and rejection of a wrong CA/hostname. The loopback-only demo Compose file deliberately uses `sslmode=disable`; do not use that local exception for a banking deployment. See [pgJDBC TLS documentation](https://jdbc.postgresql.org/documentation/ssl/) and [Spring Boot proxy guidance](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html).

## Case references, pagination and replay retention

V2 is an additive migration: existing case references and history stay unchanged. It initializes the non-cycling `dispute_case_reference_seq` above legacy decimal references and adds ordered queue/active-case/retention indexes. Sequence gaps are expected on rollback; never reset the sequence or derive the next value by counting cases. Backups/restores must preserve sequence state as well as rows. With separate migration/runtime roles, grant the runtime role USAGE on this sequence and required table access through the reviewed migration process.

Customer and staff lists return bounded summary pages and use batched transaction/activity-count reads. Full histories are loaded by the detail endpoint. Dashboard counters come from separate account-authorized/global staff aggregate queries and do not depend on which page is visible. Page size is 1–100; deep case pages are not arbitrarily cut off. For very large historical datasets, introduce cursor/date browsing and monitor query plans/latency rather than removing query timeouts or returning every record.

Cleanup is enabled by default: `app.idempotency.retention=90d`, `cleanup.batch-size=500` (maximum 5000), `cleanup.fixed-delay=PT1H`, `cleanup.initial-delay=PT5M`. Matching `IDEMPOTENCY_*` environment overrides are in application.yml. Each run deletes one batch with row locks and SKIP LOCKED, only where both the replay creation and resolved case update predate the cutoff. Active cases and recent closures are retained. The worker transaction records an aggregate audit event without raw keys; no cases, history or existing audit entries are deleted. Failures are logged for the next scheduled retry. Monitor cleanup failures/backlog and approve the retention/retry horizon before live use; payment-scheme/legal case retention is a separate policy.

See [SUPPLY-CHAIN.md](SUPPLY-CHAIN.md) for scan/SBOM scope and [RELEASE.md](RELEASE.md) for protected publishing, signatures, deployment prerequisites and manual rollback. Workflows do not themselves provision repository protections, production identities, TLS infrastructure or regulatory approval.
