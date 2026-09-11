import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  hoverable?: boolean;
}

export const Card: React.FC<CardProps> = ({
  children,
  hoverable = false,
  className,
  ...props
}) => {
  return (
    <div
      className={twMerge(
        clsx(
          'bg-surface-container-lowest rounded-xl border border-outline-variant/50 p-4 transition-all',
          'shadow-[0_2px_8px_-2px_rgba(15,76,129,0.06),0_1px_4px_-1px_rgba(0,0,0,0.04)]',
          hoverable && 'hover:shadow-[0_10px_20px_-4px_rgba(15,76,129,0.10),0_4px_6px_-2px_rgba(0,0,0,0.03)] hover:-translate-y-0.5',
          className
        )
      )}
      {...props}
    >
      {children}
    </div>
  );
};
