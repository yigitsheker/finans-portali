# Finans Portali - Clean Code Refactoring Guide

## Executive Summary

This document provides a comprehensive refactoring plan for the Finans Portali project. Due to the large scope (100+ files), a complete immediate refactoring would be high-risk. Instead, this guide provides:

1. **Immediate improvements** (already implemented)
2. **Gradual migration strategy** (recommended approach)
3. **Target architecture** (end goal)

## Current Architecture Analysis

### Backend Structure (Current)
```
com.finansportali.backend/
├── api/                    ❌ Should be 'controller'
├── config/                 ✅ Good
├── domain/                 ❌ Should be 'entity'
├── dto/                    ⚠️  Mixed request/response
├── filter/                 ✅ Good
├── repo/                   ❌ Should be 'repository'
├── service/                ⚠️  Mixed concerns (business + clients + schedulers)
└── util/                   ✅ Good
```

### Frontend Structure (Current)
```
src/
├── api/                    ⚠️  Inconsistent naming
├── components/             ❌ Flat structure, needs organization
├── pages/                  ⚠️  Duplicate files
├── hooks/                  ❌ Empty
├── types/                  ✅ Good
└── utils/                  ⚠️  Incomplete
```

## Target Architecture

### Backend (Target)
```
com.finansportali.backend/
├── controller/             # HTTP layer (renamed from api)
│   ├── MarketController
│   ├── PortfolioController
│   ├── NewsController
│   ├── BondController
│   ├── ExchangeRateController
│   ├── InvestmentFundController
│   ├── PriceAlertController
│   ├── TechnicalAnalysisController
│   └── WatchlistController
│
├── service/                # Business logic interfaces
│   ├── MarketService
│   ├── PortfolioService
│   ├── NewsService
│   ├── DebtInstrumentService
│   ├── ExchangeRateService
│   ├── InvestmentFundService
│   ├── PriceAlertService
│   ├── TechnicalAnalysisService
│   ├── WatchlistService
│   ├── HistoricalPriceService
│   └── NotificationService
│   │
│   ├── impl/               # Service implementations
│   │   ├── MarketServiceImpl
│   │   ├── PortfolioServiceImpl
│   │   └── ...
│   │
│   ├── client/             # External API clients
│   │   ├── YahooFinanceClient
│   │   ├── FinnhubClient
│   │   ├── TwelveDataClient
│   │   ├── TcmbClient
│   │   ├── TefasClient
│   │   └── NewsContentClient
│   │
│   └── scheduler/          # Scheduled tasks
│       ├── PriceRefreshScheduler
│       ├── BondDataRefreshScheduler
│       └── InvestmentFundRefreshScheduler
│
├── repository/             # Data access (renamed from repo)
│   ├── MarketInstrumentRepository
│   ├── MarketQuoteRepository
│   ├── PortfolioPositionRepository
│   ├── NewsArticleRepository
│   ├── DebtInstrumentRepository
│   ├── ExchangeRateRepository
│   ├── InvestmentFundRepository
│   ├── PriceAlertRepository
│   └── WatchlistRepository
│
├── entity/                 # Domain models (renamed from domain)
│   ├── MarketInstrument
│   ├── MarketQuote
│   ├── PortfolioPosition
│   ├── NewsArticle
│   ├── DebtInstrument
│   ├── ExchangeRate
│   ├── InvestmentFund
│   ├── PriceAlert
│   ├── Watchlist
│   └── [enums: InstrumentType, AlertType, etc.]
│
├── dto/
│   ├── request/            # API request DTOs
│   │   ├── UpsertPositionRequest
│   │   ├── SellPositionRequest
│   │   ├── CreateAlertRequest
│   │   ├── CreateWatchlistRequest
│   │   └── AddToWatchlistRequest
│   │
│   └── response/           # API response DTOs
│       ├── MarketSummaryItem
│       ├── MarketHistoryPoint
│       ├── PortfolioSummaryDetail
│       ├── PortfolioPositionDetail
│       ├── PortfolioPerformanceResponse
│       ├── BondListItemDto
│       ├── BondDetailDto
│       ├── AlertView
│       ├── WatchlistDto
│       └── TechnicalAnalysisResponse
│
├── mapper/                 # Entity-DTO conversion
│   ├── MarketMapper
│   ├── PortfolioMapper
│   ├── NewsMapper
│   ├── BondMapper
│   └── AlertMapper
│
├── exception/              # Exception handling
│   ├── GlobalExceptionHandler
│   ├── ResourceNotFoundException
│   ├── BadRequestException
│   ├── ExternalApiException
│   └── UnauthorizedException
│
├── security/               # Security utilities
│   ├── JwtRoleConverter
│   └── CurrentUserProvider
│
├── config/                 # Configuration
│   ├── SecurityConfig
│   ├── CorsConfig
│   ├── CacheConfig
│   ├── ObservabilityConfig
│   ├── LoggingConfig
│   └── DataSeeder
│
├── common/                 # Shared utilities
│   ├── Constants
│   ├── ApiResponse
│   └── ErrorResponse
│
└── util/                   # Helper utilities
    └── CorrelationIdUtil
```

