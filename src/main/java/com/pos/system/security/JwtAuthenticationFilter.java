package com.pos.system.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService         jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // ── No token — continue without authentication ────────────────────────
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        boolean tenantSet = false;

        try {
            final String userEmail = jwtService.extractEmail(jwt);

            if (userEmail != null
                    && SecurityContextHolder.getContext()
                                            .getAuthentication() == null) {

                var userDetails = userDetailsService
                        .loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {

                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null,
                            userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);

                    UUID tenantId = jwtService.extractTenantId(jwt);
                    TenantContext.setTenantId(tenantId);
                    tenantSet = true;

                    log.debug("Authenticated — user: {}, tenant: {}",
                            userEmail, tenantId);
                }
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired — path: {}", request.getRequestURI());
            sendUnauthorized(response, "Session expired. Please log in again.");
            return;

        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT — path: {}", request.getRequestURI());
            sendUnauthorized(response, "Invalid token.");
            return;

        } catch (Exception e) {
            log.error("JWT processing error — {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            sendUnauthorized(response, "Authentication failed.");
            return;
        }

        // ── Run the rest of the filter chain ──────────────────────────────────
        // TenantContext is cleared AFTER filterChain completes
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }

    private void sendUnauthorized(HttpServletResponse response,
                                   String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false," +
                "\"statusCode\":401," +
                "\"error\":{" +
                  "\"code\":\"UNAUTHORIZED\"," +
                  "\"message\":\"" + message + "\"" +
                "}}");
    }
}
