package com.pos.system.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ── Base exception ────────────────────────────────────────────────────────────
public class PosException extends RuntimeException {

    private final String errorCode;
    private final int    statusCode;

    public PosException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode  = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode()  { return errorCode;  }
    public int    getStatusCode() { return statusCode; }

    // ── 404 Not Found ─────────────────────────────────────────────────────────
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends PosException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found with id: " + id,
                  "RESOURCE_NOT_FOUND", 404);
        }
        public ResourceNotFoundException(String message) {
            super(message, "RESOURCE_NOT_FOUND", 404);
        }
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class BadRequestException extends PosException {
        public BadRequestException(String message) {
            super(message, "BAD_REQUEST", 400);
        }
        public BadRequestException(String message, String errorCode) {
            super(message, errorCode, 400);
        }
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConflictException extends PosException {
        public ConflictException(String message) {
            super(message, "CONFLICT", 409);
        }
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class ForbiddenException extends PosException {
        public ForbiddenException(String message) {
            super(message, "FORBIDDEN", 403);
        }
    }

    // ── 422 Business Logic ────────────────────────────────────────────────────
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class BusinessException extends PosException {
        public BusinessException(String message) {
            super(message, "BUSINESS_RULE_VIOLATION", 422);
        }
        public BusinessException(String message, String errorCode) {
            super(message, errorCode, 422);
        }
    }

    // ── 507 Insufficient Stock ────────────────────────────────────────────────
    public static class InsufficientStockException extends PosException {
        private final String productName;
        private final int    available;
        private final int    requested;

        public InsufficientStockException(String productName,
                                           int available, int requested) {
            super("Insufficient stock for '" + productName
                    + "'. Available: " + available
                    + ", Requested: " + requested,
                  "INSUFFICIENT_STOCK", 422);
            this.productName = productName;
            this.available   = available;
            this.requested   = requested;
        }

        public String getProductName() { return productName; }
        public int    getAvailable()   { return available;   }
        public int    getRequested()   { return requested;   }
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────────────
    public static class UnauthorizedException extends PosException {
        public UnauthorizedException(String message) {
            super(message, "UNAUTHORIZED", 401);
        }
    }

    // ── Tenant isolation violation ────────────────────────────────────────────
    public static class TenantAccessException extends PosException {
        public TenantAccessException() {
            super("Access denied: resource belongs to different tenant",
                  "TENANT_ACCESS_DENIED", 403);
        }
    }
}
