import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Building2, CalendarClock, ExternalLink, RefreshCw } from 'lucide-react';
import { fetchInquiries, fetchInquiryHistory, withdrawInquiry } from '@/entities/lead/api/leadApi';
import {
  APPOINTMENT_STATUS_LABELS,
  INQUIRY_STATUS_LABELS,
  formatDateTime,
  formatSlot,
  isVersionConflict,
  problemMessage,
  responseTime,
} from '@/entities/lead/model/labels';
import type { InquiryItem, LeadHistoryEntry, LeadPage, LeadStatus } from '@/entities/lead/model/types';
import { BecomeOwnerCard } from '@/features/owner-onboarding/BecomeOwner';
import { useAuth } from '@/shared/auth/AuthContext';
import { Badge, type BadgeVariant } from '@/shared/ui/Badge';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Skeleton } from '@/shared/ui/Skeleton';
import { TextArea } from '@/shared/ui/TextInput';

// Appointments and history load when a card is expanded (bundle budget F15.2).
const AppointmentPanel = lazy(() =>
  import('@/features/lead/ui/AppointmentPanel').then((m) => ({ default: m.AppointmentPanel })),
);
const LeadHistory = lazy(() => import('@/features/lead/ui/LeadHistory').then((m) => ({ default: m.LeadHistory })));

const PAGE_SIZE = 12;
const OPEN: LeadStatus[] = ['NEW', 'CONTACTED', 'APPOINTED'];
const VARIANT: Record<LeadStatus, BadgeVariant> = {
  NEW: 'warning',
  CONTACTED: 'info',
  APPOINTED: 'primary',
  CLOSED: 'neutral',
  SPAM: 'neutral',
  WITHDRAWN: 'neutral',
};

function InquiryCard({ item, onChanged }: { item: InquiryItem; onChanged: () => void }) {
  const [expanded, setExpanded] = useState(false);
  const [history, setHistory] = useState<LeadHistoryEntry[] | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const open = OPEN.includes(item.status);
  const responded = responseTime(item.createdAt, item.firstResponseAt);

  useEffect(() => {
    if (!expanded) return;
    setHistory(null);
    fetchInquiryHistory(item.id)
      .then(setHistory)
      .catch(() => setHistory([]));
  }, [expanded, item.id, item.version]);

  const withdraw = async () => {
    setBusy(true);
    setError('');
    try {
      await withdrawInquiry(item.id, reason.trim(), item.version);
      setWithdrawing(false);
      onChanged();
    } catch (caught) {
      setError(
        isVersionConflict(caught)
          ? 'Yêu cầu vừa được người đăng cập nhật. Đã tải lại; vui lòng kiểm tra rồi thử lại.'
          : problemMessage(caught, 'Chưa rút được yêu cầu.'),
      );
      onChanged();
    } finally {
      setBusy(false);
    }
  };

  return (
    <article className="flex min-w-0 flex-col gap-3 rounded-lg border border-outline-variant bg-surface p-4">
      <div className="flex gap-3">
        {item.listingImageUrl ? (
          <img src={item.listingImageUrl} alt="" className="h-20 w-28 shrink-0 rounded-lg object-cover" />
        ) : (
          <span className="grid h-20 w-28 shrink-0 place-items-center rounded-lg bg-surface-container">
            <Building2 className="h-6 w-6 text-on-surface-variant" aria-hidden="true" />
          </span>
        )}
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={VARIANT[item.status]}>{INQUIRY_STATUS_LABELS[item.status]}</Badge>
            <span className="text-label font-normal text-on-surface-variant">
              {item.requestType === 'VIEWING' ? 'Hẹn xem' : 'Tư vấn'}
            </span>
          </div>
          <h2 className="mt-1 line-clamp-2 text-body font-semibold text-on-surface">{item.listingTitle}</h2>
          <p className="text-label font-normal text-on-surface-variant">
            Gửi {formatDateTime(item.createdAt)} ·{' '}
            {responded
              ? `người đăng phản hồi sau ${responded}`
              : item.status === 'NEW'
                ? 'chưa có phản hồi'
                : 'đã xử lý'}
          </p>
          {!item.listingAvailable && (
            <p className="text-label font-normal text-on-surface-variant">Tin hiện không còn hiển thị công khai.</p>
          )}
        </div>
      </div>
      {item.openAppointment && (
        <p className="flex items-center gap-2 text-body-sm text-on-surface">
          <CalendarClock className="h-4 w-4" aria-hidden="true" />
          {APPOINTMENT_STATUS_LABELS[item.openAppointment.status]}
          {item.openAppointment.startsAt && item.openAppointment.endsAt
            ? `: ${formatSlot(item.openAppointment.startsAt, item.openAppointment.endsAt)}`
            : item.openAppointment.proposedBySide === 'OWNER_SIDE'
              ? ' — người đăng đã đề xuất giờ, mời bạn chọn'
              : ' — chờ người đăng xác nhận'}
        </p>
      )}
      {error && <InlineFeedback kind="error" title={error} />}
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Thu gọn' : 'Lịch hẹn & lịch sử'}
        </Button>
        <ButtonLink
          size="sm"
          variant="ghost"
          to={`/listings/${item.listingSlug || item.listingId}`}
          leftIcon={<ExternalLink className="h-4 w-4" />}
        >
          Xem lại tin
        </ButtonLink>
        {open && (
          <Button size="sm" variant="ghost" onClick={() => setWithdrawing(true)}>
            Rút yêu cầu
          </Button>
        )}
      </div>
      {expanded && (
        <Suspense fallback={<p className="text-body-sm">Đang tải…</p>}>
          <div className="flex flex-col gap-4 border-t border-outline-variant pt-3">
            <AppointmentPanel side="REQUESTER" leadId={item.id} canPropose={open} onChanged={onChanged} />
            <section aria-label="Lịch sử yêu cầu" className="flex flex-col gap-2">
              <h3 className="text-body font-semibold text-on-surface">Lịch sử</h3>
              <LeadHistory entries={history} statusLabels={INQUIRY_STATUS_LABELS} />
            </section>
          </div>
        </Suspense>
      )}
      <Dialog
        open={withdrawing}
        onClose={() => setWithdrawing(false)}
        title="Rút yêu cầu liên hệ?"
        description="Người đăng sẽ không thể xem số điện thoại của bạn cho yêu cầu này nữa; lịch hẹn đang mở sẽ bị hủy."
        footer={
          <>
            <Button variant="ghost" onClick={() => setWithdrawing(false)}>
              Giữ yêu cầu
            </Button>
            <Button variant="danger" isLoading={busy} onClick={() => void withdraw()}>
              Rút yêu cầu
            </Button>
          </>
        }
      >
        <TextArea
          aria-label="Lý do (không bắt buộc)"
          placeholder="Lý do (không bắt buộc), ví dụ: đã tìm được nhà"
          rows={3}
          maxLength={300}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </Dialog>
    </article>
  );
}

