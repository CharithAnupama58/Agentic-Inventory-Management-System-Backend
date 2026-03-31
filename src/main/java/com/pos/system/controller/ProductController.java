package com.pos.system.controller;

import com.pos.system.dto.PageResponse;
import com.pos.system.dto.ProductDto;
import com.pos.system.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductDto.Response>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<ProductDto.Response>> getProductsPaged(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String search) {
        return ResponseEntity.ok(
                productService.getProductsPaginated(page, size, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.Response> getProduct(
            @PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto.Response> createProduct(
            @Valid @RequestBody ProductDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto.Response> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDto.Request request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
