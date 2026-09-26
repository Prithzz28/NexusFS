package com.dfs.master.dto;

import com.dfs.common.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        String username,
        UserRole role
) {
    public AuthResponse(String token, UUID userId, String username, UserRole role) {
        this(token, "Bearer", userId, username, role);
    }
}
