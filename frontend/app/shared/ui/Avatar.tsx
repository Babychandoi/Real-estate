import { useState } from 'react';
import { UserRound } from 'lucide-react';

export function initialsOf(name?: string | null): string {
  return (name ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toLocaleUpperCase('vi-VN');
}

const SIZES = {
  xs: 'h-6 w-6 text-[10px]',
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-12 w-12 text-base',
  xl: 'h-20 w-20 text-lg',
};

/** Profile photo with an initials fallback; also falls back when the image fails to load. */
export function Avatar({
  name,
  src,
  size = 'md',
  className = '',
}: {
  name?: string | null;
  src?: string | null;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  const initials = initialsOf(name);
  return (
    <span
      className={`grid shrink-0 place-items-center overflow-hidden rounded-full bg-primary/10 font-bold text-primary ${SIZES[size]} ${className}`}
    >
      {src && !failed ? (
        <img
          src={src}
          alt={name ? `Ảnh đại diện của ${name}` : 'Ảnh đại diện'}
          className="h-full w-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        initials || <UserRound className="h-1/2 w-1/2" aria-hidden="true" />
      )}
    </span>
  );
}
