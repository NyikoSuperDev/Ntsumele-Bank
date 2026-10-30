package za.co.ntsumele.bank.http.httpFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.ntsumele.bank.http.httpWriter.HttpFailureWriter;

/**
 * class : JsonRequestSizeFilter
 * Author : Nyiko Chauke
 * 2026
 *
 * Enforces the 16 KiB limit on JSON API POST, PUT and PATCH bodies before deserialization, including requests with unknown or chunked lengths.
 * Rejects oversized bodies and supplies accepted bodies to downstream filters and controllers without changing form login requests.
 */
public class JsonRequestSizeFilter extends OncePerRequestFilter {

    public static final int MAX_BYTES = 16 * 1024;

    private final HttpFailureWriter httpFailureWriter;

    public JsonRequestSizeFilter(HttpFailureWriter httpFailureWriter) {
        this.httpFailureWriter = httpFailureWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        String mediaType = contentType == null
                ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean mutation = switch (request.getMethod()) {
            case "POST", "PUT", "PATCH" -> true;
            default -> false;
        };
        boolean json = "application/json".equals(mediaType)
                || (mediaType.startsWith("application/") && mediaType.endsWith("+json"));
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        if (!mutation || !requestPath.startsWith("/api/") || !json) {
            // Leave form login untouched so the servlet container can parse its parameters.
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            rejectRequest(response);
            return;
        }

        // Bounded even with absent or misleading Content-Length, or chunked transfer encoding.
        byte[] requestBody = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (requestBody.length > MAX_BYTES) {
            rejectRequest(response);
            return;
        }
        filterChain.doFilter(new CachedJsonRequest(request, requestBody), response);
    }

    private void rejectRequest(HttpServletResponse response) throws IOException {
        httpFailureWriter.writeError(
                response, 413, "PAYLOAD_TOO_LARGE", "JSON request bodies must not exceed 16 KiB.");
    }

    private static final class CachedJsonRequest extends HttpServletRequestWrapper {

        private final byte[] requestBody;

        private CachedJsonRequest(HttpServletRequest request, byte[] requestBody) {
            super(request);
            this.requestBody = requestBody;
        }

        @Override
        public int getContentLength() {
            return requestBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return requestBody.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bodyStream = new ByteArrayInputStream(requestBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bodyStream.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return bodyStream.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return bodyStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("Asynchronous body reads are not supported for this endpoint.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