### Frontend (Target)
```
src/
├── app/
│   ├── App.tsx
│   └── router.tsx
│
├── pages/                  # Page components
│   ├── DashboardPage.tsx
│   ├── PortfolioPage.tsx
│   ├── MarketPage.tsx
│   ├── NewsPage.tsx
│   ├── NewsDetailPage.tsx
│   ├── BondsPage.tsx
│   ├── FundsPage.tsx
│   ├── HistoricalComparisonPage.tsx
│   ├── AdminPage.tsx
│   ├── SettingsPage.tsx
│   └── LoginPage.tsx
│
├── components/
│   ├── layout/             # Layout components
│   │   ├── Layout.tsx
│   │   ├── Sidebar.tsx
│   │   ├── Topbar.tsx
│   │   └── Navbar.tsx
│   │
│   ├── common/             # Reusable UI components
│   │   ├── Button.tsx
│   │   ├── Card.tsx
│   │   ├── Modal.tsx
│   │   ├── LoadingSpinner.tsx
│   │   ├── ErrorMessage.tsx
│   │   └── EmptyState.tsx
│   │
│   ├── market/             # Market-related components
│   │   ├── MarketBrowser.tsx
│   │   ├── ModernMarketBrowser.tsx
│   │   ├── FinexStyleMarket.tsx
│   │   ├── InstrumentChartModal.tsx
│   │   ├── CompareInstrumentsModal.tsx
│   │   ├── TechnicalAnalysisPanel.tsx
│   │   └── TradingViewWidget.tsx
│   │
│   ├── portfolio/          # Portfolio components
│   │   ├── PortfolioSummary.tsx
│   │   ├── PortfolioChart.tsx
│   │   ├── PortfolioPositionTable.tsx
│   │   ├── AddPositionModal.tsx
│   │   ├── PriceAlertModal.tsx
│   │   └── WatchlistManager.tsx
│   │
│   ├── news/               # News components
│   │   ├── NewsCard.tsx
│   │   ├── NewsList.tsx
│   │   └── NewsDetail.tsx
│   │
│   ├── bonds/              # Bond components
│   │   ├── BondList.tsx
│   │   ├── BondDetailModal.tsx
│   │   └── BondChart.tsx
│   │
│   └── charts/             # Chart components
│       ├── PortfolioAreaChart.tsx
│       ├── LineChart.tsx
│       └── PieChart.tsx
│
├── api/                    # API layer
│   ├── client.ts           # Axios instance
│   ├── marketApi.ts
│   ├── portfolioApi.ts
│   ├── newsApi.ts
│   ├── bondApi.ts
│   ├── fundApi.ts
│   ├── alertApi.ts
│   └── watchlistApi.ts
│
├── hooks/                  # Custom hooks
│   ├── useAuth.ts
│   ├── useMarket.ts
│   ├── usePortfolio.ts
│   ├── useNews.ts
│   ├── useTheme.ts
│   ├── useCurrency.ts
│   └── useLocalStorage.ts
│
├── types/                  # TypeScript types
│   ├── market.ts
│   ├── portfolio.ts
│   ├── news.ts
│   ├── bond.ts
│   ├── fund.ts
│   ├── alert.ts
│   ├── watchlist.ts
│   └── common.ts
│
├── utils/                  # Utility functions
│   ├── formatCurrency.ts
│   ├── formatDate.ts
│   ├── formatPercentage.ts
│   ├── calculatePortfolio.ts
│   ├── currencyConverter.ts
│   └── constants.ts
│
├── contexts/               # React contexts
│   ├── ThemeContext.tsx
│   └── AuthContext.tsx
│
└── auth/                   # Authentication
    └── keycloak.ts
```

