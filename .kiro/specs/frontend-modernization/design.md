`
frontend/src/
├── components/
│   ├── common/           # Reusable UI components
│   │   ├── Badge.tsx
│   │   ├── Button.tsx
│   │   ├── Card.tsx
│   │   ├── EmptyState.tsx
│   │   ├── Modal.tsx
│   │   ├── Skeleton.tsx
│   │   ├── Table.tsx
│   │   ├── Tooltip.tsx
│   │   └── Sparkline.tsx
│   ├── features/         # Feature-specific components
│   │   ├── portfolio/
│   │   ├── market/
│   │   └── news/
│   ├── Layout.tsx        # Existing
│   ├── Sidebar.tsx       # Existing
│   ├── Topbar.tsx        # Existing
│   └── ...
├── pages/                # Page components
├── hooks/                # Custom React hooks
├── store/                # Zustand stores
├── utils/                # Utility functions
├── types/                # TypeScript type definitions
└── styles/               # Global styles and Tailwind config
`

### Core Reusable Components

#### Badge Component
`	ypescript
interface BadgeProps {
  variant: 'BIST' | 'CRYPTO' | 'US_STOCK' | 'INDEX' | 'FX' | 'COMMODITY';
  size?: 'sm' | 'md' | 'lg';
  outlined?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({ variant, size = 'md', outlined = false }) => {
  // Implementation with Tailwind classes
};
`

#### EmptyState Component
`	ypescript
interface EmptyStateProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export const EmptyState: React.FC<EmptyStateProps> = ({ icon, title, description, action }) => {
  // Implementation
};
`

#### Sparkline Component
`	ypescript
interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: 'green' | 'red' | 'gray';
  showTooltip?: boolean;
}

export const Sparkline: React.FC<SparklineProps> = ({ data, width = 100, height = 40, color, showTooltip = true }) => {
  // Uses react-sparklines library
};
`

## 4. Styling System with Tailwind CSS

### Installation
`ash
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
`

### Tailwind Configuration
`javascript
// tailwind.config.js
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Dark mode palette
        dark: {
          bg: '#0d1117',
          surface: '#161b22',
          border: '#30363d',
          hover: '#1c2128',
        },
        primary: {
          50: '#eff6ff',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
        },
        success: '#3fb950',
        error: '#f85149',
        warning: '#d29922',
      },
      animation: {
        'flash-green': 'flash-green 800ms ease-out',
        'flash-red': 'flash-red 800ms ease-out',
        'shimmer': 'shimmer 1.5s infinite',
      },
      keyframes: {
        'flash-green': {
          '0%': { backgroundColor: 'rgba(63, 185, 80, 0.3)' },
          '100%': { backgroundColor: 'transparent' },
        },
        'flash-red': {
          '0%': { backgroundColor: 'rgba(248, 81, 73, 0.3)' },
          '100%': { backgroundColor: 'transparent' },
        },
        'shimmer': {
          '0%': { backgroundPosition: '-1000px 0' },
          '100%': { backgroundPosition: '1000px 0' },
        },
      },
    },
  },
  plugins: [],
};
`

### Typography Scale
- **Headings**: text-3xl (30px), text-2xl (24px), text-xl (20px), text-lg (18px)
- **Body**: text-base (16px), text-sm (14px), text-xs (12px)
- **Font**: System font stack for performance

### Spacing System
- Uses Tailwind's default spacing scale (4px base unit)
- Common values: p-2 (8px), p-4 (16px), p-6 (24px), p-8 (32px)

## 5. State Management

### Theme Management (React Context)
`	ypescript
// contexts/ThemeContext.tsx
interface ThemeContextType {
  theme: 'light' | 'dark';
  toggleTheme: () => void;
}

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    return localStorage.getItem('theme') as 'light' | 'dark' || 'dark';
  });

  const toggleTheme = () => {
    const newTheme = theme === 'light' ? 'dark' : 'light';
    setTheme(newTheme);
    localStorage.setItem('theme', newTheme);
    document.documentElement.classList.toggle('dark');
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};
`

