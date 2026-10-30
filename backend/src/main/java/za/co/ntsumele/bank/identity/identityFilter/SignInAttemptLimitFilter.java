package za.co.ntsumele.bank.identity.identityFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.ntsumele.bank.http.httpWriter.HttpFailureWriter;

/**
 * class : SignInAttemptLimitFilter
 * Author : Nyiko Chauke
 * 2026
 *
 * Limits login attempts per direct peer address and minute in this application instance,
 * returning HTTP 429 with a retry delay when a quota or tracking limit is exceeded.
 * Forwarded headers do not determine the rate-limit key.
 */
public class SignInAttemptLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 15;
    private static final int MAX_TRACKED_PEERS = 10_000;

    private final ConcurrentHashMap<String, SignInAttemptBucket> attemptBuckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final HttpFailureWriter httpFailureWriter;

    public SignInAttemptLimitFilter(Clock clock, HttpFailureWriter httpFailureWriter) {
        this.clock = clock;
        this.httpFailureWriter = httpFailureWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/login".equals(request.getServletPath())) {
            long minuteWindow = clock.millis() / 60_000;
            if (attemptBuckets.size() > MAX_TRACKED_PEERS) {
                attemptBuckets.entrySet().removeIf(entry -> entry.getValue().minuteWindow() < minuteWindow);
            }
            if (attemptBuckets.size() > MAX_TRACKED_PEERS) {
                rejectLogin(response);
                return;
            }

            SignInAttemptBucket bucket = attemptBuckets.compute(request.getRemoteAddr(), (peer, previous) ->
                    previous == null || previous.minuteWindow() != minuteWindow
                            ? new SignInAttemptBucket(minuteWindow, 1)
                            : new SignInAttemptBucket(minuteWindow, previous.attemptCount() + 1));
            if (bucket.attemptCount() > MAX_ATTEMPTS_PER_MINUTE) {
                rejectLogin(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void rejectLogin(HttpServletResponse response) throws IOException {
        response.setHeader("Retry-After", "60");
        httpFailureWriter.writeError(response, 429, "RATE_LIMITED", "Please wait before signing in again.");
    }

    private record SignInAttemptBucket(long minuteWindow, int attemptCount) {
    }
}
