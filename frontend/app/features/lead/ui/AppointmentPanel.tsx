import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, CalendarPlus, Plus, Trash2 } from 'lucide-react';
import {
  cancelAppointment,
  confirmAppointment,
  fetchAppointments,
  proposeAppointment,
  recordAppointmentOutcome,
} from '@/entities/lead/api/leadApi';
import {
  APPOINTMENT_STATUS_LABELS,
  formatSlot,
  isVersionConflict,
  problemMessage,
  slotsToPayload,
  type SlotDraft,
} from '@/entities/lead/model/labels';
import type { AppointmentView, PartySide } from '@/entities/lead/model/types';
import { Badge, type BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Select } from '@/shared/ui/Select';
import { TextArea, TextInput } from '@/shared/ui/TextInput';

const STATUS_VARIANT: Record<AppointmentView['status'], BadgeVariant> = {
  PROPOSED: 'warning',
  CONFIRMED: 'success',
  RESCHEDULED: 'neutral',
  CANCELLED: 'neutral',
  COMPLETED: 'info',
  NO_SHOW: 'error',
};
const DURATIONS = [30, 45, 60, 90, 120].map((minutes) => ({ value: String(minutes), label: `${minutes} phút` }));
const emptySlot = (): SlotDraft => ({ date: '', start: '', minutes: 60 });

interface Props {
  side: PartySide;
  leadId: string;
  /** False when the request is finished: no new proposals. */
  canPropose: boolean;
  /** Called after any change so the page can reload the lead (status/version change on the server too). */
  onChanged?: () => void;
}

/**
 * Viewing appointment of one lead (P-03), for either party: propose 1–3 slots, confirm one of the other side's slots,
 * propose another time, cancel with a reason and — owner side, after the start — record the outcome.
 */
