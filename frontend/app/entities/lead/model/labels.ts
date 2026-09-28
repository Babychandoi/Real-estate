import { ApiProblemException } from '@/shared/types/problem-details';
import type { AppointmentStatus, LeadHistoryEntry, LeadStatus } from './types';

/** Owner-side wording of lead statuses. */
export const LEAD_STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Mới nhận',
  CONTACTED: 'Đã liên hệ',
  APPOINTED: 'Đã hẹn xem',
  CLOSED: 'Hoàn tất',
  SPAM: 'Không hợp lệ',
  WITHDRAWN: 'Khách đã rút yêu cầu',
};

/** Requester-side wording: what actually happened to their request. */
export const INQUIRY_STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Đã gửi, chờ người đăng phản hồi',
  CONTACTED: 'Người đăng đã tiếp nhận',
  APPOINTED: 'Đang hẹn xem',
  CLOSED: 'Người đăng đã đóng yêu cầu',
  SPAM: 'Yêu cầu không hợp lệ',
  WITHDRAWN: 'Bạn đã rút yêu cầu',
};

/** Owner-side transitions allowed by the server (LeadStatus.ownerCanMoveTo). */
export const OWNER_TRANSITIONS: Record<LeadStatus, LeadStatus[]> = {
  NEW: ['CONTACTED', 'APPOINTED', 'CLOSED', 'SPAM'],
  CONTACTED: ['APPOINTED', 'CLOSED', 'SPAM'],
  APPOINTED: ['CONTACTED', 'CLOSED', 'SPAM'],
  CLOSED: ['CONTACTED'],
  SPAM: ['CONTACTED'],
  WITHDRAWN: [],
};

export const QUALIFIED_REASONS: Record<string, string> = {
  BUDGET_MATCH: 'Ngân sách phù hợp',
  READY_TO_VIEW: 'Sẵn sàng đi xem',
  DECISION_MAKER: 'Là người quyết định',
  OTHER: 'Lý do khác',
};
export const UNQUALIFIED_REASONS: Record<string, string> = {
  BUDGET_MISMATCH: 'Ngân sách không phù hợp',
  NOT_REACHABLE: 'Không liên lạc được',
  JUST_BROWSING: 'Chỉ tham khảo',
  DUPLICATE: 'Trùng yêu cầu khác',
  SPAM: 'Spam',
  LISTING_UNAVAILABLE: 'Tin không còn phù hợp',
  OTHER: 'Lý do khác',
};

export const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  PROPOSED: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  RESCHEDULED: 'Đã đổi lịch',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Đã xem',
  NO_SHOW: 'Vắng mặt',
};

const HISTORY_LABELS: Record<string, string> = {
  CREATED: 'Gửi yêu cầu',
  STATUS_CHANGED: 'Đổi trạng thái',
  ASSIGNED: 'Phân công',
  QUALIFIED: 'Đánh giá lead',
  WITHDRAWN: 'Rút yêu cầu',
  APPOINTMENT_PROPOSED: 'Đề xuất lịch hẹn',
  APPOINTMENT_CONFIRMED: 'Xác nhận lịch hẹn',
  APPOINTMENT_RESCHEDULED: 'Đề xuất giờ khác',
  APPOINTMENT_CANCELLED: 'Hủy lịch hẹn',
  APPOINTMENT_COMPLETED: 'Đã xem nhà',
  APPOINTMENT_NO_SHOW: 'Ghi nhận vắng mặt',
};

const SIDE_LABELS: Record<LeadHistoryEntry['actorSide'], string> = {
  REQUESTER: 'Người gửi',
  OWNER_SIDE: 'Người phụ trách',
  STAFF: 'Quản trị',
  SYSTEM: 'Hệ thống',
};

export function historyLabel(entry: LeadHistoryEntry, statusLabels: Record<LeadStatus, string> = LEAD_STATUS_LABELS) {
  const base = HISTORY_LABELS[entry.type] ?? entry.type;
  const change = entry.type === 'STATUS_CHANGED' && entry.toStatus ? `: ${statusLabels[entry.toStatus]}` : '';
  return `${base}${change}`;
}

