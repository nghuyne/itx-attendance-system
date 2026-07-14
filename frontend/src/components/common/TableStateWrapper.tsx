import React from 'react';
import { SkeletonCard } from './SkeletonCard';

interface TableStateWrapperProps {
  isLoading: boolean;
  isError: boolean;
  isEmpty: boolean;
  errorMessage: string;
  emptyTitle: string;
  emptySubtitle?: string;
  skeletonCount?: number;
  children: React.ReactNode;
}

export const TableStateWrapper: React.FC<TableStateWrapperProps> = ({
  isLoading,
  isError,
  isEmpty,
  errorMessage,
  emptyTitle,
  emptySubtitle,
  skeletonCount = 3,
  children,
}) => {
  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: skeletonCount }, (_, i) => (
          <SkeletonCard key={i} />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
        {errorMessage}
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="text-center py-12 text-slate-500">
        <p className="text-lg">{emptyTitle}</p>
        {emptySubtitle && <p className="text-sm mt-1">{emptySubtitle}</p>}
      </div>
    );
  }

  return <>{children}</>;
};