### Real-Time Price Updates (Zustand)
`	ypescript
// store/priceStore.ts
interface PriceState {
  prices: Record<string, number>;
  flashingCells: Set<string>;
  updatePrice: (symbol: string, newPrice: number) => void;
  clearFlash: (symbol: string) => void;
}

export const usePriceStore = create<PriceState>((set) => ({
  prices: {},
  flashingCells: new Set(),
  updatePrice: (symbol, newPrice) => set((state) => {
    const oldPrice = state.prices[symbol];
    if (oldPrice !== newPrice) {
      const newFlashing = new Set(state.flashingCells);
      newFlashing.add(symbol);
      setTimeout(() => {
        set((s) => {
          const updated = new Set(s.flashingCells);
          updated.delete(symbol);
          return { flashingCells: updated };
        });
      }, 800);
      return {
        prices: { ...state.prices, [symbol]: newPrice },
        flashingCells: newFlashing,
      };
    }
    return state;
  }),
  clearFlash: (symbol) => set((state) => {
    const updated = new Set(state.flashingCells);
    updated.delete(symbol);
    return { flashingCells: updated };
  }),
}));
`

### WebSocket Connection (Zustand)
`	ypescript
// store/websocketStore.ts
interface WebSocketState {
  ws: WebSocket | null;
  connected: boolean;
  connect: () => void;
  disconnect: () => void;
  subscribe: (symbols: string[]) => void;
}

export const useWebSocketStore = create<WebSocketState>((set, get) => ({
  ws: null,
  connected: false,
  connect: () => {
    const ws = new WebSocket('ws://localhost:8080/ws/prices');
    
    ws.onopen = () => set({ connected: true });
    ws.onclose = () => {
      set({ connected: false });
      // Reconnect with exponential backoff
      setTimeout(() => get().connect(), 5000);
    };
    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      usePriceStore.getState().updatePrice(data.symbol, data.price);
    };
    
    set({ ws });
  },
  disconnect: () => {
    get().ws?.close();
    set({ ws: null, connected: false });
  },
  subscribe: (symbols) => {
    get().ws?.send(JSON.stringify({ action: 'subscribe', symbols }));
  },
}));
`

## 6. Real-Time Updates Architecture

### WebSocket Flow
1. **Connection**: Establish WebSocket on app mount
2. **Subscription**: Subscribe to visible instruments
3. **Updates**: Receive price updates and trigger flash effects
4. **Reconnection**: Auto-reconnect with exponential backoff on disconnect

### Flash Effect Implementation
`	ypescript
// components/PriceCell.tsx
export const PriceCell: React.FC<{ symbol: string; price: number }> = ({ symbol, price }) => {
  const flashingCells = usePriceStore((state) => state.flashingCells);
  const isFlashing = flashingCells.has(symbol);
  const oldPrice = useRef(price);
  
  const flashColor = price > oldPrice.current ? 'animate-flash-green' : 'animate-flash-red';
  
  useEffect(() => {
    oldPrice.current = price;
  }, [price]);
  
  return (
    <td className={isFlashing ? flashColor : ''}>
      {price.toFixed(2)}
    </td>
  );
};
`

## 7. Responsive Design Strategy

### Breakpoints
`	ypescript
// tailwind.config.js breakpoints (default)
sm: '640px'   // Mobile landscape
md: '768px'   // Tablet
lg: '1024px'  // Desktop
xl: '1280px'  // Large desktop
`

### Mobile-First Approach
- Base styles target mobile
- Use md: and lg: prefixes for larger screens
- Example: grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3

### Component Adaptations
- **Sidebar**: Full width drawer on mobile, fixed sidebar on desktop
- **Tables**: Horizontal scroll on mobile, full display on desktop
- **Modals**: Full screen on mobile, centered dialog on desktop
- **Charts**: Responsive width, adjusted height on mobile

## 8. Accessibility Strategy

