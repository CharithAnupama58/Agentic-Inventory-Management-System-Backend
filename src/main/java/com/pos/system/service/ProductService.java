package com.pos.system.service;

import com.pos.system.config.CacheConfig;
import com.pos.system.dto.PageResponse;
import com.pos.system.dto.ProductDto;
import com.pos.system.exception.PosException;
import com.pos.system.model.Product;
import com.pos.system.repository.ProductRepository;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // ── Get all products — NO CACHE (always fresh stock data) ─────────────────
    public List<ProductDto.Response> getAllProducts() {
        UUID tenantId = TenantContext.getTenantId();
        log.debug("Fetching all products for tenant: {}", tenantId);
        return productRepository.findAllByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Paginated — keep cache for performance ────────────────────────────────
    @Cacheable(
        value = CacheConfig.CACHE_PRODUCTS,
        key   = "'page:' + T(com.pos.system.security.TenantContext).getTenantId()"
              + "+ ':' + #page + ':' + #size + ':' + #search"
    )
    public PageResponse<ProductDto.Response> getProductsPaginated(
            int page, int size, String search) {

        UUID tenantId = TenantContext.getTenantId();
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Product> result = (search != null && !search.isBlank())
                ? productRepository.searchByTenantId(
                        tenantId, search.trim(), pageable)
                : productRepository.findAllByTenantId(tenantId, pageable);

        return PageResponse.of(
                result.getContent().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()),
                page, size, result.getTotalElements());
    }

    // ── Get single product — NO CACHE (always fresh) ──────────────────────────
    public ProductDto.Response getProduct(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return toResponse(
                productRepository.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(() -> new PosException
                                .ResourceNotFoundException(
                                        "Product", id.toString())));
    }

    // ── Create ────────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PRODUCTS, allEntries = true)
    public ProductDto.Response createProduct(ProductDto.Request request) {
        UUID tenantId = TenantContext.getTenantId();
        log.info("Creating product '{}' for tenant: {}",
                request.getName(), tenantId);

        if (request.getBarcode() != null
                && !request.getBarcode().isBlank()) {
            if (productRepository.existsByBarcodeAndTenantId(
                    request.getBarcode(), tenantId))
                throw new PosException.ConflictException(
                        "Barcode already exists: "
                        + request.getBarcode());
        }

        Product product = productRepository.save(Product.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .barcode(request.getBarcode())
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .stock(request.getStock())
                .build());

        return toResponse(product);
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PRODUCTS, allEntries = true)
    public ProductDto.Response updateProduct(UUID id,
                                              ProductDto.Request request) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PosException
                        .ResourceNotFoundException(
                                "Product", id.toString()));

        if (request.getBarcode() != null
                && !request.getBarcode().isBlank()
                && !request.getBarcode().equals(product.getBarcode())) {
            if (productRepository.existsByBarcodeAndTenantId(
                    request.getBarcode(), tenantId))
                throw new PosException.ConflictException(
                        "Barcode already exists: "
                        + request.getBarcode());
        }

        product.setName(request.getName());
        product.setBarcode(request.getBarcode());
        product.setPrice(request.getPrice());
        product.setCostPrice(request.getCostPrice());
        product.setStock(request.getStock());

        return toResponse(productRepository.save(product));
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PRODUCTS, allEntries = true)
    public void deleteProduct(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PosException
                        .ResourceNotFoundException(
                                "Product", id.toString()));
        productRepository.delete(product);
    }

    // ── toResponse ────────────────────────────────────────────────────────────
    private ProductDto.Response toResponse(Product p) {
        return ProductDto.Response.builder()
                .id(p.getId())
                .name(p.getName())
                .barcode(p.getBarcode())
                .price(p.getPrice())
                .costPrice(p.getCostPrice())
                .stock(p.getStock())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
