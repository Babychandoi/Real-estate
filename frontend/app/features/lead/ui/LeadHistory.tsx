import { historyLabel, formatDateTime, sideLabel } from '@/entities/lead/model/labels';
import type { LeadHistoryEntry, LeadStatus } from '@/entities/lead/model/types';

/** Append-only lead history, newest first (UI-09 / UI-10). */
export function LeadHistory({
  entries,
  statusLabels,
}: {
  entries: LeadHistoryEntry[] | null;
  statusLabels?: Record<LeadStatus, string>;
}) {
  if (!entries) return <p className="text-body-sm text-on-surface-variant">Đang tải lịch sử…</p>;
  if (entries.length === 0) return <p className="text-body-sm text-on-surface-variant">Chưa có thay đổi nào.</p>;
  return (
    <ol className="flex flex-col gap-2 border-l border-outline-variant pl-4">
      {entries.map((entry) => (
        <li key={entry.id} className="text-body-sm">
          <p className="font-semibold text-on-surface">{historyLabel(entry, statusLabels)}</p>
          <p className="text-label font-normal text-on-surface-variant">
            {formatDateTime(entry.createdAt)} · {entry.actorName || sideLabel(entry.actorSide)}
          </p>
          {entry.note && <p className="mt-1 text-on-surface-variant">{entry.note}</p>}
        </li>
      ))}
    </ol>
  );
}
