package za.co.ntsumele.bank.security;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import za.co.ntsumele.bank.identity.identityConfig.SecureTransportConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * class : SecureTransportConfigTest
 * Author : Nyiko Chauke
 * 2026
 *
 * Checks that the secure profile rejects unsafe proxy and JDBC TLS configuration before connecting.
 * Certificate authentication itself is exercised by the separate PostgreSQL TLS verification profile.
 */
class SecureTransportConfigTest {
    @TempDir
    Path temporary;
    private ApplicationContextRunner context;

    @BeforeEach
    void configureValidProperties() throws Exception {
        Path ca = Files.writeString(temporary.resolve("root.crt"), "readable test CA placeholder");
        context = new ApplicationContextRunner()
                .withInitializer(application -> application.getEnvironment().setActiveProfiles("secure"))
                .withUserConfiguration(SecureTransportConfig.class)
                .withPropertyValues("server.forward-headers-strategy=native", "app.security.require-https=true",
                        "app.security.public-origin=https://bank.example.test",
                        "server.tomcat.remoteip.internal-proxies=10\\.20\\.0\\.10",
                        "spring.datasource.url=jdbc:postgresql://db.example/ntsumele?sslmode=verify-full",
                        "spring.datasource.hikari.data-source-properties.sslmode=verify-full",
                        "spring.datasource.hikari.data-source-properties.sslrootcert=" + ca);
    }

    @Test
    void acceptsExplicitProxyAndVerifiedDatabaseConfiguration() {
        context.run(application -> assertThat(application).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "server.forward-headers-strategy=framework", "server.tomcat.remoteip.internal-proxies=",
            "server.tomcat.remoteip.internal-proxies=.*", "app.security.require-https=false",
            "app.security.public-origin=http://bank.example.test",
            "app.security.public-origin=https://bank.example.test/path",
            "app.security.public-origin=https://user:password@bank.example.test",
            "spring.datasource.hikari.data-source-properties.sslmode=require",
            "spring.datasource.hikari.data-source-properties.sslfactory=org.postgresql.ssl.NonValidatingFactory",
            "spring.datasource.hikari.data-source-properties.sslhostnameverifier=custom.HostnameVerifier",
            "spring.datasource.hikari.data-source-properties.ssl=false",
            "spring.datasource.hikari.data-source-properties.sslrootcert=/missing/ca.crt",
            "spring.datasource.url=jdbc:postgresql://db.example/ntsumele",
            "spring.datasource.url=jdbc:postgresql://db.example/ntsumele?sslmode=require",
            "spring.datasource.url=jdbc:postgresql://db.example/ntsumele?sslmode=verify-full&sslmode=disable",
            "spring.datasource.url=jdbc:postgresql://db.example/ntsumele?sslmode=verify-full&sslfactory=org.postgresql.ssl.NonValidatingFactory"
    })
    void refusesUnsafeProductionSettings(String property) {
        context.withPropertyValues(property).run(application -> assertThat(application).hasFailed());
    }
}
