package com.pos.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String  message;
    private T       data;
    private ApiError error;
    private LocalDateTime timestamp;
    private String  path;
    private int     statusCode;

    // ── Success responses ─────────────────────────────────────────────────────
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .statusCode(201)
                .build();
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .statusCode(204)
                .build();
    }

    // ── Error responses ───────────────────────────────────────────────────────
    public static <T> ApiResponse<T> error(String message, int statusCode,
                                            String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder()
                        .message(message)
                        .code("ERR_" + statusCode)
                        .build())
                .timestamp(LocalDateTime.now())
                .statusCode(statusCode)
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode,
                                            int statusCode, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder()
                        .message(message)
                        .code(errorCode)
                        .build())
                .timestamp(LocalDateTime.now())
                .statusCode(statusCode)
                .path(path)
                .build();
    }

    // ── Nested error detail ───────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiError {
        private String code;
        private String message;
        private java.util.Map<String, String> fieldErrors; // for validation
    }
}
