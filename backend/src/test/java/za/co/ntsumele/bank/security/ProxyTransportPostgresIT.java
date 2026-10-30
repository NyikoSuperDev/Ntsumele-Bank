package za.co.ntsumele.bank.security;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * class : ProxyTransportPostgresIT
 * Author : Nyiko Chauke
 * 2026
 *
 * Exercises HTTPS redirects and HSTS through the real embedded Tomcat forwarding valve.
 * Uses separate loopback source addresses to distinguish a trusted ingress from a spoofing client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
        "server.tomcat.remoteip.trusted-proxies=", "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto",
        "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For", "app.security.require-https=true",
        "app.security.public-origin=https://bank.example.test",
        "server.servlet.session.cookie.secure=true", "app.idempotency.cleanup.enabled=false",
        "spring.datasource.hikari.data-source-properties.sslmode=disable"
})
@ActiveProfiles("demo")
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class ProxyTransportPostgresIT {
    @LocalServerPort
    private int port;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = required("TEST_DATABASE_URL");
        if (!url.startsWith("jdbc:postgresql://")
                || !URI.create(url.substring(5)).getPath().matches("/[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException("Proxy transport tests require a disposable PostgreSQL _test database.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> required("TEST_DATABASE_USERNAME"));
        properties.add("spring.datasource.password", () -> required("TEST_DATABASE_PASSWORD"));
    }

    @Test
    void trustedProxyHttpsGetsHstsAndSecureSessionCookie() throws Exception {
        var response = get("/api/csrf", "https");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Strict-Transport-Security").orElseThrow())
                .contains("max-age=31536000", "includeSubDomains");
        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(cookie -> cookie.contains("Secure"));
    }

    @Test
    void plainAndMixedProtocolRequestsRedirectWithoutClaimingHsts() throws Exception {
        for (String protocol : new String[]{"http", "https,http"}) {
            var response = get("/api/csrf", protocol);
            assertThat(response.statusCode()).isEqualTo(308);
            assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo("https://bank.example.test/api/csrf");
            assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
        }
    }

    @Test
    void directClientCannotSpoofHttpsOrProxyTrust() throws Exception {
        try (Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress("127.0.0.2", 0));
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(5000);
            String request = "GET /api/csrf HTTP/1.1\r\nHost: localhost:" + port
                    + "\r\nX-Forwarded-Proto: https\r\nX-Forwarded-For: 127.0.0.1"
                    + "\r\nForwarded: for=127.0.0.1;proto=https\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).startsWith("HTTP/1.1 308").containsIgnoringCase("Location: https://bank.example.test/api/csrf");
            assertThat(response).doesNotContainIgnoringCase("Strict-Transport-Security:");
        }
    }

    @Test
    void privateHttpHealthProbeRemainsAvailableWithoutHsts() throws Exception {
        var response = get("/actuator/health", "http");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void plaintextMutationIsRejectedInsteadOfReplayingItsBody() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/login"))
                .header("X-Forwarded-Proto", "http")
                .POST(HttpRequest.BodyPublishers.ofString("username=unused&password=unused")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("HTTPS_REQUIRED");
    }

    private HttpResponse<String> get(String path, String protocol) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Forwarded-Proto", protocol).header("X-Forwarded-For", "203.0.113.25")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for proxy integration tests.");
        }
        return value;
    }
}
