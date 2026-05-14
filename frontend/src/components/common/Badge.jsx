import React from 'react';
import { BADGE_COLORS } from '../../utils/constants';

const sizeClasses = {
  sm: 'text-xs px-2 py-0.5',
  md: 'text-sm px-2.5 py-1',
  lg: 'text-base px-3 py-1.5',
};

export const Badge = ({
  variant,
  size = 'md',
  outlined = false,
  className = '',
}) => {
  const colors = BADGE_COLORS[variant];

  const baseClasses = 'inline-flex items-center justify-center rounded font-medium transition-colors';
  const variantClasses = outlined
    ? `border ${colors.border} ${colors.text} bg-transparent`
    : `${colors.bg} ${colors.text}`;

  return (
    <span className={`${baseClasses} ${sizeClasses[size]} ${variantClasses} ${className}`}>
      {variant}
    </span>
  );
};
