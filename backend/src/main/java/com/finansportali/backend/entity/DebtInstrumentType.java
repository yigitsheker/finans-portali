package com.finansportali.backend.entity;

/**
 * Borçlanma aracı türleri.
 * Türkiye finans terminolojisine göre:
 * - Tahvil: Genellikle 1 yıldan uzun vadeli borçlanma senedi
 * - Bono: Genellikle 1 yıla kadar kısa vadeli borçlanma senedi
 * - DİBS: Devlet İç Borçlanma Senetleri (tahvil ve bono içerir)
 */
public enum DebtInstrumentType {
    /** Devlet tahvili (1 yıldan uzun vadeli) */
    GOVERNMENT_BOND,
    
    /** Hazine bonosu (1 yıla kadar kısa vadeli) */
    TREASURY_BILL,
    
    /** Kira sertifikası (İslami finans aracı) */
    LEASE_CERTIFICATE,
    
    /** Eurobond (döviz cinsinden devlet tahvili) */
    EUROBOND,
    
    /** Özel sektör tahvili */
    CORPORATE_BOND,
    
    /** Diğer borçlanma araçları */
    OTHER
}
