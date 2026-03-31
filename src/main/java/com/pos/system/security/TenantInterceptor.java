package com.pos.system.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Runs AFTER JwtAuthenticationFilter has already set TenantContext.
 * Rejects requests where tenant_id is missing or invalid.
 * Public routes (/api/auth/**) are excluded via WebMvcConfig.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\": \"Tenant context missing. Please authenticate first.\"}"
            );
            return false; // block the request
        }

        return true; // allow through
    }
}
