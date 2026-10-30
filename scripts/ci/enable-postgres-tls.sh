#!/usr/bin/env sh
# CI-only: disposable certificates for the isolated PostgreSQL service, never production credentials.
set -eu
postgres_container=${1:?Pass the CI PostgreSQL service container ID}
tls_dir=$(mktemp -d "${RUNNER_TEMP:-/tmp}/ntsumele-tls.XXXXXX")
chmod 700 "$tls_dir"
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 2 -subj '/CN=Ntsumele CI CA' \
  -keyout "$tls_dir/ca.key" -out "$tls_dir/ca.crt" >/dev/null 2>&1
openssl req -newkey rsa:2048 -nodes -sha256 -subj '/CN=localhost' \
  -keyout "$tls_dir/server.key" -out "$tls_dir/server.csr" >/dev/null 2>&1
printf '%s\n' 'subjectAltName=DNS:localhost' 'extendedKeyUsage=serverAuth' > "$tls_dir/server.ext"
openssl x509 -req -sha256 -days 2 -in "$tls_dir/server.csr" -CA "$tls_dir/ca.crt" \
  -CAkey "$tls_dir/ca.key" -CAcreateserial -extfile "$tls_dir/server.ext" -out "$tls_dir/server.crt" >/dev/null 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 2 -subj '/CN=Untrusted CI CA' \
  -keyout "$tls_dir/wrong-ca.key" -out "$tls_dir/wrong-ca.crt" >/dev/null 2>&1
docker cp "$tls_dir/server.key" "$postgres_container:/var/lib/postgresql/data/ci-server.key"
docker cp "$tls_dir/server.crt" "$postgres_container:/var/lib/postgresql/data/ci-server.crt"
docker exec --user 0 "$postgres_container" sh -c \
  'chown postgres:postgres /var/lib/postgresql/data/ci-server.* && chmod 600 /var/lib/postgresql/data/ci-server.key'
docker exec -i "$postgres_container" sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
ALTER SYSTEM SET ssl_cert_file = '/var/lib/postgresql/data/ci-server.crt';
ALTER SYSTEM SET ssl_key_file = '/var/lib/postgresql/data/ci-server.key';
ALTER SYSTEM SET ssl = 'on';
SELECT pg_reload_conf();
SQL
{
  printf '%s\n' 'TEST_DATABASE_TLS_URL=jdbc:postgresql://localhost:5432/ntsumele_test?sslmode=verify-full'
  printf 'TEST_DATABASE_SSL_ROOT_CERT=%s\n' "$tls_dir/ca.crt"
  printf 'TEST_DATABASE_WRONG_SSL_ROOT_CERT=%s\n' "$tls_dir/wrong-ca.crt"
} >> "${GITHUB_ENV:?This helper is intended for GitHub Actions}"
