import React from "react";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "verified" | "vip" | "neutral" | "error";
  icon?: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = "neutral",
  icon,
  className,
  ...props
}) => {
  const baseStyles =
    "inline-flex items-center gap-1 font-semibold text-xs px-2.5 py-0.5 rounded-full";

  const variants = {
    verified: "bg-emerald-50 text-emerald-800 border border-secondary/20",
    vip: "bg-amber-50 text-amber-900 border border-tertiary/20",
    neutral: "bg-surface-container-high text-on-surface-variant",
    error: "bg-error-container text-error-on-container",
  };

  return (
    <span
      className={twMerge(clsx(baseStyles, variants[variant], className))}
      {...props}
    >
      {icon && <span className="flex-shrink-0 text-xs">{icon}</span>}
      {children}
    </span>
  );
};
