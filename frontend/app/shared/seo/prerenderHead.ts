/**
 * The prerender layer (backend `/render/**`, S7) injects per-page `<title>`, meta, canonical and JSON-LD into the
 * shell, each marked `data-prerender`; a tag that replaced an `index.html` default keeps that default in
 * `data-default`. They describe the first page only: on the first client-side navigation they go back to the shell
 * defaults (or are removed), and from then on `useDocumentMeta` alone owns the head. Until then they stay, so a
 * crawler that runs JavaScript still sees the page's own metadata on routes that declare none client-side.
 */
export const PRERENDER_ATTRIBUTE = 'data-prerender';
export const PRERENDER_DEFAULT_ATTRIBUTE = 'data-default';

export function resetPrerenderedHead(doc: Document = document): void {
  doc.head.querySelectorAll(`[${PRERENDER_ATTRIBUTE}]`).forEach((element) => {
    const fallback = element.getAttribute(PRERENDER_DEFAULT_ATTRIBUTE);
    if (fallback === null) {
      element.remove();
      return;
    }
    if (element.tagName === 'TITLE') element.textContent = fallback;
    else element.setAttribute('content', fallback);
    element.removeAttribute(PRERENDER_ATTRIBUTE);
    element.removeAttribute(PRERENDER_DEFAULT_ATTRIBUTE);
  });
}

interface RouterLike {
  subscribe(listener: (state: { location: { pathname: string; search: string } }) => void): () => void;
}

/**
 * Resets the prerendered head on the first navigation away from the landing URL. Subscribe before the
 * `RouterProvider` mounts so this runs before React renders the next route (whose metadata then applies on top).
 */
export function resetPrerenderedHeadOnNavigation(router: RouterLike, doc: Document = document): () => void {
  if (!doc.head.querySelector(`[${PRERENDER_ATTRIBUTE}]`)) return () => undefined;
  const landing = `${doc.location?.pathname ?? ''}${doc.location?.search ?? ''}`;
  let done = false;
  const unsubscribe = router.subscribe((state) => {
    if (done || `${state.location.pathname}${state.location.search}` === landing) return;
    done = true;
    resetPrerenderedHead(doc);
    // after the current notification: unsubscribing inside the listener would mutate the set being iterated
    queueMicrotask(unsubscribe);
  });
  return unsubscribe;
}
