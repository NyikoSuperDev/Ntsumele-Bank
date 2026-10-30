package za.co.ntsumele.bank.security;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * class : PostgresTlsVerification
 * Author : Nyiko Chauke
 * 2026
 *
 * Verifies an actual encrypted PostgreSQL connection and rejection of an untrusted CA or hostname.
 * Runs only in the explicit postgres-tls Maven profile with ephemeral test certificates.
 */
class PostgresTlsVerification {
    @Test
    void verifiesServerCertificateHostnameAndEncryptedSession() throws Exception {
        try (var connection = DriverManager.getConnection(url(), properties(required("TEST_DATABASE_SSL_ROOT_CERT")));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("ssl")).isTrue();
        }
    }

    @Test
    void rejectsUnknownCertificateAuthority() {
        assertThatThrownBy(() -> {
            try (var ignored = DriverManager.getConnection(url(), properties(required("TEST_DATABASE_WRONG_SSL_ROOT_CERT")))) {
                throw new AssertionError("Untrusted PostgreSQL certificate accepted");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void rejectsCertificateHostnameMismatch() {
        String correct = url();
        assertThat(correct).startsWith("jdbc:postgresql://localhost:");
        String mismatch = correct.replace("jdbc:postgresql://localhost:", "jdbc:postgresql://127.0.0.1:");
        assertThatThrownBy(() -> {
            try (var ignored = DriverManager.getConnection(mismatch, properties(required("TEST_DATABASE_SSL_ROOT_CERT")))) {
                throw new AssertionError("Mismatched PostgreSQL hostname accepted");
            }
        }).isInstanceOf(SQLException.class);
    }

    private static String url() {
        String value = required("TEST_DATABASE_TLS_URL");
        if (!value.startsWith("jdbc:postgresql://") || !value.contains("sslmode=verify-full")
                || !URI.create(value.substring(5)).getPath().matches("/[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException("TEST_DATABASE_TLS_URL requires verified TLS and a disposable _test database.");
        }
        return value;
    }

    private static Properties properties(String certificate) {
        Properties values = new Properties();
        values.setProperty("user", required("TEST_DATABASE_USERNAME"));
        values.setProperty("password", required("TEST_DATABASE_PASSWORD"));
        values.setProperty("sslmode", "verify-full");
        values.setProperty("sslrootcert", certificate);
        values.setProperty("connectTimeout", "5");
        return values;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the postgres-tls verification profile.");
        }
        return value;
    }
}
