package com.finansportali.backend.domain;

public enum InstrumentType {
    FX,         // Döviz çifti
    CRYPTO,     // Kripto para
    STOCK,      // ABD hisse senedi
    BIST,       // Borsa İstanbul hisse senedi (gecikmeli/EOD)
    COMMODITY,  // Emtia (altın vb.)
    INDEX,      // Endeks
    VIOP,       // VİOP Vadeli İşlem Sözleşmeleri
    BOND,       // Tahvil ve Bono
    FUND        // Yatırım Fonu
}
