#!/usr/bin/env sh
# Runs once on a new PostgreSQL volume, after the bootstrap database exists.
set -eu
: "${APP_DB_USERNAME:?APP_DB_USERNAME is required}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_user="$APP_DB_USERNAME" --set=app_password="$APP_DB_PASSWORD" \
  --set=database_name="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"app_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"database_name" TO :"app_user";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
SQL
