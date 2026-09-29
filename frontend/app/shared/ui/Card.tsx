import React from 'react';
import { cn } from './cn';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  hoverable?: boolean;
}

/** Surface for grouped content: white, 12 px radius, hairline border and the light card shadow. */
export const Card: React.FC<CardProps> = ({ children, hoverable = false, className, ...props }) => (
  <div
    className={cn(
      'min-w-0 [overflow-wrap:anywhere] bg-surface-container-lowest rounded-xl border border-outline-variant/50 p-4 shadow-card transition-all',
      hoverable && 'hover:shadow-card-hover hover:-translate-y-0.5 motion-reduce:hover:translate-y-0',
      className,
    )}
    {...props}
  >
    {children}
  </div>
);
