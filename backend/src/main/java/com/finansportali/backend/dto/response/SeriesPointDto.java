package com.finansportali.backend.dto.response;

/**
 * One day of price + indicators for the technical analysis chart.
 *
 * Fields beyond the moving averages are nullable — they only fill in once
 * the indicator has enough history (RSI needs ~14 days, MACD ~33, BB ~20).
 */
public class SeriesPointDto {

    private String date;
    private Double close;
    // Simple moving averages — bread-and-butter trend overlays.
    private Double sma7;
    private Double sma20;
    private Double sma50;
    // Relative Strength Index (14-period, Wilder smoothing). 0-100 oscillator.
    private Double rsi14;
    // Bollinger Bands (20-period close, ±2σ).
    private Double bbUpper;
    private Double bbMid;
    private Double bbLower;
    // MACD = EMA(12) − EMA(26); signal = EMA(MACD, 9); hist = MACD − signal.
    private Double macd;
    private Double macdSignal;
    private Double macdHist;

    public SeriesPointDto() {}

    public SeriesPointDto(String date, Double close,
                          Double sma7, Double sma20, Double sma50,
                          Double rsi14,
                          Double bbUpper, Double bbMid, Double bbLower,
                          Double macd, Double macdSignal, Double macdHist) {
        this.date = date;
        this.close = close;
        this.sma7 = sma7;
        this.sma20 = sma20;
        this.sma50 = sma50;
        this.rsi14 = rsi14;
        this.bbUpper = bbUpper;
        this.bbMid = bbMid;
        this.bbLower = bbLower;
        this.macd = macd;
        this.macdSignal = macdSignal;
        this.macdHist = macdHist;
    }

    // Back-compat constructor used by older code that only knows about SMAs.
    public SeriesPointDto(String date, Double close, Double sma7, Double sma20, Double sma50) {
        this(date, close, sma7, sma20, sma50, null, null, null, null, null, null, null);
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Double getClose() { return close; }
    public void setClose(Double close) { this.close = close; }
    public Double getSma7() { return sma7; }
    public void setSma7(Double sma7) { this.sma7 = sma7; }
    public Double getSma20() { return sma20; }
    public void setSma20(Double sma20) { this.sma20 = sma20; }
    public Double getSma50() { return sma50; }
    public void setSma50(Double sma50) { this.sma50 = sma50; }
    public Double getRsi14() { return rsi14; }
    public void setRsi14(Double rsi14) { this.rsi14 = rsi14; }
    public Double getBbUpper() { return bbUpper; }
    public void setBbUpper(Double bbUpper) { this.bbUpper = bbUpper; }
    public Double getBbMid() { return bbMid; }
    public void setBbMid(Double bbMid) { this.bbMid = bbMid; }
    public Double getBbLower() { return bbLower; }
    public void setBbLower(Double bbLower) { this.bbLower = bbLower; }
    public Double getMacd() { return macd; }
    public void setMacd(Double macd) { this.macd = macd; }
    public Double getMacdSignal() { return macdSignal; }
    public void setMacdSignal(Double macdSignal) { this.macdSignal = macdSignal; }
    public Double getMacdHist() { return macdHist; }
    public void setMacdHist(Double macdHist) { this.macdHist = macdHist; }
}
