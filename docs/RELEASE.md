# Signed image release and controlled deployment

The project includes separate workflows to verify, publish/sign and deploy an image. Publishing and deployment are opt-in and use GitHub environments. No registry image has been pushed, signed or deployed by this local implementation task. Repository, registry, certificates, secrets, deployment host and organizational approval must be provisioned before these workflows can succeed.

## Activate the repository controls

Place the project at the root of its GitHub repository so `.github/workflows` and relative build paths resolve. Protect `main` and version tags, require the verification/security checks, and restrict who may edit workflows or create releases. Configure code-scanning merge protection and the organization’s finding/exception policy. Install/enable Renovate; its checked-in configuration does not install the app.

Create two GitHub environments:

| Environment | Required protection |
| --- | --- |
| `release` | Named release approvers and permitted branches/tags; prevent unreviewed code from publishing/signing |
| `production` | Production approvers, main-only deployment policy and access limited to the dedicated deployment runner |

Environment names in YAML do not themselves create an approval requirement. Repository administrators must configure reviewers, deployment restrictions and any plan-dependent protections in GitHub. Verify that the repository/account supports the configured CodeQL, dependency-review and artifact-attestation features; private/internal repository attestations require the applicable GitHub plan.

Configure these values through the repository/environment settings, not source files:

| Setting | Where/why |
| --- | --- |
| `NVD_API_KEY` | Repository/organization Actions secret for the dependency-audit job’s vulnerability-feed access; absence does not bypass the gate and may make updates fail or time out |
| `RELEASE_ENABLED=true` | Repository variable enabling the publishing job; leave absent/false until the release environment and registry policy are ready |
| `DEPLOY_ENABLED=true` | Repository variable enabling production deployment; leave absent/false until the host and production environment are ready |
| `NTSUMELE_ENV_FILE` | Production environment/repository variable: absolute path to the protected application environment file on the deployment host |
| `NTSUMELE_DB_CA_FILE` | Production environment/repository variable: absolute path to the trusted PostgreSQL CA certificate on that host |
| `GITHUB_TOKEN` permissions | Automatically scoped by the workflow: release needs package writes, OIDC and attestation writes; deployment needs package/attestation reads |

Allow the repository’s Actions identity to publish its GHCR package and let deployment read it. The workflow derives `ghcr.io/<owner>/<repository>` in lowercase. It does not require a persistent personal access token or committed signing key. Configure package access explicitly for a private registry image.

## Provision the production host

The deploy job runs on a dedicated self-hosted **Linux x64** runner with the labels `self-hosted`, `linux`, `x64`, `ntsumele-production`. Restrict that runner/group to approved deployment workflows; do not run pull-request builds on a host that has production secrets or Docker access. Docker access is privileged and requires the same host controls as administration.

Pre-provision reviewed Docker Engine and the Compose plugin, Bash, GitHub CLI with `gh attestation verify`, and **Cosign v3.1.4**. The deployment script checks the Cosign version and tool availability. Keep these host tools updated through a controlled process together with the reviewed version constraint. The workflow does not install/provision the host, reverse proxy or database.

Create a protected environment file readable by the deployment account. At minimum it must provide:

```dotenv
DB_URL=jdbc:postgresql://db.bank.example:5432/ntsumele?sslmode=verify-full
DB_USERNAME=ntsumele_runtime
DB_PASSWORD=replace-through-your-secret-provisioning-process
PUBLIC_ORIGIN=https://bank.example
TRUSTED_PROXY_REGEX=replace-with-the-exact-approved-proxy-socket-address-pattern
```

Replace the hostname and values with the actual approved infrastructure. Do not deploy the placeholder file. `PUBLIC_ORIGIN` is required by the secure profile and must be the canonical public HTTPS origin, without an application path, query, fragment or embedded credentials; it must agree with the ingress hostname. The database certificate must match the JDBC hostname; the trusted CA file is mounted read-only at `/run/secrets/postgres-root.crt`, and the production manifest sets `DB_SSL_ROOT_CERT` to that in-container path. The secure profile rejects missing/unreadable trust material, non-`verify-full` URLs and supported TLS-bypass overrides. Use institution-managed database/migration privileges and provision real identities/entitlements and ledger data; the production manifest activates `secure`, without demo seeding.

Configure a TLS reverse proxy that overwrites incoming forwarded headers, uses a fixed public host and reaches the application’s loopback-only port. [nginx.conf.example](../deploy/nginx.conf.example) is an example requiring real certificates and a public hostname. `TRUSTED_PROXY_REGEX` must match only the actual dedicated proxy socket peer(s) observed by Tomcat; a Docker bridge gateway may be that peer when Nginx runs on the host. Do not trust all private addresses or assume every loopback/forwarded header is safe. For requests the trusted container identifies as plaintext, the application redirects only GET/HEAD with 308 to `PUBLIC_ORIGIN`; it returns 403 `HTTPS_REQUIRED` for plaintext mutations. It does not derive a redirect destination from an untrusted Host/forwarded header or an internal backend port. HSTS is emitted only when the request is securely identified; the internal health probe remains exempt from HTTPS enforcement. Verify canonical redirects, HSTS and Secure cookies through the actual ingress before traffic. See [OPERATIONS.md](OPERATIONS.md) for the transport boundary and database setup.

