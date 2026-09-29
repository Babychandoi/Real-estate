import { useEffect, useId, useState } from 'react';
import { AlertTriangle, CheckCircle2, Clock3 } from 'lucide-react';
import { Badge, type BadgeVariant } from '@/shared/ui/Badge';
import { Button, type ButtonVariant } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Select } from '@/shared/ui/Select';
import { TextArea, TextInput } from '@/shared/ui/TextInput';
import { errorMessage } from '@/shared/api/errors';

const DATE_TIME = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
});
const DATE = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'Asia/Ho_Chi_Minh',
});
const VND = new Intl.NumberFormat('vi-VN');

export const formatDateTime = (value: string | null | undefined) =>
  value ? DATE_TIME.format(new Date(value)) : 'Chưa có';
export const formatDate = (value: string | null | undefined) => (value ? DATE.format(new Date(value)) : 'Chưa có');
export const formatVnd = (value: number | null | undefined) => (value == null ? 'Chưa có' : `${VND.format(value)} đ`);

/** "3 giờ 20 phút", "2 ngày 4 giờ" (waiting time). */
export function formatAge(minutes: number | null | undefined): string {
  if (minutes == null) return 'Chưa rõ';
  const abs = Math.abs(minutes);
  if (abs < 60) return `${abs} phút`;
  const hours = Math.floor(abs / 60);
  if (hours < 24) return `${hours} giờ ${abs % 60} phút`;
  return `${Math.floor(hours / 24)} ngày ${hours % 24} giờ`;
}

/** SLA state as text + icon + colour (never colour alone). */
export function SlaBadge({
  breached,
  dueAt,
  minutesToDue,
}: {
  breached: boolean;
  dueAt: string | null;
  minutesToDue?: number;
}) {
  if (breached) {
    return (
      <Badge variant="error" icon={<AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />}>
        Quá hạn SLA{minutesToDue != null ? ` ${formatAge(minutesToDue)}` : ''}
      </Badge>
    );
  }
  const soon = minutesToDue != null && minutesToDue < 60;
  return (
    <Badge variant={soon ? 'warning' : 'neutral'} icon={<Clock3 className="h-3.5 w-3.5" aria-hidden="true" />}>
      {minutesToDue != null ? `Còn ${formatAge(minutesToDue)}` : `Hạn ${formatDateTime(dueAt)}`}
    </Badge>
  );
}

export function StatusBadge({ label, variant }: { label: string; variant: BadgeVariant }) {
  const icon =
    variant === 'success' ? (
      <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
    ) : variant === 'error' || variant === 'warning' ? (
      <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
    ) : undefined;
  return (
    <Badge variant={variant} icon={icon}>
      {label}
    </Badge>
  );
}

export interface ReasonChoice {
  code: string;
  label: string;
}

interface ReasonDialogProps {
  open: boolean;
  title: string;
  description?: string;
  /** Standard reasons; omitted → free text only. */
  reasons?: ReasonChoice[];
  /** Label of the choice field (default "Lý do"; e.g. "Vai trò mới"). */
  choiceLabel?: string;
  noteLabel?: string;
  /** Minimum length of the note (0 = optional). */
  noteMinLength?: number;
  confirmLabel: string;
  confirmVariant?: ButtonVariant;
  /** What exactly will be changed (shown above the form). */
  scope?: React.ReactNode;
  onConfirm: (reasonCode: string, note: string) => Promise<void>;
  onClose: () => void;
}

/** Every staff decision says why: a standard reason (when there is a catalogue) and a note. */
export function ReasonDialog({
  open,
  title,
  description,
  reasons,
  choiceLabel = 'Lý do',
  noteLabel = 'Ghi chú',
  noteMinLength = 0,
  confirmLabel,
  confirmVariant = 'primary',
  scope,
  onConfirm,
  onClose,
}: ReasonDialogProps) {
  const [code, setCode] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset only when the dialog opens: no reason is preselected, the staff member must choose one explicitly.
  // (Callers pass a new `reasons` array on every render, so the reset must not depend on it.)
  const validCodes = (reasons ?? []).map((r) => r.code).join('|');
  useEffect(() => {
    if (open) {
      setCode('');
      setNote('');
      setError(null);
    }
  }, [open]);
  useEffect(() => {
    if (open) setCode((current) => (current && validCodes.split('|').includes(current) ? current : ''));
  }, [open, validCodes]);

  const needsChoice = Boolean(reasons && reasons.length > 0);
  const noteTooShort = note.trim().length < noteMinLength;
  const submit = async () => {
    if (needsChoice && !code) {
      setError(`Cần chọn ${choiceLabel.toLowerCase()}.`);
      return;
    }
    if (noteTooShort) {
      setError(`${noteLabel} cần ít nhất ${noteMinLength} ký tự.`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(code, note.trim());
      onClose();
    } catch (err) {
      setError(errorMessage(err, 'Không thể lưu quyết định. Vui lòng thử lại.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={busy ? () => undefined : onClose}
      title={title}
      description={description}
      footer={
        <div className="flex flex-wrap justify-end gap-3">
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Hủy
          </Button>
          <Button variant={confirmVariant} onClick={submit} isLoading={busy}>
            {confirmLabel}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {scope}
        {reasons && reasons.length > 0 && (
          <FormField label={choiceLabel} required>
            {(control) => (
              <Select
                {...control}
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="— Chọn lý do —"
                options={reasons.map((reason) => ({ value: reason.code, label: reason.label }))}
              />
            )}
          </FormField>
        )}
        <FormField
          label={noteLabel}
          required={noteMinLength > 0}
          hint={noteMinLength > 0 ? `Bắt buộc, ít nhất ${noteMinLength} ký tự.` : 'Không bắt buộc.'}
        >
          {(control) => (
            <TextArea
              {...control}
              rows={3}
              value={note}
              maxLength={1000}
              onChange={(event) => setNote(event.target.value)}
            />
          )}
        </FormField>
        {error && <InlineFeedback kind="error" title={error} />}
      </div>
    </Dialog>
  );
}

interface PasswordReasonDialogProps {
  open: boolean;
  title: string;
  description: string;
  onConfirm: (password: string, reason: string) => Promise<void>;
  onClose: () => void;
}

/** Opening someone's identity documents: re-enter your password and say why (both are logged). */
export function PasswordReasonDialog({ open, title, description, onConfirm, onClose }: PasswordReasonDialogProps) {
  const [password, setPassword] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formId = `pw${useId().replace(/:/g, '')}`;
  useEffect(() => {
    if (open) {
      setPassword('');
      setReason('');
      setError(null);
    }
  }, [open]);
  const submit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    if (reason.trim().length < 5) {
      setError('Cần nêu lý do xem giấy tờ (ít nhất 5 ký tự).');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(password, reason.trim());
      onClose();
    } catch (err) {
      setError(errorMessage(err, 'Không thể mở giấy tờ.'));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Hủy
          </Button>
          <Button type="submit" form={formId} isLoading={busy}>
            Xem giấy tờ
          </Button>
        </div>
      }
    >
      <form id={formId} className="space-y-4" onSubmit={submit}>
        <FormField label="Lý do xem" required hint="Ví dụ: đối chiếu hồ sơ khiếu nại mã CASE-…">
          {(control) => (
            <TextArea
              {...control}
              rows={2}
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
            />
          )}
        </FormField>
        <FormField label="Mật khẩu của bạn" required>
          {(control) => (
            <TextInput
              {...control}
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </FormField>
        {error && <InlineFeedback kind="error" title={error} />}
      </form>
    </Dialog>
  );
}
