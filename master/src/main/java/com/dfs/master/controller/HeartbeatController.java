package com.dfs.master.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.master.dto.HeartbeatRequest;
import com.dfs.master.service.HeartbeatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for storage node heartbeats.
 */
@RestController
@RequestMapping("/internal/nodes")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @Valid @RequestBody HeartbeatRequest request) {
        heartbeatService.processHeartbeat(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
