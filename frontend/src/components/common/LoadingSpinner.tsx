import React from 'react';
import { SPINNER_SIZES } from '../../types/designTokens';
import type { SpinnerSize } from '../../types/designTokens';

interface LoadingSpinnerProps {
  size?: SpinnerSize;
  className?: string;
  centered?: boolean;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'md',
  className = '',
  centered = false,
}) => {
  const spinner = (
    <div
      role="status"
      aria-label="Đang tải..."
      className={`animate-spin rounded-full border-emerald-200 border-t-emerald-600 ${SPINNER_SIZES[size]} ${className}`}
    />
  );

  if (centered) {
    return <div className="flex justify-center items-center">{spinner}</div>;
  }
  return spinner;
};
