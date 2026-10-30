package za.co.ntsumele.bank.identity.identityConfig;

import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * class : SecureTransportConfig
 * Author : Nyiko Chauke
 * 2026
 *
 * Rejects unsafe secure-profile proxy and database settings before application beans connect.
 * Requires explicitly trusted proxy addresses and PostgreSQL certificate/hostname verification.
 */
@Configuration(proxyBeanMethods = false)
@Profile("secure")
public class SecureTransportConfig {

    @Bean
    public static BeanFactoryPostProcessor validateSecureTransport(Environment environment) {
        return beanFactory -> {
            require("native".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy")),
                    "The secure profile requires native forwarded-header handling.");
            require(environment.getProperty("app.security.require-https", Boolean.class, false),
                    "The secure profile requires HTTPS redirects.");
            String publicOrigin = environment.getProperty("app.security.public-origin", "");
            URI origin;
            try {
                origin = URI.create(publicOrigin);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("PUBLIC_ORIGIN must be a valid HTTPS origin.");
            }
            require("https".equals(origin.getScheme()) && origin.getHost() != null && origin.getUserInfo() == null
                            && origin.getRawQuery() == null && origin.getRawFragment() == null
                            && (origin.getPath().isEmpty() || "/".equals(origin.getPath())),
                    "PUBLIC_ORIGIN must be an HTTPS origin without a path or credentials.");
            String proxies = environment.getProperty("server.tomcat.remoteip.internal-proxies", "");
            require(!proxies.isBlank(), "Set TRUSTED_PROXY_REGEX to the dedicated ingress proxy addresses.");
            Pattern trusted = Pattern.compile(proxies);
            require(!trusted.matcher("198.51.100.1").matches() || !trusted.matcher("203.0.113.1").matches(),
                    "TRUSTED_PROXY_REGEX must not trust every address.");
            require("verify-full".equals(environment.getProperty(
                            "spring.datasource.hikari.data-source-properties.sslmode")),
                    "The secure profile requires PostgreSQL sslmode=verify-full.");
            require(environment.getProperty("spring.datasource.hikari.data-source-properties.sslfactory") == null
                            && environment.getProperty("spring.datasource.hikari.data-source-properties.sslhostnameverifier") == null
                            && !"false".equalsIgnoreCase(environment.getProperty(
                                    "spring.datasource.hikari.data-source-properties.ssl")),
                    "Custom database TLS factories and hostname verifiers are prohibited in the secure profile.");
            String jdbcUrl = environment.getRequiredProperty("spring.datasource.url");
            require(jdbcUrl.startsWith("jdbc:postgresql://"), "Configure a PostgreSQL network JDBC URL.");
            Map<String, String> options = queryOptions(jdbcUrl);
            require("verify-full".equals(options.get("sslmode")),
                    "DB_URL must explicitly contain sslmode=verify-full in the secure profile.");
            require(!options.containsKey("sslfactory") && !options.containsKey("sslhostnameverifier")
                            && !options.containsKey("sslrootcert")
                            && !"false".equalsIgnoreCase(options.get("ssl")),
                    "Configure the trusted CA through DB_SSL_ROOT_CERT; custom TLS bypass options are prohibited.");
            String root = environment.getProperty("spring.datasource.hikari.data-source-properties.sslrootcert", "");
            require(!root.isBlank() && Files.isRegularFile(Path.of(root)) && Files.isReadable(Path.of(root)),
                    "DB_SSL_ROOT_CERT must identify a readable PostgreSQL CA certificate file.");
        };
    }

    private static Map<String, String> queryOptions(String jdbcUrl) {
        Map<String, String> options = new HashMap<>();
        int separator = jdbcUrl.indexOf('?');
        if (separator >= 0) {
            for (String item : jdbcUrl.substring(separator + 1).split("&")) {
                String[] entry = item.split("=", 2);
                String key = URLDecoder.decode(entry[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                String value = entry.length == 2 ? URLDecoder.decode(entry[1], StandardCharsets.UTF_8) : "";
                require(options.putIfAbsent(key, value) == null, "Duplicate DB_URL options are prohibited.");
            }
        }
        return options;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
