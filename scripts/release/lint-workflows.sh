#!/usr/bin/env bash
set -euo pipefail
version=1.7.12
checksum=8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8
archive="${RUNNER_TEMP:?RUNNER_TEMP is required}/actionlint.tar.gz"
directory="$(mktemp -d "$RUNNER_TEMP/ntsumele-actionlint.XXXXXX")"
curl --fail --silent --show-error --location --retry 3 "https://github.com/rhysd/actionlint/releases/download/v$version/actionlint_${version}_linux_amd64.tar.gz" --output "$archive"
printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check -
tar -xzf "$archive" -C "$directory" actionlint
"$directory/actionlint" -config-file .github/actionlint.yaml .github/workflows/*.yml