export const sideLabel = (side: LeadHistoryEntry['actorSide']) => SIDE_LABELS[side] ?? side;

const dateTime = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
});
const time = new Intl.DateTimeFormat('vi-VN', { timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });

export const formatDateTime = (value: string) => dateTime.format(new Date(value));

export function formatSlot(startsAt: string, endsAt: string) {
  return `${formatDateTime(startsAt)} – ${time.format(new Date(endsAt))}`;
}

/** "25 phút", "3 giờ 5 phút", "2 ngày" — durations are whole minutes. */
export function formatMinutes(minutes: number) {
  if (minutes < 60) return `${minutes} phút`;
  if (minutes < 60 * 24) {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest ? `${hours} giờ ${rest} phút` : `${hours} giờ`;
  }
  return `${Math.round(minutes / (60 * 24))} ngày`;
}

export function responseTime(createdAt: string, firstResponseAt: string | null) {
  if (!firstResponseAt) return null;
  return formatMinutes(Math.max(0, Math.round((Date.parse(firstResponseAt) - Date.parse(createdAt)) / 60000)));
}

/** A value the server could not measure is shown as "chưa có dữ liệu", never 0. */
export const orNoData = (value: number | null | undefined, format: (value: number) => string = String) =>
  value == null ? 'Chưa có dữ liệu' : format(value);

export interface SlotDraft {
  date: string; // yyyy-mm-dd, Vietnam time
  start: string; // HH:mm
  minutes: number;
}

/** Slot drafts (Vietnam local time, UTC+7 without DST) → ISO instants; mirrors the server rules for early feedback. */
export function slotsToPayload(
  drafts: SlotDraft[],
  now = new Date(),
): { slots: Array<{ startsAt: string; endsAt: string }>; error?: string } {
  const filled = drafts.filter((draft) => draft.date && draft.start);
  if (filled.length === 0) return { slots: [], error: 'Chọn ít nhất một khung giờ.' };
  if (filled.length > 3) return { slots: [], error: 'Đề xuất tối đa 3 khung giờ.' };
  const slots = filled
    .map((draft) => {
      const start = new Date(`${draft.date}T${draft.start}:00+07:00`);
      return { start, end: new Date(start.getTime() + draft.minutes * 60000) };
    })
    .sort((a, b) => a.start.getTime() - b.start.getTime());
  for (const slot of slots) {
    if (Number.isNaN(slot.start.getTime())) return { slots: [], error: 'Ngày giờ không hợp lệ.' };
    if (slot.start.getTime() < now.getTime() + 30 * 60000)
      return { slots: [], error: 'Khung giờ phải bắt đầu sau ít nhất 30 phút kể từ bây giờ.' };
    if (slot.start.getTime() > now.getTime() + 60 * 24 * 3600000)
      return { slots: [], error: 'Chỉ đặt lịch trong vòng 60 ngày tới.' };
  }
  for (let index = 1; index < slots.length; index += 1) {
    if (slots[index].start < slots[index - 1].end)
      return { slots: [], error: 'Các khung giờ đề xuất không được chồng lên nhau.' };
  }
  return { slots: slots.map((slot) => ({ startsAt: slot.start.toISOString(), endsAt: slot.end.toISOString() })) };
}

/** Problem code of a failed call (e.g. LEAD_VERSION_CONFLICT), if the server sent one. */
export const problemCode = (error: unknown) => (error instanceof ApiProblemException ? error.problem.code : undefined);

/** True for the compare-and-set refusals: the page reloads and tells the user someone else changed it. */
export const isVersionConflict = (error: unknown) => {
  const code = problemCode(error);
  return code === 'LEAD_VERSION_CONFLICT' || code === 'APPOINTMENT_VERSION_CONFLICT';
};

export function problemMessage(error: unknown, fallback: string) {
  return error instanceof ApiProblemException && error.problem.status < 500 && error.problem.detail
    ? error.problem.detail
    : fallback;
}
