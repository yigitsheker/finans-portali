# Tasks: InvestHub Frontend Modernization

## Task 1: Setup Tailwind CSS and Project Dependencies
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** none

### Description
Install and configure Tailwind CSS along with all required dependencies for the modernization project.

### Acceptance Criteria
- [ ] Tailwind CSS installed and configured
- [ ] PostCSS and Autoprefixer installed
- [ ] tailwind.config.js created with dark mode and custom theme
- [ ] index.css updated with Tailwind directives
- [ ] zustand installed for state management
- [ ] react-hot-toast installed for notifications
- [ ] react-sparklines installed for sparkline charts
- [ ] react-window installed for virtual scrolling
- [ ] All dependencies added to package.json
- [ ] Build process works without errors

### Implementation Notes
```bash
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
npm install zustand react-hot-toast react-sparklines react-window
npm install -D @types/react-sparklines
```

---

## Task 2: Create Folder Structure and Base Components
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1

### Description
Set up the new folder structure and create base TypeScript interfaces for components.

### Acceptance Criteria
- [ ] Created `frontend/src/components/common/` directory
- [ ] Created `frontend/src/components/features/` directory with subdirectories (portfolio, market, news)
- [ ] Created `frontend/src/hooks/` directory
- [ ] Created `frontend/src/store/` directory
- [ ] Created `frontend/src/types/` directory
- [ ] Created `frontend/src/utils/` directory
- [ ] Created base TypeScript type definitions in `types/index.ts`
- [ ] Created constants file for animations and colors

### Implementation Notes
Follow the folder structure defined in the design document.

---

## Task 3: Implement Theme Context and Dark Mode
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1

### Description
Create a theme context provider for managing light/dark mode with localStorage persistence.

### Acceptance Criteria
- [ ] Created `contexts/ThemeContext.tsx`
- [ ] Theme state persisted in localStorage
- [ ] Theme toggle function implemented
- [ ] Dark mode class applied to document root
- [ ] ThemeProvider wraps App component
- [ ] useTheme hook exported for components
- [ ] Initial theme loaded from localStorage or defaults to dark

### Implementation Notes
Use React Context API as specified in the design document.

---

## Task 4: Create Badge Component
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Build a reusable Badge component for displaying instrument types with proper styling and variants.

### Acceptance Criteria
- [ ] Created `components/common/Badge.tsx`
- [ ] Supports variants: BIST, CRYPTO, US_STOCK, INDEX, FX, COMMODITY
- [ ] Supports sizes: sm, md, lg
- [ ] Supports outlined variant
- [ ] Uses Tailwind CSS for styling
- [ ] TypeScript interface defined for props
- [ ] Distinct colors for each variant
- [ ] Rounded corners and proper padding

### Implementation Notes
Reference the Badge component specification in the design document.

---

## Task 5: Create Button Component
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Build a reusable Button component with variants, sizes, and loading states.

### Acceptance Criteria
- [ ] Created `components/common/Button.tsx`
- [ ] Supports variants: primary, secondary, danger, ghost
- [ ] Supports sizes: sm, md, lg
- [ ] Loading state with spinner
- [ ] Disabled state
- [ ] Icon support (left and right)
- [ ] Proper hover and focus states
- [ ] Accessibility attributes (aria-label, aria-disabled)

---

## Task 6: Create Card Component
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Build a reusable Card component for wrapping content sections.

### Acceptance Criteria
- [ ] Created `components/common/Card.tsx`
- [ ] Dark mode styling
- [ ] Optional header and footer slots
- [ ] Proper padding and border radius
- [ ] Hover effect (optional)
- [ ] Responsive design

---

## Task 7: Create EmptyState Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 5

### Description
Build a reusable EmptyState component for displaying when no data exists.

### Acceptance Criteria
- [ ] Created `components/common/EmptyState.tsx`
- [ ] Accepts icon, title, description props
- [ ] Optional action button
- [ ] Centered layout
- [ ] Proper spacing and typography
- [ ] Dark mode styling
- [ ] Used in Portfolio, Market, and News pages

