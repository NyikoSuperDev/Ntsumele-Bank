#!/usr/bin/env bash
# Runs only on the protected, pre-provisioned Linux production deployment host.
set -euo pipefail
: "${GITHUB_REPOSITORY:?Repository identity is required}"
: "${IMAGE_DIGEST:?Immutable image digest is required}"
: "${RELEASE_REF:?Signing workflow ref is required}"
: "${NTSUMELE_ENV_FILE:?Protected environment file is required}"
: "${NTSUMELE_DB_CA_FILE:?Database root CA file is required}"
[[ "$IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid image digest' >&2; exit 2; }
[[ "$RELEASE_REF" == refs/heads/main || "$RELEASE_REF" =~ ^refs/tags/v[0-9][A-Za-z0-9._-]*$ ]] || { echo 'Invalid release ref' >&2; exit 2; }
[[ "$NTSUMELE_ENV_FILE" == /* && -r "$NTSUMELE_ENV_FILE" && "$NTSUMELE_DB_CA_FILE" == /* && -r "$NTSUMELE_DB_CA_FILE" ]] || { echo 'Protected configuration or CA file unavailable' >&2; exit 2; }
for command in docker cosign gh; do command -v "$command" >/dev/null; done
cosign version | grep -q 'v3.1.4' || { echo 'Provision the reviewed Cosign v3.1.4 binary' >&2; exit 2; }
docker compose version >/dev/null
image="ghcr.io/${GITHUB_REPOSITORY,,}"
export NTSUMELE_IMAGE_REF="$image@$IMAGE_DIGEST"
identity="https://github.com/$GITHUB_REPOSITORY/.github/workflows/release.yml@$RELEASE_REF"
issuer='https://token.actions.githubusercontent.com'
# All three checks must succeed before pulling/replacing the running application.
cosign verify --certificate-identity "$identity" --certificate-oidc-issuer "$issuer" "$NTSUMELE_IMAGE_REF" >/dev/null
cosign verify-attestation --type cyclonedx --certificate-identity "$identity" --certificate-oidc-issuer "$issuer" "$NTSUMELE_IMAGE_REF" >/dev/null
gh attestation verify "oci://$NTSUMELE_IMAGE_REF" --repo "$GITHUB_REPOSITORY" --signer-workflow "$GITHUB_REPOSITORY/.github/workflows/release.yml" --source-ref "$RELEASE_REF" >/dev/null
root="$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)"
compose=(docker compose --project-name ntsumele-production -f "$root/deploy/compose.production.yaml")
"${compose[@]}" config --quiet
"${compose[@]}" pull bank
"${compose[@]}" up -d --wait --wait-timeout 180 bank
printf 'Healthy application deployed from %s\n' "$NTSUMELE_IMAGE_REF"
