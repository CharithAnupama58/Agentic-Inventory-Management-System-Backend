package com.pos.system.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ProductDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Request {

        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 100,
              message = "Product name must be between 2 and 100 characters")
        private String name;

        @Size(max = 50, message = "Barcode must not exceed 50 characters")
        private String barcode;

        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2,
                message = "Price must have at most 2 decimal places")
        private BigDecimal price;

        @NotNull(message = "Cost price is required")
        @DecimalMin(value = "0.00", message = "Cost price cannot be negative")
        @Digits(integer = 8, fraction = 2,
                message = "Cost price must have at most 2 decimal places")
        private BigDecimal costPrice;

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 999999, message = "Stock cannot exceed 999,999")
        private Integer stock;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID        id;
        private String      name;
        private String      barcode;
        private BigDecimal  price;
        private BigDecimal  costPrice;
        private Integer     stock;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
