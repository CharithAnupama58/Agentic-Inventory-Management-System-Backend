package com.pos.system.controller;

import com.pos.system.dto.AnalyticsDto;
import com.pos.system.security.RequiresPermission;
import com.pos.system.security.RolePermissions;
import com.pos.system.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    @RequiresPermission(value = RolePermissions.PERM_ANALYTICS_VIEW,
                        message = "Only ADMIN and MANAGER can view analytics")
    public ResponseEntity<AnalyticsDto.AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getFullAnalytics());
    }
}