### Keyboard Navigation
`	ypescript
// Example: Modal with keyboard support
export const Modal: React.FC<ModalProps> = ({ isOpen, onClose, children }) => {
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      // Trap focus within modal
      const focusableElements = modalRef.current?.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      );
      // Focus first element
      (focusableElements?.[0] as HTMLElement)?.focus();
    }
    
    return () => document.removeEventListener('keydown', handleEscape);
  }, [isOpen, onClose]);
  
  // Implementation
};
`

### ARIA Attributes
- Use ria-label for icon-only buttons
- Use ria-live="polite" for price updates
- Use ole="status" for loading states
- Use ria-expanded for collapsible sections

### Focus Management
- Visible focus indicators with ocus:ring-2 focus:ring-primary-500
- Logical tab order
- Focus trap in modals
- Return focus after modal close

## 9. Performance Optimization

### Code Splitting
`	ypescript
// App.tsx
const Portfolio = lazy(() => import('./pages/Portfolio'));
const Market = lazy(() => import('./pages/Market'));
const News = lazy(() => import('./pages/News'));

function App() {
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <Routes>
        <Route path="/portfolio" element={<Portfolio />} />
        <Route path="/market" element={<Market />} />
        <Route path="/news" element={<News />} />
      </Routes>
    </Suspense>
  );
}
`

### Memoization
`	ypescript
// Expensive component
export const PortfolioChart = React.memo(({ data }: { data: ChartData[] }) => {
  return <ResponsiveContainer>...</ResponsiveContainer>;
}, (prevProps, nextProps) => {
  return prevProps.data === nextProps.data;
});

// Expensive calculation
const portfolioValue = useMemo(() => {
  return positions.reduce((sum, pos) => sum + pos.quantity * pos.currentPrice, 0);
}, [positions]);
`

### Debouncing
`	ypescript
// hooks/useDebounce.ts
export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);
  
  useEffect(() => {
    const handler = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(handler);
  }, [value, delay]);
  
  return debouncedValue;
}

// Usage in search
const [searchTerm, setSearchTerm] = useState('');
const debouncedSearch = useDebounce(searchTerm, 300);
`

### Virtual Scrolling
`	ypescript
import { FixedSizeList } from 'react-window';

export const MarketList: React.FC<{ instruments: Instrument[] }> = ({ instruments }) => {
  return (
    <FixedSizeList
      height={600}
      itemCount={instruments.length}
      itemSize={60}
      width="100%"
    >
      {({ index, style }) => (
        <div style={style}>
          <InstrumentRow instrument={instruments[index]} />
        </div>
      )}
    </FixedSizeList>
  );
};
`

## 10. Animation System

### Duration Standards
`	ypescript
// constants/animations.ts
export const ANIMATION_DURATION = {
  MICRO: 150,      // Button hover, checkbox
  STANDARD: 200,   // Modal open, dropdown
  COMPLEX: 300,    // Page transition, complex animation
  FLASH: 800,      // Price flash effect
} as const;
`

### Easing Functions
- **Entrance**: ease-out - starts fast, ends slow
- **Exit**: ease-in - starts slow, ends fast
- **Movement**: ease-in-out - smooth both ends

### Tailwind Animation Classes
`css
/* Add to index.css */
@layer utilities {
  .transition-micro {
    transition-duration: 150ms;
  }
  .transition-standard {
    transition-duration: 200ms;
  }
  .transition-complex {
    transition-duration: 300ms;
  }
}
`

### Reduced Motion Support
`	ypescript
// hooks/usePrefersReducedMotion.ts
export function usePrefersReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReducedMotion(mediaQuery.matches);
    
    const handler = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);
  
  return prefersReducedMotion;
}
`

## 11. Error Handling

### Error Boundary
`	ypescript
// components/ErrorBoundary.tsx
export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error: Error | null }
> {
  state = { hasError: false, error: null };
  
  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }
  
  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('Error caught by boundary:', error, errorInfo);
  }
  
  render() {
    if (this.state.hasError) {
      return (
        <EmptyState
          icon={<AlertIcon />}
          title="Bir hata oluştu"
          description="Sayfa yüklenirken bir sorun oluştu."
          action={{ label: 'Yeniden Dene', onClick: () => window.location.reload() }}
        />
      );
    }
    
    return this.props.children;
  }
}
`

