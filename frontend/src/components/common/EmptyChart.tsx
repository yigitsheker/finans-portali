import React from 'react';
import { Button } from './Button';

interface EmptyChartProps {
  title?: string;
  description?: string;
  onAction?: () => void;
  actionLabel?: string;
}

export const EmptyChart: React.FC<EmptyChartProps> = ({
  title = 'Henüz Veri Yok',
  description = 'İlk işleminizi ekleyerek portföy performansınızı takip etmeye başlayın',
  onAction,
  actionLabel = 'İlk İşlemini Ekle',
}) => {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[300px] py-12">
      {/* SVG Illustration - Empty Wallet/Chart Icon */}
      <svg
        className="w-24 h-24 mb-6 text-text-muted opacity-40"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
        />
      </svg>

      {/* Title */}
      <h3 className="text-lg font-semibold text-text-primary mb-2">
        {title}
      </h3>

      {/* Description */}
      <p className="text-sm text-text-muted text-center max-w-xs mb-6">
        {description}
      </p>

      {/* CTA Button */}
      {onAction && (
        <Button onClick={onAction} variant="primary" size="md">
          <svg
            className="w-5 h-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 6v6m0 0v6m0-6h6m-6 0H6"
            />
          </svg>
          {actionLabel}
        </Button>
      )}
    </div>
  );
};
