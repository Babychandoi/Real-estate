import { useEffect, useId, useRef, useState } from 'react';
import { Loader2, MapPin, Search, X } from 'lucide-react';
import { geocodePlaces, type GeocodePlace } from '@/shared/api/geocodingApi';
import { IconButton } from '@/shared/ui/IconButton';
import { cn } from '@/shared/ui/cn';
import { MAX_BBOX_SPAN, MAX_KEYWORD_LENGTH, type BBox } from '../filterSchema';

/** Bounding box of a geocoded place, at least ~1 km wide, never wider than the API allows. */
export function placeBbox(place: GeocodePlace): BBox {
  const round = (value: number) => Math.round(value * 100_000) / 100_000;
  if (place.bbox) {
    const [minLat, maxLat, minLon, maxLon] = place.bbox;
    const wide = maxLat - minLat > 0.004 && maxLon - minLon > 0.004;
    if (wide && maxLat - minLat <= MAX_BBOX_SPAN && maxLon - minLon <= MAX_BBOX_SPAN) {
      return [round(minLon), round(minLat), round(maxLon), round(maxLat)];
    }
  }
  const pad = 0.01;
  return [round(place.lon - pad), round(place.lat - pad), round(place.lon + pad), round(place.lat + pad)];
}

type Option = { kind: 'keyword'; text: string } | { kind: 'place'; place: GeocodePlace };

interface SearchBoxProps {
  keyword: string;
  place?: string;
  onKeyword: (keyword: string) => void;
  onPlace: (place: { label: string; bbox: BBox }) => void;
  onClearPlace: () => void;
  /** Read-only while the page cannot search (e.g. while a filter change is being applied). */
  disabled?: boolean;
}

/**
 * Keyword or place (audit §8.4 SearchBox): typing suggests places (geocoding, cancelled when the text changes) and
 * always offers the plain keyword first. A place becomes `bbox` + `place` in the URL; a keyword becomes `q`.
 */
