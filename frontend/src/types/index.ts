// Common types for the application

export type InstrumentType = 'BIST' | 'CRYPTO' | 'US_STOCK' | 'INDEX' | 'FX' | 'COMMODITY';

export type BadgeVariant = InstrumentType;

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

export type ButtonSize = 'sm' | 'md' | 'lg';

export type BadgeSize = 'sm' | 'md' | 'lg';

export interface Position {
  id: string;
  symbol: string;
  instrumentType: InstrumentType;
  quantity: number;
  buyPrice: number;
  currentPrice: number;
  purchaseDate?: string;
  priceHistory?: number[];
}

export interface MarketInstrument {
  id: string;
  symbol: string;
  name: string;
  instrumentType: InstrumentType;
  currentPrice: number;
  change: number;
  changePercent: number;
  priceHistory?: number[];
}

export interface NewsArticle {
  id: string;
  title: string;
  summary: string;
  source: string;
  publishedAt: string;
  url: string;
  imageUrl?: string;
}

export interface EmptyStateAction {
  label: string;
  onClick: () => void;
}

export type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';
