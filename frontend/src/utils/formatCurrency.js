/**
 * Currency formatting utilities
 */

/**
 * Format a number as currency with proper symbol and locale
 */
export const formatCurrency = (
  amount,
  currency = 'TRY',
  options
) => {
  const symbols = {
    TRY: '₺',
    USD: '$',
    EUR: '€',
  };

  const symbol = symbols[currency];
  const formatted = amount.toLocaleString('tr-TR', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
    ...options,
  });

  return `${symbol}${formatted}`;
};

/**
 * Format currency with sign for gains/losses
 */
export const formatCurrencyChange = (
  amount,
  currency = 'TRY'
) => {
  const sign = amount >= 0 ? '+' : '';
  return `${sign}${formatCurrency(amount, currency)}`;
};

/**
 * Format large numbers with K, M, B suffixes
 */
export const formatCompactCurrency = (
  amount,
  currency = 'TRY'
) => {
  const symbols = {
    TRY: '₺',
    USD: '$',
    EUR: '€',
  };

  const symbol = symbols[currency];

  if (Math.abs(amount) >= 1_000_000_000) {
    return `${symbol}${(amount / 1_000_000_000).toFixed(1)}B`;
  }
  if (Math.abs(amount) >= 1_000_000) {
    return `${symbol}${(amount / 1_000_000).toFixed(1)}M`;
  }
  if (Math.abs(amount) >= 1_000) {
    return `${symbol}${(amount / 1_000).toFixed(1)}K`;
  }

  return formatCurrency(amount, currency);
};

/**
 * Parse currency string to number
 */
export const parseCurrency = (value) => {
  const cleaned = value.replace(/[₺$€,]/g, '').trim();
  return Number.parseFloat(cleaned) || 0;
};

/**
 * Get currency symbol
 */
export const getCurrencySymbol = (currency) => {
  const symbols = {
    TRY: '₺',
    USD: '$',
    EUR: '€',
  };
  return symbols[currency];
};
