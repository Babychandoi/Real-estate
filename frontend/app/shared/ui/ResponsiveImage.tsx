import React, { useState } from 'react';
import { Building2, ImageOff } from 'lucide-react';
import { cn } from './cn';

/** Image of contract §10: original + width variants (WebP 320/640/960/1600 from S1) + dominant colour. */
export interface ImageVariant {
  url: string;
  width: number;
}

export interface ImageDto {
  url: string;
  width?: number | null;
  height?: number | null;
  srcset?: readonly ImageVariant[] | null;
  placeholder?: { dominantColor?: string | null } | null;
}

/** Adapter for legacy string URLs (v1 APIs): no variants yet, so the browser loads the original. */
export function imageFromUrl(url: string | null | undefined): ImageDto | null {
  return url ? { url, width: null, height: null, srcset: [], placeholder: null } : null;
}

export function srcSetOf(image: ImageDto): string | undefined {
  const variants = [...(image.srcset ?? [])].filter((variant) => variant.url && variant.width > 0);
  if (!variants.length) return undefined;
  return variants
    .sort((a, b) => a.width - b.width)
    .map((variant) => `${variant.url} ${variant.width}w`)
    .join(', ');
}

const HEX_COLOR = /^#[0-9a-f]{3,8}$/i;

/** Reserved when neither `aspectRatio` nor a usable `image.width`/`image.height` pair is given (m10): a 4:3 tile is
 * close enough to most listing photos that a fallback/loading tile does not noticeably jump once the real image
 * (or the "missing" placeholder) paints, and it never collapses to zero height. */
const DEFAULT_ASPECT_RATIO = '4 / 3';

interface ResponsiveImageProps {
  image: ImageDto | null | undefined;
  /** Describe the photo ("Phòng khách căn hộ 2PN"); use "" only for purely decorative images. */
  alt: string;
  /** Rendered width per breakpoint, e.g. "(min-width: 1024px) 33vw, 100vw". Required for srcset to help. */
  sizes?: string;
  loading?: 'lazy' | 'eager';
  /** "high" only for the LCP image (hero/first gallery image). */
  priority?: boolean;
  /** CSS aspect ratio reserved before loading, e.g. "16 / 10" (prevents layout shift). */
  aspectRatio?: string;
  className?: string;
  imgClassName?: string;
  /** Shown when there is no image; default: a neutral "Chưa có ảnh" tile. */
  emptyLabel?: string;
}

/**
 * Picture with srcset/sizes, reserved aspect ratio, dominant-colour placeholder and a readable fallback when the
 * image is missing or fails to load.
 */
export function ResponsiveImage({
  image,
  alt,
  sizes,
  loading = 'lazy',
  priority = false,
  aspectRatio,
  className,
  imgClassName,
  emptyLabel = 'Chưa có ảnh',
}: ResponsiveImageProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const failed = Boolean(image && failedUrl === image.url);
  const dominant = image?.placeholder?.dominantColor;
  // Space is always reserved (m10) so a missing/failed image never collapses the tile or shifts surrounding
  // layout: explicit aspectRatio wins, then the image's own intrinsic ratio, then a neutral default.
  const intrinsicRatio =
    image?.width && image?.height && image.width > 0 && image.height > 0 ? `${image.width} / ${image.height}` : null;
  const style: React.CSSProperties = {
    aspectRatio: aspectRatio ?? intrinsicRatio ?? DEFAULT_ASPECT_RATIO,
    backgroundColor: dominant && HEX_COLOR.test(dominant) ? dominant : undefined,
  };
  const srcSet = image ? srcSetOf(image) : undefined;

  return (
    <span className={cn('relative block overflow-hidden bg-surface-container', className)} style={style}>
      {image && !failed ? (
        <img
          src={image.url}
          srcSet={srcSet}
          sizes={srcSet ? (sizes ?? '100vw') : undefined}
          width={image.width ?? undefined}
          height={image.height ?? undefined}
          alt={alt}
          loading={priority ? 'eager' : loading}
          decoding="async"
          {...(priority ? { fetchpriority: 'high' } : {})}
          onError={() => setFailedUrl(image.url)}
          className={cn('h-full w-full object-cover', imgClassName)}
        />
      ) : (
        <span
          role="img"
          aria-label={failed ? `${alt ? `${alt}: ` : ''}không tải được ảnh` : alt || emptyLabel}
          className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-on-surface-variant"
        >
          {failed ? (
            <ImageOff className="h-8 w-8" aria-hidden="true" />
          ) : (
            <Building2 className="h-10 w-10" aria-hidden="true" />
          )}
          <span className="text-label font-normal" aria-hidden="true">
            {failed ? 'Không tải được ảnh' : emptyLabel}
          </span>
        </span>
      )}
    </span>
  );
}