## Immediate Improvements (Implemented)

### ✅ Created Common Package
- `Constants.java` - Centralized constants
- `ApiResponse.java` - Standard response wrapper

### ✅ Created Package Structure
- controller/
- entity/
- repository/
- service/impl/
- service/client/
- service/scheduler/
- dto/request/
- dto/response/
- mapper/
- exception/
- security/
- common/

## Gradual Migration Strategy

### Phase 1: Low-Risk Improvements (Week 1)
**Priority: HIGH | Risk: LOW**

1. **Move Schedulers**
   ```bash
   # Move scheduler classes to service/scheduler/
   PriceRefreshScheduler → service/scheduler/
   BondDataRefreshScheduler → service/scheduler/
   InvestmentFundRefreshScheduler → service/scheduler/
   ```

2. **Move External Clients**
   ```bash
   # Move API clients to service/client/
   YahooPriceFetcher → service/client/YahooFinanceClient
   FinnhubPriceFetcher → service/client/FinnhubClient
   TwelveDataFetcher → service/client/TwelveDataClient
   TcmbBondDataProvider → service/client/TcmbClient
   TefasFundFetcher → service/client/TefasClient
   NewsContentFetcher → service/client/NewsContentClient
   ```

3. **Move Exception Handler**
   ```bash
   # Move to exception package
   api/GlobalExceptionHandler → exception/GlobalExceptionHandler
   ```

4. **Organize DTOs**
   ```bash
   # Separate request/response DTOs
   dto/UpsertPositionRequest → dto/request/
   dto/SellPositionRequest → dto/request/
   dto/CreateAlertRequest → dto/request/
   dto/MarketSummaryItem → dto/response/
   dto/PortfolioSummaryDetail → dto/response/
   # ... etc
   ```

### Phase 2: Package Renaming (Week 2)
**Priority: MEDIUM | Risk: MEDIUM**

1. **Rename api → controller**
   - Update package declarations
   - Update imports across project
   - Test all endpoints

2. **Rename domain → entity**
   - Update package declarations
   - Update imports across project
   - Test database operations

3. **Rename repo → repository**
   - Update package declarations
   - Update imports across project
   - Test data access

### Phase 3: Service Layer Refactoring (Week 3)
**Priority: MEDIUM | Risk: MEDIUM**

1. **Create Service Interfaces**
   ```java
   public interface MarketService {
       MarketSummaryItem getQuote(String symbol);
       List<MarketHistoryPoint> getHistory(String symbol, String period);
       // ...
   }
   ```

2. **Move Implementations**
   ```bash
   service/MarketService → service/impl/MarketServiceImpl
   service/PortfolioService → service/impl/PortfolioServiceImpl
   # ... etc
   ```

3. **Create Mapper Classes**
   ```java
   @Component
   public class PortfolioMapper {
       public PortfolioPositionDetail toDetail(PortfolioPosition entity) {
           // mapping logic
       }
   }
   ```

### Phase 4: Frontend Refactoring (Week 4)
**Priority: MEDIUM | Risk: LOW**

1. **Organize Components**
   - Move to feature folders (market/, portfolio/, news/, etc.)
   - Extract common components
   - Split large components

