package za.co.ntsumele.bank.http.httpWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import za.co.ntsumele.bank.http.httpDto.HttpProblemResponse;

/**
 * class : HttpFailureWriter
 * Author : Nyiko Chauke
 * 2026
 *
 * Writes an HTTP status and JSON error body for failures raised before a request reaches Spring MVC exception advice.
 */
@Component
public class HttpFailureWriter {

    private final ObjectMapper objectMapper;

    public HttpFailureWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new HttpProblemResponse(code, message));
    }
}
