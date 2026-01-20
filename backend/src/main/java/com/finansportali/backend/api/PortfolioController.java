package com.finansportali.backend.api;

import com.finansportali.backend.domain.PortfolioPosition;
import com.finansportali.backend.dto.PortfolioSummary;
import com.finansportali.backend.dto.UpsertPositionRequest;
import com.finansportali.backend.service.PortfolioService;
import org.springframework.web.bind.annotation.*;
import com.finansportali.backend.dto.AllocationItem;
import com.finansportali.backend.dto.AllocationByTypeItem;
import jakarta.validation.Valid;



import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping("/positions")
    public List<PortfolioPosition> positions() {
        return service.list();
    }

    @GetMapping("/allocation")
    public List<AllocationItem> allocation() {
        return service.allocation();
    }

    @GetMapping("/allocation/by-type")
    public List<AllocationByTypeItem> allocationByType() {
        return service.allocationByType();
    }

    @PostMapping("/positions")
    public void upsert(@Valid @RequestBody UpsertPositionRequest req) {
        service.upsert(req);
    }


    @GetMapping("/summary")
    public PortfolioSummary summary() {
        return service.summary();
    }
}