2. **Create Custom Hooks**
   ```typescript
   // hooks/usePortfolio.ts
   export const usePortfolio = () => {
       const [positions, setPositions] = useState([]);
       const [loading, setLoading] = useState(false);
       // ... logic
       return { positions, loading, refresh };
   };
   ```

3. **Create Utility Functions**
   ```typescript
   // utils/formatCurrency.ts
   export const formatCurrency = (amount: number, currency: string) => {
       const symbol = currency === 'USD' ? '$' : '₺';
       return `${symbol}${amount.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;
   };
   ```

4. **Standardize API Files**
   - Rename `news.ts` → `newsApi.ts`
   - Rename `http.ts` → `client.ts`
   - Consistent export patterns

## Migration Checklist

### Before Each Phase
- [ ] Create feature branch
- [ ] Backup database
- [ ] Document current behavior
- [ ] Write/update tests

### During Migration
- [ ] Move files incrementally
- [ ] Update imports immediately
- [ ] Run tests after each change
- [ ] Commit frequently

### After Each Phase
- [ ] Full regression testing
- [ ] Update documentation
- [ ] Code review
- [ ] Merge to main

## Testing Strategy

### Backend Testing
```bash
# Compile check
mvn clean compile

# Run tests
mvn test

# Integration tests
mvn verify

# Start application
mvn spring-boot:run
```

### Frontend Testing
```bash
# Type check
npm run type-check

# Build check
npm run build

# Start dev server
npm run dev
```

### Manual Testing Checklist
- [ ] Login/Authentication
- [ ] Portfolio CRUD operations
- [ ] Market data display
- [ ] News loading
- [ ] Bond data
- [ ] Investment funds
- [ ] Price alerts
- [ ] Watchlist
- [ ] Historical comparison
- [ ] Charts rendering
- [ ] Currency conversion
- [ ] Dark/Light theme

## Key Principles

### Backend
1. **Single Responsibility**: Each class has one clear purpose
2. **Dependency Injection**: Constructor injection only
3. **Interface Segregation**: Service interfaces separate from implementations
4. **Don't Repeat Yourself**: Extract common logic
5. **Separation of Concerns**: Controllers, services, repositories, clients clearly separated

### Frontend
1. **Component Composition**: Small, focused components
2. **Custom Hooks**: Extract reusable logic
3. **Type Safety**: Proper TypeScript usage
4. **Consistent Patterns**: Same approach across features
5. **Performance**: Memoization, lazy loading where appropriate

## Risk Mitigation

### High-Risk Areas
1. **Authentication/Authorization**: Don't touch unless necessary
2. **Database Migrations**: No schema changes
3. **External API Integrations**: Preserve existing behavior
4. **Currency Calculations**: Critical business logic

### Safety Measures
1. **Incremental Changes**: Small, testable commits
2. **Feature Flags**: If available, use for gradual rollout
3. **Rollback Plan**: Keep old code commented for quick revert
4. **Monitoring**: Watch logs and metrics after deployment

## Success Metrics

### Code Quality
- [ ] Reduced average file size
- [ ] Improved test coverage
- [ ] Fewer code smells (SonarQube)
- [ ] Better maintainability index

### Developer Experience
- [ ] Faster onboarding for new developers
- [ ] Easier to locate code
- [ ] Clearer responsibilities
- [ ] Better IDE navigation

### Performance
- [ ] No regression in response times
- [ ] No increase in bundle size
- [ ] Maintained or improved load times

## Conclusion

This refactoring should be done **gradually** over 4 weeks, not all at once. Each phase should be:
1. Planned carefully
2. Tested thoroughly
3. Reviewed by team
4. Deployed incrementally

The goal is **sustainable improvement**, not a risky big-bang rewrite.

## Next Steps

1. **Review this guide** with the team
2. **Prioritize phases** based on team capacity
3. **Start with Phase 1** (low-risk improvements)
4. **Measure and adjust** based on results

---

**Remember**: Clean code is a journey, not a destination. Continuous improvement is better than perfect refactoring.
