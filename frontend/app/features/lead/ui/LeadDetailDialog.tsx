import { useCallback, useEffect, useState } from 'react';
import { ExternalLink, Phone } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  assignLead,
  fetchLead,
  fetchLeadHistory,
  qualifyLead,
  revealLeadContact,
  updateLeadStatus,
} from '@/entities/lead/api/leadApi';
import {
  LEAD_STATUS_LABELS,
  OWNER_TRANSITIONS,
  QUALIFIED_REASONS,
  UNQUALIFIED_REASONS,
  formatDateTime,
  isVersionConflict,
  problemMessage,
  responseTime,
} from '@/entities/lead/model/labels';
import type {
  LeadHistoryEntry,
  LeadItem,
  LeadQualification,
  LeadStatus,
  TeamMember,
} from '@/entities/lead/model/types';
import { Button } from '@/shared/ui/Button';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Select } from '@/shared/ui/Select';
import { Dialog } from '@/shared/ui/Dialog';
import { TextArea } from '@/shared/ui/TextInput';
import { AppointmentPanel } from './AppointmentPanel';
import { LeadHistory } from './LeadHistory';

interface Props {
  leadId: string | null;
  onClose: () => void;
  /** Called after a successful change so the list shows the new state. */
  onChanged: () => void;
  /** Team of the current user (listing owner); empty when they have none. */
  team: TeamMember[];
  currentUserId: string;
}

/**
 * Owner-side lead workspace (UI-09): status with note, qualification with reason, assignment, appointments and history.
 * Every write sends the version the page showed; a 409 conflict reloads the lead and says so, nothing is overwritten.
 */
export function LeadDetailDialog(props: Props) {
  // Keep private contact/form state isolated when the user switches between leads.
  return <LeadDetailContent key={props.leadId ?? 'closed'} {...props} />;
}

