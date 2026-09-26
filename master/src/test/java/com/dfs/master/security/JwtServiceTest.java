package com.dfs.master.security;

import com.dfs.common.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // 64-character secret for HMAC-SHA256 (>= 256 bits)
        JwtProperties props = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algo!",
                3600000 // 1 hour
        );
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("Should generate a valid JWT token")
    void generateToken_shouldReturnNonEmptyToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "testuser", "USER");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Should extract correct userId from token")
    void getUserIdFromToken_shouldReturnCorrectId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "testuser", "USER");

        UUID extractedId = jwtService.getUserIdFromToken(token);
        assertEquals(userId, extractedId);
    }

    @Test
    @DisplayName("Should extract correct username from token")
    void getUsernameFromToken_shouldReturnCorrectUsername() {
        String token = jwtService.generateToken(UUID.randomUUID(), "john_doe", "USER");

        assertEquals("john_doe", jwtService.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Should extract correct role from token")
    void getRoleFromToken_shouldReturnCorrectRole() {
        String token = jwtService.generateToken(UUID.randomUUID(), "admin", "ADMIN");

        assertEquals("ADMIN", jwtService.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Should validate a legitimate token as valid")
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user", "USER");

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    @DisplayName("Should reject a tampered token")
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user", "USER");
        String tampered = token + "tampered";

        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    @DisplayName("Should reject a garbage string")
    void isTokenValid_shouldReturnFalseForGarbage() {
        assertFalse(jwtService.isTokenValid("not.a.jwt"));
    }

    @Test
    @DisplayName("Should reject an expired token")
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        JwtProperties expiredProps = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algo!",
                -1000 // Already expired
        );
        JwtService expiredJwtService = new JwtService(expiredProps);

        String token = expiredJwtService.generateToken(UUID.randomUUID(), "user", "USER");

        assertFalse(jwtService.isTokenValid(token));
    }
}
