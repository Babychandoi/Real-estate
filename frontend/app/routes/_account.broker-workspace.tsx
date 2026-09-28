import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { AlarmClock, CalendarCheck, CalendarClock, ClipboardCheck, Inbox, UserMinus, UserPlus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { addTeamMember, fetchWorkspace, removeTeamMember, saveSla } from '@/entities/lead/api/leadApi';
import {
  LEAD_STATUS_LABELS,
  formatDateTime,
  formatMinutes,
  formatSlot,
  orNoData,
  problemMessage,
} from '@/entities/lead/model/labels';
import type { BrokerWorkspace, TeamMember, WorkspaceTask } from '@/entities/lead/model/types';
import { Badge } from '@/shared/ui/Badge';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { Dialog } from '@/shared/ui/Dialog';
import { ErrorState } from '@/shared/ui/ErrorState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { LoadingStatus } from '@/shared/ui/Skeleton';
import { Tabs } from '@/shared/ui/Tabs';
import { TextInput } from '@/shared/ui/TextInput';
import type { LucideIcon } from 'lucide-react';

const LeadReportView = lazy(() =>
  import('@/features/lead/ui/LeadReportView').then((m) => ({ default: m.LeadReportView })),
);

const HISTORY_TYPES: Record<string, string> = {
  CREATED: 'Lead mới',
  ASSIGNED: 'Phân công',
  STATUS_CHANGED: 'Đổi trạng thái',
  WITHDRAWN: 'Khách rút yêu cầu',
  QUALIFIED: 'Đánh giá lead',
};

function TaskGroup({
  title,
  icon: Icon,
  items,
  empty,
  detail,
}: {
  title: string;
  icon: LucideIcon;
  items: WorkspaceTask[];
  empty: string;
  detail: (task: WorkspaceTask) => React.ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3 rounded-lg border border-outline-variant bg-surface p-4" aria-label={title}>
      <h3 className="flex items-center gap-2 text-body font-semibold text-on-surface">
        <Icon className="h-4 w-4" aria-hidden="true" />
        {title}
        <Badge variant={items.length ? 'primary' : 'neutral'}>{items.length}</Badge>
      </h3>
      {items.length === 0 ? (
        <p className="text-body-sm text-on-surface-variant">{empty}</p>
      ) : (
        <ul className="flex flex-col divide-y divide-outline-variant">
          {items.map((task) => (
            <li key={task.appointmentId ?? task.leadId} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0">
                <p className="truncate text-body-sm font-semibold text-on-surface">{task.leadName}</p>
                <p className="truncate text-label font-normal text-on-surface-variant">{task.listingTitle}</p>
                <div className="text-label font-normal text-on-surface-variant">{detail(task)}</div>
              </div>
              <ButtonLink
                size="sm"
                variant="outline"
                to={`/my-leads?lead=${task.leadId}`}
                aria-label={`Mở yêu cầu của ${task.leadName}`}
              >
                Mở
              </ButtonLink>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Overview({ data, onChange }: { data: BrokerWorkspace; onChange: (next: BrokerWorkspace) => void }) {
  const [sla, setSla] = useState(data.sla);
  const [email, setEmail] = useState('');
  const [team, setTeam] = useState<TeamMember[]>(data.team);
  const [removing, setRemoving] = useState<TeamMember | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const metrics = data.slaMetrics;

  const act = async (action: () => Promise<void>, success: string, fallback: string) => {
    setBusy(true);
    setFeedback(null);
    try {
      await action();
      setFeedback({ kind: 'success', text: success });
    } catch (caught) {
      setFeedback({ kind: 'error', text: problemMessage(caught, fallback) });
    } finally {
      setBusy(false);
    }
  };

  const metricCards: Array<[string, string, string]> = [
    [
      'Phản hồi đầu tiên (trung vị)',
      orNoData(metrics.medianFirstResponseMinutes, formatMinutes),
      `${metrics.measuredResponses}/${metrics.leads} lead trong ${metrics.windowDays} ngày đã đo`,
    ],
    [
      '90% lead được phản hồi trong',
      orNoData(metrics.p90FirstResponseMinutes, formatMinutes),
      'Chỉ tính lead đã phản hồi',
    ],
    [
      'Phản hồi đúng hạn',
      orNoData(metrics.withinTargetPercent, (value) => `${value.toLocaleString('vi-VN')}%`),
      `Mục tiêu ${metrics.targetMinutes} phút`,
    ],
    ['Đang quá hạn', String(metrics.openBreaches), `${metrics.openNew} lead mới chưa phản hồi`],
  ];

  return (
    <div className="flex flex-col gap-6">
      {feedback && <InlineFeedback kind={feedback.kind} title={feedback.text} />}
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {metricCards.map(([label, value, hint]) => (
          <div key={label} className="rounded-lg border border-outline-variant bg-surface p-4">
            <dt className="text-label font-normal text-on-surface-variant">{label}</dt>
            <dd className="mt-1 text-headline-sm text-on-surface">{value}</dd>
            <dd className="text-label font-normal text-on-surface-variant">{hint}</dd>
          </div>
        ))}
      </dl>

      <section aria-labelledby="today-title" className="flex flex-col gap-3">
        <h2 id="today-title" className="text-headline-sm text-on-surface">
          Việc cần làm hôm nay
        </h2>
        <div className="grid gap-4 lg:grid-cols-2">
          <TaskGroup
            title="Cần phản hồi"
            icon={AlarmClock}
            items={data.tasks.respond}
            empty="Không có lead mới đang chờ."
            detail={(task) =>
              task.overdue ? (
                <span className="font-semibold text-error">Quá hạn từ {task.dueAt && formatDateTime(task.dueAt)}</span>
              ) : (
                <>Hạn phản hồi {task.dueAt && formatDateTime(task.dueAt)}</>
              )
            }
          />
          <TaskGroup
            title="Lịch hẹn hôm nay"
            icon={CalendarCheck}
            items={data.tasks.appointmentsToday}
            empty="Hôm nay không có lịch hẹn đã xác nhận."
            detail={(task) => (task.startsAt && task.endsAt ? formatSlot(task.startsAt, task.endsAt) : null)}
          />
          <TaskGroup
            title="Khách đề xuất giờ, chờ bạn xác nhận"
            icon={CalendarClock}
            items={data.tasks.proposalsAwaitingMe}
            empty="Không có đề xuất nào đang chờ."
            detail={(task) => (task.proposedAt ? `Đề xuất lúc ${formatDateTime(task.proposedAt)}` : null)}
          />
          <TaskGroup
            title="Cần ghi nhận kết quả buổi xem"
            icon={ClipboardCheck}
            items={data.tasks.outcomesToRecord}
            empty="Không có buổi xem nào chờ ghi nhận."
            detail={(task) => (task.startsAt && task.endsAt ? formatSlot(task.startsAt, task.endsAt) : null)}
          />
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section
          aria-labelledby="team-title"
          className="flex flex-col gap-3 rounded-lg border border-outline-variant bg-surface p-4"
        >
          <h2 id="team-title" className="text-headline-sm text-on-surface">
            Nhóm và phân công
          </h2>
          <p className="text-body-sm text-on-surface-variant">
            Thêm tài khoản môi giới vào nhóm để giao lead. Thành viên chỉ thấy lead được giao; khi gỡ thành viên, lead
            đang mở của họ trở về bạn.
          </p>
          {team.length === 0 ? (
            <p className="text-body-sm text-on-surface-variant">Nhóm chưa có thành viên.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-outline-variant">
              {team.map((member) => (
                <li key={member.memberId} className="flex items-center justify-between gap-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-body-sm font-semibold">{member.name}</p>
                    <p className="text-label font-normal text-on-surface-variant">
                      {member.email} · {member.openLeads} lead đang mở
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="ghost"
                    leftIcon={<UserMinus className="h-4 w-4" />}
                    onClick={() => setRemoving(member)}
                  >
                    Gỡ
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <form
            className="flex flex-col gap-2 sm:flex-row sm:items-end"
            onSubmit={(event) => {
              event.preventDefault();
              void act(
                async () => {
                  setTeam(await addTeamMember(email.trim()));
                  setEmail('');
                },
                'Đã thêm thành viên.',
                'Chưa thêm được thành viên.',
              );
            }}
          >
            <FormField label="E-mail tài khoản môi giới" className="flex-1">
              {(control) => (
                <TextInput
                  {...control}
                  type="email"
                  autoComplete="off"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              )}
            </FormField>
            <Button
              type="submit"
              variant="outline"
              disabled={!email.trim() || busy}
              leftIcon={<UserPlus className="h-4 w-4" />}
            >
              Thêm
            </Button>
          </form>
          {data.memberOf.length > 0 && (
            <p className="text-label font-normal text-on-surface-variant">
              Bạn đang là thành viên nhóm của: {data.memberOf.map((owner) => owner.ownerName).join(', ')}.
            </p>
          )}
        </section>

        <section
          aria-labelledby="sla-title"
          className="flex flex-col gap-3 rounded-lg border border-outline-variant bg-surface p-4"
        >
          <h2 id="sla-title" className="text-headline-sm text-on-surface">
            Mục tiêu phản hồi
          </h2>
          <FormField
            label="Thời gian phản hồi đầu tiên mục tiêu (phút)"
            hint="Từ 5 đến 1440 phút. Lead còn “Mới nhận” quá thời gian này được tính là quá hạn."
          >
            {(control) => (
              <TextInput
                {...control}
                type="number"
                min={5}
                max={1440}
                value={sla.firstResponseMinutes}
                onChange={(event) => setSla({ ...sla, firstResponseMinutes: Number(event.target.value) })}
              />
            )}
          </FormField>
          <Checkbox
            label="Nhắc lead quá hạn phản hồi"
            checked={sla.reminderEnabled}
            onChange={(event) => setSla({ ...sla, reminderEnabled: event.target.checked })}
          />
          <Checkbox
            label="Gửi tổng hợp hằng ngày"
            checked={sla.dailyDigestEnabled}
            onChange={(event) => setSla({ ...sla, dailyDigestEnabled: event.target.checked })}
          />
          <Button
            className="self-start"
            disabled={busy}
            onClick={() =>
              void act(
                async () => onChange(await saveSla(sla)),
                'Đã lưu mục tiêu phản hồi.',
                'Không thể lưu cấu hình phản hồi.',
              )
            }
          >
            Lưu mục tiêu
          </Button>
        </section>
      </div>

      <section aria-labelledby="intake-title" className="flex flex-col gap-3">
        <h2 id="intake-title" className="text-headline-sm text-on-surface">
          Lịch sử tiếp nhận lead
        </h2>
        {data.intakeHistory.length === 0 ? (
          <p className="text-body-sm text-on-surface-variant">Chưa có hoạt động nào.</p>
        ) : (
          <ol className="flex flex-col divide-y divide-outline-variant rounded-lg border border-outline-variant bg-surface">
            {data.intakeHistory.map((entry) => (
              <li key={entry.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-body-sm">
                <span>
                  <strong>{HISTORY_TYPES[entry.type] ?? entry.type}</strong>
                  {entry.toStatus && entry.type === 'STATUS_CHANGED'
                    ? `: ${LEAD_STATUS_LABELS[entry.toStatus]}`
                    : ''} ·{' '}
                  <Link className="text-primary hover:underline" to={`/my-leads?lead=${entry.leadId}`}>
                    {entry.leadName}
                  </Link>{' '}
                  · {entry.listingTitle}
                </span>
                <span className="text-label font-normal text-on-surface-variant">
                  {formatDateTime(entry.createdAt)}
                  {entry.actorName ? ` · ${entry.actorName}` : ''}
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>

      <Dialog
        open={removing != null}
        onClose={() => setRemoving(null)}
        title="Gỡ thành viên khỏi nhóm?"
        description="Các lead đang giao cho người này sẽ trở về bạn và được ghi vào lịch sử."
        footer={
          <>
            <Button variant="ghost" onClick={() => setRemoving(null)}>
              Giữ lại
            </Button>
            <Button
              variant="danger"
              isLoading={busy}
              onClick={() =>
                removing &&
                void act(
                  async () => {
                    setTeam(await removeTeamMember(removing.memberId));
                    setRemoving(null);
                  },
                  'Đã gỡ thành viên.',
                  'Chưa gỡ được thành viên.',
                )
              }
            >
              Gỡ thành viên
            </Button>
          </>
        }
      />
    </div>
  );
}

/** `/broker/workspace` (UI-08): measured SLA, today's tasks, team/assignment, intake history, qualified-lead report. */
export function BrokerWorkspacePage() {
  const [data, setData] = useState<BrokerWorkspace>();
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'overview' | 'report'>('overview');

  const load = useCallback(async () => {
    setError('');
    try {
      setData(await fetchWorkspace());
    } catch (caught) {
      setError(problemMessage(caught, 'Không thể tải không gian môi giới. Vui lòng thử lại.'));
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="min-h-full bg-surface-container-lowest px-4 py-8 md:px-8 lg:py-10">
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <header className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <p className="text-label text-primary">KHÔNG GIAN MÔI GIỚI</p>
            <h1 className="text-headline-lg text-on-surface">Việc hôm nay và hiệu quả phản hồi</h1>
            <p className="mt-2 text-body-sm text-on-surface-variant">
              Số liệu tính từ lead và lịch hẹn thực tế; chỉ số chưa đo được hiển thị “Chưa có dữ liệu”. Nền tảng kết nối
              hai bên, không nhận đặt cọc hay giao dịch mua bán.
            </p>
          </div>
          <div className="flex gap-2">
            <ButtonLink to="/my-leads" variant="outline" leftIcon={<Inbox className="h-4 w-4" />}>
              Hộp thư lead
            </ButtonLink>
            <ButtonLink to="/billing" variant="ghost">
              Gói đăng tin
            </ButtonLink>
          </div>
        </header>
        {error ? (
          <ErrorState title="Không tải được không gian môi giới" description={error} onRetry={() => void load()} />
        ) : !data ? (
          <LoadingStatus label="Đang tải dữ liệu không gian môi giới…" />
        ) : (
          <Tabs
            label="Không gian môi giới"
            value={tab}
            onChange={setTab}
            items={[
              { id: 'overview', label: 'Tổng quan', content: <Overview data={data} onChange={setData} /> },
              {
                id: 'report',
                label: 'Hiệu quả lead',
                content:
                  tab === 'report' ? (
                    <Suspense fallback={<LoadingStatus label="Đang tải báo cáo…" />}>
                      <LeadReportView />
                    </Suspense>
                  ) : null,
              },
            ]}
          />
        )}
      </div>
    </div>
  );
}

export default BrokerWorkspacePage;
