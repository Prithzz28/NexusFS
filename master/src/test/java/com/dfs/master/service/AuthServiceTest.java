package com.dfs.master.service;

import com.dfs.common.enums.UserRole;
import com.dfs.master.dto.AuthResponse;
import com.dfs.master.dto.LoginRequest;
import com.dfs.master.dto.RegisterRequest;
import com.dfs.master.entity.User;
import com.dfs.master.exception.AuthenticationException;
import com.dfs.master.exception.DuplicateResourceException;
import com.dfs.master.repository.UserRepository;
import com.dfs.master.security.JwtProperties;
import com.dfs.master.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Uses a real JwtService (it's a pure function with no external dependencies)
 * and mocks only interfaces (UserRepository, PasswordEncoder) which Mockito
 * can always mock regardless of Java version.
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtProperties props = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algo!",
                3600000
        );
        jwtService = new JwtService(props);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);

        registerRequest = new RegisterRequest("testuser", "test@example.com", "password123");
        loginRequest = new LoginRequest("testuser", "password123");
    }

    @Test
    @DisplayName("Should register a new user successfully")
    void register_shouldCreateUserAndReturnToken() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return createUserWithId(user.getUsername(), user.getEmail(),
                    user.getPasswordHash(), user.getRole());
        });

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertNotNull(response.token());
        assertFalse(response.token().isEmpty());
        assertEquals("Bearer", response.tokenType());
        assertEquals("testuser", response.username());
        assertEquals(UserRole.USER, response.role());

        assertTrue(jwtService.isTokenValid(response.token()));
        assertEquals("testuser", jwtService.getUsernameFromToken(response.token()));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("hashed_password", userCaptor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Should throw on duplicate username")
    void register_shouldThrowOnDuplicateUsername() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw on duplicate email")
    void register_shouldThrowOnDuplicateEmail() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should login successfully with correct credentials")
    void login_shouldReturnTokenOnValidCredentials() {
        User user = createUserWithId("testuser", "test@example.com", "hashed_password", UserRole.USER);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertNotNull(response.token());
        assertTrue(jwtService.isTokenValid(response.token()));
        assertEquals("testuser", response.username());
    }

    @Test
    @DisplayName("Should throw on non-existent username")
    void login_shouldThrowOnNonExistentUser() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class,
                () -> authService.login(loginRequest));
    }

    @Test
    @DisplayName("Should throw on wrong password")
    void login_shouldThrowOnWrongPassword() {
        User user = createUserWithId("testuser", "test@example.com", "hashed_password", UserRole.USER);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(false);

        assertThrows(AuthenticationException.class,
                () -> authService.login(loginRequest));
    }

    /**
     * Creates a User with a generated UUID, simulating what JPA does on persist.
     * The id field is package-private in the entity (set by JPA), so we use
     * reflection here only in tests.
     */
    private static User createUserWithId(String username, String email,
                                         String passwordHash, UserRole role) {
        User user = new User(username, email, passwordHash, role);
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set user ID for test", e);
        }
        return user;
    }
}