### Implementation Notes
Reference the EmptyState component specification in the design document.

---

## Task 8: Create Skeleton Loader Component
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Build reusable Skeleton loader components for loading states.

### Acceptance Criteria
- [ ] Created `components/common/Skeleton.tsx`
- [ ] Shimmer animation effect
- [ ] Variants: text, circle, rectangle
- [ ] Configurable width and height
- [ ] Dark mode styling
- [ ] Smooth transition to actual content

### Implementation Notes
Use Tailwind animation for shimmer effect as defined in tailwind.config.js.

---

## Task 9: Create Table Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2

### Description
Build an enhanced Table component with sticky headers and hover effects.

### Acceptance Criteria
- [ ] Created `components/common/Table.tsx`
- [ ] Sticky header support
- [ ] Row hover effects
- [ ] Proper column alignment (left for text, right for numbers)
- [ ] Responsive horizontal scroll on mobile
- [ ] Dark mode styling
- [ ] TypeScript generic for row data type

---

## Task 10: Create Tooltip Component
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Build a reusable Tooltip component for hover information.

### Acceptance Criteria
- [ ] Created `components/common/Tooltip.tsx`
- [ ] Positioning logic (top, bottom, left, right)
- [ ] Avoids viewport edges
- [ ] 500ms delay before showing
- [ ] Dark background with white text
- [ ] Arrow pointing to target
- [ ] Keyboard accessible

---

## Task 11: Create Sparkline Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 10

### Description
Build a Sparkline component wrapper using react-sparklines library.

### Acceptance Criteria
- [ ] Created `components/common/Sparkline.tsx`
- [ ] Wraps react-sparklines library
- [ ] Supports color variants (green, red, gray)
- [ ] Configurable width and height
- [ ] Tooltip on hover showing exact values
- [ ] Smooth curve interpolation
- [ ] No axes or labels

### Implementation Notes
Reference the Sparkline component specification in the design document.

---

## Task 12: Create Modal Component Enhancement
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Enhance the existing Modal component with backdrop blur, animations, and better accessibility.

### Acceptance Criteria
- [ ] Updated `components/Modal.tsx`
- [ ] Backdrop blur effect
- [ ] Fade and scale animation (200ms entrance, 150ms exit)
- [ ] Closes on Escape key
- [ ] Closes on backdrop click
- [ ] Focus trap within modal
- [ ] Prevents body scrolling when open
- [ ] Rounded corners (12px)
- [ ] Responsive (90% width on mobile)

### Implementation Notes
Reference the Modal component specification in the design document.

---

## Task 13: Create Price Store with Zustand
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2

### Description
Implement Zustand store for managing real-time price updates and flash effects.

### Acceptance Criteria
- [ ] Created `store/priceStore.ts`
- [ ] Stores prices by symbol
- [ ] Tracks flashing cells
- [ ] updatePrice function triggers flash effect
- [ ] Flash effect auto-clears after 800ms
- [ ] TypeScript interfaces defined
- [ ] Exported usePriceStore hook

### Implementation Notes
Reference the price store specification in the design document.

---

## Task 14: Create WebSocket Store with Zustand
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 13

### Description
Implement Zustand store for managing WebSocket connections and subscriptions.

### Acceptance Criteria
- [ ] Created `store/websocketStore.ts`
- [ ] WebSocket connection management
- [ ] Auto-reconnect with exponential backoff
- [ ] Subscribe/unsubscribe to symbols
- [ ] Connection status tracking
- [ ] Integrates with price store for updates
- [ ] Exported useWebSocketStore hook

### Implementation Notes
Reference the WebSocket store specification in the design document. Backend WebSocket endpoint needs to be implemented separately.

---

## Task 15: Create Custom Hooks
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1, Task 2

### Description
Create utility custom hooks for common patterns.

