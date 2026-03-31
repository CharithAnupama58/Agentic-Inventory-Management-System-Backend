package com.pos.system.service;

import com.pos.system.dto.InventoryDto;
import com.pos.system.model.*;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository           productRepository;
    private final InventoryLogRepository      logRepository;
    private final BatchRepository             batchRepository;
    private final ReorderSuggestionRepository reorderRepository;

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int ANALYSIS_DAYS       = 30;
    private static final int REORDER_LEAD_DAYS   = 7;
    private static final int EXPIRY_WARNING_DAYS = 30;

    // ── Full Intelligence Summary ─────────────────────────────────────────────
    public InventoryDto.IntelligenceSummary getSummary() {
        UUID tenantId = TenantContext.getTenantId();
        generateReorderSuggestions(); // always refresh before returning

        return InventoryDto.IntelligenceSummary.builder()
                .reorderSuggestions(getPendingReorderSuggestions())
                .expiringBatches(getExpiringBatches())
                .recentMovements(getRecentMovements(20))
                .stats(buildStockStats(tenantId))
                .build();
    }

    // ── Manual Stock Adjustment ───────────────────────────────────────────────
    @Transactional
    public InventoryDto.LogResponse adjustStock(InventoryDto.AdjustmentRequest req) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository
                .findByIdAndTenantId(req.getProductId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        int stockBefore = product.getStock();
        int stockAfter  = stockBefore + req.getQuantity();

        if (stockAfter < 0)
            throw new RuntimeException(
                    "Adjustment would result in negative stock. Current: "
                    + stockBefore + ", Change: " + req.getQuantity());

        product.setStock(stockAfter);
        productRepository.save(product);

        // Save movement log
        InventoryLog log = logRepository.save(InventoryLog.builder()
                .tenantId(tenantId)
                .productId(product.getId())
                .movementType(InventoryLog.MovementType.valueOf(req.getMovementType()))
                .quantity(req.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .notes(req.getNotes())
                .build());

        // ── Immediately remove any pending reorder suggestion for this product
        // if stock is now above the reorder point ─────────────────────────────
        removeReorderIfResolved(product, tenantId);

        return toLogResponse(log, product.getName());
    }

    // ── Add Batch ─────────────────────────────────────────────────────────────
    @Transactional
    public InventoryDto.BatchResponse addBatch(InventoryDto.BatchRequest req) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository
                .findByIdAndTenantId(req.getProductId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Batch batch = batchRepository.save(Batch.builder()
                .tenantId(tenantId)
                .productId(product.getId())
                .batchNumber(req.getBatchNumber())
                .quantity(req.getQuantity())
                .remainingQuantity(req.getQuantity())
                .costPrice(req.getCostPrice())
                .expiryDate(req.getExpiryDate())
                .purchaseDate(req.getPurchaseDate() != null
                        ? req.getPurchaseDate() : LocalDate.now())
                .build());

        int stockBefore = product.getStock();
        product.setStock(stockBefore + req.getQuantity());
        productRepository.save(product);

        logRepository.save(InventoryLog.builder()
                .tenantId(tenantId)
                .productId(product.getId())
                .movementType(InventoryLog.MovementType.RESTOCK)
                .quantity(req.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(product.getStock())
                .referenceId(batch.getId())
                .notes("Batch received: " + req.getBatchNumber())
                .build());

        // ── Remove alert if stock is now resolved ─────────────────────────────
        removeReorderIfResolved(product, tenantId);

        return toBatchResponse(batch, product.getName());
    }

    // ── Remove reorder suggestion if stock is now sufficient ─────────────────
    // Called after every stock addition (adjust or batch)
    private void removeReorderIfResolved(Product product, UUID tenantId) {
        // Calculate the reorder point for this product
        LocalDateTime since = LocalDateTime.now().minusDays(ANALYSIS_DAYS);
        BigDecimal avgDaily = getAvgDailySales(product.getId(), tenantId, since);

        int reorderPoint = avgDaily.compareTo(BigDecimal.ZERO) > 0
                ? (int) (avgDaily.doubleValue() * REORDER_LEAD_DAYS * 1.5)
                : LOW_STOCK_THRESHOLD;

        // If stock is NOW above the reorder point → delete the pending alert
        if (product.getStock() > reorderPoint && product.getStock() > 0) {
            reorderRepository.deleteByProductIdAndTenantIdAndStatus(
                    product.getId(), tenantId,
                    ReorderSuggestion.SuggestionStatus.PENDING);
        } else {
            // Stock still low — refresh the suggestion with updated numbers
            refreshSuggestionForProduct(product, tenantId, avgDaily, reorderPoint);
        }
    }

    // ── Refresh a single product's suggestion ─────────────────────────────────
    private void refreshSuggestionForProduct(Product product, UUID tenantId,
                                              BigDecimal avgDaily, int reorderPoint) {
        // Remove old pending suggestion
        reorderRepository.deleteByProductIdAndTenantIdAndStatus(
                product.getId(), tenantId,
                ReorderSuggestion.SuggestionStatus.PENDING);

        int daysLeft = avgDaily.compareTo(BigDecimal.ZERO) > 0
                ? (int) (product.getStock() / avgDaily.doubleValue())
                : 999;

        int suggestedQty = avgDaily.compareTo(BigDecimal.ZERO) > 0
                ? (int) (avgDaily.doubleValue() * 30)
                : Math.max(50, reorderPoint * 3);
        suggestedQty = Math.max(suggestedQty, 10);

        ReorderSuggestion.Urgency urgency;
        if (product.getStock() == 0)  urgency = ReorderSuggestion.Urgency.CRITICAL;
        else if (daysLeft <= 3)       urgency = ReorderSuggestion.Urgency.CRITICAL;
        else if (daysLeft <= 7)       urgency = ReorderSuggestion.Urgency.HIGH;
        else if (daysLeft <= 14)      urgency = ReorderSuggestion.Urgency.MEDIUM;
        else                          urgency = ReorderSuggestion.Urgency.LOW;

        reorderRepository.save(ReorderSuggestion.builder()
                .tenantId(tenantId)
                .productId(product.getId())
                .currentStock(product.getStock())
                .reorderPoint(reorderPoint)
                .suggestedQuantity(suggestedQty)
                .avgDailySales(avgDaily)
                .daysOfStockLeft(daysLeft == 999 ? null : daysLeft)
                .urgency(urgency)
                .build());
    }

    // ── Get avg daily sales for one product ───────────────────────────────────
    private BigDecimal getAvgDailySales(UUID productId, UUID tenantId,
                                         LocalDateTime since) {
        List<Object[]> rows = logRepository.findSalesSince(tenantId, since);
        for (Object[] row : rows) {
            if (row[0].equals(productId)) {
                long totalSold = ((Number) row[1]).longValue();
                return BigDecimal.valueOf(Math.abs(totalSold))
                        .divide(BigDecimal.valueOf(ANALYSIS_DAYS), 2,
                                RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    // ── Get Batches for a Product ─────────────────────────────────────────────
    public List<InventoryDto.BatchResponse> getBatchesForProduct(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return batchRepository
                .findByProductIdAndTenantIdOrderByExpiryDateAsc(productId, tenantId)
                .stream()
                .map(b -> toBatchResponse(b, product.getName()))
                .collect(Collectors.toList());
    }

    // ── Expiring Batches ──────────────────────────────────────────────────────
    public List<InventoryDto.BatchResponse> getExpiringBatches() {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository
                .findExpiringBatches(tenantId,
                        LocalDate.now().plusDays(EXPIRY_WARNING_DAYS))
                .stream()
                .map(b -> {
                    String name = productRepository.findById(b.getProductId())
                            .map(Product::getName).orElse("Unknown");
                    return toBatchResponse(b, name);
                })
                .collect(Collectors.toList());
    }

    // ── Movement History ──────────────────────────────────────────────────────
    public List<InventoryDto.LogResponse> getMovementHistory(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return logRepository
                .findByProductIdAndTenantIdOrderByCreatedAtDesc(productId, tenantId)
                .stream()
                .map(l -> toLogResponse(l, product.getName()))
                .collect(Collectors.toList());
    }

    public List<InventoryDto.LogResponse> getRecentMovements(int limit) {
        UUID tenantId = TenantContext.getTenantId();
        return logRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().limit(limit)
                .map(l -> {
                    String name = productRepository.findById(l.getProductId())
                            .map(Product::getName).orElse("Unknown");
                    return toLogResponse(l, name);
                })
                .collect(Collectors.toList());
    }

    // ── Generate All Reorder Suggestions (full refresh) ───────────────────────
    @Transactional
    public List<InventoryDto.ReorderResponse> generateReorderSuggestions() {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(ANALYSIS_DAYS);

        // Build avg daily sales map from logs
        Map<UUID, BigDecimal> avgDailySalesMap = new HashMap<>();
        for (Object[] row : logRepository.findSalesSince(tenantId, since)) {
            UUID productId = (UUID) row[0];
            long totalSold = ((Number) row[1]).longValue();
            avgDailySalesMap.put(productId,
                    BigDecimal.valueOf(Math.abs(totalSold))
                            .divide(BigDecimal.valueOf(ANALYSIS_DAYS), 2,
                                    RoundingMode.HALF_UP));
        }

        List<Product> products = productRepository.findAllByTenantId(tenantId);
        List<InventoryDto.ReorderResponse> suggestions = new ArrayList<>();

        for (Product product : products) {
            BigDecimal avgDaily = avgDailySalesMap.getOrDefault(
                    product.getId(), BigDecimal.ZERO);

            int reorderPoint = avgDaily.compareTo(BigDecimal.ZERO) > 0
                    ? (int) (avgDaily.doubleValue() * REORDER_LEAD_DAYS * 1.5)
                    : LOW_STOCK_THRESHOLD;

            int daysLeft = avgDaily.compareTo(BigDecimal.ZERO) > 0
                    ? (int) (product.getStock() / avgDaily.doubleValue())
                    : 999;

            // Only create suggestion if stock is at or below reorder point
            boolean needsReorder = product.getStock() <= reorderPoint
                    || product.getStock() == 0;

            // If stock is fine now → remove any existing pending alert
            if (!needsReorder) {
                reorderRepository.deleteByProductIdAndTenantIdAndStatus(
                        product.getId(), tenantId,
                        ReorderSuggestion.SuggestionStatus.PENDING);
                continue;
            }

            int suggestedQty = avgDaily.compareTo(BigDecimal.ZERO) > 0
                    ? (int) (avgDaily.doubleValue() * 30)
                    : Math.max(50, reorderPoint * 3);
            suggestedQty = Math.max(suggestedQty, 10);

            ReorderSuggestion.Urgency urgency;
            if (product.getStock() == 0)  urgency = ReorderSuggestion.Urgency.CRITICAL;
            else if (daysLeft <= 3)       urgency = ReorderSuggestion.Urgency.CRITICAL;
            else if (daysLeft <= 7)       urgency = ReorderSuggestion.Urgency.HIGH;
            else if (daysLeft <= 14)      urgency = ReorderSuggestion.Urgency.MEDIUM;
            else                          urgency = ReorderSuggestion.Urgency.LOW;

            // Replace old pending suggestion
            reorderRepository.deleteByProductIdAndTenantIdAndStatus(
                    product.getId(), tenantId,
                    ReorderSuggestion.SuggestionStatus.PENDING);

            ReorderSuggestion saved = reorderRepository.save(
                    ReorderSuggestion.builder()
                            .tenantId(tenantId)
                            .productId(product.getId())
                            .currentStock(product.getStock())
                            .reorderPoint(reorderPoint)
                            .suggestedQuantity(suggestedQty)
                            .avgDailySales(avgDaily)
                            .daysOfStockLeft(daysLeft == 999 ? null : daysLeft)
                            .urgency(urgency)
                            .build());

            suggestions.add(toReorderResponse(saved, product.getName()));
        }

        suggestions.sort(Comparator.comparing(s -> urgencyOrder(s.getUrgency())));
        return suggestions;
    }

    // ── Acknowledge suggestion ────────────────────────────────────────────────
    @Transactional
    public InventoryDto.ReorderResponse acknowledgeSuggestion(
            UUID suggestionId, String action) {
        UUID tenantId = TenantContext.getTenantId();

        ReorderSuggestion s = reorderRepository.findById(suggestionId)
                .orElseThrow(() -> new RuntimeException("Suggestion not found"));

        if (!s.getTenantId().equals(tenantId))
            throw new RuntimeException("Access denied");

        s.setStatus(ReorderSuggestion.SuggestionStatus.valueOf(action));
        s.setAcknowledgedAt(LocalDateTime.now());
        reorderRepository.save(s);

        String name = productRepository.findById(s.getProductId())
                .map(Product::getName).orElse("Unknown");
        return toReorderResponse(s, name);
    }

    // ── Log sale/refund movements (called from SaleService) ───────────────────
    @Transactional
    public void logSaleMovement(UUID tenantId, UUID productId,
                                 int qty, int before, int after, UUID saleId) {
        logRepository.save(InventoryLog.builder()
                .tenantId(tenantId).productId(productId)
                .movementType(InventoryLog.MovementType.SALE)
                .quantity(-qty).stockBefore(before).stockAfter(after)
                .referenceId(saleId)
                .notes("Sold in sale " + (saleId != null
                        ? saleId.toString().substring(0, 8) : ""))
                .build());
    }

    @Transactional
    public void logRefundMovement(UUID tenantId, UUID productId,
                                   int qty, int before, int after, UUID saleId) {
        logRepository.save(InventoryLog.builder()
                .tenantId(tenantId).productId(productId)
                .movementType(InventoryLog.MovementType.REFUND)
                .quantity(qty).stockBefore(before).stockAfter(after)
                .referenceId(saleId)
                .notes("Refunded from sale " + (saleId != null
                        ? saleId.toString().substring(0, 8) : ""))
                .build());
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private InventoryDto.StockStats buildStockStats(UUID tenantId) {
        List<Product> products = productRepository.findAllByTenantId(tenantId);

        BigDecimal totalValue = products.stream()
                .map(p -> p.getCostPrice()
                        .multiply(BigDecimal.valueOf(p.getStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long criticalCount = reorderRepository
                .findByTenantIdAndStatusOrderByUrgencyAsc(
                        tenantId, ReorderSuggestion.SuggestionStatus.PENDING)
                .stream()
                .filter(s -> s.getUrgency() == ReorderSuggestion.Urgency.CRITICAL)
                .count();

        long expiringCount = batchRepository
                .findExpiringBatches(tenantId,
                        LocalDate.now().plusDays(EXPIRY_WARNING_DAYS)).size();

        return InventoryDto.StockStats.builder()
                .totalProducts((long) products.size())
                .outOfStock(products.stream().filter(p -> p.getStock() == 0).count())
                .lowStock(products.stream()
                        .filter(p -> p.getStock() > 0
                                && p.getStock() <= LOW_STOCK_THRESHOLD)
                        .count())
                .criticalReorders(criticalCount)
                .expiringIn30Days(expiringCount)
                .totalStockValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int urgencyOrder(String urgency) {
        return switch (urgency) {
            case "CRITICAL" -> 0;
            case "HIGH"     -> 1;
            case "MEDIUM"   -> 2;
            default         -> 3;
        };
    }

    private List<InventoryDto.ReorderResponse> getPendingReorderSuggestions() {
        UUID tenantId = TenantContext.getTenantId();
        return reorderRepository
                .findByTenantIdAndStatusOrderByUrgencyAsc(
                        tenantId, ReorderSuggestion.SuggestionStatus.PENDING)
                .stream()
                .map(s -> {
                    String name = productRepository.findById(s.getProductId())
                            .map(Product::getName).orElse("Unknown");
                    return toReorderResponse(s, name);
                })
                .collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────
    private InventoryDto.LogResponse toLogResponse(InventoryLog log,
                                                    String productName) {
        return InventoryDto.LogResponse.builder()
                .id(log.getId()).productId(log.getProductId())
                .productName(productName)
                .movementType(log.getMovementType().name())
                .quantity(log.getQuantity())
                .stockBefore(log.getStockBefore())
                .stockAfter(log.getStockAfter())
                .notes(log.getNotes())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private InventoryDto.BatchResponse toBatchResponse(Batch batch,
                                                        String productName) {
        long daysUntilExpiry = batch.getExpiryDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpiryDate())
                : 9999;
        return InventoryDto.BatchResponse.builder()
                .id(batch.getId()).productId(batch.getProductId())
                .productName(productName)
                .batchNumber(batch.getBatchNumber())
                .quantity(batch.getQuantity())
                .remainingQuantity(batch.getRemainingQuantity())
                .costPrice(batch.getCostPrice())
                .expiryDate(batch.getExpiryDate())
                .purchaseDate(batch.getPurchaseDate())
                .status(batch.getStatus().name())
                .daysUntilExpiry(daysUntilExpiry)
                .expiringSoon(daysUntilExpiry <= EXPIRY_WARNING_DAYS)
                .build();
    }

    private InventoryDto.ReorderResponse toReorderResponse(
            ReorderSuggestion s, String productName) {
        return InventoryDto.ReorderResponse.builder()
                .id(s.getId()).productId(s.getProductId())
                .productName(productName)
                .currentStock(s.getCurrentStock())
                .reorderPoint(s.getReorderPoint())
                .suggestedQuantity(s.getSuggestedQuantity())
                .avgDailySales(s.getAvgDailySales())
                .daysOfStockLeft(s.getDaysOfStockLeft())
                .urgency(s.getUrgency().name())
                .status(s.getStatus().name())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
