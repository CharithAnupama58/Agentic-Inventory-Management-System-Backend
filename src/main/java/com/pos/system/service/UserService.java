package com.pos.system.service;

import com.pos.system.dto.UserDto;
import com.pos.system.exception.PosException;
import com.pos.system.model.User;
import com.pos.system.repository.UserRepository;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    // ── Get all users for this tenant ─────────────────────────────────────────
    public List<UserDto.Response> getAllUsers() {
        UUID tenantId = TenantContext.getTenantId();
        log.debug("Fetching all users for tenant: {}", tenantId);
        return userRepository.findAllByTenantId(tenantId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Create new staff user ─────────────────────────────────────────────────
    @Transactional
    public UserDto.Response createUser(UserDto.CreateRequest req,
                                        UUID adminId) {
        UUID tenantId = TenantContext.getTenantId();

        log.info("Creating user '{}' with role {} — tenant: {}",
                req.getEmail(), req.getRole(), tenantId);

        if (userRepository.findByEmail(req.getEmail()).isPresent())
            throw new PosException.ConflictException(
                    "Email already registered: " + req.getEmail());

        // Admin cannot create another ADMIN
        if (req.getRole() == User.Role.ADMIN)
            throw new PosException.ForbiddenException(
                    "Cannot create another ADMIN user. " +
                    "Use MANAGER or CASHIER roles.");

        User user = userRepository.save(User.builder()
                .tenantId(tenantId)
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .build());

        auditLogService.userRegistered(tenantId, user.getId(), user.getEmail());
        log.info("User created — id: {}, email: {}, role: {}",
                user.getId(), user.getEmail(), user.getRole());

        return toResponse(user);
    }

    // ── Update user role ──────────────────────────────────────────────────────
    @Transactional
    public UserDto.Response updateUserRole(UUID userId,
                                            UserDto.UpdateRoleRequest req,
                                            UUID adminId) {
        UUID tenantId = TenantContext.getTenantId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PosException.ResourceNotFoundException(
                        "User", userId.toString()));

        if (!user.getTenantId().equals(tenantId))
            throw new PosException.TenantAccessException();

        // Cannot change own role
        if (userId.equals(adminId))
            throw new PosException.BadRequestException(
                    "You cannot change your own role");

        // Cannot promote to ADMIN
        if (req.getRole() == User.Role.ADMIN)
            throw new PosException.ForbiddenException(
                    "Cannot assign ADMIN role. Only one ADMIN per tenant.");

        log.info("Updating role for user: {} from {} to {} — tenant: {}",
                userId, user.getRole(), req.getRole(), tenantId);

        user.setRole(req.getRole());
        return toResponse(userRepository.save(user));
    }

    // ── Delete user ───────────────────────────────────────────────────────────
    @Transactional
    public void deleteUser(UUID userId, UUID adminId) {
        UUID tenantId = TenantContext.getTenantId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PosException.ResourceNotFoundException(
                        "User", userId.toString()));

        if (!user.getTenantId().equals(tenantId))
            throw new PosException.TenantAccessException();

        if (userId.equals(adminId))
            throw new PosException.BadRequestException(
                    "You cannot delete your own account");

        log.info("Deleting user: {} — tenant: {}", userId, tenantId);
        userRepository.delete(user);
    }

    private UserDto.Response toResponse(User u) {
        return UserDto.Response.builder()
                .id(u.getId()).name(u.getName())
                .email(u.getEmail()).role(u.getRole().name())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
