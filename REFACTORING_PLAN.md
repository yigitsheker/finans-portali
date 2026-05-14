# Finans Portali - Clean Code Refactoring Plan

## Current Issues Identified

### Backend Issues:
1. ❌ Package naming: `api` should be `controller`, `domain` should be `entity`, `repo` should be `repository`
2. ❌ DTOs mixed together - need request/response separation
3. ❌ External API clients mixed in service package
4. ❌ Schedulers mixed with business services
5. ❌ No service interface/implementation separation
6. ❌ GlobalExceptionHandler in api package instead of exception package
7. ❌ No mapper classes - entities exposed directly in some places
8. ❌ Security utilities scattered

### Frontend Issues:
1. ❌ Flat component structure - needs categorization
2. ❌ Duplicate page files (Portfolio.tsx and PortfolioPage.tsx)
3. ❌ Empty hooks folder - custom hooks scattered in components
4. ❌ API files inconsistent naming (news.ts vs portfolioApi.ts)
5. ❌ Large components need splitting
6. ❌ No proper utils for currency/date formatting

## Refactoring Strategy

### Phase 1: Backend Package Restructuring (High Priority)
- [x] Create new package structure
- [ ] Move api → controller
- [ ] Move domain → entity
- [ ] Move repo → repository
- [ ] Separate DTOs into request/response
- [ ] Extract external API clients to service/client
- [ ] Move schedulers to service/scheduler
- [ ] Move GlobalExceptionHandler to exception package
- [ ] Create mapper classes for entity-DTO conversion
- [ ] Update all imports

### Phase 2: Backend Service Layer (Medium Priority)
- [ ] Create service interfaces
- [ ] Move implementations to service/impl
- [ ] Extract business logic from controllers
- [ ] Centralize configuration values

### Phase 3: Frontend Structure (High Priority)
- [ ] Organize components by feature
- [ ] Create custom hooks
- [ ] Standardize API file naming
- [ ] Split large components
- [ ] Create utility functions
- [ ] Remove duplicate files

### Phase 4: Testing & Validation
- [ ] Verify backend compiles
- [ ] Verify frontend builds
- [ ] Test critical user flows
- [ ] Update documentation

## Implementation Notes

Due to the large scope, I'll focus on:
1. Most impactful structural changes
2. Maintaining 100% backward compatibility
3. No business logic changes
4. Automated import updates where possible

## Risk Mitigation
- All changes preserve existing API contracts
- No database schema changes
- No authentication/authorization changes
- Incremental refactoring with validation steps
