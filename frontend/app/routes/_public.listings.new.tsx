import React, { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  CheckCircle2,
  Cloud,
  CloudOff,
  Eye,
  HelpCircle,
  Image as ImageIcon,
  Info,
  Key,
  Loader2,
  MapPin,
  Send,
  ShieldCheck,
  TrendingUp,
  Upload,
  X,
} from 'lucide-react';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Select } from '@/shared/ui/Select';
import { TextArea, TextInput } from '@/shared/ui/TextInput';
import { Money, UnitPriceText } from '@/shared/ui/Money';
import { Skeleton } from '@/shared/ui/Skeleton';
import { apiClient } from '@/shared/api/client';
import { formatMoney, formatRentTerms, moneyFromLegacy, unitPriceFromArea } from '@/shared/format/money';
import { useAuth } from '@/shared/auth/AuthContext';
import { ROLE_LABELS, ROLES } from '@/shared/auth/roles';
import { validationMessage } from '@/shared/types/problem-details';
import type { UserKycProfile } from '@/entities/verification/model/types';
import {
  EMPTY_DRAFT,
  fieldsFromDraft,
  loadDraft,
  loadPreview,
  submitDraft,
  validateDraft,
  type DraftFields,
  type DraftView,
  type FieldErrors,
  type PreviewView,
} from '@/features/listing-editor/api';
import { useDraftAutosave, type SaveState } from '@/features/listing-editor/useDraftAutosave';
import { QualityChecklist } from '@/features/listing-editor/QualityChecklist';
import { rememberSignedMediaUrl, useSignedMediaUrls } from '@/shared/media/useSignedMediaUrls';

const LocationPicker = lazy(() => import('@/features/listing-editor/LocationPicker'));

type Step = 1 | 2 | 3 | 4;
const STEPS: Array<{ id: Step; label: string; icon: typeof MapPin }> = [
  { id: 1, label: 'Cơ bản', icon: TrendingUp },
  { id: 2, label: 'Vị trí', icon: MapPin },
  { id: 3, label: 'Ảnh', icon: ImageIcon },
  { id: 4, label: 'Xem trước', icon: Eye },
];
const STEP_FIELDS: Record<Step, ReadonlyArray<string>> = {
  1: [
    'title',
    'priceVnd',
    'areaM2',
    'legalStatus',
    'description',
    'depositVnd',
    'monthlyServiceFeeVnd',
    'furnishing',
    'legalStatusCode',
  ],
  2: ['districtCode', 'provinceCode', 'wardCode', 'addressSummary', 'publicLatitude'],
  3: ['imageUrls'],
  4: [],
};
const PROPERTY_TYPES = [
  { value: 'APARTMENT', label: 'Căn hộ chung cư' },
  { value: 'HOUSE', label: 'Nhà riêng' },
  { value: 'TOWNHOUSE', label: 'Nhà phố' },
  { value: 'VILLA', label: 'Biệt thự, liền kề' },
  { value: 'LAND', label: 'Đất nền' },
];
const LEGAL_OPTIONS = [
  { value: 'RED_BOOK', label: 'Sổ đỏ' },
  { value: 'PINK_BOOK', label: 'Sổ hồng' },
  { value: 'SALE_CONTRACT', label: 'Hợp đồng mua bán' },
  { value: 'PENDING_CERTIFICATE', label: 'Đang chờ sổ' },
  { value: 'OTHER', label: 'Khác' },
];
const FURNISHING_OPTIONS = [
  { value: 'NONE', label: 'Không nội thất' },
  { value: 'BASIC', label: 'Nội thất cơ bản' },
  { value: 'FULL', label: 'Đầy đủ nội thất' },
];
const DIRECTIONS = ['Đông', 'Tây', 'Nam', 'Bắc', 'Đông Bắc', 'Đông Nam', 'Tây Bắc', 'Tây Nam'].map((d) => ({
  value: d,
  label: d,
}));

/** Digits only ("3.950.000.000" → 3950000000); empty → null. */
const parseInteger = (value: string): number | null => {
  const digits = value.replace(/\D/g, '');
  return digits === '' ? null : Number(digits);
};
const parseDecimal = (value: string): number | null => {
  const normalized = value.replace(',', '.').replace(/[^\d.]/g, '');
  if (normalized === '') return null;
  const number = Number(normalized);
  return Number.isFinite(number) ? number : null;
};
const groupDigits = (value: number | null) => (value == null ? '' : new Intl.NumberFormat('vi-VN').format(value));

