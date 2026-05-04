import React from 'react';

interface CardProps {
  children: React.ReactNode;
  header?: React.ReactNode;
  footer?: React.ReactNode;
  hoverable?: boolean;
  className?: string;
}

export const Card: React.FC<CardProps> = ({
  children,
  header,
  footer,
  hoverable = false,
  className = '',
}) => {
  const baseClasses = 'bg-dark-surface border border-dark-border rounded-lg transition-standard';
  const hoverClasses = hoverable ? 'hover:border-primary-500/50 hover:bg-primary-500/5' : '';
  
  return (
    <div className={`${baseClasses} ${hoverClasses} ${className}`}>
      {header && (
        <div className="px-6 py-4 border-b border-dark-border">
          {header}
        </div>
      )}
      <div className="p-6">
        {children}
      </div>
      {footer && (
        <div className="px-6 py-4 border-t border-dark-border">
          {footer}
        </div>
      )}
    </div>
  );
};
