package com.pos.system.controller;

import com.pos.system.dto.InventoryDto;
import com.pos.system.security.RequiresPermission;
import com.pos.system.security.RolePermissions;
import com.pos.system.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/summary")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<InventoryDto.IntelligenceSummary> getSummary() {
        return ResponseEntity.ok(inventoryService.getSummary());
    }

    @PostMapping("/adjust")
    @RequiresPermission(value = RolePermissions.PERM_INVENTORY_EDIT,
                        message = "Only ADMIN and MANAGER can adjust stock")
    public ResponseEntity<InventoryDto.LogResponse> adjust(
            @Valid @RequestBody InventoryDto.AdjustmentRequest req) {
        return ResponseEntity.ok(inventoryService.adjustStock(req));
    }

    @PostMapping("/batch")
    @RequiresPermission(value = RolePermissions.PERM_BATCH_ADD,
                        message = "Only ADMIN and MANAGER can add batches")
    public ResponseEntity<InventoryDto.BatchResponse> addBatch(
            @Valid @RequestBody InventoryDto.BatchRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.addBatch(req));
    }

    @GetMapping("/batch/{productId}")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<List<InventoryDto.BatchResponse>> getBatches(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(
                inventoryService.getBatchesForProduct(productId));
    }

    @GetMapping("/expiring")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<List<InventoryDto.BatchResponse>> getExpiring() {
        return ResponseEntity.ok(inventoryService.getExpiringBatches());
    }

    @GetMapping("/history/{productId}")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<List<InventoryDto.LogResponse>> getHistory(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(
                inventoryService.getMovementHistory(productId));
    }

    @GetMapping("/movements")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<List<InventoryDto.LogResponse>> getMovements() {
        return ResponseEntity.ok(inventoryService.getRecentMovements(50));
    }

    @GetMapping("/reorder")
    @RequiresPermission(RolePermissions.PERM_INVENTORY_VIEW)
    public ResponseEntity<List<InventoryDto.ReorderResponse>> getReorder() {
        return ResponseEntity.ok(
                inventoryService.generateReorderSuggestions());
    }

    @PatchMapping("/reorder/{id}/acknowledge")
    @RequiresPermission(value = RolePermissions.PERM_INVENTORY_EDIT,
                        message = "Only ADMIN and MANAGER can acknowledge reorder suggestions")
    public ResponseEntity<InventoryDto.ReorderResponse> acknowledge(
            @PathVariable UUID id,
            @RequestParam String action) {
        return ResponseEntity.ok(
                inventoryService.acknowledgeSuggestion(id, action));
    }
}
