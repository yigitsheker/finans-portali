import React from 'react';

const variantClasses = {
  default: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
  primary: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
  success: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  warning: 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400',
  error: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
};

export const SimpleBadge = ({
  children,
  variant = 'default',
  className = ''
}) => {
  return (
    <span className={`inline-flex items-center text-xs px-2 py-0.5 rounded-md font-medium ${variantClasses[variant]} ${className}`}>
      {children}
    </span>
  );
};
