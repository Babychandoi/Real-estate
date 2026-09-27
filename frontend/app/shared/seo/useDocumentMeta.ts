import { useEffect } from 'react';

/**
 * Per-route document metadata (F16.2). A page declares what it needs; everything the hook set is undone when the
 * page unmounts or its metadata changes (e.g. another listing on the same route), so a canonical URL, a robots
 * directive or JSON-LD never leaks into the next route. Tags that already existed in index.html get their
 * original value back; tags the hook created are removed.
 *
 * Only one mounted component should own a given tag at a time (the route page). Prerendering (S7) injects the same
 * fields server-side; this hook keeps client-side navigation consistent with it.
 */
export interface DocumentMeta {
  /** Full document title, e.g. "Căn hộ 2PN … | Nhà Đất Chuẩn". */
  title?: string;
  description?: string;
  /** Absolute URL or a path ("/listings/abc"), resolved against the current origin. */
  canonical?: string;
  /** e.g. "noindex,follow" for thin or not-found pages. */
  robots?: string;
  og?: {
    title?: string;
    description?: string;
    type?: string;
    url?: string;
    image?: string;
  };
  /** Structured data; one `<script type="application/ld+json">` per object. */
  jsonLd?: object | readonly object[] | null;
}

const MARKER = 'data-document-meta';

type Undo = () => void;

function absoluteUrl(value: string, doc: Document): string {
  try {
    return new URL(value, doc.location?.origin ?? undefined).toString();
  } catch {
    return value;
  }
}

function setAttributeTag(
  doc: Document,
  selector: string,
  create: () => HTMLElement,
  attribute: string,
  value: string,
): Undo {
  const existing = doc.head.querySelector<HTMLElement>(selector);
  if (existing) {
    const previous = existing.getAttribute(attribute);
    existing.setAttribute(attribute, value);
    return () => {
      if (previous == null) existing.removeAttribute(attribute);
      else existing.setAttribute(attribute, previous);
    };
  }
  const element = create();
  element.setAttribute(attribute, value);
  element.setAttribute(MARKER, '');
  doc.head.appendChild(element);
  return () => element.remove();
}

function metaTag(doc: Document, key: 'name' | 'property', name: string, content: string): Undo {
  return setAttributeTag(
    doc,
    `meta[${key}="${name}"]`,
    () => {
      const meta = doc.createElement('meta');
      meta.setAttribute(key, name);
      return meta;
    },
    'content',
    content,
  );
}

/** Serialises JSON-LD so that no value can close the surrounding <script> element. */
export function serializeJsonLd(value: object): string {
  return JSON.stringify(value).replace(/</g, '\\u003c');
}

/** Applies `meta` to `doc` and returns a function that restores the previous state. */
export function applyDocumentMeta(meta: DocumentMeta, doc: Document = document): Undo {
  const undo: Undo[] = [];

  if (meta.title) {
    const previousTitle = doc.title;
    doc.title = meta.title;
    undo.push(() => {
      doc.title = previousTitle;
    });
  }
  if (meta.description) undo.push(metaTag(doc, 'name', 'description', meta.description));
  if (meta.robots) undo.push(metaTag(doc, 'name', 'robots', meta.robots));
  if (meta.canonical) {
    undo.push(
      setAttributeTag(
        doc,
        'link[rel="canonical"]',
        () => {
          const link = doc.createElement('link');
          link.rel = 'canonical';
          return link;
        },
        'href',
        absoluteUrl(meta.canonical, doc),
      ),
    );
  }
  const og = meta.og ?? {};
  for (const key of ['title', 'description', 'type', 'url', 'image'] as const) {
    const value = og[key];
    if (value) undo.push(metaTag(doc, 'property', `og:${key}`, key === 'url' ? absoluteUrl(value, doc) : value));
  }
  const structured = meta.jsonLd == null ? [] : Array.isArray(meta.jsonLd) ? meta.jsonLd : [meta.jsonLd];
  for (const item of structured) {
    const script = doc.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute(MARKER, '');
    script.text = serializeJsonLd(item);
    doc.head.appendChild(script);
    undo.push(() => script.remove());
  }

  return () => {
    for (let index = undo.length - 1; index >= 0; index -= 1) undo[index]();
  };
}

/**
 * Declares the metadata of the current route. Pass `null` while data is loading to keep the defaults.
 * The effect re-runs only when the serialised metadata changes, so inline object literals are fine.
 */
export function useDocumentMeta(meta: DocumentMeta | null): void {
  const key = meta ? JSON.stringify(meta) : '';
  useEffect(() => {
    if (!key) return undefined;
    return applyDocumentMeta(JSON.parse(key) as DocumentMeta);
  }, [key]);
}
