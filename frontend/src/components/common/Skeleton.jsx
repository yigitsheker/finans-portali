import React from 'react';

export const Skeleton = ({
  variant = 'text',
  width,
  height,
  className = '',
}) => {
  const baseClasses = 'animate-shimmer bg-gradient-to-r from-dark-surface via-dark-hover to-dark-surface bg-[length:1000px_100%]';

  const variantClasses = {
    text: 'h-4 rounded',
    circle: 'rounded-full',
    rectangle: 'rounded-lg',
  };

  const style = {
    width: width || (variant === 'text' ? '100%' : undefined),
    height: height || (variant === 'circle' ? width : undefined),
  };

  return (
    <div
      className={`${baseClasses} ${variantClasses[variant]} ${className}`}
      style={style}
    />
  );
};

// Convenience components for common patterns
export const SkeletonText = ({
  lines = 1,
  className = ''
}) => {
  return (
    <div className={`space-y-2 ${className}`}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} variant="text" width={i === lines - 1 ? '80%' : '100%'} />
      ))}
    </div>
  );
};

export const SkeletonCard = ({ className = '' }) => {
  return (
    <div className={`bg-dark-surface border border-dark-border rounded-lg p-6 ${className}`}>
      <Skeleton variant="text" width="60%" height={24} className="mb-4" />
      <SkeletonText lines={3} />
    </div>
  );
};
