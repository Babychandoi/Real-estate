import { useCallback, useEffect, useRef, useState } from 'react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type {
  ListingSummaryV2,
  SearchEngineName,
  SearchResponseV2,
  SearchSuggestion,
} from '@/entities/listing/model/v2';
import { track } from '@/shared/analytics/track';
import { ApiProblemException } from '@/shared/types/problem-details';
import type { ResultTotal } from '@/shared/ui/Pagination';
import { filterHash, PAGE_SIZE, toApiParams, type FilterError, type SearchFilters } from './filterSchema';

export interface SearchState {
  status: 'loading' | 'ready' | 'error';
  items: ListingSummaryV2[];
  hasNext: boolean;
  nextCursor: string | null;
  total: ResultTotal | null;
  engine: SearchEngineName | null;
  degraded: boolean;
  notices: string[];
  suggestions: SearchSuggestion[];
  loadingMore: boolean;
  loadMoreError: string | null;
  /** Invalid filter (400) or unreachable service. */
  error: { message: string; errors: FilterError[] } | null;
  /** The list restarted from page 1 (the cursor was refused); tell the user. */
  restarted: boolean;
  /** Restored from the session snapshot (back from a detail page): the page restores its scroll position. */
  restored: boolean;
  scrollY: number;
}

const EMPTY: SearchState = {
  status: 'loading',
  items: [],
  hasNext: false,
  nextCursor: null,
  total: null,
  engine: null,
  degraded: false,
  notices: [],
  suggestions: [],
  loadingMore: false,
  loadMoreError: null,
  error: null,
  restarted: false,
  restored: false,
  scrollY: 0,
};

/** Loaded pages per history entry, so "back" from a detail page shows the same list at the same place. */
const snapshots = new Map<string, { state: SearchState; savedAt: number }>();
const SNAPSHOT_TTL_MS = 10 * 60 * 1000;
const MAX_SNAPSHOTS = 6;

function remember(key: string, state: SearchState) {
  snapshots.delete(key);
  snapshots.set(key, { state, savedAt: Date.now() });
  while (snapshots.size > MAX_SNAPSHOTS) snapshots.delete(snapshots.keys().next().value!);
}

function problemOf(error: unknown) {
  return error instanceof ApiProblemException
    ? (error.problem as ApiProblemException['problem'] & { errors?: Array<{ param?: string; message: string }> })
    : null;
}

function describe(error: unknown): SearchState['error'] {
  const problem = problemOf(error);
  if (problem?.status === 400 && problem.code === 'INVALID_FILTER') {
    return {
      message: 'Một số điều kiện tìm kiếm không hợp lệ.',
      errors: ((problem.errors ?? []) as unknown as Array<{ param?: string; message: string }>).map((item) => ({
        param: item.param ?? '',
        message: item.message,
      })),
    };
  }
  return { message: 'Không tải được kết quả tìm kiếm. Vui lòng thử lại.', errors: [] };
}

const isCursorRefused = (error: unknown) => {
  const code = problemOf(error)?.code;
  return code === 'CURSOR_ENGINE_CHANGED' || code === 'CURSOR_INVALID';
};

/**
 * Search v2 with cursor paging ("Xem thêm"). A new filter set starts from page 1 (the cursor is never reused); a
 * refused cursor (engine changed, expired) restarts from page 1 and says so. Emits `search_performed` and
 * `search_results_viewed` (contract §5).
 */
