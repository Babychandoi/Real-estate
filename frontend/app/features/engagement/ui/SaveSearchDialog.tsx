import { useState } from 'react';
import { Link } from 'react-router-dom';
import { serializeFilters, type SearchFilters } from '@/features/search/filterSchema';
import { Button } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { RadioGroup } from '@/shared/ui/Radio';
import { TextInput } from '@/shared/ui/TextInput';
import { useToast } from '@/shared/ui/Toast';
import { engagementApi, FREQUENCY_LABELS, problemCode, problemDetail, type AlertFrequency } from '../api';

/** The filter as saved: the URL filter parameters (contract §7) without the UI-only view. */
export function savedFilterParams(filters: SearchFilters): Record<string, string> {
  const params = serializeFilters(filters);
  params.delete('view');
  return Object.fromEntries(params.entries());
}

const FREQUENCIES: AlertFrequency[] = ['INSTANT', 'DAILY', 'WEEKLY', 'OFF'];

export default function SaveSearchDialog({ filters, onClose }: { filters: SearchFilters; onClose: () => void }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [frequency, setFrequency] = useState<AlertFrequency>('DAILY');
  const [alertNew, setAlertNew] = useState(true);
  const [alertPriceDrop, setAlertPriceDrop] = useState(true);
  const [alertBackOnMarket, setAlertBackOnMarket] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState(false);
  const noKind = frequency !== 'OFF' && !alertNew && !alertPriceDrop && !alertBackOnMarket;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (noKind) return;
    setSaving(true);
    setError(null);
    setDuplicate(false);
    try {
      await engagementApi.createSavedSearch(savedFilterParams(filters), {
        name: name.trim() || undefined,
        frequency,
        alertNew,
        alertPriceDrop,
        alertBackOnMarket,
      });
      toast.show({
        kind: 'success',
        title: 'Đã lưu tìm kiếm',
        description:
          frequency === 'OFF'
            ? 'Bạn có thể bật cảnh báo trong mục Tin đã lưu.'
            : 'Bạn sẽ nhận thông báo khi có tin phù hợp.',
      });
      onClose();
    } catch (failure) {
      if (problemCode(failure) === 'SAVED_SEARCH_EXISTS') setDuplicate(true);
      else setError(problemDetail(failure, 'Chưa lưu được tìm kiếm. Vui lòng thử lại.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title="Lưu tìm kiếm và nhận cảnh báo"
      description="Chúng tôi báo cho bạn khi có tin mới, tin giảm giá hoặc tin hiển thị trở lại khớp bộ lọc hiện tại."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" form="save-search-form" isLoading={saving} disabled={noKind}>
            Lưu tìm kiếm
          </Button>
        </>
      }
    >
      <form id="save-search-form" onSubmit={submit} className="flex flex-col gap-4">
        {duplicate && (
          <InlineFeedback kind="info" title="Bạn đã lưu tìm kiếm này rồi">
            Xem và chỉnh tần suất trong <Link to="/saved?tab=searches">Tìm kiếm đã lưu</Link>.
          </InlineFeedback>
        )}
        {error && <InlineFeedback kind="error" title={error} />}
        <FormField label="Tên gợi nhớ" hint="Để trống để hệ thống tự đặt tên theo bộ lọc.">
          {(control) => (
            <TextInput {...control} value={name} maxLength={80} onChange={(event) => setName(event.target.value)} />
          )}
        </FormField>
        <RadioGroup
          legend="Tần suất thông báo"
          name="frequency"
          value={frequency}
          onChange={setFrequency}
          options={FREQUENCIES.map((value) => ({ value, label: FREQUENCY_LABELS[value] }))}
        />
        <fieldset className="flex flex-col gap-2" disabled={frequency === 'OFF'}>
          <legend className="mb-1 text-sm font-semibold text-on-surface">Báo cho tôi khi</legend>
          <Checkbox label="Có tin mới phù hợp" checked={alertNew} onChange={(e) => setAlertNew(e.target.checked)} />
          <Checkbox
            label="Tin phù hợp giảm giá"
            checked={alertPriceDrop}
            onChange={(e) => setAlertPriceDrop(e.target.checked)}
          />
          <Checkbox
            label="Tin phù hợp hiển thị trở lại"
            checked={alertBackOnMarket}
            onChange={(e) => setAlertBackOnMarket(e.target.checked)}
          />
          {noKind && (
            <p className="text-sm text-error">Hãy chọn ít nhất một loại cảnh báo, hoặc chọn “Tắt cảnh báo”.</p>
          )}
        </fieldset>
        <p className="text-sm text-on-surface-variant">
          Email cảnh báo có liên kết ngừng nhận một chạm. Quản lý kênh nhận trong{' '}
          <Link to="/account#thong-bao" className="font-semibold text-primary underline">
            Tùy chọn thông báo
          </Link>
          .
        </p>
      </form>
    </Dialog>
  );
}
