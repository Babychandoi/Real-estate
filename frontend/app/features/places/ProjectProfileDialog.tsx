import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { projectProfileApi, type PublicProfileInput } from '@/entities/content/adminApi';
import {
  AMENITY_CATEGORIES,
  PROJECT_STATUS_LABELS,
  type Amenity,
  type AmenityCategory,
} from '@/entities/content/model';
import { errorMessage } from '@/shared/api/errors';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { IconButton } from '@/shared/ui/IconButton';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Select } from '@/shared/ui/Select';
import { TextArea, TextInput } from '@/shared/ui/TextInput';

type AmenityRow = Omit<Amenity, 'id'>;

const today = () => new Date().toISOString().slice(0, 10);
const EMPTY_AMENITY = (): AmenityRow => ({
  name: '',
  category: 'EDUCATION',
  distanceM: null,
  sourceName: '',
  sourceUrl: '',
  checkedAt: today(),
});

/**
 * Staff editor of a project's public page (P-06): description, where the facts come from, and amenities — each with
 * a source and the date someone checked it (the server refuses an amenity without them). Locking hides the public
 * page (410).
 */
export function ProjectProfileDialog({
  projectId,
  projectName,
  onClose,
  onSaved,
}: {
  projectId: string | null;
  projectName: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<PublicProfileInput | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!projectId) {
      setForm(null);
      return;
    }
    setError(null);
    projectProfileApi.get(projectId).then(
      ({ project, amenities }) =>
        setForm({
          description: project.description ?? '',
          websiteUrl: project.websiteUrl ?? '',
          infoSource: project.infoSource ?? '',
          infoCheckedAt: project.infoCheckedAt,
          status: project.status,
          amenities: amenities.map((amenity) => ({
            name: amenity.name,
            category: amenity.category,
            distanceM: amenity.distanceM,
            sourceName: amenity.sourceName,
            sourceUrl: amenity.sourceUrl ?? '',
            checkedAt: amenity.checkedAt,
          })),
        }),
      (failure: unknown) => setError(errorMessage(failure, 'Không tải được hồ sơ dự án.')),
    );
  }, [projectId]);

  const update = (patch: Partial<PublicProfileInput>) =>
    setForm((previous) => (previous ? { ...previous, ...patch } : previous));
  const updateAmenity = (index: number, patch: Partial<AmenityRow>) =>
    update({ amenities: (form?.amenities ?? []).map((row, i) => (i === index ? { ...row, ...patch } : row)) });

  const save = async () => {
    if (!projectId || !form) return;
    setBusy(true);
    setError(null);
    try {
      await projectProfileApi.save(projectId, {
        ...form,
        amenities: form.amenities.map((row) => ({ ...row, sourceUrl: row.sourceUrl || null })),
      });
      onSaved();
    } catch (failure) {
      setError(errorMessage(failure, 'Không lưu được hồ sơ dự án.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={Boolean(projectId)}
      onClose={onClose}
      size="lg"
      title={`Trang công khai: ${projectName}`}
      description="Chỉ ghi thông tin có nguồn. Số tin và thống kê giá chào được tính tự động từ tin đang hiển thị."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Hủy
          </Button>
          <Button onClick={save} isLoading={busy} disabled={!form}>
            Lưu
          </Button>
        </>
      }
    >
      {error && (
        <InlineFeedback kind="error" title="Chưa lưu được">
          {error}
        </InlineFeedback>
      )}
      {!form ? (
        !error && <p role="status">Đang tải…</p>
      ) : (
        <div className="flex flex-col gap-4">
          <FormField label="Trạng thái hiển thị" hint="“Tạm ẩn” gỡ trang dự án khỏi trang công khai.">
            {(control) => (
              <Select
                {...control}
                value={form.status ?? 'ACTIVE'}
                options={Object.entries(PROJECT_STATUS_LABELS).map(([value, label]) => ({ value, label }))}
                onChange={(event) => update({ status: event.target.value })}
              />
            )}
          </FormField>
          <FormField label="Mô tả dự án">
            {(control) => (
              <TextArea
                {...control}
                rows={4}
                maxLength={5000}
                value={form.description}
                onChange={(event) => update({ description: event.target.value })}
              />
            )}
          </FormField>
          <div className="grid items-start gap-4 sm:grid-cols-3">
            <FormField label="Nguồn thông tin">
              {(control) => (
                <TextInput
                  {...control}
                  value={form.infoSource}
                  maxLength={255}
                  placeholder="Ví dụ: Hồ sơ pháp lý"
                  onChange={(event) => update({ infoSource: event.target.value })}
                />
              )}
            </FormField>
            <FormField label="Ngày kiểm tra">
              {(control) => (
                <TextInput
                  {...control}
                  type="date"
                  max={today()}
                  value={form.infoCheckedAt ?? ''}
                  onChange={(event) => update({ infoCheckedAt: event.target.value || null })}
                />
              )}
            </FormField>
            <FormField label="Website chủ đầu tư">
              {(control) => (
                <TextInput
                  {...control}
                  type="url"
                  value={form.websiteUrl}
                  placeholder="https://…"
                  onChange={(event) => update({ websiteUrl: event.target.value })}
                />
              )}
            </FormField>
          </div>
          <fieldset className="flex flex-col gap-3">
            <legend className="font-semibold">Tiện ích xung quanh</legend>
            {form.amenities.length === 0 && <p className="text-sm text-on-surface-variant">Chưa có tiện ích.</p>}
            {form.amenities.map((row, index) => (
              <div
                key={index}
                className="grid items-start gap-3 rounded-xl border border-outline-variant/40 p-3 sm:grid-cols-[2fr_1fr_1fr_auto]"
              >
                <FormField label="Tên tiện ích" required>
                  {(control) => (
                    <TextInput
                      {...control}
                      value={row.name}
                      maxLength={150}
                      onChange={(event) => updateAmenity(index, { name: event.target.value })}
                    />
                  )}
                </FormField>
                <FormField label="Loại">
                  {(control) => (
                    <Select
                      {...control}
                      value={row.category}
                      options={AMENITY_CATEGORIES}
                      onChange={(event) => updateAmenity(index, { category: event.target.value as AmenityCategory })}
                    />
                  )}
                </FormField>
                <FormField label="Khoảng cách (m)">
                  {(control) => (
                    <TextInput
                      {...control}
                      type="number"
                      min={0}
                      max={100000}
                      value={row.distanceM ?? ''}
                      onChange={(event) =>
                        updateAmenity(index, {
                          distanceM: event.target.value === '' ? null : Number(event.target.value),
                        })
                      }
                    />
                  )}
                </FormField>
                <div className="flex items-end self-end">
                  <IconButton
                    aria-label={`Xóa tiện ích ${row.name || index + 1}`}
                    icon={Trash2}
                    onClick={() => update({ amenities: form.amenities.filter((_, i) => i !== index) })}
                  />
                </div>
                <FormField label="Nguồn" required>
                  {(control) => (
                    <TextInput
                      {...control}
                      value={row.sourceName}
                      maxLength={255}
                      onChange={(event) => updateAmenity(index, { sourceName: event.target.value })}
                    />
                  )}
                </FormField>
                <FormField label="Đường dẫn nguồn">
                  {(control) => (
                    <TextInput
                      {...control}
                      type="url"
                      value={row.sourceUrl ?? ''}
                      placeholder="https://…"
                      onChange={(event) => updateAmenity(index, { sourceUrl: event.target.value })}
                    />
                  )}
                </FormField>
                <FormField label="Ngày kiểm tra" required>
                  {(control) => (
                    <TextInput
                      {...control}
                      type="date"
                      max={today()}
                      value={row.checkedAt}
                      onChange={(event) => updateAmenity(index, { checkedAt: event.target.value })}
                    />
                  )}
                </FormField>
              </div>
            ))}
            <Button
              variant="outline"
              size="sm"
              className="self-start"
              leftIcon={<Plus className="h-4 w-4" />}
              disabled={form.amenities.length >= 50}
              onClick={() => update({ amenities: [...form.amenities, EMPTY_AMENITY()] })}
            >
              Thêm tiện ích
            </Button>
          </fieldset>
        </div>
      )}
    </Dialog>
  );
}