The [production Compose file](../deploy/compose.production.yaml) runs only the application. PostgreSQL, ingress, backups and monitoring are external prerequisites. The application binds `127.0.0.1:8080`, runs as its image’s non-root user, has a read-only filesystem and mounts only the database CA plus its temporary directory. Existing persistent database data remains outside the application container.

## Build, scan, publish and sign

1. Merge the reviewed source to protected `main` and wait for all required checks and finding policies to pass. Review dependency and database migration changes, scan reports and backups before selecting the commit.
2. Create an approved version tag matching `v[0-9]*`, or manually run **Release signed image** from `main`. The publishing job requires `RELEASE_ENABLED=true`, the `release` environment and a source commit that is an ancestor of `origin/main`.
3. The release workflow first calls the verification/scanning workflow. Its publishing job builds the local image, scans that same image before pushing, generates runtime/frontend inventories and pushes the convenient `sha-<commit>` tag to GHCR. The digest captured after push is the authoritative deployment reference.
4. Cosign signs and verifies the digest using the exact release-workflow/ref identity. The workflow attaches/verifies a runtime CycloneDX attestation and creates GitHub provenance plus runtime/frontend SBOM attestations. OIDC credentials are short-lived and scoped to the job.
5. Retain the `signed-release-<commit>` artifact and the job summary. Record the image digest, signing ref, source commit, scan results, approvals and SBOMs in the bank’s release record. Actions artifact retention is finite; export required evidence to controlled storage.

The SHA tag is a useful locator, not a guarantee that a registry tag can never change. Deployment uses the recorded `sha256:` digest. If scanning, signing or attestation fails after an image was pushed, the registry may contain an incomplete release; do not deploy it. The deployment verification gate must still pass for that digest.

## Deploy a verified digest

Run **Deploy verified image** manually from `main`, supplying:

| Input | Required value |
| --- | --- |
| `image_digest` | The released `sha256:` value followed by 64 lowercase hexadecimal characters |
| `release_ref` | The exact signing ref reported by that release, such as `refs/tags/v1.0.0` or `refs/heads/main` |

The job requires `DEPLOY_ENABLED=true`, production environment approval and the dedicated runner. It logs into GHCR using a temporary Docker configuration and removes those credentials afterward. [deploy.sh](../scripts/release/deploy.sh) validates its inputs and host paths, then verifies all of the following **before** pulling or replacing the application:

- Cosign signature for this repository’s release workflow and the supplied ref, issued by GitHub Actions OIDC.
- A matching Cosign runtime CycloneDX SBOM attestation for the same digest.
- GitHub SLSA provenance constrained to this repository, release workflow and source ref.

The gate binds the image to that trusted signing identity/ref. Operators must still verify the intended source commit in the release evidence; the input does not independently require the image to be built from the current tip of main. A prior approved signed digest may therefore be selected deliberately for rollback.

After verification, the script sets `NTSUMELE_IMAGE_REF`, validates Compose, pulls the digest and runs `docker compose up -d --wait --wait-timeout 180 bank` under project name `ntsumele-production`. A healthy container is required for success. Perform synthetic ownership/lifecycle smoke checks through HTTPS, confirm the new release/digest and monitor errors, database connectivity and dispute operations before declaring the rollout complete. A container health result alone does not prove ledger freshness or business correctness.

## Rollback and database state

The supplied script has **no automatic rollback**. If `compose up --wait` fails, the application container may already have been replaced. Keep the previous approved digest/ref and release evidence before starting, inspect the failed container and logs, and use the protected Deploy workflow again with a known compatible previous digest if the incident owner approves. Do not turn off signature, attestation or TLS verification to recover availability.

Application rollback is safe only when the older binary and bundled Angular API contract support the current database schema. The V2 migration creates sequence/index objects and preserves existing cases/references; Flyway history must remain intact. Do not edit an applied migration, delete Flyway history or rewind `dispute_case_reference_seq` to make an older image start. Sequence gaps from rolled-back transactions are expected, and references already issued must not be reused. Prefer an approved forward fix when compatibility cannot be demonstrated.

Idempotency cleanup also changes durable state: the default policy removes eligible replay keys only after both the key and its resolved case are older than 90 days, retaining active/recent keys and all case/history/audit records. Rolling back an application does not recreate purged replay keys. Include that retry horizon and any changed retention policy in release/recovery review.

If database recovery is required, use an isolated, tested restore and reconcile transactions, account access, cases, history, audit and idempotency records before cutover. Restoring a database can lose later actions and reference allocations; it is not the default application rollback procedure. Follow [OPERATIONS.md](OPERATIONS.md) and the institution’s incident/recovery process.

## Checks actually performed

The workflows, scripts, pins and manifest can be statically validated locally, and application/database tests can run against an isolated environment. This repository change does not establish a successful GitHub scan, GHCR publication, cryptographic registry verification or production rollout. Those outcomes require their actual configured runs and evidence. Refer to [VERIFICATION.md](VERIFICATION.md) for the current local execution record and [SUPPLY-CHAIN.md](SUPPLY-CHAIN.md) for scan and inventory scope.
