import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { BellRing, Heart, Lock, Search, Users } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { SkeletonText } from '@/shared/ui/Skeleton';
import { Switch } from '@/shared/ui/Switch';
import { CATEGORY_LABELS, engagementApi, problemDetail, type NotificationPreference } from '../api';

/**
 * `/account#thong-bao` (UI-13): in-app and e-mail choice per category. Account and own-listing notices always reach
 * the notification centre (switch disabled, with the reason).
 */
export function NotificationPreferencesSection() {
  const location = useLocation();
  const [items, setItems] = useState<NotificationPreference[] | null>(null);
  const [draft, setDraft] = useState<NotificationPreference[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  const load = () => {
    setStatus('loading');
    engagementApi
      .preferences()
      .then((value) => {
        setItems(value);
        setDraft(value);
        setStatus('ready');
      })
      .catch(() => setStatus('error'));
  };
  useEffect(load, []);
  useEffect(() => {
    if (location.hash === '#thong-bao' && status === 'ready') {
      document.getElementById('thong-bao')?.scrollIntoView({ block: 'start' });
    }
  }, [location.hash, status]);

  const change = (category: NotificationPreference['category'], patch: Partial<NotificationPreference>) =>
    setDraft((current) => current.map((item) => (item.category === category ? { ...item, ...patch } : item)));
  const dirty = JSON.stringify(items) !== JSON.stringify(draft);

  const save = async () => {
    setSaving(true);
    setFeedback(null);
    try {
      const saved = await engagementApi.savePreferences(
        draft.map(({ category, inApp, email }) => ({ category, inApp, email })),
      );
      setItems(saved);
      setDraft(saved);
      setFeedback({ kind: 'success', text: 'Đã lưu tùy chọn thông báo.' });
    } catch (error) {
      setFeedback({ kind: 'error', text: problemDetail(error, 'Chưa lưu được tùy chọn. Vui lòng thử lại.') });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      id="thong-bao"
      aria-labelledby="thong-bao-heading"
      className="mt-6 scroll-mt-24 rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5 md:p-7"
    >
      <h2 id="thong-bao-heading" className="flex items-center gap-2 text-lg font-bold text-on-surface">
        <BellRing className="h-5 w-5 text-primary" aria-hidden="true" /> Tùy chọn thông báo
      </h2>
      <p className="mt-1 text-sm text-on-surface-variant">
        Chọn nhận thông báo trong ứng dụng và qua email cho từng loại. Mọi email cảnh báo đều có liên kết ngừng nhận.
      </p>
      {status === 'loading' ? (
        <SkeletonText lines={6} className="mt-4" />
      ) : status === 'error' ? (
        <ErrorState title="Không tải được tùy chọn thông báo" onRetry={load} headingLevel={3} />
      ) : (
        <ul className="mt-4 divide-y divide-outline-variant/50">
          {draft.map((item) => {
            const label = CATEGORY_LABELS[item.category];
            return (
              <li key={item.category} className="grid gap-3 py-4 sm:grid-cols-[1fr_auto_auto] sm:items-center">
                <div>
                  <h3 className="text-sm font-semibold text-on-surface">{label.title}</h3>
                  <p className="text-xs text-on-surface-variant">
                    {label.description}
                    {item.mandatoryInApp &&
                      ' Luôn hiển thị trong trung tâm thông báo vì liên quan đến tài khoản của bạn.'}
                  </p>
                </div>
                <Switch
                  checked={item.inApp}
                  disabled={item.mandatoryInApp}
                  onCheckedChange={(value) => change(item.category, { inApp: value })}
                  label="Trong ứng dụng"
                  aria-label={`${label.title}: thông báo trong ứng dụng`}
                />
                <Switch
                  checked={item.email}
                  onCheckedChange={(value) => change(item.category, { email: value })}
                  label="Email"
                  aria-label={`${label.title}: email`}
                />
              </li>
            );
          })}
        </ul>
      )}
      {feedback && <InlineFeedback kind={feedback.kind} title={feedback.text} className="mt-3" />}
      {status === 'ready' && (
        <div className="mt-4 flex gap-2">
          <Button onClick={save} isLoading={saving} disabled={!dirty}>
            Lưu tùy chọn
          </Button>
          {dirty && (
            <Button variant="ghost" onClick={() => setDraft(items ?? [])}>
              Hoàn tác
            </Button>
          )}
        </div>
      )}
    </section>
  );
}

/** What the account shares and keeps (UI-13 "quyền riêng tư"): links to manage it, no invented policy text. */
export function PrivacySection() {
  return (
    <section
      aria-labelledby="rieng-tu-heading"
      className="mt-6 rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5 md:p-7"
    >
      <h2 id="rieng-tu-heading" className="flex items-center gap-2 text-lg font-bold text-on-surface">
        <Lock className="h-5 w-5 text-primary" aria-hidden="true" /> Quyền riêng tư
      </h2>
      <ul className="mt-3 flex flex-col gap-3 text-sm text-on-surface">
        <li className="flex gap-3">
          <Users className="mt-0.5 h-5 w-5 shrink-0 text-on-surface-variant" aria-hidden="true" />
          <span>
            Danh sách chia sẻ: người có liên kết chỉ thấy tên danh sách, tên gọi của bạn và các tin đang hiển thị; không
            thấy email hay số điện thoại. Thu hồi hoặc tạo lại liên kết, xóa thành viên trong{' '}
            <Link className="font-semibold text-primary underline" to="/saved?tab=shortlists">
              Danh sách chia sẻ
            </Link>
            .
          </span>
        </li>
        <li className="flex gap-3">
          <Heart className="mt-0.5 h-5 w-5 shrink-0 text-on-surface-variant" aria-hidden="true" />
          <span>
            Tin đã lưu chỉ bạn nhìn thấy. Quản lý trong{' '}
            <Link className="font-semibold text-primary underline" to="/saved">
              Tin đã lưu
            </Link>
            .
          </span>
        </li>
        <li className="flex gap-3">
          <Search className="mt-0.5 h-5 w-5 shrink-0 text-on-surface-variant" aria-hidden="true" />
          <span>
            Tìm kiếm đã lưu dùng để gửi cảnh báo cho riêng bạn; tắt cảnh báo hoặc xóa trong{' '}
            <Link className="font-semibold text-primary underline" to="/saved?tab=searches">
              Tìm kiếm đã lưu
            </Link>
            . Thông báo đã đọc được xóa sau 180 ngày.
          </span>
        </li>
      </ul>
    </section>
  );
}
