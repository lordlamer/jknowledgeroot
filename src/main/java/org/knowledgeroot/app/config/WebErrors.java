package org.knowledgeroot.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class WebErrors extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return super.handleExceptionInternal(ex, safeBody(status), headers, status, request);
    }

    @ExceptionHandler({EmptyResultDataAccessException.class, org.knowledgeroot.app.security.context.domain.UserNotFoundException.class})
    ResponseEntity<Object> missing(Exception ex) { return response(HttpStatus.NOT_FOUND); }

    @ExceptionHandler(org.knowledgeroot.app.file.domain.StoredFileNotFoundException.class)
    ResponseEntity<Object> missingObject(Exception ex) { return response(HttpStatus.NOT_FOUND); }

    @ExceptionHandler(org.knowledgeroot.app.file.domain.StorageException.class)
    ResponseEntity<Object> storage(Exception ex) {
        log.error("Storage request failed", ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalid(IllegalArgumentException ex) { return response(HttpStatus.BAD_REQUEST); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException ex) { return response(HttpStatus.FORBIDDEN); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> conflict(DataIntegrityViolationException ex) {
        log.warn("Database constraint rejected request", ex);
        return response(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    ResponseEntity<Object> concurrentWrite(Exception ex) { return response(HttpStatus.CONFLICT); }

    @ExceptionHandler(org.springframework.jdbc.UncategorizedSQLException.class)
    ResponseEntity<Object> databaseFailure(org.springframework.jdbc.UncategorizedSQLException ex) {
        // MariaDB can reject a locking read after a concurrent commit with ER_CHECKREAD.
        if (ex.getSQLException() != null && ex.getSQLException().getErrorCode() == 1020)
            return response(HttpStatus.CONFLICT);
        return failed(ex);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> failed(Exception ex) {
        log.error("Request failed", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Object> response(HttpStatus status) {
        return ResponseEntity.status(status).body(safeBody(status));
    }

    private ProblemDetail safeBody(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return ProblemDetail.forStatusAndDetail(status, known == null ? "Request failed" : known.getReasonPhrase());
    }
}
