# Technical Analysis Module

## Overview

The Technical Analysis module provides basic technical indicators for financial instruments. This is an educational-level implementation designed for learning purposes.

**⚠️ IMPORTANT DISCLAIMER**: This module provides basic technical indicators only and is **NOT investment advice**. All calculations are for educational purposes.

## Supported Indicators

### 1. Simple Moving Average (SMA)

Calculates the average closing price over a specified period.

**Formula**: `SMA(n) = (P1 + P2 + ... + Pn) / n`

Where:
- `P` = closing price
- `n` = period (7, 20, or 50 days)

**Supported Periods**:
- **SMA 7**: Short-term trend (1 week)
- **SMA 20**: Medium-term trend (1 month)
- **SMA 50**: Long-term trend (2.5 months)

### 2. Trend Analysis

Uses linear regression to determine the overall trend direction.

**Method**: Linear regression slope over historical close prices

**Trend Classification**:
- **UPWARD**: Slope > 0.01 (rising trend)
- **DOWNWARD**: Slope < -0.01 (falling trend)
- **SIDEWAYS**: -0.01 ≤ Slope ≤ 0.01 (horizontal trend)
- **INSUFFICIENT_DATA**: Less than 2 data points

**Calculation**:
```
y = mx + b (linear regression)
slope (m) = (n * Σ(xy) - Σx * Σy) / (n * Σ(x²) - (Σx)²)
changePercent = ((lastPrice - firstPrice) / firstPrice) * 100
```

### 3. Summary Statistics

- **Latest Close**: Most recent closing price
- **Highest Close**: Maximum closing price in range
- **Lowest Close**: Minimum closing price in range (support level)
- **Average Close**: Mean closing price
- **Volatility**: Standard deviation as percentage of mean

**Volatility Formula**:
```
stdDev = √(Σ(price - average)² / n)
volatility% = (stdDev / average) * 100
```

## API Endpoints

### Get Technical Analysis

```
GET /api/v1/technical-analysis/{symbol}?from=2026-04-01&to=2026-05-01
```

**Parameters**:
- `symbol` (required): Instrument symbol (e.g., THYAO, AAPL)
- `from` (optional): Start date (ISO format: YYYY-MM-DD). Default: 3 months ago
- `to` (optional): End date (ISO format: YYYY-MM-DD). Default: today

**Response Example**:
```json
{
  "symbol": "THYAO",
  "from": "2026-04-01",
  "to": "2026-05-01",
  "trend": {
    "direction": "UPWARD",
    "slope": 0.42,
    "changePercent": 12.5,
    "description": "Seçilen aralıkta yükselen trend gözlemleniyor."
  },
  "summary": {
    "latestClose": 145.20,
    "highestClose": 151.00,
    "lowestClose": 132.40,
    "averageClose": 141.10,
    "volatilityPercent": 3.2
  },
  "series": [
    {
      "date": "2026-04-01",
      "close": 132.40,
      "sma7": null,
      "sma20": null,
      "sma50": null
    },
    {
      "date": "2026-04-20",
      "close": 140.50,
      "sma7": 138.20,
      "sma20": 136.70,
      "sma50": null
    }
  ]
}
```

### Legacy Endpoints (Backward Compatibility)

```
GET /api/v1/technical-analysis/{symbol}/moving-averages?period=20
GET /api/v1/technical-analysis/{symbol}/trend
GET /api/v1/technical-analysis/{symbol}/support-resistance
GET /api/v1/technical-analysis/{symbol}/momentum?period=14
```

## Frontend Integration

### Component: TechnicalAnalysisPanel

Located in: `frontend/src/components/TechnicalAnalysisPanel.tsx`

**Usage**:
```tsx
<TechnicalAnalysisPanel symbol="THYAO" period="30D" />
```

**Features**:
- Interactive chart with Recharts
- Toggle SMA 7, 20, 50 indicators
- Trend direction card
- Summary statistics cards
- Responsive design
- Dark/light theme support

### Integration in InstrumentChartModal

The technical analysis panel is integrated as a tab in the instrument chart modal:

1. Click on any instrument in the market browser
2. Select "🔍 Teknik Analiz" tab
3. View indicators, trend, and statistics

## Data Source

**Historical Price Data**: 
- Fetched from Yahoo Finance via `YahooPriceFetcher`
- Cached in `historical_prices` table
- Automatically fetched if not available in database

**Database Schema**:
```sql
CREATE TABLE historical_prices (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    price_date DATE NOT NULL,
    close_price DECIMAL(19,6) NOT NULL,
    adjusted_close_price DECIMAL(19,6),
    open_price DECIMAL(19,6),
    high_price DECIMAL(19,6),
    low_price DECIMAL(19,6),
    volume BIGINT,
    UNIQUE(symbol, price_date)
);
```

## Validation Rules

### Backend Validation

1. **Date Range**: `from` must be before `to`
2. **Minimum Data Points**:
   - Trend analysis: 2 points minimum
   - SMA 7: 7 points minimum
   - SMA 20: 20 points minimum
   - SMA 50: 50 points minimum
