package com.dfs.master.service;

import com.dfs.common.enums.UserRole;
import com.dfs.master.dto.AuthResponse;
import com.dfs.master.dto.LoginRequest;
import com.dfs.master.dto.RegisterRequest;
import com.dfs.master.entity.User;
import com.dfs.master.exception.AuthenticationException;
import com.dfs.master.exception.DuplicateResourceException;
import com.dfs.master.repository.UserRepository;
import com.dfs.master.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already exists: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(
                request.username(),
                request.email(),
                hashedPassword,
                UserRole.USER
        );

        user = userRepository.save(user);
        log.info("User registered: {} ({})", user.getUsername(), user.getId());

        String token = jwtService.generateToken(
                user.getId(), user.getUsername(), user.getRole().name());

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for user: {}", request.username());
            throw new AuthenticationException("Invalid username or password");
        }

        log.info("User logged in: {} ({})", user.getUsername(), user.getId());

        String token = jwtService.generateToken(
                user.getId(), user.getUsername(), user.getRole().name());

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}
