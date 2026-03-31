package com.pos.system.controller;

import com.pos.system.dto.SaleDto;
import com.pos.system.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleDto.SaleResponse> createSale(
            @Valid @RequestBody SaleDto.SaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saleService.createSale(req));
    }

    @GetMapping
    public ResponseEntity<List<SaleDto.SaleResponse>> getAllSales() {
        return ResponseEntity.ok(saleService.getAllSales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleDto.SaleResponse> getSale(
            @PathVariable UUID id) {
        return ResponseEntity.ok(saleService.getSale(id));
    }

    @PostMapping("/refund")
    public ResponseEntity<SaleDto.RefundResponse> refundSale(
            @Valid @RequestBody SaleDto.RefundRequest req) {
        return ResponseEntity.ok(saleService.refundSale(req));
    }

    // ── Check if product has active campaign discount ─────────────────────────
    @GetMapping("/campaign/{productId}")
    public ResponseEntity<SaleDto.ProductCampaignInfo> getProductCampaign(
            @PathVariable UUID productId) {
        SaleDto.ProductCampaignInfo info =
                saleService.getProductCampaignInfo(productId);
        return info != null
                ? ResponseEntity.ok(info)
                : ResponseEntity.noContent().build();
    }
}
