package com.pos.system.security;

import com.pos.system.model.User;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * AOP guard that runs before every service method.
 * If a request object has a tenantId field, it is OVERWRITTEN
 * with the value from TenantContext — the JWT-verified tenant.
 * This prevents tenantId spoofing via request body manipulation.
 */
@Aspect
@Component
public class TenantInjectionGuard {

    @Before("execution(* com.pos.system.service.*.*(..))")
    public void enforceTenantId(JoinPoint joinPoint) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;

        // For each argument passed to the service method
        for (Object arg : joinPoint.getArgs()) {
            if (arg == null) continue;
            overwriteTenantIdField(arg, tenantId);
        }
    }

    private void overwriteTenantIdField(Object obj, UUID tenantId) {
        try {
            Field field = obj.getClass().getDeclaredField("tenantId");
            field.setAccessible(true);
            field.set(obj, tenantId); // force overwrite with JWT-verified value
        } catch (NoSuchFieldException ignored) {
            // DTO does not have tenantId field — safe to ignore
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to enforce tenant isolation", e);
        }
    }
}
