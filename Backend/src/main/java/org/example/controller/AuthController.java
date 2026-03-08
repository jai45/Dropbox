package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.AuthResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.example.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Register, login, refresh and logout operations")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        // Use raw header to support SameSite (not available on Cookie API pre-Servlet 6)
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Max-Age=%d; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict",
                        REFRESH_COOKIE, rawToken, (int) (refreshExpiryMs / 1000)));
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                String.format("%s=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict",
                        REFRESH_COOKIE));
    }

    // ── endpoints ────────────────────────────────────────────────────────────

    @Operation(
            summary = "Register a new user",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Email or username already in use")
            }
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request,
                                                  HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @Operation(
            summary = "Login with username or email",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            }
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    @Operation(
            summary = "Refresh access token",
            description = "Reads the refresh token from the HttpOnly cookie, issues a new access token and rotates the refresh token cookie",
            responses = {
                    @ApiResponse(responseCode = "200", description = "New tokens issued",
                            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Missing, invalid, expired or revoked refresh token")
            }
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefreshToken,
            HttpServletResponse response) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuthService.AuthResult result = authService.refresh(rawRefreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    @Operation(
            summary = "Logout",
            description = "Revokes the refresh token cookie and clears it",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Logged out successfully")
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefreshToken,
            HttpServletResponse response) {

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            authService.logout(rawRefreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }
}
