package com.pos.system.service;

import com.pos.system.dto.AuthDto;
import com.pos.system.exception.PosException;
import com.pos.system.model.*;
import com.pos.system.repository.*;
import com.pos.system.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final TenantRepository      tenantRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtService            jwtService;
    private final AuthenticationManager authManager;

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent())
            throw new PosException.ConflictException(
                    "An account with this email already exists: "
                    + request.getEmail());

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.getBusinessName())
                .businessType(request.getBusinessType())
                .build());

        User user = userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.ADMIN)
                .build());

        String token = jwtService.generateToken(user);

        log.info("Registration successful — user: {}, tenant: {}",
                user.getId(), tenant.getId());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .tenantId(tenant.getId().toString())
                .build();
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            log.warn("Login failed — invalid credentials for: {}",
                    request.getEmail());
            throw new PosException.UnauthorizedException(
                    "Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new PosException.ResourceNotFoundException(
                        "User not found"));

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new PosException.ResourceNotFoundException(
                        "Tenant not found"));

        String token = jwtService.generateToken(user);

        log.info("Login successful — user: {}, tenant: {}",
                user.getId(), tenant.getId());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .tenantId(tenant.getId().toString())
                .build();
    }
}
