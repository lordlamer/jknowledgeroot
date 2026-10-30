package org.knowledgeroot.app.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client API errors keep the application's own 4xx reasons so apps can show actionable messages.
 * Server failures fall through to the generic {@code WebErrors} handling without internal details.
 */
@RestControllerAdvice(basePackageClasses = ApiErrors.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String reason = status.is4xxClientError() && ex.getReason() != null ? ex.getReason() : null;
        if (status.is5xxServerError()) log.error("Client API request failed", ex);
        return problem(status, reason);
    }

    @ExceptionHandler({EmptyResultDataAccessException.class,
            org.knowledgeroot.app.security.context.domain.UserNotFoundException.class})
    ResponseEntity<ProblemDetail> missing(Exception ex) {
        return problem(HttpStatus.NOT_FOUND, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
        log.warn("Database constraint rejected client API request", ex);
        return problem(HttpStatus.CONFLICT, "The change conflicts with existing data.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String reason) {
        HttpStatus known = HttpStatus.resolve(status.value());
        String detail = reason != null ? reason : known == null ? "Request failed" : known.getReasonPhrase();
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
