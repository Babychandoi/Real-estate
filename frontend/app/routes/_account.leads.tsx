import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { AlarmClock, CalendarClock, Inbox, RefreshCw, Search, X } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { fetchInbox, fetchTeam, type InboxFilters } from '@/entities/lead/api/leadApi';
import {
  APPOINTMENT_STATUS_LABELS,
  LEAD_STATUS_LABELS,
  formatDateTime,
  formatSlot,
  problemMessage,
  responseTime,
} from '@/entities/lead/model/labels';
import type { LeadItem, LeadPage, LeadStatus, TeamMember } from '@/entities/lead/model/types';
import { useAuth } from '@/shared/auth/AuthContext';
import { Badge, type BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Pagination } from '@/shared/ui/Pagination';
import { Select } from '@/shared/ui/Select';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Tabs } from '@/shared/ui/Tabs';
import { TextInput } from '@/shared/ui/TextInput';

// Loaded on demand: the detail dialog (with appointments/history) opens on click, the report on its tab (bundle budget F15.2).
const LeadDetailDialog = lazy(() =>
  import('@/features/lead/ui/LeadDetailDialog').then((m) => ({ default: m.LeadDetailDialog })),
);
const LeadReportView = lazy(() =>
  import('@/features/lead/ui/LeadReportView').then((m) => ({ default: m.LeadReportView })),
);

const PAGE_SIZE = 20;
const STATUS_VARIANT: Record<LeadStatus, BadgeVariant> = {
  NEW: 'warning',
  CONTACTED: 'info',
  APPOINTED: 'primary',
  CLOSED: 'success',
  SPAM: 'neutral',
  WITHDRAWN: 'neutral',
};

function LeadCard({ lead, onOpen }: { lead: LeadItem; onOpen: () => void }) {
  const responded = responseTime(lead.createdAt, lead.firstResponseAt);
  return (
    <article className="flex flex-col gap-3 rounded-lg border border-outline-variant bg-surface p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-body font-semibold text-on-surface">{lead.fullName}</h3>
          <Badge variant={STATUS_VARIANT[lead.status]}>{LEAD_STATUS_LABELS[lead.status]}</Badge>
          {lead.overdue && (
            <Badge variant="error" icon={<AlarmClock className="h-3.5 w-3.5" />}>
              Quá hạn phản hồi
            </Badge>
          )}
          {lead.qualification && (
            <Badge variant={lead.qualification === 'QUALIFIED' ? 'success' : 'neutral'}>
              {lead.qualification === 'QUALIFIED' ? 'Đủ điều kiện' : 'Không đủ điều kiện'}
            </Badge>
          )}
        </div>
        <p className="mt-1 truncate text-body-sm text-on-surface-variant">
          {lead.requestType === 'VIEWING' ? 'Muốn hẹn xem' : 'Cần tư vấn'} · {lead.listingTitle}
        </p>
        <p className="text-label font-normal text-on-surface-variant">
          Gửi {formatDateTime(lead.createdAt)}
          {responded
            ? ` · phản hồi sau ${responded}`
            : lead.status === 'NEW'
              ? ` · hạn ${formatDateTime(lead.responseDueAt)}`
              : ''}
          {lead.assigneeName ? ` · phụ trách: ${lead.assigneeName}` : ''}
        </p>
        {lead.openAppointment && (
          <p className="mt-1 flex items-center gap-1 text-label text-on-surface">
            <CalendarClock className="h-3.5 w-3.5" aria-hidden="true" />
            {APPOINTMENT_STATUS_LABELS[lead.openAppointment.status]}
            {lead.openAppointment.startsAt && lead.openAppointment.endsAt
              ? `: ${formatSlot(lead.openAppointment.startsAt, lead.openAppointment.endsAt)}`
              : ''}
          </p>
        )}
      </div>
      <Button size="sm" onClick={onOpen} className="shrink-0" aria-label={`Xử lý yêu cầu của ${lead.fullName}`}>
        Xử lý
      </Button>
    </article>
  );
}