3. **Insufficient Data**: Returns `INSUFFICIENT_DATA` trend if less than 2 points

### Frontend Behavior

- Shows loading spinner while fetching data
- Displays error message if API fails
- Shows "Yetersiz Veri" message if insufficient data
- Gracefully handles null SMA values (not enough data points)

## Insufficient Data Handling

When there is not enough historical data:

**Backend Response**:
```json
{
  "symbol": "NEWSTOCK",
  "from": "2026-04-01",
  "to": "2026-05-01",
  "trend": {
    "direction": "INSUFFICIENT_DATA",
    "slope": 0.0,
    "changePercent": 0.0,
    "description": "Bu enstrüman için teknik analiz oluşturacak yeterli tarihsel veri bulunamadı."
  },
  "summary": {
    "latestClose": 0.0,
    "highestClose": 0.0,
    "lowestClose": 0.0,
    "averageClose": 0.0,
    "volatilityPercent": 0.0
  },
  "series": []
}
```

**Frontend Display**:
- Shows "📊 Yetersiz Veri" message
- Displays description from backend
- No chart is rendered

## Limitations

1. **Basic Indicators Only**: This is an educational implementation
2. **No Advanced Indicators**: RSI, MACD, Bollinger Bands not implemented
3. **Simple Trend Detection**: Uses linear regression only
4. **No Real-time Updates**: Data is fetched on demand
5. **Limited Historical Data**: Depends on Yahoo Finance availability
6. **No Backtesting**: Cannot test strategies
7. **No Alerts**: No automatic notifications for indicator signals

## Performance Considerations

- **Caching**: Historical prices are cached in database
- **On-Demand Calculation**: Indicators calculated when requested
- **No Pre-computation**: No scheduled indicator updates
- **API Rate Limits**: Yahoo Finance may rate limit requests

## Future Enhancements (Not Implemented)

- RSI (Relative Strength Index)
- MACD (Moving Average Convergence Divergence)
- Bollinger Bands
- Volume analysis
- Candlestick patterns
- Real-time indicator updates
- Indicator-based alerts
- Backtesting framework

## Testing

### Manual Test Steps

1. **Start Services**:
   ```bash
   docker compose up -d
   ```

2. **Open Frontend**: http://localhost

3. **Navigate to Stocks**: Click "Hisse Senetleri" in sidebar

4. **Open Instrument**: Click on any stock (e.g., THYAO)

5. **View Technical Analysis**: Click "🔍 Teknik Analiz" tab

6. **Verify**:
   - ✅ Chart loads with price line
   - ✅ SMA 7, 20, 50 lines appear (if enough data)
   - ✅ Trend card shows direction (Yükselen/Düşen/Yatay)
   - ✅ Summary cards show correct values
   - ✅ Toggles work (hide/show SMA lines)
   - ✅ Period buttons update analysis
   - ✅ Disclaimer is visible

7. **Test API Directly**:
   ```bash
   curl "http://localhost:8080/api/v1/technical-analysis/THYAO?from=2026-04-01&to=2026-05-01"
   ```

### Expected Results

- **THYAO**: Should have sufficient data (Turkish stock)
- **AAPL**: Should have sufficient data (US stock)
- **New/Unknown Symbol**: Should show "INSUFFICIENT_DATA"

## Troubleshooting

### Issue: SMA values are null

**Cause**: Not enough data points for the period

**Solution**: 
- SMA 7 requires at least 7 data points
- SMA 20 requires at least 20 data points
- SMA 50 requires at least 50 data points
- Select a longer date range

### Issue: Trend always shows SIDEWAYS

**Cause**: Slope threshold too high or data is actually sideways

**Solution**: Check the actual price change percentage in the response

### Issue: No historical data found

**Cause**: Symbol not available in Yahoo Finance or network error

**Solution**:
- Verify symbol is correct
- Check backend logs for Yahoo Finance errors
- Try a different symbol

### Issue: Chart does not show moving averages

**Cause**: Frontend toggles are off or data is null

**Solution**:
- Check toggle switches are enabled
- Verify API response contains non-null SMA values
- Check browser console for errors

### Issue: API returns 401/403

**Cause**: Security configuration issue

**Solution**: Technical analysis endpoints should be public (GET only)

## Security

- **Public Access**: GET endpoints are public (no authentication required)
- **Read-Only**: No data modification endpoints
- **Rate Limiting**: Consider adding rate limiting in production
- **Input Validation**: Date ranges are validated
- **SQL Injection**: Protected by JPA/Hibernate

## Disclaimer

**⚠️ IMPORTANT**: This technical analysis module is for **educational purposes only**. 

- Not financial advice
- Not investment recommendations
- No guarantee of accuracy
- Past performance does not indicate future results
- Always consult a licensed financial advisor

---

**Last Updated**: 2026-05-10
**Version**: 1.0.0
**Author**: Finance Portal Team
