package com.example.banking.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised error mapping. Exceptions thrown from controllers/services
 * become uniform JSON responses (RFC 7807-flavoured).
 *
 * Critical security rule: never include raw exception messages or stack
 * traces in the response. Log them server-side; show the caller a generic
 * error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException e,
                                                            HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", req);
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> onMalformedJson(HttpMessageNotReadableException e,
                                                               HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body could not be parsed", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(ResourceNotFoundException e,
                                                          HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), req);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> onInsufficientFunds(InsufficientFundsException e,
                                                                   HttpServletRequest req) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", e.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> onBusinessRule(BusinessRuleException e,
                                                              HttpServletRequest req) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", e.getMessage(), req);
    }

    @ExceptionHandler(PaymentProcessorException.class)
    public ResponseEntity<Map<String, Object>> onPaymentProcessor(PaymentProcessorException e,
                                                                  HttpServletRequest req) {
        log.warn("Payment processor error", e);
        return body(HttpStatus.BAD_GATEWAY, "PAYMENT_PROCESSOR_ERROR",
                "External payment processor is unavailable", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onAccessDenied(AccessDeniedException e,
                                                              HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to access this resource", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onUnhandled(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", req);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code,
                                                     String detail, HttpServletRequest req) {
        return ResponseEntity.status(status).body(baseBody(status, code, detail, req));
    }

    private Map<String, Object> baseBody(HttpStatus status, String code,
                                         String detail, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("title", status.getReasonPhrase());
        m.put("code", code);
        m.put("detail", detail);
        m.put("instance", req.getRequestURI());
        return m;
    }
}
