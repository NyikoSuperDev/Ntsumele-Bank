package za.co.ntsumele.bank.identity.identityFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.ntsumele.bank.http.httpWriter.HttpFailureWriter;

/**
 * class : HttpsEnforcementFilter
 * Author : Nyiko Chauke
 * 2026
 *
 * Redirects plaintext reads to the configured public HTTPS origin and rejects plaintext mutations.
 * Trusts the container's secure flag after proxy validation, rather than client-supplied headers.
 */
public class HttpsEnforcementFilter extends OncePerRequestFilter {
    private final String publicOrigin;
    private final HttpFailureWriter failures;

    public HttpsEnforcementFilter(String publicOrigin, HttpFailureWriter failures) {
        URI origin = URI.create(publicOrigin);
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("PUBLIC_ORIGIN must be an HTTPS origin without a path or credentials.");
        }
        this.publicOrigin = publicOrigin.endsWith("/") ? publicOrigin.substring(0, publicOrigin.length() - 1) : publicOrigin;
        this.failures = failures;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.isSecure() || "/actuator/health".equals(request.getServletPath())) {
            chain.doFilter(request, response);
        } else if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
            String query = request.getQueryString();
            response.setStatus(308);
            response.setHeader("Location", publicOrigin + request.getRequestURI() + (query == null ? "" : "?" + query));
            response.setHeader("Cache-Control", "no-store");
        } else {
            failures.writeError(response, 403, "HTTPS_REQUIRED", "Use the secure banking address to continue.");
        }
    }
}
