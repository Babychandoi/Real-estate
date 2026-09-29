import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { engagementApi, type ShortlistSummary } from '@/features/engagement/api';
import { SavedListingsPanel } from '@/features/engagement/ui/SavedListingsPanel';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Tabs } from '@/shared/ui/Tabs';

/** The other two tabs load when opened. */
const ShortlistsPanel = lazy(() =>
  import('@/features/engagement/ui/ShortlistsPanel').then((m) => ({ default: m.ShortlistsPanel })),
);
const SavedSearchesPanel = lazy(() =>
  import('@/features/engagement/ui/SavedSearchesPanel').then((m) => ({ default: m.SavedSearchesPanel })),
);
const panelFallback = <Skeleton className="h-64 rounded-card" />;

type Tab = 'listings' | 'shortlists' | 'searches';
const TABS: readonly Tab[] = ['listings', 'shortlists', 'searches'];

/** `/saved` (audit P-02): saved listings, shared shortlists and saved searches of the account. */
export function SavedPage() {
  useDocumentMeta({ title: 'Tin và tìm kiếm đã lưu | Nhà Đất Chuẩn', robots: 'noindex' });
  const [params, setParams] = useSearchParams();
  const tab = (TABS as readonly string[]).includes(params.get('tab') ?? '') ? (params.get('tab') as Tab) : 'listings';
  const selected = params.get('list');
  const [shortlists, setShortlists] = useState<ShortlistSummary[]>([]);

  const loadShortlists = useCallback(() => {
    engagementApi
      .shortlists()
      .then(setShortlists)
      .catch(() => setShortlists([]));
  }, []);
  useEffect(loadShortlists, [loadShortlists]);

  const go = (next: Partial<{ tab: Tab; list: string | null }>) => {
    const out = new URLSearchParams(params);
    if (next.tab) out.set('tab', next.tab);
    if (next.list !== undefined) {
      if (next.list) out.set('list', next.list);
      else out.delete('list');
    }
    setParams(out, { replace: next.tab === undefined });
  };

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-5 px-4 py-8 sm:px-6 lg:px-8" data-ready="true">
      <header>
        <h1 className="text-headline-md text-on-surface">Đã lưu</h1>
        <p className="text-body-sm text-on-surface-variant">
          Tin bạn đã lưu, danh sách chia sẻ với người thân và các tìm kiếm đang theo dõi.
        </p>
      </header>
      <Tabs<Tab>
        label="Mục đã lưu"
        value={tab}
        onChange={(id) => go({ tab: id })}
        items={[
          { id: 'listings', label: 'Tin đã lưu', content: <SavedListingsPanel shortlists={shortlists} /> },
          {
            id: 'shortlists',
            label: 'Danh sách chia sẻ',
            count: shortlists.length,
            content: tab === 'shortlists' && (
              <Suspense fallback={panelFallback}>
                <ShortlistsPanel
                  shortlists={shortlists}
                  selectedId={selected}
                  onSelect={(id) => go({ list: id })}
                  onChanged={loadShortlists}
                />
              </Suspense>
            ),
          },
          {
            id: 'searches',
            label: 'Tìm kiếm đã lưu',
            content: tab === 'searches' && (
              <Suspense fallback={panelFallback}>
                <SavedSearchesPanel />
              </Suspense>
            ),
          },
        ]}
      />
    </div>
  );
}
