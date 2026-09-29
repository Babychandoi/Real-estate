import { useCallback, useEffect, useState } from 'react';
import { apiClient } from '@/shared/api/client';
import { ClampedText } from '@/shared/ui/ClampedText';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { TextInput } from '@/shared/ui/TextInput';
import { PROJECT_STATUS_LABELS, type ProjectDetail } from '@/entities/content/model';
import { ProjectProfileDialog } from '@/features/places/ProjectProfileDialog';

interface Project {
  id: string;
  name: string;
  slug: string;
  developerName: string;
  provinceCode: string;
  districtCode: string;
  address: string;
  totalAreaM2: number;
  totalBlocks: number;
  totalUnits: number;
  handoverYear?: number;
  legalLicenseNumber?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}
const EMPTY = {
  name: '',
  developerName: '',
  provinceCode: '',
  districtCode: '',
  address: '',
  totalAreaM2: 0,
  totalBlocks: 0,
  totalUnits: 0,
  handoverYear: '',
  legalLicenseNumber: '',
};

export function ProjectCatalogPage() {
  const [items, setItems] = useState<Project[]>([]);
  const [keyword, setKeyword] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [profileOf, setProfileOf] = useState<Project | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(
        await apiClient<Project[]>(
          `/catalog/projects${keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : ''}`,
        ),
      );
    } catch {
      setItems([]);
      setError('Không thể tải danh mục dự án từ máy chủ.');
    } finally {
      setLoading(false);
    }
  }, [keyword]);
  useEffect(() => {
    void load();
  }, [load]);
  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const created = await apiClient<Project>('/catalog/projects', {
        method: 'POST',
        body: JSON.stringify({ ...form, handoverYear: form.handoverYear ? Number(form.handoverYear) : null }),
      });
      setItems((current) => [created, ...current]);
      setForm(EMPTY);
      setShowForm(false);
    } catch {
      setError('Không thể tạo dự án; dữ liệu chưa được lưu.');
    } finally {
      setBusy(false);
    }
  };
  const labels = {
    name: 'Tên dự án',
    developerName: 'Chủ đầu tư',
    provinceCode: 'Mã tỉnh/thành',
    districtCode: 'Mã quận/huyện',
    address: 'Địa chỉ',
    totalAreaM2: 'Tổng diện tích m²',
    totalBlocks: 'Số khối',
    totalUnits: 'Số căn',
    handoverYear: 'Năm bàn giao',
    legalLicenseNumber: 'Số giấy phép pháp lý',
  };
  return (
    <section className="space-y-6" data-ready={loading ? undefined : 'true'}>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Danh mục dự án</h1>
          <p className="mt-1 text-slate-600">Quản lý thông tin, vị trí và tiến độ các dự án bất động sản.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="min-h-11 rounded-xl bg-primary px-5 font-bold text-white"
        >
          Tạo dự án
        </button>
      </header>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load();
        }}
        className="flex gap-2"
      >
        <input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="Tìm theo tên dự án"
          className="min-h-11 flex-1 rounded-xl border px-4"
        />
        <button className="min-h-11 rounded-xl border px-5 font-bold">Tìm</button>
      </form>
      {error && (
        <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-800">
          {error}
        </p>
      )}
      {loading ? (
        <p role="status">Đang tải dự án…</p>
      ) : items.length === 0 ? (
        <p className="rounded-xl bg-slate-50 p-8 text-center text-slate-600">Chưa có dự án phù hợp.</p>
      ) : (
        <section className="grid gap-4 md:grid-cols-2">
          {items.map((item) => (
            <article key={item.id} className="flex h-full min-w-0 flex-col rounded-2xl border bg-white p-5">
              <div className="flex min-w-0 justify-between gap-3">
                <ClampedText as="h2" text={item.name} lines={3} className="min-w-0 text-lg font-bold" />
                <span className="shrink-0 text-right text-xs font-bold text-on-surface-variant">
                  {PROJECT_STATUS_LABELS[item.status as ProjectDetail['status']] ?? item.status}
                </span>
              </div>
              <ClampedText text={item.developerName} className="mt-1 text-sm text-slate-600" />
              <ClampedText text={item.address || 'Chưa cung cấp địa chỉ'} lines={3} className="mt-3 text-sm" />
              <p className="mt-3 text-sm">
                {item.totalAreaM2.toLocaleString('vi-VN')} m² · {item.totalBlocks} khối · {item.totalUnits} căn
              </p>
              <p className="mt-4 text-xs text-on-surface-variant">
                Cập nhật: {new Date(item.updatedAt).toLocaleString('vi-VN')}
              </p>
              <div className="mt-auto flex flex-wrap gap-3 pt-4 text-sm">
                <button
                  type="button"
                  className="min-h-11 font-semibold text-primary"
                  onClick={() => setProfileOf(item)}
                >
                  Trang công khai: mô tả, nguồn, tiện ích
                </button>
                {item.status !== 'LOCKED' && (
                  <a
                    className="inline-flex min-h-11 items-center font-semibold text-primary underline"
                    href={`/du-an/${item.slug}`}
                    target="_blank"
                    rel="noopener"
                  >
                    Xem trang dự án
                  </a>
                )}
              </div>
            </article>
          ))}
        </section>
      )}
      <ProjectProfileDialog
        projectId={profileOf?.id ?? null}
        projectName={profileOf?.name ?? ''}
        onClose={() => setProfileOf(null)}
        onSaved={() => {
          setProfileOf(null);
          void load();
        }}
      />
      <Dialog
        open={showForm}
        onClose={() => setShowForm(false)}
        size="lg"
        title="Tạo dự án mới"
        footer={
          <>
            <Button type="button" variant="ghost" onClick={() => setShowForm(false)}>
              Hủy
            </Button>
            <Button type="submit" form="project-create-form" isLoading={busy}>
              Lưu dự án
            </Button>
          </>
        }
      >
        <form id="project-create-form" onSubmit={create} className="grid items-start gap-4 sm:grid-cols-2">
          {(Object.keys(labels) as Array<keyof typeof labels>).map((key) => (
            <FormField
              key={key}
              label={labels[key]}
              required={!['handoverYear', 'legalLicenseNumber'].includes(key)}
              className={key === 'name' || key === 'address' ? 'sm:col-span-2' : undefined}
            >
              {(control) => (
                <TextInput
                  {...control}
                  type={['totalAreaM2', 'totalBlocks', 'totalUnits', 'handoverYear'].includes(key) ? 'number' : 'text'}
                  value={form[key]}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      [key]: ['totalAreaM2', 'totalBlocks', 'totalUnits'].includes(key)
                        ? Number(event.target.value)
                        : event.target.value,
                    })
                  }
                />
              )}
            </FormField>
          ))}
        </form>
      </Dialog>
    </section>
  );
}
export default ProjectCatalogPage;
