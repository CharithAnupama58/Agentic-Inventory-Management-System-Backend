package com.pos.system.config;

import com.pos.system.dto.ApiResponse;
import com.pos.system.exception.PosException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation errors (@Valid) ────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("Validation failed for request [{}]: {}",
                request.getRequestURI(), fieldErrors);

        return ApiResponse.builder()
                .success(false)
                .statusCode(400)
                .path(request.getRequestURI())
                .timestamp(java.time.LocalDateTime.now())
                .error(ApiResponse.ApiError.builder()
                        .code("VALIDATION_FAILED")
                        .message("Request validation failed. Check field errors.")
                        .fieldErrors(fieldErrors)
                        .build())
                .build();
    }

    // ── Custom POS business exceptions ───────────────────────────────────────
    @ExceptionHandler(PosException.class)
    public ResponseEntity<ApiResponse<?>> handlePosException(
            PosException ex,
            HttpServletRequest request) {

        log.warn("[{}] {} — path: {}",
                ex.getErrorCode(), ex.getMessage(), request.getRequestURI());

        ApiResponse<?> response = ApiResponse.error(
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getStatusCode(),
                request.getRequestURI());

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(response);
    }

    // ── Insufficient stock (special case with extra detail) ───────────────────
    @ExceptionHandler(PosException.InsufficientStockException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<?> handleInsufficientStock(
            PosException.InsufficientStockException ex,
            HttpServletRequest request) {

        log.warn("Stock shortage — product: '{}', available: {}, requested: {}",
                ex.getProductName(), ex.getAvailable(), ex.getRequested());

        Map<String, String> details = new HashMap<>();
        details.put("product",   ex.getProductName());
        details.put("available", String.valueOf(ex.getAvailable()));
        details.put("requested", String.valueOf(ex.getRequested()));

        return ApiResponse.builder()
                .success(false)
                .statusCode(422)
                .path(request.getRequestURI())
                .timestamp(java.time.LocalDateTime.now())
                .error(ApiResponse.ApiError.builder()
                        .code("INSUFFICIENT_STOCK")
                        .message(ex.getMessage())
                        .fieldErrors(details)
                        .build())
                .build();
    }

    // ── Spring Security exceptions ────────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Bad credentials attempt for path: {}", request.getRequestURI());

        return ApiResponse.error(
                "Invalid email or password",
                "INVALID_CREDENTIALS", 401,
                request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleAuthException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.warn("Authentication failed: {} — path: {}",
                ex.getMessage(), request.getRequestURI());

        return ApiResponse.error(
                "Authentication required",
                "AUTHENTICATION_REQUIRED", 401,
                request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied — path: {}, message: {}",
                request.getRequestURI(), ex.getMessage());

        return ApiResponse.error(
                "You do not have permission to perform this action",
                "ACCESS_DENIED", 403,
                request.getRequestURI());
    }

    // ── Database constraint violations ────────────────────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.error("Data integrity violation — path: {}, cause: {}",
                request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());

        String message = "Data conflict: a record with this value already exists";
        if (ex.getMostSpecificCause().getMessage().contains("barcode"))
            message = "A product with this barcode already exists";
        if (ex.getMostSpecificCause().getMessage().contains("email"))
            message = "An account with this email already exists";

        return ApiResponse.error(message, "DATA_CONFLICT", 409,
                request.getRequestURI());
    }

    // ── Type mismatch (invalid UUID etc.) ─────────────────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = String.format(
                "Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());

        log.warn("Type mismatch — {}", message);

        return ApiResponse.error(message, "INVALID_PARAMETER", 400,
                request.getRequestURI());
    }

    // ── Generic RuntimeException ──────────────────────────────────────────────
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        log.error("Unhandled runtime exception — path: {}, message: {}",
                request.getRequestURI(), ex.getMessage(), ex);

        return ApiResponse.error(
                "An unexpected error occurred. Please try again.",
                "INTERNAL_ERROR", 500,
                request.getRequestURI());
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleAllExceptions(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error — path: {}", request.getRequestURI(), ex);

        return ApiResponse.error(
                "Something went wrong. Our team has been notified.",
                "UNEXPECTED_ERROR", 500,
                request.getRequestURI());
    }
}
