#!/usr/bin/env sh
set -eu
: "${TEST_DATABASE_URL:?Set TEST_DATABASE_URL for the dedicated PostgreSQL test database; see README.md}"
: "${TEST_DATABASE_USERNAME:?Set TEST_DATABASE_USERNAME}"
: "${TEST_DATABASE_PASSWORD:?Set TEST_DATABASE_PASSWORD}"
cd "$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
(cd frontend && npm ci --no-audit --no-fund && npm test && npm run build)
sh ./mvnw -B -ntp -f backend/pom.xml -Ppostgres-integration verify
printf '%s\n' 'Set DB_URL, DB_USERNAME and DB_PASSWORD, then run: java -jar backend/target/ntsumele-bank-1.0.0.jar'