/** `/my-inquiries` (UI-10): real response state, appointments (confirm/reschedule/cancel), withdrawal, history. */
export function MyInquiriesPage() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<LeadPage<InquiryItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await fetchInquiries(page, PAGE_SIZE));
    } catch (caught) {
      setError(problemMessage(caught, 'Không thể tải các yêu cầu bạn đã gửi. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }, [page]);
  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="min-h-full bg-surface px-4 py-8 md:px-8 lg:py-10">
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <h1 className="text-headline-lg text-on-surface">Yêu cầu đã gửi</h1>
            <p className="mt-2 max-w-2xl text-body-sm text-on-surface-variant">
              Theo dõi phản hồi của người đăng, chọn hoặc đổi lịch hẹn xem và rút yêu cầu khi không còn cần. Mọi trao
              đổi diễn ra qua nền tảng; không chuyển tiền đặt cọc cho bất kỳ ai.
            </p>
          </div>
          <Button
            variant="outline"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void load()}
            disabled={loading}
          >
            Làm mới
          </Button>
        </header>
        {user?.role === 'USER' && <BecomeOwnerCard />}
        {error ? (
          <ErrorState title="Không tải được yêu cầu" description={error} onRetry={() => void load()} />
        ) : loading && !result ? (
          <div className="grid gap-4 sm:grid-cols-2" role="status" aria-label="Đang tải">
            {[0, 1, 2, 3].map((index) => (
              <Skeleton key={index} className="h-40" />
            ))}
          </div>
        ) : !result || result.items.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="Bạn chưa gửi yêu cầu nào"
            description="Khi bạn gửi yêu cầu hẹn xem hoặc tư vấn từ trang chi tiết bất động sản, yêu cầu sẽ xuất hiện tại đây."
            actions={<ButtonLink to="/search">Tìm bất động sản</ButtonLink>}
          />
        ) : (
          <section className="grid gap-4 lg:grid-cols-2">
            {result.items.map((item) => (
              <InquiryCard key={`${item.id}:${item.version}`} item={item} onChanged={() => void load()} />
            ))}
          </section>
        )}
        {result && result.totalPages > 1 && (
          <Pagination
            page={page + 1}
            pageCount={result.totalPages}
            onPageChange={(next) => setPage(next - 1)}
            label="Phân trang yêu cầu"
          />
        )}
      </div>
    </div>
  );
}

export default MyInquiriesPage;
