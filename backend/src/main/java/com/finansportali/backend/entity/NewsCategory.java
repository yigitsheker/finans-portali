package com.finansportali.backend.entity;

public enum NewsCategory {
    GENEL_EKONOMI("genel-ekonomi", "Genel Ekonomi"),
    HISSE("hisse", "Hisse Senetleri"),
    DOVIZ("doviz", "Döviz"),
    TAHVIL("tahvil", "Tahvil & Bono"),
    KRIPTO("kripto", "Kripto Para"),
    EMTIA("emtia", "Emtia"),
    FONDS("fonlar", "Yatırım Fonları"),
    BORSA("borsa", "Borsa Haberleri"),
    TCMB("tcmb", "TCMB Kararları"),
    ULUSLARARASI("uluslararasi", "Uluslararası Piyasalar");

    private final String code;
    private final String displayName;

    NewsCategory(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static NewsCategory fromCode(String code) {
        for (NewsCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return GENEL_EKONOMI; // Default
    }
}