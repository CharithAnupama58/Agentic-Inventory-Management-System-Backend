
package com.pos.system.controller;

import com.pos.system.dto.DashboardDto;
import com.pos.system.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/dashboard/summary
    @GetMapping("/summary")
    public ResponseEntity<DashboardDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
