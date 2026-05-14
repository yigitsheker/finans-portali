package com.finansportali.backend.service.client.bond;

import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tahvil ve bono verisi sağlayıcı arayüzü.
 * Farklı veri kaynaklarından (TCMB, BIST, demo) borçlanma aracı verilerini
 * çekmek için soyutlama katmanı.
 */
public interface BondDataProvider {

    /**
     * Provider adı (TCMB, BIST, DEMO)
     */
    String getProviderName();

    /**
     * Provider aktif mi?
     */
    boolean isEnabled();

    /**
     * Güncel tahvil/bono verilerini çeker.
     * @return Borçlanma araçları ve güncel fiyat/getiri bilgileri
     */
    List<BondQuoteDto> fetchLatestBondQuotes();

    /**
     * Belirli bir enstrümanın geçmiş getiri verilerini çeker.
     * @param symbol Enstrüman sembolü
     * @param from Başlangıç tarihi
     * @param to Bitiş tarihi
     * @return Tarihsel getiri verileri
     */
    List<BondQuoteDto> fetchHistoricalYield(String symbol, LocalDate from, LocalDate to);

    /**
     * Belirli bir enstrümanın güncel verisini çeker.
     * @param symbol Enstrüman sembolü
     * @return Güncel veri varsa
     */
    Optional<BondQuoteDto> fetchBySymbol(String symbol);
}
