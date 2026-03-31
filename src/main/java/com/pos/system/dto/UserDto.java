package com.pos.system.dto;

import com.pos.system.model.User;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be 2–80 characters")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Provide a valid email")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @NotNull(message = "Role is required")
        private User.Role role;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRoleRequest {
        @NotNull(message = "Role is required")
        private User.Role role;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID          id;
        private String        name;
        private String        email;
        private String        role;
        private LocalDateTime createdAt;
    }
}