function SaveStatus({ state, onRetry, blocked }: { state: SaveState; onRetry: () => void; blocked: boolean }) {
  let content: React.ReactNode;
  if (state.kind === 'saving') {
    content = (
      <>
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Đang lưu…
      </>
    );
  } else if (state.kind === 'saved') {
    content = (
      <>
        <CheckCircle2 className="h-4 w-4 text-success" aria-hidden="true" /> Đã lưu lúc{' '}
        {state.at.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
      </>
    );
  } else if (state.kind === 'offline') {
    content = (
      <>
        <CloudOff className="h-4 w-4 text-warning" aria-hidden="true" /> Mất kết nối, sẽ tự lưu khi có mạng
        <Button size="sm" variant="ghost" onClick={onRetry}>
          Thử lại
        </Button>
      </>
    );
  } else if (state.kind === 'error') {
    content = (
      <>
        <CloudOff className="h-4 w-4 text-error" aria-hidden="true" /> Chưa lưu được
        <Button size="sm" variant="ghost" onClick={onRetry}>
          Thử lại
        </Button>
      </>
    );
  } else if (state.kind === 'conflict') {
    content = 'Có phiên bản mới hơn ở nơi khác';
  } else {
    content = (
      <>
        <Cloud className="h-4 w-4" aria-hidden="true" />
        {blocked ? 'Tự lưu khi có tiêu đề, giá và diện tích' : 'Tự động lưu bản nháp'}
      </>
    );
  }
  return (
    <p
      role="status"
      aria-live="polite"
      data-testid="autosave-status"
      data-state={state.kind}
      className="flex min-h-11 items-center gap-2 text-body-sm text-on-surface-variant"
    >
      {content}
    </p>
  );
}

export const CreateListingPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const editId = searchParams.get('edit');

  const [step, setStep] = useState<Step>(1);
  const [fields, setFields] = useState<DraftFields>(EMPTY_DRAFT);
  const [loaded, setLoaded] = useState<DraftView | null>(null);
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>(editId ? 'loading' : 'ready');
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [kyc, setKyc] = useState<UserKycProfile | null | 'loading'>('loading');
  const [preview, setPreview] = useState<PreviewView | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  // Draft images are never public (S1, F14.4): the owner sees them through short-lived signed URLs.
  const displayMedia = useSignedMediaUrls([...fields.imageUrls, preview?.images[0]?.url]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [mapOpen, setMapOpen] = useState(false);
  const stepHeading = useRef<HTMLHeadingElement>(null);
  const [formKey, setFormKey] = useState(0);

  const createdId = useRef<string | null>(null);
  const onCreated = useCallback(
    (id: string) => {
      createdId.current = id;
      setSearchParams(
        (params) => {
          const next = new URLSearchParams(params);
          next.set('edit', id);
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );
  const autosave = useDraftAutosave({
    fields,
    listingId: null,
    version: null,
    onCreated,
    enabled: loadState === 'ready' && !submitted,
  });
  const { reset } = autosave;

  const applyDraft = useCallback(
    (view: DraftView) => {
      reset(view.listingId, view.version);
      setLoaded(view);
      setFields(fieldsFromDraft(view));
      setFormKey((key) => key + 1);
    },
    [reset],
  );

  useEffect(() => {
    if (!user) return;
    apiClient<UserKycProfile>(`/kyc/user/${user.id}`)
      .then((profile) => setKyc(profile ?? null))
      .catch(() => setKyc(null));
  }, [user]);

  // Reload keeps the draft: the id is in the URL (?edit=…) from the first autosave on.
  useEffect(() => {
    if (!editId || loaded?.listingId === editId || createdId.current === editId) return;
    setLoadState('loading');
    loadDraft(editId)
      .then((view) => {
        applyDraft(view);
        setLoadState('ready');
      })
      .catch(() => setLoadState('error'));
    // Only a different id in the URL loads again (our own first save also writes the id there).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

  // Refresh the server checklist after each save (the fields stay as typed).
  const savedAt = autosave.state.kind === 'saved' ? autosave.state.at.getTime() : null;
  useEffect(() => {
    const id = editId ?? loaded?.listingId;
    if (!savedAt || !id) return;
    loadDraft(id)
      .then((view) => setLoaded(view))
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [savedAt]);

  const clientErrors = useMemo(() => validateDraft(fields), [fields]);
  const errors: FieldErrors = { ...clientErrors, ...autosave.serverErrors };
  const shownError = (name: string) => (touched[name] || touched.__all ? errors[name] : autosave.serverErrors[name]);
  const update = <K extends keyof DraftFields>(key: K, value: DraftFields[K]) => {
    setFields((current) => {
      const next = { ...current, [key]: value };
      if (key === 'purpose' && value === 'SALE') {
        next.depositVnd = null;
        next.monthlyServiceFeeVnd = null;
      }
      return next;
    });
  };
  const touch = (name: string) => setTouched((current) => ({ ...current, [name]: true }));

  const goTo = (next: Step) => {
    setStep(next);
    window.scrollTo({ top: 0 });
    window.setTimeout(() => stepHeading.current?.focus(), 0);
  };

  const leaveStep = async (next: Step) => {
    if (next > step) {
      const blocking = STEP_FIELDS[step].filter((name) => errors[name]);
      if (blocking.length) {
        setTouched((current) => ({ ...current, ...Object.fromEntries(blocking.map((name) => [name, true])) }));
        document.getElementById(`field-${blocking[0]}`)?.focus();
        return;
      }
    }
    if (autosave.isDirty() && !autosave.blocked) await autosave.saveNow();
    goTo(next);
  };

  const currentId = editId ?? loaded?.listingId ?? null;

  useEffect(() => {
    if (step !== 4 || !currentId) return;
    let cancelled = false;
    setPreviewError(null);
    const run = async () => {
      if (autosave.isDirty()) await autosave.saveNow();
      try {
        const view = await loadPreview(currentId);
        if (!cancelled) setPreview(view);
      } catch {
        if (!cancelled) setPreviewError('Không tải được bản xem trước.');
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
    // Re-run when entering step 4 or once the draft id exists.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, currentId]);

  const handleUpload = async (files: FileList | null) => {
    if (!files?.length) return;
    const allowed = new Set(['image/jpeg', 'image/png', 'image/webp']);
    const selected = Array.from(files).slice(0, Math.max(0, 20 - fields.imageUrls.length));
    const invalid = selected.find((file) => !allowed.has(file.type) || file.size <= 0 || file.size > 10 * 1024 * 1024);
    if (invalid) {
      setUploadError(`Ảnh “${invalid.name}” không hợp lệ: chỉ nhận JPEG, PNG hoặc WebP, tối đa 10 MB.`);
      return;
    }
    setUploadError(selected.length < files.length ? 'Mỗi tin tối đa 20 ảnh.' : null);
    setUploading(true);
    try {
      for (const file of selected) {
        const form = new FormData();
        form.append('file', file);
        const result = await apiClient<{ url: string; previewUrl?: string | null; previewExpiresAt?: string | null }>(
          '/media/images',
          { method: 'POST', body: form },
        );
        rememberSignedMediaUrl(result.url, result.previewUrl, result.previewExpiresAt);
        setFields((current) => ({ ...current, imageUrls: [...current.imageUrls, result.url] }));
      }
    } catch {
      setUploadError('Một số ảnh chưa tải lên được. Ảnh đã tải vẫn được giữ; hãy thử lại phần còn thiếu.');
    } finally {
      setUploading(false);
    }
  };

  const moveImage = (index: number, delta: -1 | 1) =>
    setFields((current) => {
      const images = [...current.imageUrls];
      const target = index + delta;
      if (target < 0 || target >= images.length) return current;
      [images[index], images[target]] = [images[target], images[index]];
      return { ...current, imageUrls: images };
    });

  const handleSubmit = async () => {
    setTouched({ __all: true });
    if (Object.keys(clientErrors).length) {
      const first = ([1, 2, 3] as Step[]).find((s) => STEP_FIELDS[s].some((name) => clientErrors[name]));
      if (first) goTo(first);
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      const ok = await autosave.saveNow();
      const id = editId ?? loaded?.listingId;
      if (!ok || !id) {
        setSubmitError('Chưa lưu được bản nháp mới nhất nên chưa gửi duyệt. Kiểm tra các trường báo lỗi rồi thử lại.');
        return;
      }
      const result = await submitDraft(id);
      setSubmitted(result.status);
    } catch (error) {
      setSubmitError(validationMessage(error, 'Chưa gửi duyệt được. Bản nháp vẫn được giữ; vui lòng thử lại.'));
    } finally {
      setSubmitting(false);
    }
  };

  const resolveConflict = async (keepMine: boolean) => {
    if (!currentId) return;
    const latest = await loadDraft(currentId);
    if (keepMine) {
      autosave.adoptVersion(latest.version);
      setLoaded(latest);
      await autosave.saveNow();
    } else {
      applyDraft(latest);
    }
  };

  if (kyc === 'loading' || loadState === 'loading') {
    return (
      <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6 lg:px-8" role="status" aria-label="Đang tải">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="mt-6 h-64 w-full" />
      </div>
    );
  }

  if (kyc?.status !== 'VERIFIED') {
    const pending = kyc?.status === 'PENDING';
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 md:px-8">
        <section className="rounded-xl border border-slate-200 bg-white p-6 md:p-8">
          <ShieldCheck className="h-9 w-9 text-slate-900" aria-hidden="true" />
          <h1 className="mt-4 text-2xl font-bold text-slate-950">Xác minh danh tính trước khi đăng tin</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">
            Chỉ tài khoản đã được duyệt eKYC mới tạo hoặc gửi tin đăng, để bảo vệ người đăng và người liên hệ.
          </p>
          {pending ? (
            <p className="mt-5 rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
              Hồ sơ eKYC đang chờ duyệt. Bạn có thể đăng tin khi hồ sơ được xác nhận.
            </p>
          ) : (
            <ButtonLink to="/kyc" className="mt-6">
              Đi tới xác minh eKYC
            </ButtonLink>
          )}
        </section>
      </div>
    );
  }

  if (loadState === 'error') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <InlineFeedback
          kind="error"
          title="Không tải được tin để chỉnh sửa"
          action={{ label: 'Tải lại', onClick: () => window.location.reload() }}
        >
          Tin không tồn tại hoặc bạn không có quyền sửa. Quay lại{' '}
          <Link to="/my-listings" className="underline">
            Tin đăng của tôi
          </Link>
          .
        </InlineFeedback>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 py-16">
        <div className="container mx-auto max-w-xl px-4">
          <Card className="border border-slate-200 bg-white p-8 text-center shadow-lg" data-testid="submit-success">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
              <CheckCircle2 className="h-10 w-10" aria-hidden="true" />
            </div>
            <h1 className="mb-2 text-2xl font-bold text-slate-900">Đã gửi duyệt</h1>
            <p className="mb-6 text-sm leading-relaxed text-slate-600">
              {submitted === 'ACTIVE'
                ? 'Bản đang hiển thị vẫn giữ nguyên cho tới khi bản sửa được duyệt.'
                : 'Tin vào hàng đợi kiểm duyệt. Bạn sẽ nhận thông báo khi có kết quả.'}
            </p>
            <div className="flex flex-col justify-center gap-3 sm:flex-row">
              <Button onClick={() => navigate('/my-listings')}>Về Tin đăng của tôi</Button>
              <Button variant="outline" onClick={() => window.location.assign('/listings/new')}>
                Đăng tin khác
              </Button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  const rent = fields.purpose === 'RENT';
  const role = user?.role ?? ROLES.USER;
  const rejected = loaded?.revisionStatus === 'REJECTED' ? loaded : null;
  const price = fields.priceVnd != null ? moneyFromLegacy(fields.priceVnd, fields.purpose) : null;
  const StepIcon = STEPS[step - 1].icon;

  return (
    <div className="min-h-screen bg-slate-50 py-8" data-ready={loadState === 'ready' ? 'true' : undefined}>
      <div className="container mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
          <Link
            to="/my-listings"
            className="inline-flex min-h-11 items-center gap-1.5 text-sm font-medium text-slate-600 transition-colors hover:text-slate-900"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Tin đăng của tôi
          </Link>
          <SaveStatus state={autosave.state} blocked={autosave.blocked} onRetry={() => void autosave.saveNow()} />
        </div>

        <h1 className="text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">
          {currentId ? 'Sửa tin đăng' : 'Đăng tin mới'}
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Bạn đăng với vai trò <strong className="text-slate-900">{ROLE_LABELS[role]}</strong>
          {role === ROLES.OWNER
            ? ' — tin hiển thị là chủ nhà tự đăng, không qua môi giới.'
            : role === ROLES.BROKER
              ? ' — tin hiển thị là môi giới đăng.'
              : '.'}
        </p>

        {rejected && (
          <InlineFeedback kind="warning" title="Bản sửa trước bị từ chối" className="mt-4">
            <p>Lý do: {rejected.rejectionReason || 'Không ghi lý do.'}</p>
            <p className="mt-1">Sửa theo lý do trên rồi gửi duyệt lại; bản mới sẽ được kiểm duyệt từ đầu.</p>
          </InlineFeedback>
        )}
        {loaded?.revisionStatus === 'SUBMITTED' && (
          <InlineFeedback kind="info" title="Có bản đang chờ duyệt" className="mt-4">
            Chỉnh sửa lúc này sẽ tạo bản nháp mới; bản đang chờ duyệt không bị thay đổi.
          </InlineFeedback>
        )}

        <nav aria-label="Các bước đăng tin" className="mb-8 mt-6">
          <ol className="grid grid-cols-2 gap-2 sm:grid-cols-4 sm:gap-4">
            {STEPS.map((s) => {
              const Icon = s.icon;
              const active = step === s.id;
              const completed = step > s.id;
              return (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => void leaveStep(s.id)}
                    aria-current={active ? 'step' : undefined}
                    className={`flex min-h-11 w-full items-center gap-3 rounded-xl border p-3.5 text-left transition-all ${
                      active
                        ? 'border-emerald-500 bg-emerald-50 text-emerald-900 shadow-sm'
                        : completed
                          ? 'border-slate-300 bg-white text-slate-800'
                          : 'border-slate-200 bg-white/60 text-slate-500'
                    }`}
                  >
                    <span
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-bold ${
                        active
                          ? 'bg-emerald-600 text-white shadow'
                          : completed
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      {completed ? (
                        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                      ) : (
                        <Icon className="h-4 w-4" aria-hidden="true" />
                      )}
                    </span>
                    <span className="overflow-hidden">
                      <span className="block truncate text-sm font-bold">{s.label}</span>
                      <span className="block text-sm text-slate-500">Bước {s.id} / 4</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        </nav>

        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          <Card className="p-6 lg:col-span-8">
            <form className="flex flex-col gap-5" noValidate onSubmit={(event) => event.preventDefault()}>
              <h2
                ref={stepHeading}
                tabIndex={-1}
                className="flex items-center gap-2 border-b border-slate-100 pb-2 text-base font-bold text-slate-900 outline-none"
              >
                <StepIcon className="h-5 w-5 text-emerald-600" aria-hidden="true" />
                Bước {step}: {STEPS[step - 1].label}
              </h2>

              {step === 1 && (
                <>
                  <fieldset>
                    <legend className="text-xs font-bold uppercase tracking-wider text-slate-700">Nhu cầu</legend>
                    <div className="mt-2 grid grid-cols-2 gap-3">
                      {(['SALE', 'RENT'] as const).map((value) => (
                        <button
                          key={value}
                          type="button"
                          aria-pressed={fields.purpose === value}
                          onClick={() => update('purpose', value)}
                          className={`flex min-h-11 items-center gap-3 rounded-xl border p-4 text-left text-sm font-bold transition-all ${
                            fields.purpose === value
                              ? 'border-emerald-500 bg-emerald-50 text-emerald-900 shadow-sm'
                              : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                          }`}
                        >
                          {value === 'SALE' ? (
                            <Building2 className="h-5 w-5 text-emerald-600" aria-hidden="true" />
                          ) : (
                            <Key className="h-5 w-5 text-emerald-600" aria-hidden="true" />
                          )}
                          {value === 'SALE' ? 'Bán' : 'Cho thuê'}
                        </button>
                      ))}
                    </div>
                  </fieldset>
                  <FormField label="Loại bất động sản" required id="field-propertyType">
                    {(control) => (
                      <Select
                        {...control}
                        options={PROPERTY_TYPES}
                        value={fields.propertyType}
                        onChange={(e) => update('propertyType', e.target.value)}
                      />
                    )}
                  </FormField>
                  <FormField
                    label="Tiêu đề"
                    required
                    id="field-title"
                    hint="10–200 ký tự: loại nhà, số phòng, khu vực. Không ghi số điện thoại."
                    error={shownError('title')}
                  >
                    {(control) => (
                      <TextInput
                        {...control}
                        value={fields.title}
                        maxLength={200}
                        onBlur={() => touch('title')}
                        onChange={(e) => update('title', e.target.value)}
                      />
                    )}
                  </FormField>
                  <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
                    <FormField
                      label={rent ? 'Giá thuê mỗi tháng (VNĐ)' : 'Giá bán (VNĐ)'}
                      required
                      id="field-priceVnd"
                      hint={price && fields.priceVnd ? `= ${formatMoney(price)}` : undefined}
                      error={shownError('priceVnd')}
                    >
                      {(control) => (
                        <TextInput
                          {...control}
                          inputMode="numeric"
                          autoComplete="off"
                          placeholder="Ví dụ: 3950000000"
                          value={groupDigits(fields.priceVnd)}
                          onBlur={() => touch('priceVnd')}
                          onChange={(e) => update('priceVnd', parseInteger(e.target.value))}
                        />
                      )}
                    </FormField>
                    <FormField
                      label="Diện tích (m²)"
                      required
                      id="field-areaM2"
                      hint={
                        fields.priceVnd && fields.areaM2 ? (
                          <UnitPriceText
                            unitPrice={unitPriceFromArea(fields.priceVnd, fields.areaM2, fields.purpose)}
                          />
                        ) : undefined
                      }
                      error={shownError('areaM2')}
                    >
                      {(control) => (
                        <TextInput
                          {...control}
                          inputMode="decimal"
                          autoComplete="off"
                          defaultValue={fields.areaM2 ?? ''}
                          key={`area-${formKey}`}
                          onBlur={() => touch('areaM2')}
                          onChange={(e) => update('areaM2', parseDecimal(e.target.value))}
                        />
                      )}
                    </FormField>
                  </div>
                  {rent && (
                    <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
                      <FormField label="Tiền đặt cọc (VNĐ)" id="field-depositVnd" error={shownError('depositVnd')}>
                        {(control) => (
                          <TextInput
                            {...control}
                            inputMode="numeric"
                            value={groupDigits(fields.depositVnd)}
                            onChange={(e) => update('depositVnd', parseInteger(e.target.value))}
                          />
                        )}
                      </FormField>
                      <FormField
                        label="Phí dịch vụ mỗi tháng (VNĐ)"
                        id="field-monthlyServiceFeeVnd"
                        hint="Phí quản lý, dịch vụ tòa nhà (nếu có)"
                        error={shownError('monthlyServiceFeeVnd')}
                      >
                        {(control) => (
                          <TextInput
                            {...control}
                            inputMode="numeric"
                            value={groupDigits(fields.monthlyServiceFeeVnd)}
                            onChange={(e) => update('monthlyServiceFeeVnd', parseInteger(e.target.value))}
                          />
                        )}
                      </FormField>
                    </div>
                  )}
                  <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
                    {(['bedrooms', 'bathrooms', 'floors'] as const).map((key) => (
                      <FormField
                        key={key}
                        label={{ bedrooms: 'Phòng ngủ', bathrooms: 'Phòng tắm', floors: 'Số tầng' }[key]}
                        id={`field-${key}`}
                      >
                        {(control) => (
                          <TextInput
                            {...control}
                            inputMode="numeric"
                            value={fields[key] ?? ''}
                            onChange={(e) => update(key, parseInteger(e.target.value))}
                          />
                        )}
                      </FormField>
                    ))}
                  </div>
                  <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
                    <FormField label="Giấy tờ pháp lý" id="field-legalStatusCode" error={shownError('legalStatusCode')}>
                      {(control) => (
                        <Select
                          {...control}
                          placeholder="Chưa chọn"
                          options={LEGAL_OPTIONS}
                          value={fields.legalStatusCode}
                          onChange={(e) => update('legalStatusCode', e.target.value as DraftFields['legalStatusCode'])}
                        />
                      )}
                    </FormField>
                    <FormField
                      label="Chi tiết giấy tờ"
                      id="field-legalStatus"
                      required={fields.legalStatusCode === 'OTHER'}
                      error={shownError('legalStatus')}
                    >
                      {(control) => (
                        <TextInput
                          {...control}
                          maxLength={100}
                          placeholder="Ví dụ: sổ hồng riêng, đã hoàn công"
                          value={fields.legalStatus}
                          onBlur={() => touch('legalStatus')}
                          onChange={(e) => update('legalStatus', e.target.value)}
                        />
                      )}
                    </FormField>
                    <FormField label="Nội thất" id="field-furnishing">
                      {(control) => (
                        <Select
                          {...control}
                          placeholder="Chưa chọn"
                          options={FURNISHING_OPTIONS}
                          value={fields.furnishing}
                          onChange={(e) => update('furnishing', e.target.value as DraftFields['furnishing'])}
                        />
                      )}
                    </FormField>
                    <FormField label="Hướng nhà" id="field-direction">
                      {(control) => (
                        <Select
                          {...control}
                          placeholder="Chưa chọn"
                          options={DIRECTIONS}
                          value={fields.direction}
                          onChange={(e) => update('direction', e.target.value)}
                        />
                      )}
                    </FormField>
                  </div>
                  <FormField
                    label="Mô tả"
                    id="field-description"
                    hint={`${fields.description.length} ký tự; nên từ 200 ký tự. Không ghi số điện thoại, email hay link Zalo/Facebook.`}
                    error={shownError('description')}
                  >
                    {(control) => (
                      <TextArea
                        {...control}
                        rows={6}
                        maxLength={5000}
                        value={fields.description}
                        onChange={(e) => update('description', e.target.value)}
                      />
                    )}
                  </FormField>
                </>
              )}

              {step === 2 && (
                <>
                  <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
                    <FormField label="Mã tỉnh/thành" id="field-provinceCode" error={shownError('provinceCode')}>
                      {(control) => (
                        <TextInput
                          {...control}
                          value={fields.provinceCode}
                          onChange={(e) => update('provinceCode', e.target.value)}
                        />
                      )}
                    </FormField>
                    <FormField
                      label="Mã quận/huyện"
                      id="field-districtCode"
                      hint="Dùng để so sánh giá trong khu vực"
                      error={shownError('districtCode')}
                    >
                      {(control) => (
                        <TextInput
                          {...control}
                          value={fields.districtCode}
                          onChange={(e) => update('districtCode', e.target.value)}
                        />
                      )}
                    </FormField>
                    <FormField label="Mã phường/xã" id="field-wardCode" error={shownError('wardCode')}>
                      {(control) => (
                        <TextInput
                          {...control}
                          value={fields.wardCode}
                          onChange={(e) => update('wardCode', e.target.value)}
                        />
                      )}
                    </FormField>
                  </div>
                  <FormField
                    label="Địa chỉ hiển thị"
                    id="field-addressSummary"
                    hint="Chỉ tên đường/phường/dự án; không cần số nhà."
                    error={shownError('addressSummary')}
                  >
                    {(control) => (
                      <TextInput
                        {...control}
                        maxLength={255}
                        value={fields.addressSummary}
                        onChange={(e) => update('addressSummary', e.target.value)}
                      />
                    )}
                  </FormField>
                  <section className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="flex items-center gap-2 text-body-sm font-semibold text-on-surface">
                        <MapPin className="h-4 w-4 text-emerald-600" aria-hidden="true" />
                        {fields.publicLatitude != null && fields.publicLongitude != null
                          ? `Đã chọn vị trí (${fields.publicLatitude.toFixed(4)}, ${fields.publicLongitude.toFixed(4)})`
                          : 'Chưa chọn vị trí trên bản đồ'}
                      </p>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setMapOpen((open) => !open)}
                        aria-expanded={mapOpen}
                      >
                        {mapOpen ? 'Ẩn bản đồ' : 'Chọn trên bản đồ'}
                      </Button>
                    </div>
                    <p className="mt-2 text-label text-on-surface-variant">
                      Vị trí công khai được làm tròn để bảo vệ riêng tư.
                    </p>
                    {mapOpen && (
                      <Suspense fallback={<Skeleton className="mt-3 h-72 w-full" />}>
                        <LocationPicker
                          latitude={fields.publicLatitude}
                          longitude={fields.publicLongitude}
                          onChange={(lat, lng) =>
                            setFields((current) => ({ ...current, publicLatitude: lat, publicLongitude: lng }))
                          }
                        />
                      </Suspense>
                    )}
                  </section>
                </>
              )}

              {step === 3 && (
                <section aria-describedby="images-hint">
                  <p id="images-hint" className="text-body-sm text-on-surface-variant">
                    {fields.imageUrls.length} ảnh · nên có ít nhất 5 ảnh thật (phòng khách, phòng ngủ, bếp, mặt tiền).
                    Ảnh đầu tiên là ảnh bìa.
                  </p>
                  {uploadError && <InlineFeedback kind="error" title={uploadError} className="mt-3" />}
                  <ul className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {fields.imageUrls.map((url, index) => (
                      <li
                        key={url}
                        className="relative overflow-hidden rounded-xl border border-slate-200 bg-slate-100"
                      >
                        <img
                          src={displayMedia(url)}
                          alt={`Ảnh ${index + 1}`}
                          className="aspect-video w-full object-cover"
                          loading="lazy"
                        />
                        {index === 0 && (
                          <span className="absolute left-1 top-1 rounded bg-emerald-600 px-1.5 py-0.5 text-xs font-bold text-white shadow">
                            Ảnh bìa
                          </span>
                        )}
                        <div className="flex justify-between bg-surface p-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Đưa ảnh ${index + 1} lên trước`}
                            disabled={index === 0}
                            onClick={() => moveImage(index, -1)}
                          >
                            <ArrowLeft className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Xóa ảnh ${index + 1}`}
                            onClick={() =>
                              setFields((current) => ({
                                ...current,
                                imageUrls: current.imageUrls.filter((item) => item !== url),
                              }))
                            }
                          >
                            <X className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Đưa ảnh ${index + 1} ra sau`}
                            disabled={index === fields.imageUrls.length - 1}
                            onClick={() => moveImage(index, 1)}
                          >
                            <ArrowRight className="h-4 w-4" />
                          </Button>
                        </div>
                      </li>
                    ))}
                  </ul>
                  <label className="mt-4 flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-emerald-500 bg-emerald-50/50 px-5 py-6 text-center focus-within:ring-2 focus-within:ring-emerald-600">
                    <input
                      type="file"
                      multiple
                      accept="image/jpeg,image/png,image/webp"
                      className="sr-only"
                      disabled={uploading || fields.imageUrls.length >= 20}
                      onChange={(event) => {
                        void handleUpload(event.target.files);
                        event.target.value = '';
                      }}
                    />
                    {uploading ? (
                      <Loader2 className="h-7 w-7 animate-spin text-emerald-700" aria-hidden="true" />
                    ) : (
                      <Upload className="h-7 w-7 text-emerald-700" aria-hidden="true" />
                    )}
                    <span className="text-sm font-bold text-emerald-950">
                      {uploading ? 'Đang tải ảnh…' : 'Chọn ảnh từ thiết bị'}
                    </span>
                    <span className="text-xs leading-relaxed text-emerald-800">
                      JPEG, PNG, WebP · tối đa 10 MB/ảnh · 20 ảnh
                    </span>
                  </label>
                </section>
              )}

              {step === 4 && (
                <section aria-label="Xem trước tin đăng" data-testid="listing-preview">
                  {!currentId ? (
                    <InlineFeedback kind="info" title="Chưa có bản nháp">
                      Điền tiêu đề, giá và diện tích ở bước 1 để lưu nháp và xem trước.
                    </InlineFeedback>
                  ) : previewError ? (
                    <InlineFeedback kind="error" title={previewError} />
                  ) : !preview ? (
                    <Skeleton className="h-64 w-full" />
                  ) : (
                    <article className="mx-auto max-w-md overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                      {preview.images[0] ? (
                        <img
                          src={displayMedia(preview.images[0].url)}
                          alt="Ảnh bìa"
                          className="aspect-video w-full object-cover"
                        />
                      ) : (
                        <div className="flex aspect-video items-center justify-center bg-surface-container text-on-surface-variant">
                          <ImageIcon className="h-8 w-8" aria-hidden="true" /> Chưa có ảnh
                        </div>
                      )}
                      <div className="p-4">
                        <p className="text-lg font-black text-emerald-700">
                          <Money price={preview.price} />
                        </p>
                        <p className="text-body-sm text-on-surface-variant">
                          {preview.areaM2} m² {preview.unitPrice && <UnitPriceText unitPrice={preview.unitPrice} />}
                        </p>
                        <h3 className="mt-2 text-body font-semibold text-on-surface">{preview.title}</h3>
                        <p className="mt-1 text-body-sm text-on-surface-variant">
                          {preview.location.addressSummary || 'Chưa có địa chỉ'}
                        </p>
                        {preview.rentTerms && (
                          <p className="mt-2 text-body-sm">
                            Đặt cọc: {formatRentTerms(preview.rentTerms).deposit ?? 'chưa ghi'} · Phí dịch vụ:{' '}
                            {formatRentTerms(preview.rentTerms).monthlyServiceFee ?? 'chưa ghi'}
                          </p>
                        )}
                        {preview.legal?.label && <p className="mt-1 text-body-sm">Pháp lý: {preview.legal.label}</p>}
                        {preview.description && (
                          <p className="mt-3 whitespace-pre-line text-body-sm [overflow-wrap:anywhere]">{preview.description}</p>
                        )}
                      </div>
                    </article>
                  )}
                  <p className="mt-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-relaxed text-amber-900">
                    <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    Gửi duyệt nghĩa là bạn xác nhận thông tin, giá và giấy tờ là đúng. Tin có thể bị từ chối hoặc tạm ẩn
                    nếu sai sự thật.
                  </p>
                  {submitError && <InlineFeedback kind="error" title={submitError} className="mt-3" />}
                </section>
              )}

              <div className="flex justify-between gap-3 border-t border-slate-100 pt-4">
                {step > 1 ? (
                  <Button
                    variant="outline"
                    leftIcon={<ArrowLeft className="h-4 w-4" />}
                    onClick={() => void leaveStep((step - 1) as Step)}
                  >
                    Quay lại
                  </Button>
                ) : (
                  <span />
                )}
                {step < 4 ? (
                  <Button
                    rightIcon={<ArrowRight className="h-4 w-4" />}
                    onClick={() => void leaveStep((step + 1) as Step)}
                  >
                    Tiếp tục: {STEPS[step].label}
                  </Button>
                ) : (
                  <Button
                    leftIcon={<Send className="h-4 w-4" />}
                    isLoading={submitting}
                    onClick={() => void handleSubmit()}
                  >
                    Gửi duyệt
                  </Button>
                )}
              </div>
            </form>
          </Card>

          <aside className="flex flex-col gap-6 lg:col-span-4">
            <QualityChecklist report={step === 4 && preview ? preview.quality : (loaded?.quality ?? null)} />
            <div className="rounded-xl bg-slate-900 p-4 text-white shadow-md">
              <p className="mb-2 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-400">
                <HelpCircle className="h-4 w-4" aria-hidden="true" />
                Chính sách duyệt tin an toàn
              </p>
              <p className="text-xs leading-relaxed text-slate-300">
                Mỗi tin đăng sau khi gửi sẽ vào hàng đợi kiểm duyệt. Nếu cần bổ sung nội dung, trạng thái tin sẽ được
                cập nhật trong Tin đăng của tôi.
              </p>
            </div>
          </aside>
        </div>
      </div>

      <Dialog
        open={autosave.state.kind === 'conflict'}
        onClose={() => void resolveConflict(false)}
        title="Tin vừa được lưu ở nơi khác"
        description="Có thể bạn đang mở tin này ở tab hoặc thiết bị khác. Chọn bản muốn giữ."
        footer={
          <>
            <Button variant="outline" onClick={() => void resolveConflict(false)}>
              Tải bản mới nhất
            </Button>
            <Button onClick={() => void resolveConflict(true)}>Giữ nội dung đang sửa</Button>
          </>
        }
      >
        <p className="text-body-sm text-on-surface-variant">
          “Tải bản mới nhất” bỏ các thay đổi chưa lưu trên trang này. “Giữ nội dung đang sửa” ghi đè bản đã lưu ở nơi
          khác.
        </p>
      </Dialog>
    </div>
  );
};

export default CreateListingPage;