function LeadInbox() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const listingId = params.get('listingId') ?? undefined;
  const [filters, setFilters] = useState<InboxFilters>({
    status: '',
    requestType: '',
    qualification: '',
    overdue: false,
    q: '',
  });
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<LeadPage | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<string | null>(params.get('lead'));
  const [team, setTeam] = useState<TeamMember[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await fetchInbox({ ...filters, listingId }, page, PAGE_SIZE));
    } catch (caught) {
      setError(problemMessage(caught, 'Không tải được hộp thư. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }, [filters, listingId, page]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    if (user?.role === 'BROKER' || user?.role === 'ADMIN')
      fetchTeam()
        .then(setTeam)
        .catch(() => setTeam([]));
  }, [user?.role]);

  const update = (patch: Partial<InboxFilters>) => {
    setPage(0);
    setFilters((current) => ({ ...current, ...patch }));
  };
  const listingTitle = listingId ? result?.items[0]?.listingTitle : undefined;

  return (
    <div className="flex flex-col gap-4">
      <form
        className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          update({ q: query.trim() });
        }}
      >
        <TextInput
          aria-label="Tìm theo tên hoặc lời nhắn"
          placeholder="Tìm theo tên hoặc lời nhắn"
          leadingIcon={<Search className="h-4 w-4" />}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <Select
          aria-label="Trạng thái"
          options={[
            { value: '', label: 'Mọi trạng thái' },
            ...Object.entries(LEAD_STATUS_LABELS).map(([value, label]) => ({ value, label })),
          ]}
          value={filters.status}
          onChange={(event) => update({ status: event.target.value as LeadStatus | '' })}
        />
        <Select
          aria-label="Nhu cầu"
          options={[
            { value: '', label: 'Mọi nhu cầu' },
            { value: 'VIEWING', label: 'Muốn hẹn xem' },
            { value: 'CONSULTATION', label: 'Cần tư vấn' },
          ]}
          value={filters.requestType}
          onChange={(event) => update({ requestType: event.target.value as InboxFilters['requestType'] })}
        />
        <Select
          aria-label="Đánh giá"
          options={[
            { value: '', label: 'Mọi đánh giá' },
            { value: 'QUALIFIED', label: 'Đủ điều kiện' },
            { value: 'UNQUALIFIED', label: 'Không đủ điều kiện' },
            { value: 'UNSET', label: 'Chưa đánh giá' },
          ]}
          value={filters.qualification}
          onChange={(event) => update({ qualification: event.target.value as InboxFilters['qualification'] })}
        />
        <Button type="submit" variant="outline">
          Tìm
        </Button>
      </form>
      <div className="flex flex-wrap items-center gap-3">
        <Checkbox
          label="Chỉ lead quá hạn phản hồi"
          checked={Boolean(filters.overdue)}
          onChange={(event) => update({ overdue: event.target.checked })}
        />
        {listingId && (
          <Button
            size="sm"
            variant="ghost"
            leftIcon={<X className="h-4 w-4" />}
            onClick={() => {
              params.delete('listingId');
              setParams(params);
            }}
          >
            Đang lọc theo tin{listingTitle ? `: ${listingTitle}` : ''} — bỏ lọc
          </Button>
        )}
        <Button
          size="sm"
          variant="ghost"
          leftIcon={<RefreshCw className="h-4 w-4" />}
          onClick={() => void load()}
          disabled={loading}
        >
          Làm mới
        </Button>
      </div>
      {result && (
        <p className="text-body-sm text-on-surface-variant" aria-live="polite">
          {result.totalElements} yêu cầu phù hợp
          {Object.entries(result.statusCounts)
            .filter(([, count]) => count)
            .map(([status, count]) => ` · ${LEAD_STATUS_LABELS[status as LeadStatus]}: ${count}`)
            .join('')}
        </p>
      )}
      {error ? (
        <ErrorState title="Không tải được hộp thư" description={error} onRetry={() => void load()} />
      ) : loading && !result ? (
        <div className="flex flex-col gap-3" role="status" aria-label="Đang tải">
          {[0, 1, 2].map((index) => (
            <Skeleton key={index} className="h-24" />
          ))}
        </div>
      ) : result && result.items.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title="Không có yêu cầu phù hợp"
          description="Khi có người gửi yêu cầu liên hệ cho tin của bạn, yêu cầu sẽ xuất hiện tại đây."
        />
      ) : (
        <div className="flex flex-col gap-3">
          {result?.items.map((lead) => (
            <LeadCard key={lead.id} lead={lead} onOpen={() => setSelected(lead.id)} />
          ))}
        </div>
      )}
      {result && result.totalPages > 1 && (
        <Pagination
          page={page + 1}
          pageCount={result.totalPages}
          onPageChange={(next) => setPage(next - 1)}
          label="Phân trang hộp thư"
        />
      )}
      {selected && (
        <Suspense fallback={null}>
          <LeadDetailDialog
            leadId={selected}
            onClose={() => setSelected(null)}
            onChanged={() => void load()}
            team={team}
            currentUserId={user?.id ?? ''}
          />
        </Suspense>
      )}
    </div>
  );
}

/** `/my-leads` (UI-09): one server-filtered inbox over every listing of the owner, plus the qualified-lead report. */
export function MyLeadsPage() {
  const [tab, setTab] = useState<'inbox' | 'report'>('inbox');
  return (
    <div className="min-h-full bg-surface px-4 py-8 md:px-8 lg:py-10" data-ready="true">
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <header>
          <h1 className="text-headline-lg text-on-surface">Hộp thư khách quan tâm</h1>
          <p className="mt-2 max-w-3xl text-body-sm text-on-surface-variant">
            Mọi yêu cầu liên hệ cho các tin của bạn và các lead được giao cho bạn. Số điện thoại của khách chỉ hiện khi
            bạn bấm xem và khách đã đồng ý; thông tin liên hệ của bạn không được gửi cho khách.
          </p>
        </header>
        <Tabs
          label="Hộp thư khách quan tâm"
          value={tab}
          onChange={setTab}
          items={[
            { id: 'inbox', label: 'Yêu cầu', content: <LeadInbox /> },
            {
              id: 'report',
              label: 'Hiệu quả',
              content:
                tab === 'report' ? (
                  <Suspense fallback={<p className="text-body-sm">Đang tải báo cáo…</p>}>
                    <LeadReportView />
                  </Suspense>
                ) : null,
            },
          ]}
        />
      </div>
    </div>
  );
}

export default MyLeadsPage;
