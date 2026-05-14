/**
 * Percentage formatting utilities
 */

/**
 * Format number as percentage
 */
export const formatPercentage = (
  value: number,
  decimals: number = 2
): string => {
  return `${value.toFixed(decimals)}%`;
};

/**
 * Format percentage with sign for gains/losses
 */
export const formatPercentageChange = (
  value: number,
  decimals: number = 2
): string => {
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(decimals)}%`;
};

/**
 * Format percentage with arrow indicator
 */
export const formatPercentageWithArrow = (
  value: number,
  decimals: number = 2
): string => {
  const arrow = value >= 0 ? '▲' : '▼';
  const absValue = Math.abs(value);
  return `${arrow} ${absValue.toFixed(decimals)}%`;
};

/**
 * Get color class for percentage value
 */
export const getPercentageColor = (value: number): string => {
  return value >= 0 ? 'var(--green)' : 'var(--red)';
};

/**
 * Parse percentage string to number
 */
export const parsePercentage = (value: string): number => {
  const cleaned = value.replace(/[%+▲▼]/g, '').trim();
  return parseFloat(cleaned) || 0;
};
