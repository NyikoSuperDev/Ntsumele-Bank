# Verification record

Submission review completed on 30 October 2026. Backend, transport, inventory and packaging checks were completed on 30 October. The frontend tests/build were repeated successfully on 30 October after removing demo presentation from the screens. This record supersedes the earlier documentation-only verification record.

## Executed application checks

| Check | Result |
| --- | --- |
| Architecture | 5 passing checks enforce feature ownership, dependency boundaries and repository-only SQL/JDBC |
| Secure configuration | 17 passing checks cover required HTTPS origin, trusted proxy configuration, verified database TLS, readable CA material and rejected bypass settings |
| PostgreSQL banking integration | 24 passing checks against real PostgreSQL 17.11, including authentication/CSRF, account isolation, immutable financial inputs, eligibility, duplicate/replay concurrency, optimistic versions, lifecycle/history, timeouts and rollback |
| Proxy and HTTPS integration | 5 passing embedded-server checks cover trusted and untrusted proxy peers, forwarded-header spoofing, canonical redirects, rejected plaintext writes, HSTS and Secure cookies |
| Database TLS | 3 passing checks establish a real encrypted JDBC connection with the correct CA/hostname, and reject an incorrect CA and certificate hostname mismatch |
| Backend total | **54 tests, zero failures, errors or skips** across the five suites above; repeated unit executions are counted only once |
| Angular | **36 tests across 10 suites**, all passing; production build successful |
| Production browser assets | 313.27 kB initial bundle, 77.91 kB estimated transfer; final executable JAR contains the matching current HTML, JavaScript and CSS |
| Java documentation | All 71 top-level Java sources (66 main, 5 test) carry the correct type name, `Author : Nyiko Chauke`, year 2026 and a nonempty implementation description after imports, or after the package when no imports exist |

The PostgreSQL tests use an isolated disposable database, SCRAM credentials and a non-superuser application role. No customer Docker database or private application credentials are needed for these results. Test servers were stopped after verification.

The UI follow-up removes the sign-in environment banner, published account-credentials panel, demonstration footer wording and customer/staff environment badge, along with their unused CSS. A source review and packaged-asset check confirm that these labels and published credentials no longer appear in the frontend. The existing 36 frontend tests and production build pass after the change; backend behavior is unchanged, and its earlier results above are retained.

## Hardening behavior covered

- Customer and staff lists use authorized, bounded summary pages with stable ordering. A nonempty page executes **four SQL statements**, independently of page size: count, case page, batched transaction projections and grouped history counts. The tests check both small and larger pages, status filters, deep/empty pages, authorization and detail-only timeline loading. Reads use a repeatable-read snapshot.
- Flyway V1-to-V2 upgrade tests preserve existing cases, history, audit and legacy references. New references come from a PostgreSQL sequence, including initialization above existing numeric references. Concurrent allocation is unique. Gaps after rollbacks are expected.
- Retention tests remove only old replay keys linked to old resolved cases, in bounded batches. Active/recent keys and all case/history/audit records remain. Cleanup adds an aggregate audit event. The default replay retention is 90 days; this is distinct from legal case-record retention.
- Ledger payment status and dispute status remain separate. Pending/reversed/credit/stale snapshots do not become disputable merely because they have no active case. Submission and resolution never alter the original amount, owner, reference or payment status. Resolution does not post a refund.
- Frontend tests cover server-side paging/filtering, aggregate counts, detail loading, stale-response/session guards and the Finished action. Finished closes the dialog and refreshes accounts, transactions and cases while preserving selections. No lifecycle change is triggered by that action.

## Executed build and supply-chain checks