### Acceptance Criteria
- [ ] Created `hooks/useDebounce.ts` for debouncing values
- [ ] Created `hooks/usePrefersReducedMotion.ts` for motion preferences
- [ ] Created `hooks/useMediaQuery.ts` for responsive breakpoints
- [ ] Created `hooks/useLocalStorage.ts` for localStorage persistence
- [ ] All hooks properly typed with TypeScript
- [ ] Unit tests for hooks (optional)

---

## Task 16: Modernize Sidebar Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 3

### Description
Update the Sidebar component with Tailwind CSS, better styling, and responsive behavior.

### Acceptance Criteria
- [ ] Updated `components/Sidebar.tsx` with Tailwind classes
- [ ] Active navigation item highlighted
- [ ] Hover effects on navigation items
- [ ] Icons alongside text labels
- [ ] Collapsible on mobile (hamburger menu)
- [ ] Smooth transitions
- [ ] Dark mode styling
- [ ] Proper spacing and typography

---

## Task 17: Modernize Topbar Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 3, Task 5

### Description
Update the Topbar component with theme toggle button and modern styling.

### Acceptance Criteria
- [ ] Updated `components/Topbar.tsx` with Tailwind classes
- [ ] Theme toggle button added
- [ ] User profile section styled
- [ ] Responsive design
- [ ] Dark mode styling
- [ ] Proper spacing and alignment

---

## Task 18: Modernize Layout Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 1, Task 2, Task 16, Task 17

### Description
Update the Layout component to work with modernized Sidebar and Topbar.

### Acceptance Criteria
- [ ] Updated `components/Layout.tsx` with Tailwind classes
- [ ] Proper grid/flex layout
- [ ] Responsive behavior
- [ ] Dark mode background colors
- [ ] Smooth transitions between pages

---

## Task 19: Add Empty State to Portfolio Page
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 7

### Description
Implement empty state in Portfolio page when no positions exist.

### Acceptance Criteria
- [ ] Updated `pages/Portfolio.tsx`
- [ ] Shows EmptyState when positions.length === 0
- [ ] Appropriate icon and message
- [ ] Action button navigates to market page
- [ ] Loading state shows skeleton loaders

---

## Task 20: Add Sparklines to Portfolio Table
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 9, Task 11, Task 13

### Description
Add sparkline charts to the portfolio position table showing price trends.

### Acceptance Criteria
- [ ] Updated `pages/Portfolio.tsx` position table
- [ ] Sparkline column added
- [ ] Shows last 30 price points
- [ ] Green for positive trend, red for negative
- [ ] Tooltip shows exact values on hover
- [ ] Fetches historical price data for sparklines

---

## Task 21: Implement Portfolio Allocation Pie Chart
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 6

### Description
Create an interactive pie chart showing portfolio allocation by symbol.

### Acceptance Criteria
- [ ] Created `components/features/portfolio/AllocationChart.tsx`
- [ ] Uses Recharts PieChart
- [ ] Shows allocation by symbol
- [ ] Hover effects on slices
- [ ] Tooltip with value and percentage
- [ ] Legend with colors
- [ ] Responsive design
- [ ] Dark mode styling

### Implementation Notes
Reference the pie chart example in the design document.

---

## Task 22: Implement Flash Effects for Price Updates
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 9, Task 13

### Description
Add flash effects to price cells when prices update in real-time.

### Acceptance Criteria
- [ ] Created `components/PriceCell.tsx` or updated table cells
- [ ] Green flash for price increase
- [ ] Red flash for price decrease
- [ ] 800ms fade-out animation
- [ ] Uses Tailwind animation classes
- [ ] Integrates with price store
- [ ] Does not interfere with readability

### Implementation Notes
Reference the PriceCell example in the design document.

---

## Task 23: Implement Sticky Table Headers
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 9

### Description
Make table headers sticky so they remain visible when scrolling.

### Acceptance Criteria
- [ ] Table headers use `sticky top-0` class
- [ ] Headers have proper background color
- [ ] Z-index set correctly
- [ ] Works in Portfolio and Market pages
- [ ] Smooth scrolling behavior

---

