package com.finansportali.backend.domain;

public enum AlertType {
    /** Fiyat hedef seviyenin üzerine çıktığında tetiklenir */
    PRICE_ABOVE,
    
    /** Fiyat hedef seviyenin altına düştüğünde tetiklenir */
    PRICE_BELOW,
    
    /** Fiyat belirli yüzde kadar yükseldiğinde tetiklenir */
    PERCENT_GAIN,
    
    /** Fiyat belirli yüzde kadar düştüğünde tetiklenir */
    PERCENT_LOSS
}