function LeadDetailContent({ leadId, onClose, onChanged, team, currentUserId }: Props) {
  const [lead, setLead] = useState<LeadItem | null>(null);
  const [history, setHistory] = useState<LeadHistoryEntry[] | null>(null);
  const [phone, setPhone] = useState('');
  const [status, setStatus] = useState<LeadStatus | ''>('');
  const [statusNote, setStatusNote] = useState('');
  const [qualification, setQualification] = useState<LeadQualification | ''>('');
  const [reason, setReason] = useState('');
  const [qualificationNote, setQualificationNote] = useState('');
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error' | 'conflict'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    if (!leadId) return;
    const [fresh, entries] = await Promise.all([fetchLead(leadId), fetchLeadHistory(leadId)]);
    setLead(fresh);
    setHistory(entries);
    setQualification(fresh.qualification ?? '');
    setReason(fresh.qualificationReason ?? '');
    setQualificationNote(fresh.qualificationNote ?? '');
  }, [leadId]);

  useEffect(() => {
    setLead(null);
    setHistory(null);
    setPhone('');
    setStatus('');
    setStatusNote('');
    setFeedback(null);
    if (leadId)
      reload().catch((caught) =>
        setFeedback({ kind: 'error', text: problemMessage(caught, 'Không tải được yêu cầu.') }),
      );
  }, [leadId, reload]);

  const write = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    setFeedback(null);
    try {
      await action();
      setFeedback({ kind: 'success', text: success });
      setStatus('');
      setStatusNote('');
      onChanged();
    } catch (caught) {
      setFeedback(
        isVersionConflict(caught)
          ? {
              kind: 'conflict',
              text: 'Yêu cầu vừa được người khác cập nhật. Đã tải lại dữ liệu mới nhất, vui lòng kiểm tra rồi thử lại.',
            }
          : { kind: 'error', text: problemMessage(caught, 'Chưa lưu được thay đổi. Dữ liệu chưa bị thay đổi.') },
      );
    } finally {
      setBusy(false);
      await reload().catch(() => undefined);
    }
  };

  const reasons =
    qualification === 'QUALIFIED' ? QUALIFIED_REASONS : qualification === 'UNQUALIFIED' ? UNQUALIFIED_REASONS : {};
  const isOwner = lead != null && lead.assigneeId !== currentUserId;
  const open = lead != null && ['NEW', 'CONTACTED', 'APPOINTED'].includes(lead.status);

  return (
    <Dialog
      size="lg"
      open={leadId != null}
      onClose={onClose}
      title={lead ? lead.fullName : 'Yêu cầu liên hệ'}
      description={lead?.listingTitle}
      footer={
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      {!lead ? (
        feedback ? (
          <InlineFeedback kind="error" title={feedback.text} />
        ) : (
          <p className="text-body-sm">Đang tải…</p>
        )
      ) : (
        <div className="flex flex-col gap-5">
          {feedback && <InlineFeedback kind={feedback.kind} title={feedback.text} />}
          <dl className="grid grid-cols-2 gap-2 text-body-sm">
            <dt className="text-on-surface-variant">Trạng thái</dt>
            <dd className="font-semibold">{LEAD_STATUS_LABELS[lead.status]}</dd>
            <dt className="text-on-surface-variant">Gửi lúc</dt>
            <dd>{formatDateTime(lead.createdAt)}</dd>
            <dt className="text-on-surface-variant">Phản hồi đầu tiên</dt>
            <dd>
              {responseTime(lead.createdAt, lead.firstResponseAt) ??
                (lead.overdue
                  ? `Quá hạn từ ${formatDateTime(lead.responseDueAt)}`
                  : `Hạn ${formatDateTime(lead.responseDueAt)}`)}
            </dd>
            <dt className="text-on-surface-variant">Nhu cầu</dt>
            <dd>{lead.requestType === 'VIEWING' ? 'Muốn hẹn xem' : 'Cần tư vấn'}</dd>
            {lead.assigneeName && (
              <>
                <dt className="text-on-surface-variant">Phụ trách</dt>
                <dd>{lead.assigneeName}</dd>
              </>
            )}
          </dl>
          {lead.note && <p className="rounded-lg bg-surface-container-low p-3 text-body-sm">{lead.note}</p>}
          <div className="flex flex-wrap gap-2">
            {phone ? (
              <a
                href={`tel:${phone}`}
                className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-primary px-4 text-body-sm font-semibold text-primary"
              >
                <Phone className="h-4 w-4" aria-hidden="true" />
                {phone}
              </a>
            ) : (
              <Button
                variant="outline"
                size="sm"
                leftIcon={<Phone className="h-4 w-4" />}
                disabled={!lead.consentPolicy || lead.status === 'WITHDRAWN'}
                onClick={() =>
                  void revealLeadContact(lead.id)
                    .then((result) => setPhone(result.phone))
                    .catch((caught) =>
                      setFeedback({ kind: 'error', text: problemMessage(caught, 'Không xem được số liên hệ.') }),
                    )
                }
              >
                {lead.consentPolicy ? `Xem số (${lead.maskedPhone})` : 'Khách chưa đồng ý chia sẻ số'}
              </Button>
            )}
            <Link
              to={`/listings/${lead.listingSlug || lead.listingId}`}
              className="inline-flex min-h-11 items-center gap-2 px-2 text-body-sm font-semibold text-primary"
            >
              <ExternalLink className="h-4 w-4" aria-hidden="true" />
              Xem tin
            </Link>
          </div>

          {OWNER_TRANSITIONS[lead.status].length > 0 && (
            <section className="flex flex-col gap-2" aria-label="Cập nhật trạng thái">
              <FormField label="Chuyển trạng thái">
                {(control) => (
                  <Select
                    {...control}
                    placeholder="Chọn trạng thái mới"
                    options={OWNER_TRANSITIONS[lead.status].map((value) => ({
                      value,
                      label: LEAD_STATUS_LABELS[value],
                    }))}
                    value={status}
                    onChange={(event) => setStatus(event.target.value as LeadStatus)}
                  />
                )}
              </FormField>
              <TextArea
                aria-label="Ghi chú cho thay đổi"
                rows={2}
                maxLength={500}
                placeholder="Ghi chú nội bộ (không bắt buộc)"
                value={statusNote}
                onChange={(event) => setStatusNote(event.target.value)}
              />
              <Button
                size="sm"
                className="self-start"
                disabled={!status || busy}
                onClick={() =>
                  status &&
                  void write(
                    () => updateLeadStatus(lead.id, status, lead.version, statusNote.trim() || undefined),
                    'Đã cập nhật trạng thái.',
                  )
                }
              >
                Lưu trạng thái
              </Button>
            </section>
          )}

          <section className="flex flex-col gap-2" aria-label="Đánh giá lead">
            <FormField label="Đánh giá lead" hint="Dùng cho báo cáo lead đủ điều kiện; khách không nhìn thấy.">
              {(control) => (
                <Select
                  {...control}
                  options={[
                    { value: '', label: 'Chưa đánh giá' },
                    { value: 'QUALIFIED', label: 'Đủ điều kiện' },
                    { value: 'UNQUALIFIED', label: 'Không đủ điều kiện' },
                  ]}
                  value={qualification}
                  onChange={(event) => {
                    setQualification(event.target.value as LeadQualification | '');
                    setReason('');
                  }}
                />
              )}
            </FormField>
            {qualification && (
              <Select
                aria-label="Lý do đánh giá"
                placeholder="Chọn lý do"
                options={Object.entries(reasons).map(([value, label]) => ({ value, label }))}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            )}
            {qualification && (
              <TextArea
                aria-label="Ghi chú đánh giá"
                rows={2}
                maxLength={500}
                value={qualificationNote}
                onChange={(event) => setQualificationNote(event.target.value)}
              />
            )}
            <Button
              size="sm"
              variant="outline"
              className="self-start"
              disabled={busy || (qualification !== '' && !reason)}
              onClick={() =>
                void write(
                  () =>
                    qualifyLead(lead.id, qualification || null, reason || null, qualificationNote.trim(), lead.version),
                  'Đã lưu đánh giá lead.',
                )
              }
            >
              Lưu đánh giá
            </Button>
          </section>

          {isOwner && team.length > 0 && (
            <FormField label="Phân công" hint="Thành viên nhóm chỉ thấy các lead được giao cho họ.">
              {(control) => (
                <Select
                  {...control}
                  options={[
                    { value: '', label: 'Tôi tự xử lý' },
                    ...team.map((member) => ({ value: member.memberId, label: member.name })),
                  ]}
                  value={lead.assigneeId ?? ''}
                  disabled={busy}
                  onChange={(event) =>
                    void write(
                      () => assignLead(lead.id, event.target.value || null, lead.version),
                      'Đã cập nhật phân công.',
                    )
                  }
                />
              )}
            </FormField>
          )}

          <AppointmentPanel
            side="OWNER_SIDE"
            leadId={lead.id}
            canPropose={open}
            onChanged={() => void reload().then(onChanged)}
          />

          <section aria-label="Lịch sử yêu cầu" className="flex flex-col gap-2">
            <h3 className="text-body font-semibold text-on-surface">Lịch sử</h3>
            <LeadHistory entries={history} />
          </section>
        </div>
      )}
    </Dialog>
  );
}
