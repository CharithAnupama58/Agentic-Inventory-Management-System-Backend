package com.pos.system.dto;

import jakarta.validation.constraints.*;
import lombok.*;

public class AuthDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "Business name is required")
        @Size(min = 2, max = 100, message = "Business name must be 2–100 characters")
        private String businessName;

        @Size(max = 50, message = "Business type must not exceed 50 characters")
        private String businessType;

        @NotBlank(message = "Your name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100,
              message = "Password must be at least 6 characters")
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).{6,}$",
                 message = "Password must contain at least one letter and one number")
        private String password;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String email;
        private String name;
        private String role;
        private String tenantId;
    }
}
