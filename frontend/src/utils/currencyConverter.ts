/**
 * Currency conversion utilities
 */

export interface ExchangeRates {
  USDTRY: number;
  EURTRY: number;
  EURUSD: number;
}

/**
 * Convert amount from one currency to another
 */
export const convertCurrency = (
  amount: number,
  fromCurrency: string,
  toCurrency: string,
  rates: ExchangeRates
): number => {
  if (fromCurrency === toCurrency) {
    return amount;
  }

  // Convert to TRY first if needed
  let amountInTRY = amount;
  if (fromCurrency === 'USD') {
    amountInTRY = amount * rates.USDTRY;
  } else if (fromCurrency === 'EUR') {
    amountInTRY = amount * rates.EURTRY;
  }

  // Convert from TRY to target currency
  if (toCurrency === 'TRY') {
    return amountInTRY;
  } else if (toCurrency === 'USD') {
    return amountInTRY / rates.USDTRY;
  } else if (toCurrency === 'EUR') {
    return amountInTRY / rates.EURTRY;
  }

  return amount;
};

/**
 * Convert USD to TRY
 */
export const usdToTry = (amount: number, usdTryRate: number): number => {
  return amount * usdTryRate;
};

/**
 * Convert TRY to USD
 */
export const tryToUsd = (amount: number, usdTryRate: number): number => {
  return amount / usdTryRate;
};

/**
 * Get default exchange rate (fallback)
 */
export const getDefaultExchangeRates = (): ExchangeRates => {
  return {
    USDTRY: 35.0,
    EURTRY: 38.0,
    EURUSD: 1.09,
  };
};

/**
 * Determine if a symbol is USD-based
 */
export const isUsdBased = (symbol: string, type?: string): boolean => {
  // International stocks (not ending with .IS and not 3-5 letter Turkish symbols)
  if (type === 'STOCK' && !symbol.endsWith('.IS') && !symbol.match(/^[A-Z]{3,5}$/)) {
    return true;
  }
  
  // Crypto is usually USD-based
  if (type === 'CRYPTO') {
    return true;
  }
  
  // Check symbol patterns
  if (symbol.includes('USD') && !symbol.includes('TRY')) {
    return true;
  }
  
  return false;
};

/**
 * Determine if a symbol is TRY-based
 */
export const isTryBased = (symbol: string, type?: string): boolean => {
  // BIST stocks
  if (type === 'BIST' || symbol.endsWith('.IS')) {
    return true;
  }
  
  // Turkish symbol pattern
  if (symbol.match(/^[A-Z]{3,5}$/) && !symbol.includes('USD') && !symbol.includes('BTC') && !symbol.includes('ETH')) {
    return true;
  }
  
  // Contains TRY
  if (symbol.includes('TRY') && !symbol.includes('USD')) {
    return true;
  }
  
  return false;
};
