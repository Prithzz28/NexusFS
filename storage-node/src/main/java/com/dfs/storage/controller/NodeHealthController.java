package com.dfs.storage.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.common.dto.NodeHealthResponse;
import com.dfs.common.enums.NodeStatus;
import com.dfs.storage.config.StorageNodeProperties;
import com.dfs.storage.service.LocalStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class NodeHealthController {

    private final StorageNodeProperties properties;
    private final LocalStorageService storageService;

    public NodeHealthController(StorageNodeProperties properties, LocalStorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public ApiResponse<NodeHealthResponse> getHealth() {
        long usedBytes = storageService.getUsedStorageBytes();
        NodeHealthResponse health = new NodeHealthResponse(
                properties.nodeId(),
                NodeStatus.ACTIVE,
                properties.capacityBytes(),
                usedBytes,
                properties.capacityBytes() - usedBytes,
                storageService.getChunkCount()
        );
        return ApiResponse.success(health);
    }
}
