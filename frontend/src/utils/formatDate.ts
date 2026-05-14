/**
 * Date formatting utilities
 */

/**
 * Format date to Turkish locale
 */
export const formatDate = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('tr-TR');
};

/**
 * Format date with time
 */
export const formatDateTime = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString('tr-TR');
};

/**
 * Format date to ISO string (YYYY-MM-DD)
 */
export const formatDateISO = (date: Date): string => {
  return date.toISOString().split('T')[0];
};

/**
 * Get relative time (e.g., "2 hours ago")
 */
export const formatRelativeTime = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffSec < 60) return 'Az önce';
  if (diffMin < 60) return `${diffMin} dakika önce`;
  if (diffHour < 24) return `${diffHour} saat önce`;
  if (diffDay < 7) return `${diffDay} gün önce`;
  
  return formatDate(d);
};

/**
 * Check if date is today
 */
export const isToday = (date: string | Date): boolean => {
  const d = typeof date === 'string' ? new Date(date) : date;
  const today = new Date();
  return d.toDateString() === today.toDateString();
};

/**
 * Get date range label
 */
export const getDateRangeLabel = (range: string): string => {
  const labels: Record<string, string> = {
    '1D': 'Bugün',
    '5D': '5 Gün',
    '1M': '1 Ay',
    '3M': '3 Ay',
    '1Y': '1 Yıl',
    'ALL': 'Tümü',
  };
  return labels[range] || range;
};

/**
 * Calculate date from range
 */
export const getDateFromRange = (range: string): Date => {
  const now = new Date();
  const date = new Date(now);

  switch (range) {
    case '1D':
      date.setDate(date.getDate() - 1);
      break;
    case '5D':
      date.setDate(date.getDate() - 5);
      break;
    case '1M':
      date.setMonth(date.getMonth() - 1);
      break;
    case '3M':
      date.setMonth(date.getMonth() - 3);
      break;
    case '1Y':
      date.setFullYear(date.getFullYear() - 1);
      break;
    default:
      date.setFullYear(date.getFullYear() - 10); // ALL
  }

  return date;
};
