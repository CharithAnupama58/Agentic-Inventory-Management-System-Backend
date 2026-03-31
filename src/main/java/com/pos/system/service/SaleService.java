package com.pos.system.service;

import com.pos.system.dto.SaleDto;
import com.pos.system.model.*;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository       saleRepository;
    private final SaleItemRepository   saleItemRepository;
    private final ProductRepository    productRepository;
    private final CampaignRepository   campaignRepository;

    @Lazy private final InventoryService  inventoryService;
    @Lazy private final DashboardService  dashboardService;

    // ── Create Sale ───────────────────────────────────────────────────────────
    @Transactional
    public SaleDto.SaleResponse createSale(SaleDto.SaleRequest req) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId   = getAuthUserId();

        // ── Load all active campaigns for this tenant ─────────────────────────
        List<Campaign> activeCampaigns = campaignRepository
                .findByTenantIdAndStatus(tenantId,
                        Campaign.CampaignStatus.ACTIVE)
                .stream()
                .filter(c -> {
                    LocalDate today = LocalDate.now();
                    boolean started = c.getStartDate() == null
                            || !today.isBefore(c.getStartDate());
                    boolean notEnded = c.getEndDate() == null
                            || !today.isAfter(c.getEndDate());
                    return started && notEnded;
                })
                .collect(Collectors.toList());

        log.debug("Active campaigns for tenant: {}", activeCampaigns.size());

        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleDto.SaleItemRequest itemReq : req.getItems()) {
            Product product = productRepository
                    .findByIdAndTenantId(itemReq.getProductId(), tenantId)
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found: " + itemReq.getProductId()));

            if (product.getStock() < itemReq.getQuantity())
                throw new RuntimeException(
                        "Insufficient stock for: " + product.getName()
                        + ". Available: " + product.getStock());

            // ── Check if product has active campaign discount ─────────────────
            BigDecimal unitPrice = product.getPrice();
            UUID       appliedCampaignId = null;
            BigDecimal campaignDiscount  = BigDecimal.ZERO;

            for (Campaign campaign : activeCampaigns) {
                if (campaign.getProductIds() != null
                        && campaign.getProductIds()
                                   .contains(product.getId())) {

                    // Calculate discounted price
                    if (campaign.getDiscountType()
                            == Campaign.DiscountType.PERCENTAGE) {
                        campaignDiscount = product.getPrice()
                                .multiply(campaign.getDiscountValue())
                                .divide(BigDecimal.valueOf(100),
                                        2, RoundingMode.HALF_UP);
                    } else {
                        campaignDiscount = campaign.getDiscountValue()
                                .min(product.getPrice());
                    }

                    unitPrice = product.getPrice()
                            .subtract(campaignDiscount)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                    appliedCampaignId = campaign.getId();

                    log.info("Campaign '{}' applied to '{}': ${} → ${}",
                            campaign.getName(), product.getName(),
                            product.getPrice(), unitPrice);
                    break;
                }
            }

            int stockBefore = product.getStock();
            product.setStock(stockBefore - itemReq.getQuantity());
            productRepository.save(product);

            inventoryService.logSaleMovement(tenantId, product.getId(),
                    itemReq.getQuantity(), stockBefore,
                    product.getStock(), null);

            BigDecimal itemSubtotal = unitPrice
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            SaleItem saleItem = SaleItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity())
                    .price(unitPrice)            // discounted price
                    .originalPrice(product.getPrice())  // original price
                    .campaignId(appliedCampaignId)
                    .subtotal(itemSubtotal)
                    .build();

            saleItems.add(saleItem);
            subtotal = subtotal.add(itemSubtotal);
        }

        // ── Manual discount on top of campaign discounts ──────────────────────
        BigDecimal discountAmount = calculateDiscount(
                subtotal, req.getDiscountType(), req.getDiscountValue());
        BigDecimal totalAmount = subtotal.subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);

        validatePayment(req, totalAmount);

        BigDecimal changeAmount = BigDecimal.ZERO;
        if ("CASH".equals(req.getPaymentMethod())
                && req.getCashTendered() != null)
            changeAmount = req.getCashTendered()
                    .subtract(totalAmount)
                    .setScale(2, RoundingMode.HALF_UP);

        Sale sale = Sale.builder()
                .tenantId(tenantId).userId(userId)
                .subtotal(subtotal.setScale(2, RoundingMode.HALF_UP))
                .discountType(req.getDiscountType())
                .discountValue(req.getDiscountValue())
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .totalRefundedAmount(BigDecimal.ZERO)
                .paymentMethod(req.getPaymentMethod())
                .cashAmount(req.getCashAmount())
                .cardAmount(req.getCardAmount())
                .cashTendered(req.getCashTendered())
                .changeAmount(changeAmount)
                .status(Sale.SaleStatus.COMPLETED)
                .notes(req.getNotes())
                .build();

        saleItems.forEach(item -> item.setSale(sale));
        sale.setItems(saleItems);

        SaleDto.SaleResponse response =
                toResponse(saleRepository.save(sale));
        dashboardService.evictDashboardCache();
        return response;
    }

    // ── Refund ────────────────────────────────────────────────────────────────
    @Transactional
    public SaleDto.RefundResponse refundSale(SaleDto.RefundRequest req) {
        UUID tenantId = TenantContext.getTenantId();

        Sale sale = saleRepository.findByIdAndTenantId(
                req.getSaleId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (sale.getStatus() == Sale.SaleStatus.REFUNDED)
            throw new RuntimeException("Sale already fully refunded");

        BigDecimal     refundAmount  = BigDecimal.ZERO;
        List<String>   refundedItems = new ArrayList<>();

        for (SaleDto.RefundItem refundItem : req.getItems()) {
            SaleItem saleItem = sale.getItems().stream()
                    .filter(i -> i.getProductId()
                            .equals(refundItem.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "Product not in sale"));

            int available = saleItem.getQuantity()
                    - saleItem.getRefundedQuantity();
            if (refundItem.getQuantity() > available)
                throw new RuntimeException(
                        "Cannot refund more than purchased. Available: "
                        + available);

            Product product = productRepository
                    .findByIdAndTenantId(
                            refundItem.getProductId(), tenantId)
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found"));

            int stockBefore = product.getStock();
            product.setStock(stockBefore + refundItem.getQuantity());
            productRepository.save(product);

            inventoryService.logRefundMovement(tenantId, product.getId(),
                    refundItem.getQuantity(), stockBefore,
                    product.getStock(), sale.getId());

            saleItem.setRefundedQuantity(
                    saleItem.getRefundedQuantity()
                    + refundItem.getQuantity());

            BigDecimal itemRefund = saleItem.getPrice()
                    .multiply(BigDecimal.valueOf(refundItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            refundAmount = refundAmount.add(itemRefund);
            refundedItems.add(product.getName()
                    + " x" + refundItem.getQuantity()
                    + " = $" + itemRefund);
        }

        sale.setTotalRefundedAmount(
                sale.getTotalRefundedAmount().add(refundAmount)
                        .setScale(2, RoundingMode.HALF_UP));

        boolean fullyRefunded = sale.getItems().stream()
                .allMatch(i -> i.getRefundedQuantity()
                        .equals(i.getQuantity()));
        sale.setStatus(fullyRefunded
                ? Sale.SaleStatus.REFUNDED
                : Sale.SaleStatus.PARTIALLY_REFUNDED);

        saleRepository.save(sale);
        dashboardService.evictDashboardCache();

        return SaleDto.RefundResponse.builder()
                .saleId(sale.getId())
                .refundAmount(refundAmount)
                .status(sale.getStatus().name())
                .refundedItems(refundedItems)
                .build();
    }

    // ── Get all sales ─────────────────────────────────────────────────────────
    public List<SaleDto.SaleResponse> getAllSales() {
        return saleRepository
                .findAllByTenantIdOrderByCreatedAtDesc(
                        TenantContext.getTenantId())
                .stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SaleDto.SaleResponse getSale(UUID id) {
        return toResponse(saleRepository
                .findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new RuntimeException("Sale not found")));
    }

    // ── Get active campaign for a product ─────────────────────────────────────
    public SaleDto.ProductCampaignInfo getProductCampaignInfo(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        return campaignRepository
                .findByTenantIdAndStatus(tenantId,
                        Campaign.CampaignStatus.ACTIVE)
                .stream()
                .filter(c -> {
                    boolean started = c.getStartDate() == null
                            || !today.isBefore(c.getStartDate());
                    boolean notEnded = c.getEndDate() == null
                            || !today.isAfter(c.getEndDate());
                    return started && notEnded
                            && c.getProductIds() != null
                            && c.getProductIds().contains(productId);
                })
                .findFirst()
                .map(c -> {
                    Product product = productRepository
                            .findById(productId).orElse(null);
                    if (product == null) return null;

                    BigDecimal discountAmt;
                    if (c.getDiscountType()
                            == Campaign.DiscountType.PERCENTAGE) {
                        discountAmt = product.getPrice()
                                .multiply(c.getDiscountValue())
                                .divide(BigDecimal.valueOf(100),
                                        2, RoundingMode.HALF_UP);
                    } else {
                        discountAmt = c.getDiscountValue();
                    }

                    BigDecimal discountedPrice = product.getPrice()
                            .subtract(discountAmt)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);

                    return SaleDto.ProductCampaignInfo.builder()
                            .campaignId(c.getId().toString())
                            .campaignName(c.getName())
                            .discountType(c.getDiscountType().name())
                            .discountValue(c.getDiscountValue())
                            .discountAmount(discountAmt)
                            .originalPrice(product.getPrice())
                            .discountedPrice(discountedPrice)
                            .endDate(c.getEndDate() != null
                                    ? c.getEndDate().toString() : null)
                            .build();
                })
                .orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private BigDecimal calculateDiscount(BigDecimal subtotal,
                                          String type, BigDecimal value) {
        if (type == null || value == null
                || value.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        return switch (type.toUpperCase()) {
            case "PERCENTAGE" -> subtotal
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100),
                            2, RoundingMode.HALF_UP);
            case "FIXED"      -> value.setScale(2, RoundingMode.HALF_UP);
            default -> throw new RuntimeException(
                    "Invalid discount type: " + type);
        };
    }

    private void validatePayment(SaleDto.SaleRequest req,
                                  BigDecimal total) {
        switch (req.getPaymentMethod().toUpperCase()) {
            case "CASH" -> {
                if (req.getCashTendered() == null)
                    throw new RuntimeException("Cash tendered required");
                if (req.getCashTendered().compareTo(total) < 0)
                    throw new RuntimeException(
                            "Insufficient cash. Need: $" + total);
            }
            case "CARD"  -> {}
            case "SPLIT" -> {
                if (req.getCashAmount() == null
                        || req.getCardAmount() == null)
                    throw new RuntimeException(
                            "Both amounts required for split");
                if (req.getCashAmount().add(req.getCardAmount())
                        .compareTo(total) != 0)
                    throw new RuntimeException(
                            "Split amounts must equal total");
            }
            default -> throw new RuntimeException(
                    "Invalid payment method");
        }
    }

    private UUID getAuthUserId() {
        User user = (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return user.getId();
    }

    private SaleDto.SaleResponse toResponse(Sale sale) {
        List<SaleDto.SaleItemResponse> items = sale.getItems().stream()
                .map(item -> {
                    String name = productRepository
                            .findById(item.getProductId())
                            .map(Product::getName)
                            .orElse("Deleted Product");
                    return SaleDto.SaleItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(name)
                            .quantity(item.getQuantity())
                            .refundedQuantity(item.getRefundedQuantity())
                            .price(item.getPrice())
                            .originalPrice(item.getOriginalPrice())
                            .subtotal(item.getSubtotal())
                            .build();
                }).collect(Collectors.toList());

        return SaleDto.SaleResponse.builder()
                .id(sale.getId())
                .subtotal(sale.getSubtotal())
                .discountType(sale.getDiscountType())
                .discountValue(sale.getDiscountValue())
                .discountAmount(sale.getDiscountAmount())
                .totalAmount(sale.getTotalAmount())
                .totalRefundedAmount(sale.getTotalRefundedAmount())
                .paymentMethod(sale.getPaymentMethod())
                .cashAmount(sale.getCashAmount())
                .cardAmount(sale.getCardAmount())
                .cashTendered(sale.getCashTendered())
                .changeAmount(sale.getChangeAmount())
                .status(sale.getStatus() != null
                        ? sale.getStatus().name() : null)
                .notes(sale.getNotes())
                .createdAt(sale.getCreatedAt())
                .items(items)
                .build();
    }
}
