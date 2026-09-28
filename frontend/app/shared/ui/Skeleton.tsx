import React from 'react';
import { cn } from './cn';

/** Placeholder shape while content loads; decorative (announce loading with LoadingStatus). */
export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('block rounded-input bg-surface-container-high motion-safe:animate-pulse', className)}
    />
  );
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <span aria-hidden="true" className={cn('flex flex-col gap-2', className)}>
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton key={index} className={cn('h-4', index === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </span>
  );
}

/** Wraps skeletons: a polite status with a text label so screen readers know something is loading. */
export function LoadingStatus({
  label = 'Đang tải…',
  children,
  className,
}: {
  label?: string;
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}
