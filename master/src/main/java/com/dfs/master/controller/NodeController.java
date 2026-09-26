package com.dfs.master.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.master.dto.NodeRegistrationRequest;
import com.dfs.master.dto.NodeResponse;
import com.dfs.master.service.StorageNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
@Tag(name = "Storage Nodes", description = "Storage node registration and management")
public class NodeController {

    private final StorageNodeService storageNodeService;

    public NodeController(StorageNodeService storageNodeService) {
        this.storageNodeService = storageNodeService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register or re-register a storage node")
    public ResponseEntity<ApiResponse<NodeResponse>> registerNode(
            @Valid @RequestBody NodeRegistrationRequest request) {
        NodeResponse response = storageNodeService.registerNode(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List all storage nodes")
    public ResponseEntity<ApiResponse<List<NodeResponse>>> listNodes() {
        List<NodeResponse> nodes = storageNodeService.listNodes();
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }

    @GetMapping("/active")
    @Operation(summary = "List active storage nodes")
    public ResponseEntity<ApiResponse<List<NodeResponse>>> listActiveNodes() {
        List<NodeResponse> nodes = storageNodeService.listActiveNodes();
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }

    @GetMapping("/{nodeId}")
    @Operation(summary = "Get storage node details")
    public ResponseEntity<ApiResponse<NodeResponse>> getNode(@PathVariable String nodeId) {
        NodeResponse response = storageNodeService.getNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