### Toast Notifications
`	ypescript
import toast from 'react-hot-toast';

// Success
toast.success('Pozisyon başarıyla eklendi');

// Error
toast.error('Fiyat güncellenirken hata oluştu');

// Custom
toast.custom((t) => (
  <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
    <p>Özel bildirim</p>
  </div>
));
`

### Inline Error Display
`	ypescript
// components/FormField.tsx
export const FormField: React.FC<FormFieldProps> = ({ error, ...props }) => {
  return (
    <div>
      <input
        className={order }
        {...props}
      />
      {error && (
        <p className="text-error text-sm mt-1">{error}</p>
      )}
    </div>
  );
};
`

## 12. Chart Library Justification

### Recharts (Already Installed)
**Pros:**
- Already in package.json (no additional bundle size)
- Excellent TypeScript support
- Responsive by default
- Good documentation
- Supports pie charts, area charts, line charts

**Cons:**
- Larger bundle size (~100KB gzipped)
- Not ideal for sparklines

**Use for:** Pie charts, portfolio performance charts, detailed instrument charts

### react-sparklines (Recommended Addition)
**Pros:**
- Very lightweight (~5KB gzipped)
- Perfect for inline sparklines
- Simple API
- No dependencies

**Cons:**
- Limited customization
- No TypeScript definitions (need @types)

**Use for:** Inline sparklines in tables and cards

**Installation:**
`ash
npm install react-sparklines
npm install -D @types/react-sparklines
`

## 13. Migration Strategy

### Phase 1: Setup (Week 1)
1. Install Tailwind CSS and configure
2. Install additional libraries (zustand, react-hot-toast, react-sparklines)
3. Create folder structure
4. Set up theme context

### Phase 2: Core Components (Week 2)
1. Build reusable components (Badge, Button, Card, etc.)
2. Create EmptyState component
3. Create Skeleton loaders
4. Build Sparkline wrapper

### Phase 3: Layout & Navigation (Week 3)
1. Modernize Sidebar with Tailwind
2. Modernize Topbar with theme toggle
3. Update Layout component
4. Implement responsive behavior

### Phase 4: Portfolio Page (Week 4)
1. Add empty state
2. Add sparklines to position table
3. Implement pie chart for allocation
4. Add flash effects for price updates
5. Implement sticky table headers

### Phase 5: Market Page (Week 5)
1. Add empty state
2. Add sparklines to instrument list
3. Improve search and filters
4. Add skeleton loaders

### Phase 6: WebSocket Integration (Week 6)
1. Set up WebSocket store
2. Implement connection management
3. Add price subscriptions
4. Implement flash effects

### Phase 7: Polish & Optimization (Week 7)
1. Add animations and transitions
2. Implement virtual scrolling
3. Add accessibility features
4. Performance optimization
5. Testing and bug fixes

## 14. Testing Strategy

### Unit Tests
- Test reusable components in isolation
- Test custom hooks
- Test utility functions

### Integration Tests
- Test page components with mocked API
- Test WebSocket integration
- Test theme switching

### Accessibility Tests
- Keyboard navigation testing
- Screen reader testing
- Color contrast validation

### Performance Tests
- Lighthouse audits
- Bundle size monitoring
- Render performance profiling

## 15. Code Examples

### Example: Portfolio Page with Empty State
`	ypescript
// pages/Portfolio.tsx
export const Portfolio: React.FC = () => {
  const { positions, loading } = usePortfolio();
  
  if (loading) {
    return <PortfolioSkeleton />;
  }
  
  if (positions.length === 0) {
    return (
      <EmptyState
        icon={<PortfolioIcon />}
        title="Portföyünüz boş"
        description="Henüz hiç pozisyon eklemediniz. İlk pozisyonunuzu ekleyerek başlayın."
        action={{
          label: 'Pozisyon Ekle',
          onClick: () => navigate('/market'),
        }}
      />
    );
  }
  
  return (
    <div className="space-y-6">
      <PortfolioSummary positions={positions} />
      <PortfolioAllocationChart positions={positions} />
      <PositionTable positions={positions} />
    </div>
  );
};
`

