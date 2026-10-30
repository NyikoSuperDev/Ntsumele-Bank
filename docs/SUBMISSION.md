# Ntsumele Bank submission guide

Prepared on 30 October 2026. Submit the source ZIP; it contains the Angular frontend, Spring Boot backend, PostgreSQL migrations, Docker/Compose setup, tests, CI/release configuration and documentation. Private `.env` values, database files, backups, test certificate keys and dependency/build caches are excluded. Use JDK 21, Node.js 24 and PostgreSQL 17, or the supplied Linux-container build.

## Start the demonstration

1. Extract the archive and open the `ntsumele-bank` directory.
2. Copy `.env.example` to `.env` and replace both database password placeholders with different random values.
3. Run `docker compose up --build`, then open http://localhost:8080.
4. Sign in with the fictional users below. The first build needs internet access for locked dependencies and pinned images.

| User | Password | Demonstration |
| --- | --- | --- |
| amy | Ntsumele!2026 | Two customer accounts and their transactions/cases |
| ben | Ntsumele!2026 | A separate customer account for authorization isolation |
| reviewer | Review!2026 | Staff review, information requests and resolution |

The local demo binds the application to loopback and keeps PostgreSQL internal. Its explicit plaintext database exception is for this isolated evaluation only. Data survives ordinary restarts; `docker compose down -v` deletes it. The production secure profile instead requires approved proxy addresses, `PUBLIC_ORIGIN`, verified database TLS and a trusted CA file.

## Suggested assessment flow

- Compare Amy and Ben's accounts to confirm that customers see only authorized records.
- Submit a dispute on a fresh booked debit. The transaction's payment status and original amount/reference remain unchanged; its separate dispute status becomes Disputed.
- Press Finished: the popup closes and account activity, summaries and history refresh while selections are retained.
- Review the case as staff, request information, respond as the customer and resolve it. View retained history afterward, including from the transaction's View history action.
- Page/filter the customer/staff queue. Counts reflect all authorized cases; full timelines load only when opening a case.

## Verified locally

54 backend checks passed: 5 architecture checks, 17 secure-configuration checks, 24 real PostgreSQL banking tests, 5 embedded-server proxy/HSTS checks and 3 real PostgreSQL TLS checks. The frontend passed 36 tests across 10 suites and its production build. Four additional tests exercise the fail-closed CodeQL SARIF gate. Workflow/pin policy checks and native actionlint validation pass. The npm audit reported zero known vulnerabilities on the verification date. See [VERIFICATION.md](VERIFICATION.md) for exact scope and limitations.

The review fixes include bounded case pages/batched reads, a database reference sequence, bounded closed-case idempotency cleanup, digest/SHA-pinned build inputs, Renovate, scan/SBOM workflows and protected image signing/publishing/deployment. [README.md](../README.md) explains builds/tests; [API.md](API.md) describes the changed paginated list contracts.

## Configured controls that need the submission repository or hosting

OWASP Dependency-Check, Trivy, CodeQL and registry signature/provenance/deployment steps are configured, but a successful GitHub scan, image publication or production rollout is not claimed. Enable repository protections/Renovate, provision the NVD secret, protected environments and approved deployment host/TLS credentials to run them. See [SUPPLY-CHAIN.md](SUPPLY-CHAIN.md) and [RELEASE.md](RELEASE.md).

This is a tested banking reference implementation with fictional data. Live identity/ledger/refund integrations, institutional compliance approvals, approved retention/RPO/RTO, production monitoring and hosting remain institution-specific work. The compliance assessment and recovery procedures are included; passing tests does not certify a live bank deployment.
