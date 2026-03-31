package com.pos.system.controller;

import com.pos.system.dto.PredictionDto;
import com.pos.system.service.InventoryPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final InventoryPredictionService predictionService;

    // GET /api/predictions — full prediction report
    @GetMapping
    public ResponseEntity<PredictionDto.PredictionResponse> getPredictions() {
        log.info("Inventory predictions requested");
        return ResponseEntity.ok(
                predictionService.generatePredictions());
    }

    // GET /api/predictions/{productId} — single product
    @GetMapping("/{productId}")
    public ResponseEntity<PredictionDto.ProductPrediction> getProductPrediction(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(
                predictionService.getProductPrediction(productId));
    }
}
