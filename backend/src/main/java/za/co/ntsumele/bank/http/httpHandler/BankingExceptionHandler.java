package za.co.ntsumele.bank.http.httpHandler;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import za.co.ntsumele.bank.failure.failureException.BankingRuleViolation;
import za.co.ntsumele.bank.http.httpDto.HttpProblemResponse;

/**
 * class : BankingExceptionHandler
 * Author : Nyiko Chauke
 * 2026
 *
 * Maps supported business-rule, request-validation and database exceptions to consistent HTTP error responses.
 * Returns sanitized messages and retry guidance for database contention, timeouts or unavailability.
 */
@RestControllerAdvice
public class BankingExceptionHandler {

    @ExceptionHandler(BankingRuleViolation.class)
    public ResponseEntity<HttpProblemResponse> handleBusinessException(BankingRuleViolation exception) {
        HttpStatus status = switch (exception.getCategory()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new HttpProblemResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<HttpProblemResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new HttpProblemResponse("INVALID_REQUEST", "Check the request fields and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<HttpProblemResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new HttpProblemResponse(
                "CONFLICT", "The request conflicts with an existing record. Refresh and try again."));
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<HttpProblemResponse> handleDatabaseContention(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(new HttpProblemResponse("BUSY", "The record is busy. Retry with the same idempotency key."));
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<HttpProblemResponse> handleDatabaseUnavailable(DataAccessResourceFailureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(new HttpProblemResponse("DATABASE_UNAVAILABLE",
                        "The banking service is temporarily unavailable. Retry with the same idempotency key."));
    }
}