| Check | Result and scope |
| --- | --- |
| Maven packaging | Java 21 executable Spring Boot JAR built successfully with the current Angular distribution |
| Java inventory | CycloneDX Maven generation successful: 59 dependency components in `ntsumele-bank-backend.cdx.json` |
| Frontend inventory | npm lockfile CycloneDX generation successful: 551 dependency components in `ntsumele-bank-frontend.cdx.json`, including development tooling |
| npm audit | Online audit on 30 October reported zero known vulnerabilities at every severity, including development dependencies; this is a dated feed result |
| Workflow and pin policy | `validate-policy.py` passes for pinned images/actions and protected release/deployment boundaries |
| SARIF gate tests | 4 Python tests pass for clean, lower-severity, high-severity, malformed and unclassified security results |
| Workflow syntax | Native actionlint passes for all three workflows; optional ShellCheck/Pyflakes integrations were disabled in this Windows invocation. CI runs the Linux lint helper |
| Compose | Default, optional loopback database and production manifests validate through `docker compose config --quiet`; production validation uses placeholders and does not connect to hosting |
| Submission archive | ZIP integrity, required source/configuration files, executable Unix script modes, current JAR assets/classes, PostgreSQL dependencies and SHA-256 hashes checked. Private `.env`, databases, backups, test keys and dependency/build caches excluded |

The two delivered inventories describe Java dependencies and the frontend lockfile. A runtime OS/image inventory requires the configured container job; it was not generated locally. Inventories are component lists, not vulnerability-clearance certificates. Regenerate them whenever dependencies change.

## Limits and remaining environment requirements

Container build/execution and image scanning were not rerun here because this sandbox cannot access the Docker Desktop named pipe. The application was compiled and exercised directly with PostgreSQL instead. Full visual/responsive browser review remains unverified because the preview could not reach the sandbox loopback server; DOM behavior is covered by the frontend suite.

OWASP Dependency-Check, Trivy, CodeQL, dependency review, GHCR publication, OIDC signing, attestation verification and production deployment are **configured but were not executed successfully in this workspace**. Their jobs must run in the submission repository with the required features, NVD feed access, protected environments, registry permissions and approved deployment host. No successful registry publication or production rollout is claimed. See [SUPPLY-CHAIN.md](SUPPLY-CHAIN.md) and [RELEASE.md](RELEASE.md).

The isolated local demonstration explicitly disables PostgreSQL TLS and binds the application to loopback. Production instead requires the `secure` profile, a verified-TLS JDBC URL, mounted CA, approved proxy socket addresses and canonical `PUBLIC_ORIGIN`. Tests establish the implementation behavior; operators must verify the actual ingress/database certificates and trust boundary before traffic.

The earlier PostgreSQL delivery also exercised restart persistence and an isolated custom-format backup/restore. This hardening run verifies migration compatibility; it does not establish a production recovery-point/recovery-time objective. The operating institution must approve and exercise backups, recovery, retention, identity/ledger integration, monitoring and applicable South African obligations. See [OPERATIONS.md](OPERATIONS.md) and [COMPLIANCE.md](COMPLIANCE.md). These results are not security or regulatory certification.

## Reproduce

From the project root, configure the dedicated `TEST_DATABASE_*` variables as described in the [README](../README.md), then run:

```sh
sh ./mvnw -B -ntp -f backend/pom.xml -Ppostgres-integration,sbom verify
sh ./mvnw -B -ntp -f backend/pom.xml -Ppostgres-tls verify
cd frontend
npm ci
npm test
npm run build
npm audit
```

The TLS profile additionally requires `TEST_DATABASE_TLS_URL`, `TEST_DATABASE_SSL_ROOT_CERT` and `TEST_DATABASE_WRONG_SSL_ROOT_CERT` and an ephemeral TLS-enabled PostgreSQL server; CI provisions these with `scripts/ci/enable-postgres-tls.sh`. Both database profiles fail when their required settings are absent. The banking suite deletes/replaces records and refuses database names that do not end in `_test`; use disposable databases only. Plain `mvn package` or `mvn verify` runs unit/architecture checks, not the PostgreSQL integration suites. On Windows use `./mvnw.cmd` and quote `'-Ppostgres-integration,sbom'` in PowerShell. Reports are written under `backend/target/surefire-reports` and `backend/target/failsafe-reports`.
