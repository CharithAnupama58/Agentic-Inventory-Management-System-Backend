package com.pos.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j(topic = "com.pos.system.audit")
@Service
public class AuditLogService {

    // ── Sale events ───────────────────────────────────────────────────────────
    public void saleCeated(UUID tenantId, UUID userId,
                            UUID saleId, double amount) {
        log.info("SALE_CREATED | tenant={} | user={} | sale={} | amount={}",
                tenantId, userId, saleId, amount);
    }

    public void saleRefunded(UUID tenantId, UUID userId,
                              UUID saleId, double refundAmount) {
        log.info("SALE_REFUNDED | tenant={} | user={} | sale={} | refund={}",
                tenantId, userId, saleId, refundAmount);
    }

    // ── Product events ────────────────────────────────────────────────────────
    public void productCreated(UUID tenantId, UUID userId,
                                UUID productId, String name) {
        log.info("PRODUCT_CREATED | tenant={} | user={} | product={} | name={}",
                tenantId, userId, productId, name);
    }

    public void productUpdated(UUID tenantId, UUID userId,
                                UUID productId, String name) {
        log.info("PRODUCT_UPDATED | tenant={} | user={} | product={} | name={}",
                tenantId, userId, productId, name);
    }

    public void productDeleted(UUID tenantId, UUID userId,
                                UUID productId, String name) {
        log.info("PRODUCT_DELETED | tenant={} | user={} | product={} | name={}",
                tenantId, userId, productId, name);
    }

    // ── Inventory events ──────────────────────────────────────────────────────
    public void stockAdjusted(UUID tenantId, UUID userId,
                               UUID productId, int qty, String type) {
        log.info("STOCK_ADJUSTED | tenant={} | user={} | product={} | qty={} | type={}",
                tenantId, userId, productId, qty, type);
    }

    public void batchAdded(UUID tenantId, UUID userId,
                            UUID productId, String batchNumber, int qty) {
        log.info("BATCH_ADDED | tenant={} | user={} | product={} | batch={} | qty={}",
                tenantId, userId, productId, batchNumber, qty);
    }

    // ── Auth events ───────────────────────────────────────────────────────────
    public void userLoggedIn(UUID tenantId, UUID userId, String email) {
        log.info("USER_LOGIN | tenant={} | user={} | email={}",
                tenantId, userId, email);
    }

    public void userRegistered(UUID tenantId, UUID userId, String email) {
        log.info("USER_REGISTERED | tenant={} | user={} | email={}",
                tenantId, userId, email);
    }

    // ── Security events ───────────────────────────────────────────────────────
    public void loginFailed(String email, String ipAddress) {
        log.warn("LOGIN_FAILED | email={} | ip={}", email, ipAddress);
    }

    public void unauthorizedAccess(String path, String ipAddress) {
        log.warn("UNAUTHORIZED_ACCESS | path={} | ip={}", path, ipAddress);
    }
}
