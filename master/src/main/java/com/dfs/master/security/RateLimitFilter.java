package com.dfs.master.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Token-bucket rate limiter per user or IP address.
 * Uses Redis to track request counts with a sliding window.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String RATE_LIMIT_PREFIX = "dfs:rate:";
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RedisTemplate<String, Object> redisTemplate,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip rate limiting for internal endpoints and health checks
        String path = request.getRequestURI();
        if (path.startsWith("/internal/") || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        if (clientKey != null && isRateLimited(clientKey)) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");

            Map<String, Object> body = Map.of(
                    "success", false,
                    "error", Map.of(
                            "code", "RATE_LIMITED",
                            "message", "Too many requests. Please try again later."
                    ),
                    "timestamp", Instant.now().toString()
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        // Try to use authenticated user ID first, fall back to IP
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DfsUserDetails userDetails) {
            return "user:" + userDetails.getUserId();
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }

    private boolean isRateLimited(String clientKey) {
        String key = RATE_LIMIT_PREFIX + clientKey;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW);
            }
            return count != null && count > MAX_REQUESTS_PER_MINUTE;
        } catch (Exception e) {
            // If Redis is unavailable, don't rate limit (fail open)
            log.debug("Rate limiter unavailable: {}", e.getMessage());
            return false;
        }
    }
}