export function useListingSearch(filters: SearchFilters, historyKey: string) {
  const apiKey = toApiParams(filters).toString();
  const snapshotKey = `${historyKey}|${apiKey}`;
  const [state, setState] = useState<SearchState>(() => {
    const snapshot = snapshots.get(snapshotKey);
    return snapshot && Date.now() - snapshot.savedAt < SNAPSHOT_TTL_MS
      ? { ...snapshot.state, restored: true, loadingMore: false }
      : EMPTY;
  });
  const stateRef = useRef(state);
  stateRef.current = state;
  /** The snapshot the initial state came from: that request is not repeated on mount. */
  const restoredKey = useRef<string | null>(state.restored ? snapshotKey : null);
  const filtersRef = useRef(filters);
  filtersRef.current = filters;
  const controller = useRef<AbortController | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const applyFirstPage = useCallback((page: SearchResponseV2, restarted: boolean) => {
    setState({
      ...EMPTY,
      status: 'ready',
      items: page.items,
      hasNext: page.pageInfo.hasNext,
      nextCursor: page.pageInfo.nextCursor,
      total: page.total,
      engine: page.engine,
      degraded: page.degraded,
      notices: page.notices,
      suggestions: page.suggestions ?? [],
      restarted,
    });
    const current = filtersRef.current;
    void filterHash(current).then((hash) => {
      track('search_performed', {
        filterHash: hash,
        purpose: current.purpose,
        resultCount: page.total?.value ?? null,
        zeroResult: page.items.length === 0,
        engine: page.engine,
        hasBbox: Boolean(current.bbox),
        hasKeyword: Boolean(current.q),
      });
      if (page.items.length) {
        track('search_results_viewed', { filterHash: hash, listingIds: page.items.map((item) => item.id), offset: 0 });
      }
    });
  }, []);

  const loadFirstPage = useCallback(
    async (restarted: boolean) => {
      controller.current?.abort();
      const abort = new AbortController();
      controller.current = abort;
      setState((previous) => ({ ...EMPTY, items: restarted ? [] : previous.items, status: 'loading' }));
      try {
        const page = await listingV2Api.search(toApiParams(filtersRef.current, { size: PAGE_SIZE }), abort.signal);
        if (!abort.signal.aborted) applyFirstPage(page, restarted);
      } catch (error) {
        if (abort.signal.aborted) return;
        setState({ ...EMPTY, status: 'error', error: describe(error) });
      }
    },
    [applyFirstPage],
  );

  useEffect(() => {
    if (restoredKey.current === snapshotKey && reloadToken === 0) {
      restoredKey.current = null;
      return;
    }
    restoredKey.current = null;
    void loadFirstPage(false);
    return () => controller.current?.abort();
    // apiKey identifies the request; the filters themselves are read through filtersRef.
  }, [apiKey, snapshotKey, reloadToken, loadFirstPage]);

  useEffect(() => {
    if (state.status === 'ready') remember(snapshotKey, state);
  }, [snapshotKey, state]);

  const loadMore = useCallback(async () => {
    const current = stateRef.current;
    if (!current.nextCursor || current.loadingMore) return;
    const cursor = current.nextCursor;
    const requestKey = toApiParams(filtersRef.current).toString();
    const abort = new AbortController();
    controller.current = abort;
    setState((previous) => ({ ...previous, loadingMore: true, loadMoreError: null }));
    try {
      const page = await listingV2Api.search(
        toApiParams(filtersRef.current, { size: PAGE_SIZE, cursor }),
        abort.signal,
      );
      if (abort.signal.aborted || requestKey !== toApiParams(filtersRef.current).toString()) return;
      setState((previous) => {
        if (previous.status !== 'ready' || previous.nextCursor !== cursor) return previous;
        const seen = new Set(previous.items.map((item) => item.id));
        const newItems = page.items.filter((item) => {
          if (seen.has(item.id)) return false;
          seen.add(item.id);
          return true;
        });
        return {
          ...previous,
          items: [...previous.items, ...newItems],
          hasNext: page.pageInfo.hasNext,
          nextCursor: page.pageInfo.nextCursor,
          engine: page.engine,
          degraded: page.degraded,
          notices: page.notices,
          loadingMore: false,
        };
      });
      const offset = current.items.length;
      void filterHash(filtersRef.current).then((hash) => {
        if (page.items.length) {
          track('search_results_viewed', { filterHash: hash, listingIds: page.items.map((item) => item.id), offset });
        }
      });
    } catch (error) {
      if (abort.signal.aborted || requestKey !== toApiParams(filtersRef.current).toString()) return;
      if (isCursorRefused(error)) {
        await loadFirstPage(true);
        return;
      }
      setState((previous) => ({
        ...previous,
        loadingMore: false,
        loadMoreError: 'Không tải thêm được. Vui lòng thử lại.',
      }));
    }
  }, [loadFirstPage]);

  const retry = useCallback(() => setReloadToken((token) => token + 1), []);

  /** Remember where the visitor was on the page before leaving it (restored on "back"). */
  const saveScroll = useCallback(
    (scrollY: number) => {
      const current = stateRef.current;
      if (current.status === 'ready') remember(snapshotKey, { ...current, scrollY });
    },
    [snapshotKey],
  );

  return { state, loadMore, retry, saveScroll };
}