export function AppointmentPanel({ side, leadId, canPropose, onChanged }: Props) {
  const [items, setItems] = useState<AppointmentView[] | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [drafting, setDrafting] = useState(false);
  const [drafts, setDrafts] = useState<SlotDraft[]>([emptySlot()]);
  const [note, setNote] = useState('');
  const [chosenSlot, setChosenSlot] = useState('');
  const [cancelling, setCancelling] = useState<AppointmentView | null>(null);
  const [reason, setReason] = useState('');

  const load = useCallback(async () => {
    try {
      setItems(await fetchAppointments(side, leadId));
    } catch (caught) {
      setError(problemMessage(caught, 'Không tải được lịch hẹn.'));
    }
  }, [side, leadId]);
  useEffect(() => {
    void load();
  }, [load]);

  const open = items?.find((item) => item.status === 'PROPOSED' || item.status === 'CONFIRMED') ?? null;
  const past = (items ?? []).filter((item) => item !== open).slice(0, 5);

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setBusy(true);
    setError('');
    try {
      await action();
      setDrafting(false);
      setDrafts([emptySlot()]);
      setNote('');
      setCancelling(null);
      setReason('');
      onChanged?.();
    } catch (caught) {
      setError(
        isVersionConflict(caught)
          ? 'Lịch hẹn vừa được bên kia cập nhật. Đã tải lại để bạn xem trạng thái mới nhất.'
          : problemMessage(caught, fallback),
      );
    } finally {
      setBusy(false);
      await load();
    }
  };

  const submitProposal = () => {
    const payload = slotsToPayload(drafts);
    if (payload.error) {
      setError(payload.error);
      return;
    }
    void run(
      () => proposeAppointment(side, leadId, payload.slots, note.trim(), open ? open.version : null),
      'Chưa gửi được đề xuất lịch hẹn.',
    );
  };

  if (!items) return <p className="text-body-sm text-on-surface-variant">Đang tải lịch hẹn…</p>;
  const started = open?.status === 'CONFIRMED' && open.startsAt != null && Date.parse(open.startsAt) <= Date.now();

  return (
    <section aria-labelledby={`appointments-${leadId}`} className="flex flex-col gap-3">
      <h3 id={`appointments-${leadId}`} className="flex items-center gap-2 text-body font-semibold text-on-surface">
        <CalendarClock className="h-4 w-4" aria-hidden="true" />
        Lịch hẹn xem
      </h3>
      {error && <InlineFeedback kind="error" title={error} />}
      {open ? (
        <div className="rounded-lg border border-outline-variant p-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={STATUS_VARIANT[open.status]}>{APPOINTMENT_STATUS_LABELS[open.status]}</Badge>
            <span className="text-label text-on-surface-variant">
              {open.proposedBySide === side ? 'Bạn đã đề xuất' : 'Bên kia đề xuất'}
            </span>
          </div>
          {open.status === 'CONFIRMED' && open.startsAt && open.endsAt ? (
            <p className="mt-2 text-body font-semibold text-on-surface">{formatSlot(open.startsAt, open.endsAt)}</p>
          ) : open.awaitingMe ? (
            <fieldset className="mt-2">
              <legend className="text-body-sm font-semibold text-on-surface">Chọn một khung giờ phù hợp</legend>
              {open.slots.map((slot) => (
                <label key={slot.id} className="flex min-h-11 cursor-pointer items-center gap-3 text-body-sm">
                  <input
                    type="radio"
                    name={`slot-${open.id}`}
                    value={slot.id}
                    checked={chosenSlot === slot.id}
                    onChange={() => setChosenSlot(slot.id)}
                    className="h-5 w-5 accent-primary"
                  />
                  {formatSlot(slot.startsAt, slot.endsAt)}
                </label>
              ))}
            </fieldset>
          ) : (
            <ul className="mt-2 list-disc pl-5 text-body-sm text-on-surface">
              {open.slots.map((slot) => (
                <li key={slot.id}>{formatSlot(slot.startsAt, slot.endsAt)}</li>
              ))}
            </ul>
          )}
          {open.note && <p className="mt-2 text-body-sm text-on-surface-variant">Ghi chú: {open.note}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            {open.awaitingMe && (
              <Button
                size="sm"
                disabled={!chosenSlot || busy}
                onClick={() =>
                  void run(() => confirmAppointment(open.id, chosenSlot, open.version), 'Chưa xác nhận được lịch hẹn.')
                }
              >
                Xác nhận khung giờ
              </Button>
            )}
            {side === 'OWNER_SIDE' && started && (
              <>
                <Button
                  size="sm"
                  disabled={busy}
                  onClick={() =>
                    void run(
                      () => recordAppointmentOutcome(open.id, 'COMPLETED', null, '', open.version),
                      'Chưa ghi nhận được kết quả.',
                    )
                  }
                >
                  Đã dẫn khách xem
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={busy}
                  onClick={() =>
                    void run(
                      () => recordAppointmentOutcome(open.id, 'NO_SHOW', 'REQUESTER', '', open.version),
                      'Chưa ghi nhận được kết quả.',
                    )
                  }
                >
                  Khách không đến
                </Button>
              </>
            )}
            {!started && (
              <Button size="sm" variant="outline" disabled={busy} onClick={() => setDrafting(true)}>
                Đề xuất giờ khác
              </Button>
            )}
            {!started && (
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => setCancelling(open)}>
                Hủy lịch hẹn
              </Button>
            )}
          </div>
        </div>
      ) : canPropose && !drafting ? (
        <Button
          size="sm"
          variant="outline"
          leftIcon={<CalendarPlus className="h-4 w-4" />}
          onClick={() => setDrafting(true)}
          className="self-start"
        >
          Đề xuất lịch hẹn xem
        </Button>
      ) : !canPropose ? (
        <p className="text-body-sm text-on-surface-variant">Yêu cầu đã kết thúc nên không thể đặt lịch mới.</p>
      ) : null}

      {drafting && (
        <div className="flex flex-col gap-3 rounded-lg bg-surface-container-low p-3">
          <p className="text-body-sm text-on-surface-variant">
            Đề xuất 1–3 khung giờ (giờ Việt Nam), bắt đầu sau ít nhất 30 phút và trong 60 ngày tới. Bên kia chọn một
            khung giờ để xác nhận.
          </p>
          {drafts.map((draft, index) => (
            <div key={index} className="grid grid-cols-[1fr_1fr] gap-2 sm:grid-cols-[1fr_7rem_7rem_auto]">
              <TextInput
                type="date"
                aria-label={`Ngày của khung giờ ${index + 1}`}
                value={draft.date}
                onChange={(event) =>
                  setDrafts(drafts.map((item, i) => (i === index ? { ...item, date: event.target.value } : item)))
                }
              />
              <TextInput
                type="time"
                aria-label={`Giờ bắt đầu khung giờ ${index + 1}`}
                value={draft.start}
                onChange={(event) =>
                  setDrafts(drafts.map((item, i) => (i === index ? { ...item, start: event.target.value } : item)))
                }
              />
              <Select
                aria-label={`Thời lượng khung giờ ${index + 1}`}
                options={DURATIONS}
                value={String(draft.minutes)}
                onChange={(event) =>
                  setDrafts(
                    drafts.map((item, i) => (i === index ? { ...item, minutes: Number(event.target.value) } : item)),
                  )
                }
              />
              {drafts.length > 1 && (
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={`Bỏ khung giờ ${index + 1}`}
                  onClick={() => setDrafts(drafts.filter((_, i) => i !== index))}
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
            </div>
          ))}
          {drafts.length < 3 && (
            <Button
              size="sm"
              variant="ghost"
              leftIcon={<Plus className="h-4 w-4" />}
              className="self-start"
              onClick={() => setDrafts([...drafts, emptySlot()])}
            >
              Thêm khung giờ
            </Button>
          )}
          <TextArea
            aria-label="Ghi chú cho lịch hẹn"
            placeholder="Ví dụ: gặp ở sảnh tòa nhà (không ghi số điện thoại)"
            rows={2}
            maxLength={500}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <div className="flex gap-2">
            <Button size="sm" isLoading={busy} onClick={submitProposal}>
              Gửi đề xuất
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setDrafting(false)}>
              Thôi
            </Button>
          </div>
        </div>
      )}

      {past.length > 0 && (
        <details className="text-body-sm">
          <summary className="min-h-11 cursor-pointer py-2 font-semibold text-on-surface">Lịch hẹn trước đây</summary>
          <ul className="flex flex-col gap-1">
            {past.map((item) => (
              <li key={item.id} className="flex flex-wrap items-center gap-2 text-on-surface-variant">
                <Badge variant={STATUS_VARIANT[item.status]}>{APPOINTMENT_STATUS_LABELS[item.status]}</Badge>
                {item.startsAt && item.endsAt
                  ? formatSlot(item.startsAt, item.endsAt)
                  : `${item.slots.length} khung giờ`}
                {item.cancelReason && <span>· {item.cancelReason}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}

      <Dialog
        open={cancelling != null}
        onClose={() => setCancelling(null)}
        title="Hủy lịch hẹn xem"
        description="Bên kia sẽ nhận được thông báo kèm lý do."
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelling(null)}>
              Giữ lịch hẹn
            </Button>
            <Button
              variant="danger"
              isLoading={busy}
              disabled={reason.trim().length < 3}
              onClick={() =>
                cancelling &&
                void run(() => cancelAppointment(cancelling.id, reason.trim(), cancelling.version), 'Chưa hủy được.')
              }
            >
              Hủy lịch hẹn
            </Button>
          </>
        }
      >
        <TextArea
          aria-label="Lý do hủy"
          rows={3}
          maxLength={300}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Ví dụ: tôi có việc đột xuất"
        />
      </Dialog>
    </section>
  );
}
