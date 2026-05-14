# Phase 5: Split MarketService - Summary

## Overview

Phase 5 is complete. The large MarketService has been split into four focused market services while MarketService remains as the backward-compatible facade used by existing controllers and services.

## Services

- MarketInstrumentService: instrument listing, lookup, search, and Yahoo symbol normalization.
- MarketPriceService: current prices, latest price lookup, and market summary caching.
- MarketHistoryService: historical chart data, Yahoo history fetching, data cleaning, and database fallback.
- MarketDataSeedService: startup seed data for instruments, fallback quotes, and fallback candles.

## MarketService Role

MarketService now coordinates calls only:
- getCurrentPrice() delegates to MarketPriceService.
- getInstrumentBySymbol(), instruments(), searchInstruments(), and normalizeSymbolForYahoo() delegate to MarketInstrumentService.
- latestPrice() and summary() delegate to MarketPriceService.
- history() delegates to MarketHistoryService.
- seedIfEmpty() delegates to MarketDataSeedService.

## Design Decision

The seed list stays together inside MarketDataSeedService because it is declarative initialization data. Splitting individual seed rows into smaller abstractions would add indirection without improving the business logic.

## Files Changed

Created:
- backend/src/main/java/com/finansportali/backend/service/market/MarketDataSeedService.java

Already present from the earlier Phase 5 work:
- backend/src/main/java/com/finansportali/backend/service/market/MarketInstrumentService.java
- backend/src/main/java/com/finansportali/backend/service/market/MarketPriceService.java
- backend/src/main/java/com/finansportali/backend/service/market/MarketHistoryService.java

Modified:
- backend/src/main/java/com/finansportali/backend/service/MarketService.java

## Validation

- Backend compile passed with Maven using the local dependency cache.
- clean compile could not run because Maven could not delete backend/target due an AccessDeniedException on the target directory.

## Status

Phase 5 is complete. Next phase: Phase 6 - split large frontend components.