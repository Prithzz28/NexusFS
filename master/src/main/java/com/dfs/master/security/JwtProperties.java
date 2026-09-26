package com.dfs.master.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dfs.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}