export function SearchBox({ keyword, place, onKeyword, onPlace, onClearPlace, disabled = false }: SearchBoxProps) {
  const [text, setText] = useState(keyword);
  const [openState, setOpen] = useState(false);
  const open = openState && !disabled;
  const [places, setPlaces] = useState<GeocodePlace[]>([]);
  // idle → loading → (suggestions | empty: no place matched | error: provider failed)
  const [status, setStatus] = useState<'idle' | 'loading' | 'empty' | 'error'>('idle');
  const [active, setActive] = useState(0);
  const listId = `places${useId().replace(/:/g, '')}`;
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => setText(keyword), [keyword]);

  useEffect(() => {
    const query = text.trim();
    if (!open || query.length < 3 || query === keyword) {
      setPlaces([]);
      setStatus('idle');
      return;
    }
    setPlaces([]);
    setStatus('loading');
    const abort = new AbortController();
    const timer = window.setTimeout(() => {
      geocodePlaces(query, abort.signal)
        .then((found) => {
          if (abort.signal.aborted) return;
          setPlaces(found.slice(0, 5));
          setStatus(found.length === 0 ? 'empty' : 'idle');
        })
        .catch(() => {
          if (!abort.signal.aborted) setStatus('error');
        });
    }, 350);
    return () => {
      abort.abort();
      window.clearTimeout(timer);
    };
  }, [text, open, keyword]);

  const options: Option[] = [
    ...(text.trim() ? [{ kind: 'keyword' as const, text: text.trim() }] : []),
    ...places.map((item) => ({ kind: 'place' as const, place: item })),
  ];

  const choose = (option: Option) => {
    setOpen(false);
    if (option.kind === 'keyword') onKeyword(option.text);
    else {
      setText('');
      onPlace({ label: option.place.label, bbox: placeBbox(option.place) });
    }
  };

  return (
    <div className="relative flex min-w-0 flex-1 flex-col gap-1">
      <form
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          choose((open ? options[active] : undefined) ?? { kind: 'keyword', text: text.trim() });
        }}
        className="relative"
      >
        <label htmlFor={`${listId}-input`} className="sr-only">
          Tìm theo từ khóa hoặc địa điểm
        </label>
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-on-surface-variant"
          aria-hidden="true"
        />
        <input
          ref={inputRef}
          id={`${listId}-input`}
          type="search"
          role="combobox"
          aria-expanded={open && options.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && options[active] ? `${listId}-${active}` : undefined}
          maxLength={MAX_KEYWORD_LENGTH}
          disabled={disabled}
          value={text}
          placeholder="Từ khóa, dự án hoặc địa điểm (ví dụ: Cầu Giấy)"
          onChange={(event) => {
            setText(event.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              setOpen(true);
              setActive((index) => Math.min(index + 1, Math.max(options.length - 1, 0)));
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              setActive((index) => Math.max(index - 1, 0));
            } else if (event.key === 'Escape') {
              setOpen(false);
            }
          }}
          className="min-h-control-md w-full rounded-input border border-outline bg-surface-container-lowest pl-10 pr-12 text-body text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:cursor-not-allowed disabled:bg-surface-container disabled:text-on-surface-variant"
        />
        <span className="absolute right-1 top-1/2 -translate-y-1/2">
          {status === 'loading' ? (
            <span className="grid h-11 w-11 place-items-center" role="status" aria-label="Đang tìm địa điểm">
              <Loader2 className="h-4 w-4 motion-safe:animate-spin text-on-surface-variant" aria-hidden="true" />
            </span>
          ) : (
            text &&
            !disabled && (
              <IconButton
                aria-label="Xóa ô tìm kiếm"
                icon={X}
                variant="ghost"
                onClick={() => {
                  setText('');
                  if (keyword) onKeyword('');
                  inputRef.current?.focus();
                }}
              />
            )
          )}
        </span>
        {open && (options.length > 0 || status === 'error' || status === 'empty') && (
          <ul
            id={listId}
            role="listbox"
            aria-label="Gợi ý tìm kiếm"
            className="absolute left-0 right-0 top-full z-overlay mt-1 max-h-80 overflow-auto rounded-card border border-outline-variant bg-surface-container-lowest py-1 shadow-elevated"
          >
            {options.map((option, index) => (
              // Keyboard selection is handled by the combobox input (arrow keys + Enter), the ARIA listbox pattern.
              // eslint-disable-next-line jsx-a11y/click-events-have-key-events
              <li
                key={option.kind === 'keyword' ? 'keyword' : `${option.place.label}-${index}`}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={index === active}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(option)}
                className={cn(
                  'flex min-h-11 cursor-pointer items-center gap-2 px-3 text-body-sm text-on-surface',
                  index === active && 'bg-surface-container',
                )}
              >
                {option.kind === 'keyword' ? (
                  <>
                    <Search className="h-4 w-4 shrink-0 text-on-surface-variant" aria-hidden="true" />
                    <span className="min-w-0 [overflow-wrap:anywhere]">
                      Tìm tin có từ khóa “<strong>{option.text}</strong>”
                    </span>
                  </>
                ) : (
                  <>
                    <MapPin className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                    <span className="truncate">Khu vực: {option.place.label}</span>
                  </>
                )}
              </li>
            ))}
            {status === 'error' && (
              <li
                role="option"
                aria-selected={false}
                aria-disabled="true"
                className="px-3 py-2 text-body-sm text-on-surface-variant"
              >
                Chưa tìm được địa điểm lúc này; bạn vẫn có thể tìm theo từ khóa.
              </li>
            )}
            {status === 'empty' && (
              <li
                role="option"
                aria-selected={false}
                aria-disabled="true"
                className="px-3 py-2 text-body-sm text-on-surface-variant [overflow-wrap:anywhere]"
              >
                Không có địa điểm nào khớp “{text.trim()}”; bạn vẫn có thể tìm theo từ khóa.
              </li>
            )}
          </ul>
        )}
      </form>
      {place && (
        <p className="flex items-center gap-2 text-label text-on-surface-variant">
          <MapPin className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="truncate">
            Trong khu vực: <strong className="text-on-surface">{place}</strong>
          </span>
          <button
            type="button"
            onClick={onClearPlace}
            className="min-h-11 rounded-pill px-2 font-semibold text-primary hover:underline"
          >
            Bỏ khu vực
          </button>
        </p>
      )}
    </div>
  );
}
