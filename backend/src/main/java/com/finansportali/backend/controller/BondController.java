package com.finansportali.backend.controller;

import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.dto.response.bond.BondDetailDto;
import com.finansportali.backend.dto.response.bond.BondHistoryPointDto;
import com.finansportali.backend.dto.response.bond.BondListItemDto;
import com.finansportali.backend.dto.response.bond.BondSummaryDto;
import com.finansportali.backend.service.BondDataRefreshService;
import com.finansportali.backend.service.DebtInstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Tahvil ve Bono (Borçlanma Araçları) REST API Controller.
 * 
 * Türkiye finans piyasalarındaki devlet tahvilleri, hazine bonoları ve
 * diğer borçlanma araçlarının verilerini sunar.
 */
@RestController
@RequestMapping("/api/v1/bonds")
@Tag(name = "Bonds", description = "Tahvil ve Bono API")
public class BondController {

    private static final Logger log = LoggerFactory.getLogger(BondController.class);

    private final DebtInstrumentService debtInstrumentService;
    private final BondDataRefreshService refreshService;

    public BondController(DebtInstrumentService debtInstrumentService,
                         BondDataRefreshService refreshService) {
        this.debtInstrumentService = debtInstrumentService;
        this.refreshService = refreshService;
    }

    /**
     * Tahvil ve bono listesini getirir.
     * 
     * @param type Enstrüman türü (GOVERNMENT_BOND, TREASURY_BILL, vb.)
     * @param currency Para birimi (TRY, USD, EUR)
     * @param maturityFrom Vade başlangıç tarihi
     * @param maturityTo Vade bitiş tarihi
     * @param search Arama terimi (sembol, isim, ISIN)
     * @return Tahvil/bono listesi
     */
    @GetMapping
    @Operation(summary = "Tahvil ve bono listesi", 
               description = "Aktif borçlanma araçlarını filtrelerle listeler")
    public ResponseEntity<List<BondListItemDto>> listBonds(
        @Parameter(description = "Enstrüman türü") 
        @RequestParam(required = false) DebtInstrumentType type,
        
        @Parameter(description = "Para birimi") 
        @RequestParam(required = false) String currency,
        
        @Parameter(description = "Vade başlangıç tarihi (YYYY-MM-DD)") 
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate maturityFrom,
        
        @Parameter(description = "Vade bitiş tarihi (YYYY-MM-DD)") 
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate maturityTo,
        
        @Parameter(description = "Arama terimi") 
        @RequestParam(required = false) String search
    ) {
        // Convert empty strings to null for proper query handling
        String normalizedSearch = (search != null && search.trim().isEmpty()) ? null : search;
        
        log.info("GET /api/v1/bonds - type={}, currency={}, maturityFrom={}, maturityTo={}, search={}", 
                type, currency, maturityFrom, maturityTo, normalizedSearch);

        List<BondListItemDto> bonds = debtInstrumentService.listBonds(
            type, currency, maturityFrom, maturityTo, normalizedSearch
        );

        return ResponseEntity.ok(bonds);
    }

    /**
     * Tahvil/bono detayını getirir.
     * 
     * @param id Enstrüman ID
     * @return Detaylı bilgi
     */
    @GetMapping("/{id}")
    @Operation(summary = "Tahvil/bono detayı", 
               description = "Belirli bir borçlanma aracının detaylı bilgilerini getirir")
    public ResponseEntity<BondDetailDto> getBondDetail(
        @Parameter(description = "Enstrüman ID") 
        @PathVariable Long id
    ) {
        log.info("GET /api/v1/bonds/{}", id);

        BondDetailDto detail = debtInstrumentService.getBondDetail(id);
        return ResponseEntity.ok(detail);
    }

    /**
     * Tahvil/bono tarihsel getiri verilerini getirir.
     * 
     * @param id Enstrüman ID
     * @param from Başlangıç tarihi
     * @param to Bitiş tarihi
     * @return Tarihsel veri noktaları
     */
    @GetMapping("/{id}/history")
    @Operation(summary = "Tarihsel getiri verileri", 
               description = "Belirli bir borçlanma aracının tarihsel fiyat ve getiri verilerini getirir")
    public ResponseEntity<List<BondHistoryPointDto>> getBondHistory(
        @Parameter(description = "Enstrüman ID") 
        @PathVariable Long id,
        
        @Parameter(description = "Başlangıç tarihi (YYYY-MM-DD)") 
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        
        @Parameter(description = "Bitiş tarihi (YYYY-MM-DD)") 
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("GET /api/v1/bonds/{}/history - from={}, to={}", id, from, to);

        List<BondHistoryPointDto> history = debtInstrumentService.getBondHistory(id, from, to);
        return ResponseEntity.ok(history);
    }

    /**
     * Tahvil/bono özet istatistiklerini getirir.
     * 
     * @return Özet istatistikler
     */
    @GetMapping("/summary")
    @Operation(summary = "Özet istatistikler", 
               description = "Tahvil ve bono piyasası özet istatistiklerini getirir")
    public ResponseEntity<BondSummaryDto> getSummary() {
        log.info("GET /api/v1/bonds/summary");

        BondSummaryDto summary = debtInstrumentService.getSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Manuel veri güncelleme (sadece ADMIN).
     * 
     * @return Güncelleme sonucu
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manuel veri güncelleme (ADMIN)", 
               description = "Tahvil ve bono verilerini manuel olarak günceller. Sadece ADMIN rolü erişebilir.")
    public ResponseEntity<String> manualRefresh() {
        log.info("POST /api/v1/bonds/refresh - Manual refresh triggered by admin");

        String result = refreshService.triggerManualRefresh();
        return ResponseEntity.ok(result);
    }
}