### Example: Position Table with Sparklines
`	ypescript
// components/features/portfolio/PositionTable.tsx
export const PositionTable: React.FC<{ positions: Position[] }> = ({ positions }) => {
  const prices = usePriceStore((state) => state.prices);
  const flashingCells = usePriceStore((state) => state.flashingCells);
  
  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="sticky top-0 bg-dark-surface">
          <tr>
            <th className="text-left p-4">Sembol</th>
            <th className="text-left p-4">Trend</th>
            <th className="text-right p-4">Fiyat</th>
            <th className="text-right p-4">Miktar</th>
            <th className="text-right p-4">Değer</th>
            <th className="text-right p-4">Kar/Zarar</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((position) => {
            const currentPrice = prices[position.symbol] || position.currentPrice;
            const isFlashing = flashingCells.has(position.symbol);
            const flashColor = currentPrice > position.buyPrice ? 'animate-flash-green' : 'animate-flash-red';
            
            return (
              <tr key={position.id} className="hover:bg-dark-hover transition-colors">
                <td className="p-4">
                  <div className="flex items-center gap-2">
                    <Badge variant={position.instrumentType} />
                    <span className="font-medium">{position.symbol}</span>
                  </div>
                </td>
                <td className="p-4">
                  <Sparkline
                    data={position.priceHistory}
                    width={100}
                    height={40}
                    color={currentPrice > position.buyPrice ? 'green' : 'red'}
                  />
                </td>
                <td className={	ext-right p-4 }>
                  {currentPrice.toFixed(2)} ₺
                </td>
                <td className="text-right p-4">{position.quantity}</td>
                <td className="text-right p-4">
                  {(position.quantity * currentPrice).toFixed(2)} ₺
                </td>
                <td className="text-right p-4">
                  <span className={currentPrice > position.buyPrice ? 'text-success' : 'text-error'}>
                    {((currentPrice - position.buyPrice) / position.buyPrice * 100).toFixed(2)}%
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
`

### Example: Pie Chart for Allocation
`	ypescript
// components/features/portfolio/AllocationChart.tsx
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts';

const COLORS = ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#06b6d4'];

export const AllocationChart: React.FC<{ positions: Position[] }> = ({ positions }) => {
  const data = positions.map((pos, index) => ({
    name: pos.symbol,
    value: pos.quantity * pos.currentPrice,
    color: COLORS[index % COLORS.length],
  }));
  
  return (
    <Card>
      <h3 className="text-xl font-semibold mb-4">Portföy Dağılımı</h3>
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            labelLine={false}
            label={({ name, percent }) => ${name} %}
            outerRadius={80}
            fill="#8884d8"
            dataKey="value"
          >
            {data.map((entry, index) => (
              <Cell key={cell-} fill={entry.color} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value: number) => ${value.toFixed(2)} ₺}
            contentStyle={{
              backgroundColor: '#161b22',
              border: '1px solid #30363d',
              borderRadius: '8px',
            }}
          />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </Card>
  );
};
`

## 16. Performance Targets

- **Initial Load**: < 2 seconds on 3G
- **Lighthouse Score**: > 85
- **First Contentful Paint**: < 1.5 seconds
- **Time to Interactive**: < 3 seconds
- **Bundle Size**: < 500KB (gzipped)
- **Re-render Time**: < 16ms (60fps)

## 17. Accessibility Targets

- **WCAG Level**: AA compliance
- **Keyboard Navigation**: 100% coverage
- **Screen Reader**: Full compatibility
- **Color Contrast**: Minimum 4.5:1 for normal text
- **Focus Indicators**: Visible on all interactive elements

## Conclusion

This design provides a comprehensive blueprint for modernizing the InvestHub frontend. The architecture prioritizes performance, accessibility, and maintainability while delivering a professional user experience. The phased migration strategy allows for incremental implementation without disrupting existing functionality.
