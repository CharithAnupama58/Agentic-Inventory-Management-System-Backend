package com.pos.system.service;

import com.pos.system.dto.ChatDto;
import com.pos.system.model.*;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolsService {

    private final ProductRepository      productRepository;
    private final SaleItemRepository     saleItemRepository;
    private final CampaignRepository     campaignRepository;
    private final InventoryService       inventoryService;
    private final ReorderSuggestionRepository reorderRepository;

    // ── TOOL 1: Get business snapshot ─────────────────────────────────────────
    public Map<String, Object> getBusinessSnapshot() {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findAllByTenantId(tenantId);

        LocalDateTime since30 = LocalDateTime.now().minusDays(30);
        Set<UUID> activeSellers = new HashSet<>(
                saleItemRepository.findProductsWithSalesSince(
                        tenantId, since30));

        long outOfStock  = products.stream()
                .filter(p -> p.getStock() == 0).count();
        long lowStock    = products.stream()
                .filter(p -> p.getStock() > 0 && p.getStock() <= 10).count();
        long slowMoving  = products.stream()
                .filter(p -> p.getStock() > 0
                        && !activeSellers.contains(p.getId())).count();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("totalProducts",  products.size());
        snapshot.put("outOfStock",     outOfStock);
        snapshot.put("lowStock",       lowStock);
        snapshot.put("slowMoving",     slowMoving);
        snapshot.put("timestamp",      LocalDateTime.now().toString());
        return snapshot;
    }

    // ── TOOL 2: Find slow moving products ────────────────────────────────────
    public List<Map<String, Object>> findSlowMovingProducts(int days) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        Set<UUID> activeSellers = new HashSet<>(
                saleItemRepository.findProductsWithSalesSince(
                        tenantId, since));

        return productRepository.findAllByTenantId(tenantId).stream()
                .filter(p -> p.getStock() > 0
                        && !activeSellers.contains(p.getId()))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",       p.getId().toString());
                    m.put("name",     p.getName());
                    m.put("stock",    p.getStock());
                    m.put("price",    p.getPrice());
                    m.put("costPrice",p.getCostPrice());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── TOOL 3: Find low stock products ──────────────────────────────────────
    public List<Map<String, Object>> findLowStockProducts(int threshold) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findAllByTenantId(tenantId).stream()
                .filter(p -> p.getStock() <= threshold && p.getStock() > 0)
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",    p.getId().toString());
                    m.put("name",  p.getName());
                    m.put("stock", p.getStock());
                    m.put("price", p.getPrice());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── TOOL 4: Find out of stock products ────────────────────────────────────
    public List<Map<String, Object>> findOutOfStockProducts() {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findAllByTenantId(tenantId).stream()
                .filter(p -> p.getStock() == 0)
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",    p.getId().toString());
                    m.put("name",  p.getName());
                    m.put("price", p.getPrice());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── TOOL 5: Create discount campaign ─────────────────────────────────────
    @Transactional
    public Map<String, Object> createCampaign(
            String name,
            String description,
            String discountType,
            double discountValue,
            List<String> productIds,
            int durationDays,
            String aiReason) {

        UUID tenantId = TenantContext.getTenantId();

        List<UUID> pIds = productIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

        Campaign campaign = campaignRepository.save(Campaign.builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .discountType(Campaign.DiscountType
                        .valueOf(discountType.toUpperCase()))
                .discountValue(BigDecimal.valueOf(discountValue))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(durationDays))
                .status(Campaign.CampaignStatus.ACTIVE)
                .productIds(pIds)
                .createdByAi(true)
                .aiReason(aiReason)
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("campaignId",    campaign.getId().toString());
        result.put("name",          campaign.getName());
        result.put("discount",      discountValue + "% off");
        result.put("products",      productIds.size() + " products");
        result.put("duration",      durationDays + " days");
        result.put("endDate",       campaign.getEndDate().toString());
        result.put("status",        "ACTIVE");
        return result;
    }

    // ── TOOL 6: Adjust stock for product ─────────────────────────────────────
    @Transactional
    public Map<String, Object> adjustProductStock(
            String productId, int quantity, String reason) {

        UUID tenantId = TenantContext.getTenantId();
        UUID pid      = UUID.fromString(productId);

        Product product = productRepository
                .findByIdAndTenantId(pid, tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Product not found: " + productId));

        int stockBefore = product.getStock();
        int stockAfter  = stockBefore + quantity;

        if (stockAfter < 0)
            throw new RuntimeException("Cannot reduce below zero");

        product.setStock(stockAfter);
        productRepository.save(product);

        inventoryService.logSaleMovement(tenantId, pid,
                Math.abs(quantity), stockBefore, stockAfter, null);

        Map<String, Object> result = new HashMap<>();
        result.put("product",     product.getName());
        result.put("stockBefore", stockBefore);
        result.put("stockAfter",  stockAfter);
        result.put("change",      quantity);
        result.put("reason",      reason);
        return result;
    }

    // ── TOOL 7: Get active campaigns ──────────────────────────────────────────
    public List<Map<String, Object>> getActiveCampaigns() {
        UUID tenantId = TenantContext.getTenantId();
        return campaignRepository
                .findByTenantIdAndStatus(tenantId,
                        Campaign.CampaignStatus.ACTIVE)
                .stream().map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",       c.getId().toString());
                    m.put("name",     c.getName());
                    m.put("discount", c.getDiscountValue() + "% off");
                    m.put("endDate",  c.getEndDate() != null
                            ? c.getEndDate().toString() : "ongoing");
                    m.put("products", c.getProductIds() != null
                            ? c.getProductIds().size() : 0);
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── TOOL 8: Get top selling products ─────────────────────────────────────
    public List<Map<String, Object>> getTopSellingProducts(int limit) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to   = LocalDateTime.now();

        return saleItemRepository
                .findProductPerformance(tenantId, from, to)
                .stream().limit(limit)
                .map(row -> {
                    UUID pid  = (UUID) row[0];
                    String name = productRepository.findById(pid)
                            .map(Product::getName).orElse("Unknown");
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",       pid.toString());
                    m.put("name",     name);
                    m.put("soldQty",  ((Number) row[1]).longValue());
                    m.put("revenue",  new BigDecimal(row[2].toString())
                            .setScale(2, RoundingMode.HALF_UP));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── TOOL 9: Generate reorder suggestions ──────────────────────────────────
    public Map<String, Object> triggerReorderAnalysis() {
        UUID tenantId = TenantContext.getTenantId();
        List<ReorderSuggestion> suggestions =
                reorderRepository.findByTenantIdAndStatusOrderByUrgencyAsc(
                        tenantId, ReorderSuggestion.SuggestionStatus.PENDING);

        long critical = suggestions.stream()
                .filter(s -> s.getUrgency() ==
                        ReorderSuggestion.Urgency.CRITICAL).count();
        long high = suggestions.stream()
                .filter(s -> s.getUrgency() ==
                        ReorderSuggestion.Urgency.HIGH).count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalPending",     suggestions.size());
        result.put("criticalCount",    critical);
        result.put("highRiskCount",    high);
        result.put("topUrgent",        suggestions.stream().limit(3)
                .map(s -> {
                    Product p = productRepository
                            .findById(s.getProductId()).orElse(null);
                    return p != null ? p.getName() : "Unknown";
                })
                .collect(Collectors.toList()));
        return result;
    }
}
