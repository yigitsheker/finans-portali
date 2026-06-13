package com.finansportali.backend.service;

import com.finansportali.backend.dto.request.HistoricalPositionRequest;
import com.finansportali.backend.dto.response.HistoricalPositionResponse;
import com.finansportali.backend.entity.HistoricalPosition;
import com.finansportali.backend.repository.HistoricalPositionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoricalPositionService {

    private final HistoricalPositionRepository repository;

    public HistoricalPositionService(HistoricalPositionRepository repository) {
        this.repository = repository;
    }

    public List<HistoricalPositionResponse> getUserPositions(String userId) {
        return repository.findByUserIdOrderByBuyDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public HistoricalPositionResponse addPosition(String userId, HistoricalPositionRequest request) {
        HistoricalPosition position = new HistoricalPosition(
                userId,
                request.symbol(),
                request.name(),
                request.buyDate(),
                request.buyPrice(),
                request.lots(),
                request.currency()
        );
        HistoricalPosition saved = repository.save(position);
        return toResponse(saved);
    }

    @Transactional
    public HistoricalPositionResponse updatePosition(String userId, Long id, HistoricalPositionRequest request) {
        HistoricalPosition position = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));

        if (!position.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to update this position");
        }

        position.setSymbol(request.symbol());
        position.setName(request.name());
        position.setBuyDate(request.buyDate());
        position.setBuyPrice(request.buyPrice());
        position.setLots(request.lots());
        position.setCurrency(request.currency());

        HistoricalPosition updated = repository.save(position);
        return toResponse(updated);
    }

    @Transactional
    public void deletePosition(String userId, Long id) {
        if (!repository.existsByIdAndUserId(id, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found or not authorized");
        }
        repository.deleteByIdAndUserId(id, userId);
    }

    @Transactional
    public void deleteAllUserPositions(String userId) {
        List<HistoricalPosition> positions = repository.findByUserIdOrderByBuyDateDesc(userId);
        repository.deleteAll(positions);
    }

    private HistoricalPositionResponse toResponse(HistoricalPosition position) {
        return new HistoricalPositionResponse(
                position.getId(),
                position.getSymbol(),
                position.getName(),
                position.getBuyDate(),
                position.getBuyPrice(),
                position.getLots(),
                position.getCurrency()
        );
    }
}
