import { useCallback, useEffect, useState } from 'react';
import { SearchX } from 'lucide-react';
import { Badge } from '@/shared/ui/Badge';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Select } from '@/shared/ui/Select';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Switch } from '@/shared/ui/Switch';
import { useToast } from '@/shared/ui/Toast';
import {
  engagementApi,
  FREQUENCY_LABELS,
  problemCode,
  problemDetail,
  type AlertFrequency,
  type SavedSearch,
  type SavedSearchSettings,
} from '../api';

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });

/** Saved searches: open, frequency, alert kinds, pause, delete (P-02). */
export function SavedSearchesPanel() {
  const toast = useToast();
  const [items, setItems] = useState<SavedSearch[]>([]);
  const [limit, setLimit] = useState(20);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [deleting, setDeleting] = useState<SavedSearch | null>(null);

  const load = useCallback(async () => {
    setStatus('loading');
    try {
      const result = await engagementApi.savedSearches();
      setItems(result.items);
      setLimit(result.limit);
      setStatus('ready');
    } catch {
      setStatus('error');
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  const update = async (search: SavedSearch, settings: SavedSearchSettings) => {
    try {
      const next = await engagementApi.updateSavedSearch(search.id, search.version, settings);
      setItems((current) =>
        current.map((item) => (item.id === search.id ? { ...next, pendingMatches: item.pendingMatches } : item)),
      );
    } catch (error) {
      if (problemCode(error) === 'SAVED_SEARCH_VERSION_CONFLICT') {
        toast.show({
          kind: 'warning',
          title: 'Tìm kiếm vừa được đổi ở nơi khác',
          description: 'Đã tải lại danh sách.',
        });
        void load();
      } else {
        toast.show({
          kind: 'error',
          title: 'Chưa lưu được thay đổi',
          description: problemDetail(error, 'Vui lòng thử lại.'),
        });
      }
    }
  };

  const remove = async (search: SavedSearch) => {
    try {
      await engagementApi.deleteSavedSearch(search.id);
      setItems((current) => current.filter((item) => item.id !== search.id));
      setDeleting(null);
    } catch {
      toast.show({ kind: 'error', title: 'Chưa xóa được tìm kiếm' });
    }
  };

  if (status === 'loading') return <Skeleton className="h-48 rounded-card" />;
  if (status === 'error') return <ErrorState title="Không tải được tìm kiếm đã lưu" onRetry={() => void load()} />;
  if (items.length === 0) {
    return (
      <EmptyState
        icon={SearchX}
        title="Chưa có tìm kiếm đã lưu"
        description="Trên trang tìm kiếm, chọn bộ lọc rồi nhấn “Lưu tìm kiếm” để nhận cảnh báo tin mới, giảm giá và hiển thị lại."
        actions={<ButtonLink to="/search">Mở trang tìm kiếm</ButtonLink>}
      />
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <p className="text-body-sm text-on-surface-variant">
        {items.length}/{limit} tìm kiếm đã lưu
      </p>
      <ul className="flex flex-col gap-3">
        {items.map((search) => {
          const off = search.frequency === 'OFF';
          return (
            <li key={search.id} className="flex flex-col gap-3 rounded-card border border-outline-variant p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="text-body font-semibold text-on-surface">{search.name}</h3>
                  <p className="text-xs text-on-surface-variant">
                    {search.lastDigestAt
                      ? `Cảnh báo gần nhất: ${dateTime.format(new Date(search.lastDigestAt))}`
                      : 'Chưa gửi cảnh báo nào'}
                    {search.pendingMatches > 0 && ` · ${search.pendingMatches} tin đang chờ gửi`}
                  </p>
                </div>
                <div className="flex gap-2">
                  {search.paused && <Badge variant="warning">Tạm dừng</Badge>}
                  {off && <Badge>Đã tắt cảnh báo</Badge>}
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <FormField label="Tần suất">
                  {(control) => (
                    <Select
                      {...control}
                      value={search.frequency}
                      onChange={(event) => void update(search, { frequency: event.target.value as AlertFrequency })}
                      options={(Object.keys(FREQUENCY_LABELS) as AlertFrequency[]).map((value) => ({
                        value,
                        label: FREQUENCY_LABELS[value],
                      }))}
                    />
                  )}
                </FormField>
                <Switch
                  checked={!search.paused}
                  disabled={off}
                  onCheckedChange={(on) => void update(search, { paused: !on })}
                  label="Đang theo dõi"
                  description="Tạm dừng để ngừng cảnh báo mà vẫn giữ tìm kiếm."
                />
              </div>
              <fieldset className="flex flex-wrap gap-x-4 gap-y-2" disabled={off}>
                <legend className="sr-only">Loại cảnh báo của “{search.name}”</legend>
                <Checkbox
                  label="Tin mới"
                  checked={search.alertNew}
                  onChange={(event) => void update(search, { alertNew: event.target.checked })}
                />
                <Checkbox
                  label="Giảm giá"
                  checked={search.alertPriceDrop}
                  onChange={(event) => void update(search, { alertPriceDrop: event.target.checked })}
                />
                <Checkbox
                  label="Hiển thị trở lại"
                  checked={search.alertBackOnMarket}
                  onChange={(event) => void update(search, { alertBackOnMarket: event.target.checked })}
                />
              </fieldset>
              <div className="flex flex-wrap gap-2">
                <ButtonLink to={`/search?${search.query}`} variant="outline" size="sm">
                  Xem kết quả
                </ButtonLink>
                <Button variant="ghost" size="sm" onClick={() => setDeleting(search)}>
                  Xóa
                </Button>
              </div>
            </li>
          );
        })}
      </ul>
      <Dialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title="Xóa tìm kiếm đã lưu?"
        description={deleting ? `“${deleting.name}” và các cảnh báo đang chờ sẽ bị xóa.` : undefined}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)}>
              Giữ lại
            </Button>
            <Button variant="danger" onClick={() => deleting && void remove(deleting)}>
              Xóa
            </Button>
          </>
        }
      />
    </div>
  );
}
