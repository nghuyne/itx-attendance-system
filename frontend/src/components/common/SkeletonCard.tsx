import React from 'react';

interface SkeletonCardProps {
  lines?: number;
  showHeader?: boolean;
  className?: string;
}

export const SkeletonCard: React.FC<SkeletonCardProps> = ({
  lines = 3,
  showHeader = true,
  className = '',
}) => (
  <div
    aria-busy="true"
    aria-label="Đang tải nội dung..."
    className={`animate-pulse rounded-lg bg-white shadow p-4 ${className}`}
  >
    {showHeader && <div className="h-4 bg-slate-200 rounded mb-4 w-3/4" />}
    {Array.from({ length: lines }).map((_, i) => (
      <div
        key={i}
        className={`h-3 bg-slate-200 rounded mb-2 ${i === lines - 1 ? 'w-1/2' : 'w-full'}`}
      />
    ))}
  </div>
);
