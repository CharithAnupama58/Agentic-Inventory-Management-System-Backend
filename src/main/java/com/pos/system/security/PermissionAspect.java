package com.pos.system.security;

import com.pos.system.exception.PosException;
import com.pos.system.model.User;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint,
                                 RequiresPermission requiresPermission) {
        try {
            Authentication auth = SecurityContextHolder
                    .getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated()
                    || auth.getPrincipal().equals("anonymousUser")) {
                throw new PosException.UnauthorizedException(
                        "Authentication required");
            }

            User user = (User) auth.getPrincipal();

            if (!RolePermissions.hasPermission(
                    user.getRole(), requiresPermission.value())) {

                log.warn("Permission denied — user: {}, role: {}, required: {}",
                        user.getId(), user.getRole(),
                        requiresPermission.value());

                throw new PosException.ForbiddenException(
                        "Your role (" + user.getRole().name()
                        + ") cannot perform: " + requiresPermission.value());
            }

        } catch (PosException e) {
            throw e;
        } catch (Exception e) {
            log.error("Permission check failed: {}", e.getMessage());
            throw new PosException.UnauthorizedException(
                    "Authentication required");
        }
    }
}
