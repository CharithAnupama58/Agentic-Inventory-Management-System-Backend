package com.pos.system.controller;

import com.pos.system.dto.InsightsDto;
import com.pos.system.service.AIInsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final AIInsightsService aiInsightsService;

    @GetMapping
    public ResponseEntity<InsightsDto.InsightResponse> getInsights() {
        log.info("Insights requested");
        return ResponseEntity.ok(aiInsightsService.generateInsights());
    }
}
