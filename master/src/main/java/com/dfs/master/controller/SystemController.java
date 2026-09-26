package com.dfs.master.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.master.config.DfsProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final DfsProperties dfsProperties;
    private final Instant startTime = Instant.now();

    public SystemController(DfsProperties dfsProperties) {
        this.dfsProperties = dfsProperties;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = Map.of(
                "service", "DFS Master",
                "status", "UP",
                "startTime", startTime.toString(),
                "configuration", Map.of(
                        "chunkSizeBytes", dfsProperties.chunkSizeBytes(),
                        "replicationFactor", dfsProperties.replicationFactor(),
                        "heartbeatIntervalSeconds", dfsProperties.heartbeatIntervalSeconds(),
                        "nodeFailureTimeoutSeconds", dfsProperties.nodeFailureTimeoutSeconds(),
                        "maxFileSizeBytes", dfsProperties.maxFileSizeBytes()
                )
        );
        return ApiResponse.success(status);
    }
}
