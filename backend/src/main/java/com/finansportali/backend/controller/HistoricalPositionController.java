package com.finansportali.backend.controller;

import com.finansportali.backend.common.ApiResponse;
import com.finansportali.backend.dto.request.HistoricalPositionRequest;
import com.finansportali.backend.dto.response.HistoricalPositionResponse;
import com.finansportali.backend.service.HistoricalPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historical-positions")
@Tag(name = "Historical Positions", description = "User's historical investment tracking")
@SecurityRequirement(name = "bearer-jwt")
public class HistoricalPositionController {

    private final HistoricalPositionService service;

    public HistoricalPositionController(HistoricalPositionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get all historical positions for the authenticated user")
    public ResponseEntity<ApiResponse<List<HistoricalPositionResponse>>> getUserPositions(
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<HistoricalPositionResponse> positions = service.getUserPositions(userId);
        return ResponseEntity.ok(ApiResponse.success(positions));
    }

    @PostMapping
    @Operation(summary = "Add a new historical position")
    public ResponseEntity<ApiResponse<HistoricalPositionResponse>> addPosition(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HistoricalPositionRequest request) {
        String userId = jwt.getSubject();
        HistoricalPositionResponse created = service.addPosition(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing historical position")
    public ResponseEntity<ApiResponse<HistoricalPositionResponse>> updatePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody HistoricalPositionRequest request) {
        String userId = jwt.getSubject();
        HistoricalPositionResponse updated = service.updatePosition(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a historical position")
    public ResponseEntity<ApiResponse<Void>> deletePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        String userId = jwt.getSubject();
        service.deletePosition(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    @Operation(summary = "Delete all historical positions for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> deleteAllPositions(
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        service.deleteAllUserPositions(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
