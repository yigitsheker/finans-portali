# Finans Portali - Practical Refactoring Execution Plan

## Current State Analysis

### Backend Issues (Confirmed)
- ✅ PortfolioService.java: **712 lines** - Too large, needs splitting
- ✅ MarketService.java: **785 lines** - Too large, needs splitting
- ✅ External API clients (YahooPriceFetcher, FinnhubPriceFetcher, TwelveDataFetcher) in service package
- ✅ Schedulers (PriceRefreshScheduler, BondDataRefreshScheduler, InvestmentFundRefreshScheduler) in service package
- ✅ Package naming: `api` → should be `controller`, `domain` → should be `entity`, `repo` → should be `repository`
- ✅ DTOs mixed in one package
- ✅ GlobalExceptionHandler in api package
- ✅ Utility classes (CheckDatabase, ResetDatabase, FixFlywayHistory) in main package

### Frontend Issues (Confirmed)
- ✅ Portfolio.tsx: **823 lines** - Too large, needs splitting
- ✅ App.tsx: **142 lines** - Contains routing, layout, theme, alerts
- ✅ Components flat structure
- ✅ No custom hooks for data fetching
- ✅ API files inconsistent naming
- ✅ No utility functions for formatting

## Execution Strategy

### Phase 1: Backend Structure (Low Risk)
1. Create new package structure
2. Move schedulers to `service/scheduler/`
3. Move external clients to `service/client/`
4. Move GlobalExceptionHandler to `exception/`
5. Organize DTOs into `dto/request/` and `dto/response/`
6. Move utility classes to `util/` or mark as test utilities

### Phase 2: Backend Service Splitting (Medium Risk)
1. Split PortfolioService into:
   - PortfolioCommandService (add/update/sell)
   - PortfolioQueryService (fetch positions/summary)
   - PortfolioCalculationService (calculations)
   - PortfolioPerformanceService (performance data)
2. Split MarketService into smaller services
3. Update all imports and dependencies

### Phase 3: Frontend Structure (Low Risk)
1. Create feature-based folder structure
2. Extract utility functions (formatCurrency, formatDate, etc.)
3. Create custom hooks (usePortfolio, useMarket, etc.)
4. Move components to feature folders

### Phase 4: Frontend Component Splitting (Medium Risk)
1. Split Portfolio.tsx into smaller components
2. Extract portfolio hooks and utilities
3. Split other large components
4. Update all imports

### Phase 5: Package Renaming (High Risk - Do Last)
1. Rename `api` → `controller`
2. Rename `domain` → `entity`
3. Rename `repo` → `repository`
4. Update all imports across the project

## Risk Mitigation
- Test after each phase
- Commit after each successful change
- Keep old code commented for quick rollback if needed
- Validate endpoints still work
- Ensure authentication still works

## Success Criteria
- ✅ Backend compiles: `mvn clean install`
- ✅ Frontend builds: `npm run build`
- ✅ All endpoints respond correctly
- ✅ Authentication works
- ✅ Portfolio operations work
- ✅ Market data displays
- ✅ No broken imports