## Task 24: Add Skeleton Loaders to Portfolio Page
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 8

### Description
Add skeleton loaders to Portfolio page for loading states.

### Acceptance Criteria
- [ ] Created `components/features/portfolio/PortfolioSkeleton.tsx`
- [ ] Skeleton for summary cards
- [ ] Skeleton for table rows
- [ ] Skeleton for pie chart
- [ ] Shimmer animation
- [ ] Smooth transition to actual content

---

## Task 25: Modernize Market Browser Component
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 4, Task 6, Task 9, Task 11

### Description
Update MarketBrowser component with modern styling, sparklines, and badges.

### Acceptance Criteria
- [ ] Updated `components/MarketBrowser.tsx` with Tailwind classes
- [ ] Badge component used for instrument types
- [ ] Sparklines added to instrument list
- [ ] Card component used for layout
- [ ] Table component used for instrument list
- [ ] Dark mode styling
- [ ] Responsive design

---

## Task 26: Implement Search and Filter Enhancements
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 5, Task 15

### Description
Enhance search and filtering in Market page with debouncing and better UX.

### Acceptance Criteria
- [ ] Search input debounced by 300ms
- [ ] Filter buttons for instrument types
- [ ] Result count displayed
- [ ] Clear filters button
- [ ] Empty state when no results
- [ ] Highlight matching text in results
- [ ] Responsive design

---

## Task 27: Add Empty State to Market Page
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 7

### Description
Implement empty state in Market page when no instruments match filters.

### Acceptance Criteria
- [ ] Shows EmptyState when filtered results are empty
- [ ] Appropriate icon and message
- [ ] Suggestion to clear filters
- [ ] Does not show when loading

---

## Task 28: Add Skeleton Loaders to Market Page
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 8

### Description
Add skeleton loaders to Market page for loading states.

### Acceptance Criteria
- [ ] Created `components/features/market/MarketSkeleton.tsx`
- [ ] Skeleton for instrument cards/rows
- [ ] Shimmer animation
- [ ] Smooth transition to actual content

---

## Task 29: Implement Virtual Scrolling for Long Lists
**Status:** pending
**Assignee:** unassigned
**Priority:** low
**Dependencies:** Task 1, Task 9

### Description
Add virtual scrolling to Market page instrument list for better performance with many items.

### Acceptance Criteria
- [ ] Uses react-window FixedSizeList
- [ ] Applied to instrument list when > 50 items
- [ ] Maintains scroll position
- [ ] Works with search and filters
- [ ] Smooth scrolling

### Implementation Notes
Reference the virtual scrolling example in the design document. This is optional and can be implemented if performance issues arise.

---

## Task 30: Modernize Instrument Chart Modal
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 12

### Description
Update InstrumentChartModal with modern modal styling and animations.

### Acceptance Criteria
- [ ] Updated `components/InstrumentChartModal.tsx`
- [ ] Uses enhanced Modal component
- [ ] Backdrop blur effect
- [ ] Smooth animations
- [ ] Responsive design
- [ ] Dark mode styling

---

## Task 31: Add News Page Empty State
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 7

### Description
Implement empty state in News page when no articles exist.

### Acceptance Criteria
- [ ] Shows EmptyState when no news articles
- [ ] Appropriate icon and message
- [ ] Optional refresh button
- [ ] Loading state shows skeleton loaders

---

## Task 32: Add News Card Skeleton Loaders
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 8

### Description
Add skeleton loaders to News page for loading states.

### Acceptance Criteria
- [ ] Created `components/features/news/NewsSkeleton.tsx`
- [ ] Skeleton for news cards
- [ ] Shimmer animation
- [ ] Smooth transition to actual content

---

## Task 33: Implement Code Splitting and Lazy Loading
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1

### Description
Implement route-based code splitting for better performance.

### Acceptance Criteria
- [ ] Updated `App.tsx` with React.lazy
- [ ] Portfolio page lazy loaded
- [ ] Market page lazy loaded
- [ ] News page lazy loaded
- [ ] Suspense with loading spinner
- [ ] Build produces separate chunks

