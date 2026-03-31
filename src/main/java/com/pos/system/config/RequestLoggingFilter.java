package com.pos.system.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    // Skip logging for these paths
    private static final String[] SKIP_PATHS = {
            "/actuator", "/favicon.ico"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                          FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Skip non-API paths
        String path = req.getRequestURI();
        for (String skip : SKIP_PATHS) {
            if (path.startsWith(skip)) {
                chain.doFilter(request, response);
                return;
            }
        }

        ContentCachingRequestWrapper  wrappedReq  =
                new ContentCachingRequestWrapper(req);
        ContentCachingResponseWrapper wrappedResp =
                new ContentCachingResponseWrapper(resp);

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedReq, wrappedResp);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int  status   = wrappedResp.getStatus();

            if (status >= 500) {
                log.error("HTTP {} {} → {} ({}ms)",
                        req.getMethod(), path, status, duration);
            } else if (status >= 400) {
                log.warn("HTTP {} {} → {} ({}ms)",
                        req.getMethod(), path, status, duration);
            } else {
                log.info("HTTP {} {} → {} ({}ms)",
                        req.getMethod(), path, status, duration);
            }

            wrappedResp.copyBodyToResponse();
        }
    }
}
