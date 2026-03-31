package com.pos.system.security;

import com.pos.system.model.User;
import java.util.Map;
import java.util.Set;

public class RolePermissions {

    // ── Permission constants ──────────────────────────────────────────────────
    public static final String PERM_PRODUCT_VIEW    = "PRODUCT_VIEW";
    public static final String PERM_PRODUCT_CREATE  = "PRODUCT_CREATE";
    public static final String PERM_PRODUCT_EDIT    = "PRODUCT_EDIT";
    public static final String PERM_PRODUCT_DELETE  = "PRODUCT_DELETE";

    public static final String PERM_SALE_CREATE     = "SALE_CREATE";
    public static final String PERM_SALE_VIEW       = "SALE_VIEW";
    public static final String PERM_SALE_REFUND     = "SALE_REFUND";

    public static final String PERM_INVENTORY_VIEW  = "INVENTORY_VIEW";
    public static final String PERM_INVENTORY_EDIT  = "INVENTORY_EDIT";
    public static final String PERM_BATCH_ADD       = "BATCH_ADD";

    public static final String PERM_REPORT_VIEW     = "REPORT_VIEW";
    public static final String PERM_ANALYTICS_VIEW  = "ANALYTICS_VIEW";
    public static final String PERM_DASHBOARD_VIEW  = "DASHBOARD_VIEW";

    public static final String PERM_USER_MANAGE     = "USER_MANAGE";
    public static final String PERM_SETTINGS_EDIT   = "SETTINGS_EDIT";

    // ── Role → Permissions mapping ────────────────────────────────────────────
    private static final Map<User.Role, Set<String>> ROLE_PERMISSIONS = Map.of(

        User.Role.ADMIN, Set.of(
            PERM_PRODUCT_VIEW,   PERM_PRODUCT_CREATE,
            PERM_PRODUCT_EDIT,   PERM_PRODUCT_DELETE,
            PERM_SALE_CREATE,    PERM_SALE_VIEW,    PERM_SALE_REFUND,
            PERM_INVENTORY_VIEW, PERM_INVENTORY_EDIT, PERM_BATCH_ADD,
            PERM_REPORT_VIEW,    PERM_ANALYTICS_VIEW, PERM_DASHBOARD_VIEW,
            PERM_USER_MANAGE,    PERM_SETTINGS_EDIT
        ),

        User.Role.MANAGER, Set.of(
            PERM_PRODUCT_VIEW,   PERM_PRODUCT_CREATE, PERM_PRODUCT_EDIT,
            PERM_SALE_CREATE,    PERM_SALE_VIEW,    PERM_SALE_REFUND,
            PERM_INVENTORY_VIEW, PERM_INVENTORY_EDIT, PERM_BATCH_ADD,
            PERM_REPORT_VIEW,    PERM_ANALYTICS_VIEW, PERM_DASHBOARD_VIEW
            // No: PRODUCT_DELETE, USER_MANAGE, SETTINGS_EDIT
        ),

        User.Role.CASHIER, Set.of(
            PERM_PRODUCT_VIEW,
            PERM_SALE_CREATE,    PERM_SALE_VIEW,
            PERM_INVENTORY_VIEW,
            PERM_DASHBOARD_VIEW
            // No: edit/delete products, refund, analytics, reports, inventory edit
        )
    );

    public static Set<String> getPermissions(User.Role role) {
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of());
    }

    public static boolean hasPermission(User.Role role, String permission) {
        return getPermissions(role).contains(permission);
    }
}
