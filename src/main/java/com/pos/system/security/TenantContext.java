package com.pos.system.security;

import java.util.UUID;

/**
 * Holds the current tenant's UUID for the duration of a request.
 * Stored in a ThreadLocal so it is isolated per request thread.
 * Must be cleared after every request to prevent leaking into thread-pool reuse.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getTenantId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}