package com.cybersim.identityservice.security;

import com.cybersim.identityservice.controller.AuthController.UserResponse;
import com.cybersim.identityservice.persistence.PlatformUserEntity;
import com.cybersim.identityservice.persistence.PlatformUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class UserAccountService implements UserDetailsService, ApplicationRunner {
    private final PlatformUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final List<SeedAccount> seedAccounts;

    public UserAccountService(
            PlatformUserRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${identity.users.admin-password}") String adminPassword,
            @Value("${identity.users.operator-password}") String operatorPassword,
            @Value("${identity.users.auditor-password}") String auditorPassword
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.seedAccounts = List.of(
                new SeedAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "demo-admin", "ADMIN", adminPassword),
                new SeedAccount(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "demo-operator", "SIMULATION_OPERATOR", operatorPassword),
                new SeedAccount(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "demo-auditor", "AUDITOR", auditorPassword)
        );
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedAccount account : seedAccounts) {
            if (!repository.existsByUsername(account.username())) {
                repository.save(new PlatformUserEntity(account.id(), account.username(),
                        passwordEncoder.encode(account.password()), account.role(), true));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        PlatformUserEntity account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(roles(account))
                .disabled(!account.isEnabled())
                .build();
    }

    @Transactional(readOnly = true)
    public UUID idFor(String username) {
        return repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"))
                .getId();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return repository.findAll().stream()
                .map(account -> new UserResponse(account.getId(), account.getUsername(),
                        List.of(roles(account)), account.isEnabled()))
                .toList();
    }

    @Transactional
    public UserResponse setEnabled(String username, boolean enabled) {
        PlatformUserEntity account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        account.setEnabled(enabled);
        return toResponse(account);
    }

    @Transactional
    public UserResponse resetPassword(String username, String password) {
        PlatformUserEntity account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        account.setPasswordHash(passwordEncoder.encode(password));
        return toResponse(account);
    }

    private UserResponse toResponse(PlatformUserEntity account) {
        return new UserResponse(account.getId(), account.getUsername(), List.of(roles(account)), account.isEnabled());
    }

    private String[] roles(PlatformUserEntity account) {
        return Arrays.stream(account.getRoles().split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toArray(String[]::new);
    }

    private record SeedAccount(UUID id, String username, String role, String password) {
    }
}
