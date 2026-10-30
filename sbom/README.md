# Submission dependency inventories

Generated on 30 October 2026 for the submitted dependency inputs:

- `backend.cdx.json`: CycloneDX Maven profile, 59 Java dependency components, excluding test scope.
- `frontend.cdx.json`: `npm sbom --sbom-format=cyclonedx --package-lock-only`, 551 locked frontend dependency components including development tooling.

Regenerate after dependency changes. From the project root run `sh ./mvnw -f backend/pom.xml -Psbom package` (Windows: `./mvnw.cmd`), then copy `backend/target/bom.json`. From `frontend/` run the npm command above. These snapshots do not inventory the runtime OS image or certify absence of vulnerabilities. CI separately generates runtime and frontend inventories with Syft and attaches them to a released image digest. See [SUPPLY-CHAIN.md](../docs/SUPPLY-CHAIN.md) and [VERIFICATION.md](../docs/VERIFICATION.md).
