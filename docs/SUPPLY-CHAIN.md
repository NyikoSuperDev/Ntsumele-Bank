# Build inputs, security scans and release evidence

The repository now contains verification, scanning, inventory and signed-image release workflows. These are configured controls that execute after the source is placed in a suitably configured GitHub repository. Their presence does not mean a vulnerability scan, registry push, signature, attestation or production deployment has already completed. See [VERIFICATION.md](VERIFICATION.md) for checks actually run in this workspace and [RELEASE.md](RELEASE.md) for activation requirements.

## Immutable inputs and dependency updates

The Dockerfile's Node, Maven and Java images, its Dockerfile frontend, and the PostgreSQL image in Compose/CI use explicit `sha256` digests. External GitHub Actions use full 40-character commit SHAs, with version comments for review and update tooling. The lock inventory at [scripts/release/supply-chain-lock.json](../scripts/release/supply-chain-lock.json) records the selected publishers, versions, hashes and verification sources. Tags remain readable labels; the digest or SHA determines the fetched content.

The [Renovate configuration](../.github/renovate.json) covers npm, Maven, Dockerfiles, Compose and GitHub Actions. It requests digest pinning, lock-file maintenance, a dependency dashboard and reviewed update pull requests; automerge is disabled. A repository owner must install/enable the Renovate app or operate an approved Renovate instance. Committing the configuration alone does not activate the bot.

For every update, review the upstream release and publisher, resolve its commit/digest, update the lock inventory and run the required verification. Also review manually pinned security-tool versions and the actionlint archive checksum. Do not remove a digest to make a failing build pass. The pin validator checks reference format and configured safety boundaries; it does not independently establish that a new publisher or hash is trustworthy.

## Verification and scan gates

[ci.yml](../.github/workflows/ci.yml) runs on pull requests, main pushes, its weekly schedule and release-workflow calls. Jobs have bounded timeouts and limited permissions. It does not use `pull_request_target` or ignore failing security jobs.

| Control | Configured execution and evidence |
| --- | --- |
| Application verification | Angular tests/build and the PostgreSQL integration profile; reports are uploaded with the Java inventory |
| Transport verification | Application transport checks cover trusted proxy handling, the required canonical `PUBLIC_ORIGIN`, plaintext GET/HEAD redirects, rejected plaintext writes and secure-request HSTS. A disposable CI PostgreSQL certificate hierarchy exercises encryption, trusted CA and hostname checks through the `postgres-tls` profile |
| Dependency audit | `npm audit --audit-level=high`, including development dependencies; OWASP Dependency-Check with CVSS threshold 7, analysis errors treated as failures and test-scope dependencies included |
| Dependency review | Pull-request dependency review rejects high-severity additions according to its action policy |
| CodeQL | Java/Kotlin and JavaScript/TypeScript analysis with `security-extended`; `check-codeql.py` blocks SARIF security severity 7 or higher and unclassified errors, in addition to requiring successful analyzer execution |
| Container scan | Trivy checks vulnerabilities and secrets in the built image, including unfixed HIGH/CRITICAL vulnerabilities; findings fail the job and SARIF is uploaded |
| Java SBOM | CycloneDX Maven profile produces `backend/target/bom.json` for application dependencies |
| Runtime image SBOM | Syft inventories the built runtime image in CycloneDX JSON, including discovered OS/Java packages |
| Frontend SBOM | Syft inventories the locked frontend source dependencies separately, since bundled browser assets do not expose a complete package inventory |
| Workflow/pin policy | `validate-policy.py` and checksum-verified actionlint validate immutable references, workflow syntax and protected release boundaries |

The image job waits for application verification, dependency audit and CodeQL. The release workflow invokes the verification workflow before its publishing job and scans the exact local image again before pushing it. Scan or analysis failure is a failed gate, not a clean result. Review all tool output and investigate false positives through a documented, time-bounded exception process; no vulnerability suppression list is supplied.

Set the repository or organization `NVD_API_KEY` Actions secret for reliable Dependency-Check updates. The configuration accepts an absent key, but unauthenticated feed access can be slower or rate-limited; feed/update errors still fail the build. A cold vulnerability-data download can take substantial time. Never expose secrets to untrusted pull-request code or introduce a privileged PR trigger as a workaround. Provision an approved data/cache strategy if fork builds need to operate without repository secrets.

Repository administrators must enable the available CodeQL/code-scanning and dependency-review features, configure required checks on main, and enforce the organization’s code-scanning merge policy. Workflow files cannot create those repository settings. GitHub feature availability differs between public and private repositories and account plans; verify the repository’s entitlement before enabling the release gate. See GitHub’s [artifact-attestation availability](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) and the [CodeQL documentation](https://docs.github.com/en/code-security/code-scanning).

## Inventories, signing and provenance

Verification artifacts are retained for 30 days by the configured workflows. Signed-release evidence is retained for 90 days and includes the immutable image reference, runtime/frontend SBOMs and signature/SBOM verification output. Choose institution-approved retention and export durable evidence to controlled storage; Actions artifact retention is not the bank’s audit-retention system.

The release workflow publishes to `ghcr.io/<owner>/<repository>` using its scoped `GITHUB_TOKEN`, then signs the resulting digest with Cosign and GitHub OIDC. It verifies that signature immediately. It attaches and verifies a Cosign CycloneDX runtime-SBOM attestation, and creates GitHub provenance plus runtime/frontend SBOM attestations for the same image digest. It does not keep a signing private key in the repository.

The deployment script accepts only a digest and a matching signing ref. Before it pulls/replaces the application, it verifies the Cosign signature, Cosign runtime-SBOM attestation and GitHub provenance for this repository’s release workflow. `gh attestation verify` defaults to the SLSA provenance predicate; the script additionally constrains signer workflow and source ref. Consult the [GitHub CLI verification reference](https://cli.github.com/manual/gh_attestation_verify) and [Cosign verification guidance](https://docs.sigstore.dev/cosign/verifying/verify/) when maintaining these checks.

A valid signature establishes an approved build identity and artifact binding; it does not establish absence of vulnerabilities, regulatory approval, backup health or safe database compatibility. Keep scan results, human release approval and deployment evidence associated with the exact digest. The release and production workflows are disabled until their explicit repository variables and protected environments are configured as described in [RELEASE.md](RELEASE.md).

## Local commands

Run from the project root; the scan/SBOM profiles may download additional plugins and vulnerability data:

```sh
python3 scripts/release/validate-policy.py
bash scripts/release/lint-workflows.sh
sh ./mvnw -B -ntp -f backend/pom.xml -Ppostgres-integration,sbom verify
sh ./mvnw -B -ntp -f backend/pom.xml -Psecurity -DskipTests verify
```

Set `RUNNER_TEMP` to a writable scratch directory for the actionlint helper. The PostgreSQL integration command requires the dedicated test connection variables from the README. On Windows, use the Maven wrapper’s `.cmd` form and quote comma-separated profile arguments in PowerShell (for example `'-Ppostgres-integration,sbom'`); the release and workflow-lint shell helpers target Linux. For Trivy, SBOM, CodeQL and signed-registry execution, use the configured Linux workflow jobs or a separately provisioned equivalent environment. Do not report these scans as passed unless their actual run completed successfully.
