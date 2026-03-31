package com.pos.system.controller;

import com.pos.system.dto.UserDto;
import com.pos.system.model.User;
import com.pos.system.security.RequiresPermission;
import com.pos.system.security.RolePermissions;
import com.pos.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET /api/users
    @GetMapping
    @RequiresPermission(value = RolePermissions.PERM_USER_MANAGE,
                        message = "Only ADMIN can manage users")
    public ResponseEntity<List<UserDto.Response>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // POST /api/users
    @PostMapping
    @RequiresPermission(value = RolePermissions.PERM_USER_MANAGE,
                        message = "Only ADMIN can create users")
    public ResponseEntity<UserDto.Response> createUser(
            @Valid @RequestBody UserDto.CreateRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(req, admin.getId()));
    }

    // PATCH /api/users/{id}/role
    @PatchMapping("/{id}/role")
    @RequiresPermission(value = RolePermissions.PERM_USER_MANAGE,
                        message = "Only ADMIN can change user roles")
    public ResponseEntity<UserDto.Response> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserDto.UpdateRoleRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(
                userService.updateUserRole(id, req, admin.getId()));
    }

    // DELETE /api/users/{id}
    @DeleteMapping("/{id}")
    @RequiresPermission(value = RolePermissions.PERM_USER_MANAGE,
                        message = "Only ADMIN can delete users")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        userService.deleteUser(id, admin.getId());
        return ResponseEntity.noContent().build();
    }
}
