import { useState } from 'react';

const CLAMP_CLASS = {
  1: 'line-clamp-1',
  2: 'line-clamp-2',
  3: 'line-clamp-3',
  4: 'line-clamp-4',
  5: 'line-clamp-5',
} as const;

/**
 * User-supplied text in a dense row or card: at most `lines` lines (equal-ish row and card heights), breaks anywhere so
 * an unbroken value cannot widen anything, and the full text stays in the DOM (screen readers) and in the tooltip; the
 * detail drawer of the row shows it in full.
 */
export function ClampedText({
  text,
  lines = 2,
  as: Tag = 'p',
  className,
}: {
  text: string;
  lines?: keyof typeof CLAMP_CLASS;
  as?: 'p' | 'span' | 'div' | 'h2' | 'h3';
  className?: string;
}) {
  return (
    <Tag title={text} className={`${CLAMP_CLASS[lines]} [overflow-wrap:anywhere]${className ? ` ${className}` : ''}`}>
      {text}
    </Tag>
  );
}

/**
 * A note or description of any length: clamped to `lines` lines with a "Xem thêm" toggle once it is long, so one
 * 1000 character message cannot make its card or row several screens tall. Whitespace and paragraphs are kept.
 */
export function ExpandableText({
  text,
  lines = 4,
  threshold = 240,
  className,
}: {
  text: string;
  lines?: keyof typeof CLAMP_CLASS;
  /** Shorter texts are shown in full, without a toggle. */
  threshold?: number;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const long = text.length > threshold;
  return (
    <div className={className}>
      <p className={`whitespace-pre-line [overflow-wrap:anywhere]${long && !open ? ` ${CLAMP_CLASS[lines]}` : ''}`}>
        {text}
      </p>
      {long && (
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
          className="mt-1 min-h-6 text-xs font-semibold text-primary underline"
        >
          {open ? 'Thu gọn' : 'Xem thêm'}
        </button>
      )}
    </div>
  );
}