### Implementation Notes
Reference the code splitting example in the design document.

---

## Task 34: Implement Error Boundary
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 7

### Description
Create an Error Boundary component to catch and display errors gracefully.

### Acceptance Criteria
- [ ] Created `components/ErrorBoundary.tsx`
- [ ] Catches component errors
- [ ] Displays EmptyState with error message
- [ ] Logs errors to console
- [ ] Retry button reloads page
- [ ] Wraps App component

### Implementation Notes
Reference the ErrorBoundary example in the design document.

---

## Task 35: Integrate Toast Notifications
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 1

### Description
Set up react-hot-toast for notifications throughout the app.

### Acceptance Criteria
- [ ] Toaster component added to App.tsx
- [ ] Custom toast styling for dark mode
- [ ] Success toasts for successful actions
- [ ] Error toasts for failed actions
- [ ] Proper positioning (top-right)
- [ ] Auto-dismiss after 3-5 seconds

---

## Task 36: Implement Accessibility Features
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 5, Task 9, Task 10, Task 12

### Description
Ensure all components meet accessibility standards.

### Acceptance Criteria
- [ ] All buttons have aria-labels
- [ ] All form inputs have labels
- [ ] Keyboard navigation works throughout
- [ ] Focus indicators visible
- [ ] ARIA live regions for dynamic content
- [ ] Semantic HTML used
- [ ] Color contrast meets WCAG AA
- [ ] Screen reader tested

---

## Task 37: Implement Reduced Motion Support
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 15

### Description
Respect user's prefers-reduced-motion setting.

### Acceptance Criteria
- [ ] usePrefersReducedMotion hook implemented
- [ ] Animations disabled when prefers-reduced-motion is set
- [ ] Essential animations (flash effects) remain but simplified
- [ ] Tested with browser setting enabled

---

## Task 38: Performance Optimization Pass
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** Task 33

### Description
Optimize application performance with memoization and other techniques.

### Acceptance Criteria
- [ ] React.memo applied to expensive components
- [ ] useMemo used for expensive calculations
- [ ] useCallback used for callback props
- [ ] Lighthouse performance score > 85
- [ ] Bundle size < 500KB gzipped
- [ ] No unnecessary re-renders

---

## Task 39: Responsive Design Testing
**Status:** pending
**Assignee:** unassigned
**Priority:** high
**Dependencies:** Task 16, Task 17, Task 18

### Description
Test and fix responsive design across all breakpoints.

### Acceptance Criteria
- [ ] Tested at 320px (mobile)
- [ ] Tested at 768px (tablet)
- [ ] Tested at 1024px (desktop)
- [ ] Tested at 1440px (large desktop)
- [ ] No horizontal scrolling (except tables)
- [ ] Touch targets at least 44x44px on mobile
- [ ] Readable text at all sizes

---

## Task 40: Final Polish and Bug Fixes
**Status:** pending
**Assignee:** unassigned
**Priority:** medium
**Dependencies:** All previous tasks

### Description
Final testing, polish, and bug fixes before completion.

### Acceptance Criteria
- [ ] All components working correctly
- [ ] No console errors or warnings
- [ ] Smooth animations and transitions
- [ ] Consistent styling throughout
- [ ] Dark mode works perfectly
- [ ] All empty states working
- [ ] All loading states working
- [ ] Cross-browser testing (Chrome, Firefox, Safari, Edge)

---

## Summary

**Total Tasks:** 40
**High Priority:** 16
**Medium Priority:** 20
**Low Priority:** 1

**Estimated Timeline:** 7-8 weeks

**Dependencies Flow:**
1. Setup (Tasks 1-3)
2. Core Components (Tasks 4-12)
3. State Management (Tasks 13-15)
4. Layout Modernization (Tasks 16-18)
5. Portfolio Features (Tasks 19-24)
6. Market Features (Tasks 25-29)
7. Other Pages (Tasks 30-32)
8. Performance & Polish (Tasks 33-40)
