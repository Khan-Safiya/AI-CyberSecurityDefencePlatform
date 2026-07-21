package com.cybersim.identityservice.controller;

import com.cybersim.identityservice.security.JwtTokenService;
import com.cybersim.identityservice.security.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.cybersim.shared.observability.ApiErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final UserAccountService userAccountService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenService tokenService,
                          UserAccountService userAccountService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication);
    }

    @GetMapping("/me")
    public UserResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(UUID.fromString(jwt.getClaimAsString("userId")), jwt.getSubject(),
                jwt.getClaimAsStringList("roles"), true);
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return userAccountService.users();
    }

    @PostMapping("/users/{username}/enable")
    public UserResponse enable(@PathVariable String username) {
        return userAccountService.setEnabled(username, true);
    }

    @PostMapping("/users/{username}/disable")
    public UserResponse disable(@PathVariable String username) {
        return userAccountService.setEnabled(username, false);
    }

    @PostMapping("/users/{username}/password")
    public UserResponse resetPassword(@PathVariable String username,
                                      @Valid @RequestBody PasswordResetRequest request) {
        return userAccountService.resetPassword(username, request.password());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> authenticationFailed() {
        return ApiErrors.response(HttpStatus.UNAUTHORIZED, "Invalid username or password", "/auth/login");
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordResetRequest(@NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record TokenResponse(String tokenType, String accessToken, Instant expiresAt, List<String> roles) {
    }

    public record UserResponse(UUID id, String username, List<String> roles, boolean enabled) {
    }
}
