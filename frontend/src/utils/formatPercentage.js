/**
 * Percentage formatting utilities
 */

/**
 * Format number as percentage
 */
export const formatPercentage = (
  value,
  decimals = 2
) => {
  return `${value.toFixed(decimals)}%`;
};

/**
 * Format percentage with sign for gains/losses
 */
export const formatPercentageChange = (
  value,
  decimals = 2
) => {
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(decimals)}%`;
};

/**
 * Format percentage with arrow indicator
 */
export const formatPercentageWithArrow = (
  value,
  decimals = 2
) => {
  const arrow = value >= 0 ? '▲' : '▼';
  const absValue = Math.abs(value);
  return `${arrow} ${absValue.toFixed(decimals)}%`;
};

/**
 * Get color class for percentage value
 */
export const getPercentageColor = (value) => {
  return value >= 0 ? 'var(--green)' : 'var(--red)';
};

/**
 * Parse percentage string to number
 */
export const parsePercentage = (value) => {
  const cleaned = value.replace(/[%+▲▼]/g, '').trim();
  return Number.parseFloat(cleaned) || 0;
};
