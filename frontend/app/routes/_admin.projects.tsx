import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '@/shared/api/client';
import { useModal } from '@/shared/ui/useModal';
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
  const formPanelRef = useRef<HTMLFormElement>(null);
  const [profileOf, setProfileOf] = useState<Project | null>(null);
  // Shared modal stack (M2): this dialog had no focus trap, no Escape handling and no focus return at all.
  useModal({ open: showForm, onClose: () => setShowForm(false), panelRef: formPanelRef });
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
    <section className="mx-auto max-w-6xl space-y-6 px-4 py-10" data-ready={loading ? undefined : 'true'}>
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
            <article key={item.id} className="rounded-2xl border bg-white p-5">
              <div className="flex justify-between gap-3">
                <h2 className="text-lg font-bold">{item.name}</h2>
                <span className="text-xs font-bold text-on-surface-variant">
                  {PROJECT_STATUS_LABELS[item.status as ProjectDetail['status']] ?? item.status}
                </span>
              </div>
              <p className="mt-1 text-sm text-slate-600">{item.developerName}</p>
              <p className="mt-3 text-sm">{item.address || 'Chưa cung cấp địa chỉ'}</p>
              <p className="mt-3 text-sm">
                {item.totalAreaM2.toLocaleString('vi-VN')} m² · {item.totalBlocks} khối · {item.totalUnits} căn
              </p>
              <p className="mt-4 text-xs text-on-surface-variant">
                Cập nhật: {new Date(item.updatedAt).toLocaleString('vi-VN')}
              </p>
              <div className="mt-4 flex flex-wrap gap-3 text-sm">
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
      {showForm && (
        <div
          className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/60 p-4"
          role="presentation"
          // click (not mousedown): see shared/ui/Dialog.tsx for why mousedown races focus-return on close (m1).
          onClick={(event) => {
            if (event.target === event.currentTarget) setShowForm(false);
          }}
        >
          <form
            ref={formPanelRef}
            tabIndex={-1}
            aria-modal="true"
            role="dialog"
            aria-labelledby="project-form-title"
            onSubmit={create}
            className="grid w-full max-w-2xl gap-3 rounded-2xl bg-white p-6"
          >
            <h2 id="project-form-title" className="text-xl font-bold">
              Tạo dự án mới
            </h2>
            {(Object.keys(labels) as Array<keyof typeof labels>).map((key) => (
              <label key={key} className="text-sm font-semibold">
                {labels[key]}
                <input
                  required={!['handoverYear', 'legalLicenseNumber'].includes(key)}
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
                  className="mt-1 min-h-11 w-full rounded-lg border px-3"
                />
              </label>
            ))}
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setShowForm(false)} className="min-h-11 rounded-lg border px-4">
                Hủy
              </button>
              <button
                disabled={busy}
                className="min-h-11 rounded-lg bg-primary px-5 font-bold text-white disabled:opacity-50"
              >
                {busy ? 'Đang lưu…' : 'Lưu dự án'}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
export default ProjectCatalogPage;
