import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, ImageOff, Images, X } from 'lucide-react';
import { IconButton } from '@/shared/ui/IconButton';
import { ResponsiveImage, srcSetOf, type ImageDto } from '@/shared/ui/ResponsiveImage';
import { useModal } from '@/shared/ui/useModal';

interface GalleryProps {
  images: readonly ImageDto[];
  title: string;
}

const altOf = (title: string, index: number, count: number) => `Ảnh ${index + 1}/${count} của ${title}`;

/**
 * Listing photos (F14.1): the first image is the LCP candidate (eager, high priority, srcset/sizes so the original is
 * not downloaded on phones, F14.3), up to four thumbnails, and a lightbox with every photo: counter, arrow keys,
 * Escape, focus trapped and returned to the button that opened it, alt text per photo.
 */
export function Gallery({ images, title }: GalleryProps) {
  const [openAt, setOpenAt] = useState<number | null>(null);
  const count = images.length;
  if (count === 0) {
    return (
      <ResponsiveImage
        image={null}
        alt={`${title}: chưa có ảnh`}
        aspectRatio="16 / 9"
        className="rounded-card"
        emptyLabel="Tin chưa có ảnh"
      />
    );
  }
  const thumbs = images.slice(1, 5);
  return (
    <section aria-label="Ảnh bất động sản" className="flex flex-col gap-2">
      <div className="grid gap-2 md:grid-cols-4 md:grid-rows-2">
        <button
          type="button"
          onClick={() => setOpenAt(0)}
          aria-label={`Mở ảnh 1/${count} cỡ lớn`}
          className="relative overflow-hidden rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary md:col-span-2 md:row-span-2"
        >
          <ResponsiveImage
            image={images[0]}
            alt={altOf(title, 0, count)}
            aspectRatio="16 / 10"
            sizes="(min-width: 1024px) 640px, 100vw"
            priority
          />
        </button>
        {thumbs.map((image, index) => (
          <button
            key={`${image.url}-${index}`}
            type="button"
            onClick={() => setOpenAt(index + 1)}
            aria-label={`Mở ảnh ${index + 2}/${count} cỡ lớn`}
            className="relative hidden overflow-hidden rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary md:block"
          >
            <ResponsiveImage image={image} alt={altOf(title, index + 1, count)} aspectRatio="16 / 10" sizes="320px" />
          </button>
        ))}
      </div>
      {count > 1 && (
        <button
          type="button"
          onClick={() => setOpenAt(0)}
          className="inline-flex min-h-11 w-fit items-center gap-2 rounded-pill border border-outline bg-surface-container-lowest px-4 text-body-sm font-semibold text-on-surface hover:bg-surface-container"
        >
          <Images className="h-4 w-4" aria-hidden="true" />
          Xem tất cả {count} ảnh
        </button>
      )}
      {openAt !== null && <Lightbox images={images} title={title} start={openAt} onClose={() => setOpenAt(null)} />}
    </section>
  );
}

function Lightbox({
  images,
  title,
  start,
  onClose,
}: {
  images: readonly ImageDto[];
  title: string;
  start: number;
  onClose: () => void;
}) {
  const [index, setIndex] = useState(start);
  const panelRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = `lightbox${useId().replace(/:/g, '')}`;
  const count = images.length;
  useModal({ open: true, onClose, panelRef, initialFocusRef: closeRef });
  const previous = () => setIndex((value) => (value - 1 + count) % count);
  const next = () => setIndex((value) => (value + 1) % count);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'ArrowLeft') previous();
      else if (event.key === 'ArrowRight') next();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // previous/next only depend on count
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [count]);

  const image = images[index];
  const srcSet = srcSetOf(image);
  // DS-08 Gallery "error": a photo that fails to load in the viewer says so instead of showing a broken image; the
  // other photos stay reachable with the arrows and thumbnails.
  const [failed, setFailed] = useState<ReadonlySet<string>>(() => new Set());
  const broken = failed.has(image.url);
  return createPortal(
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      tabIndex={-1}
      className="fixed inset-0 z-overlay flex flex-col bg-inverse-surface text-inverse-on-surface focus:outline-none"
    >
      <div className="flex items-center justify-between gap-3 px-4 py-2">
        <h2 id={titleId} className="min-w-0 truncate text-body-sm font-semibold">
          {title}
        </h2>
        <p className="shrink-0 text-body-sm tabular-nums" aria-live="polite">
          Ảnh {index + 1}/{count}
        </p>
        <IconButton
          ref={closeRef}
          aria-label="Đóng xem ảnh"
          icon={X}
          onClick={onClose}
          className="text-inverse-on-surface hover:bg-inverse-on-surface/10 hover:text-inverse-on-surface"
        />
      </div>
      <div className="relative flex min-h-0 flex-1 items-center justify-center px-2 pb-2">
        {broken ? (
          <p role="alert" className="flex flex-col items-center gap-2 text-center text-body-sm">
            <ImageOff className="h-8 w-8" aria-hidden="true" />
            Không tải được ảnh {index + 1}/{count}. Bạn vẫn có thể xem các ảnh khác.
          </p>
        ) : (
          <img
            key={image.url}
            src={image.url}
            srcSet={srcSet}
            sizes={srcSet ? '100vw' : undefined}
            alt={altOf(title, index, count)}
            className="max-h-full max-w-full object-contain"
            decoding="async"
            onError={() => setFailed((previous) => new Set(previous).add(image.url))}
          />
        )}
        {count > 1 && (
          <>
            <IconButton
              aria-label="Ảnh trước"
              icon={ChevronLeft}
              size="lg"
              onClick={previous}
              className="absolute left-2 top-1/2 -translate-y-1/2 rounded-pill bg-inverse-surface/70 text-inverse-on-surface hover:bg-inverse-surface"
            />
            <IconButton
              aria-label="Ảnh sau"
              icon={ChevronRight}
              size="lg"
              onClick={next}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded-pill bg-inverse-surface/70 text-inverse-on-surface hover:bg-inverse-surface"
            />
          </>
        )}
      </div>
      {count > 1 && (
        <ol className="flex gap-2 overflow-x-auto px-4 pb-4" aria-label="Chọn ảnh">
          {images.map((thumb, thumbIndex) => (
            <li key={`${thumb.url}-${thumbIndex}`} className="shrink-0">
              <button
                type="button"
                onClick={() => setIndex(thumbIndex)}
                aria-label={`Xem ảnh ${thumbIndex + 1}/${count}`}
                aria-current={thumbIndex === index ? 'true' : undefined}
                className={`block h-14 w-20 overflow-hidden rounded-input border-2 ${thumbIndex === index ? 'border-primary-fixed' : 'border-transparent opacity-70 hover:opacity-100'}`}
              >
                <img
                  src={thumb.srcset?.[0]?.url ?? thumb.url}
                  alt=""
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              </button>
            </li>
          ))}
        </ol>
      )}
    </div>,
    document.body,
  );
}
