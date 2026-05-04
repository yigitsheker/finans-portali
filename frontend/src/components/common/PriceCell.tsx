import React, { useEffect, useRef, useState } from 'react';

interface PriceCellProps {
  value: number;
  previousValue?: number;
  format?: 'currency' | 'percent' | 'number';
  currency?: string;
  decimals?: number;
  className?: string;
}

export const PriceCell: React.FC<PriceCellProps> = ({
  value,
  previousValue,
  format = 'currency',
  currency = '₺',
  decimals = 2,
  className = '',
}) => {
  const [flashClass, setFlashClass] = useState('');
  const prevValueRef = useRef(previousValue);

  useEffect(() => {
    if (prevValueRef.current !== undefined && prevValueRef.current !== value) {
      // Determine flash color based on change direction
      const flashColor = value > prevValueRef.current ? 'animate-flash-green' : 'animate-flash-red';
      setFlashClass(flashColor);

      // Remove flash class after animation completes
      const timer = setTimeout(() => {
        setFlashClass('');
      }, 800);

      return () => clearTimeout(timer);
    }
    prevValueRef.current = value;
  }, [value]);

  const formatValue = () => {
    switch (format) {
      case 'currency':
        return `${value.toFixed(decimals)} ${currency}`;
      case 'percent':
        return `${value >= 0 ? '+' : ''}${value.toFixed(decimals)}%`;
      case 'number':
        return value.toLocaleString('tr-TR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
      default:
        return value.toString();
    }
  };

  const getColorClass = () => {
    if (format === 'percent') {
      return value >= 0 ? 'text-success' : 'text-error';
    }
    return '';
  };

  return (
    <span className={`${flashClass} ${getColorClass()} ${className} transition-colors duration-200`}>
      {formatValue()}
    </span>
  );
};
