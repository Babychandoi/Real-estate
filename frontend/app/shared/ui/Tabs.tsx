import React, { useId, useRef, useState } from 'react';
import { cn } from './cn';

export interface TabItem<Id extends string = string> {
  id: Id;
  label: React.ReactNode;
  content: React.ReactNode;
  /** Small count shown after the label, e.g. pending items. */
  count?: number;
  disabled?: boolean;
}

interface TabsProps<Id extends string> {
  /** Names the tab list for screen readers ("Trạng thái tin"). */
  label: string;
  items: ReadonlyArray<TabItem<Id>>;
  /** Controlled selection… */
  value?: Id;
  onChange?: (id: Id) => void;
  /** …or uncontrolled. */
  defaultValue?: Id;
  className?: string;
}

/**
 * WAI-ARIA tabs: arrow keys (and Home/End) move between tabs and activate them, Tab moves into the panel.
 * Only the selected tab is in the tab order.
 */
export function Tabs<Id extends string>({ label, items, value, onChange, defaultValue, className }: TabsProps<Id>) {
  const base = `tabs${useId().replace(/:/g, '')}`;
  const [internal, setInternal] = useState<Id | undefined>(defaultValue ?? items.find((item) => !item.disabled)?.id);
  const selected = value ?? internal;
  const tabRefs = useRef(new Map<Id, HTMLButtonElement>());

  const select = (id: Id) => {
    if (value === undefined) setInternal(id);
    onChange?.(id);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    const enabled = items.map((item, position) => ({ item, position })).filter(({ item }) => !item.disabled);
    const current = enabled.findIndex(({ position }) => position === index);
    let next: number | null = null;
    if (event.key === 'ArrowRight') next = (current + 1) % enabled.length;
    else if (event.key === 'ArrowLeft') next = (current - 1 + enabled.length) % enabled.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = enabled.length - 1;
    if (next == null) return;
    event.preventDefault();
    const target = enabled[next].item;
    select(target.id);
    tabRefs.current.get(target.id)?.focus();
  };

  return (
    <div className={className}>
      <div
        role="tablist"
        aria-label={label}
        className="flex gap-1 overflow-x-auto border-b border-outline-variant no-scrollbar"
      >
        {items.map((item, index) => {
          const isSelected = item.id === selected;
          return (
            <button
              key={item.id}
              ref={(element) => {
                if (element) tabRefs.current.set(item.id, element);
                else tabRefs.current.delete(item.id);
              }}
              type="button"
              role="tab"
              id={`${base}-tab-${item.id}`}
              aria-selected={isSelected}
              aria-controls={`${base}-panel-${item.id}`}
              tabIndex={isSelected ? 0 : -1}
              disabled={item.disabled}
              onClick={() => select(item.id)}
              onKeyDown={(event) => onKeyDown(event, index)}
              className={cn(
                '-mb-px inline-flex min-h-control-md shrink-0 items-center gap-2 border-b-2 px-4 text-body-sm font-semibold transition-colors',
                'disabled:cursor-not-allowed disabled:opacity-50',
                isSelected
                  ? 'border-primary text-primary'
                  : 'border-transparent text-on-surface-variant hover:border-outline hover:text-on-surface',
              )}
            >
              {item.label}
              {item.count != null && (
                <span
                  className={cn(
                    'rounded-pill px-2 py-0.5 text-xs font-semibold',
                    isSelected ? 'bg-primary text-primary-on' : 'bg-surface-container-high text-on-surface-variant',
                  )}
                >
                  {item.count}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {items.map((item) => (
        <div
          key={item.id}
          role="tabpanel"
          id={`${base}-panel-${item.id}`}
          aria-labelledby={`${base}-tab-${item.id}`}
          hidden={item.id !== selected}
          tabIndex={0}
          className="pt-4 focus-visible:outline-offset-2"
        >
          {item.id === selected && item.content}
        </div>
      ))}
    </div>
  );
}
