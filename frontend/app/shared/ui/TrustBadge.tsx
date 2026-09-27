import {
  BadgeCheck,
  CircleDashed,
  Clock,
  FileCheck2,
  FileWarning,
  ShieldAlert,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';
import { cn } from './cn';

/**
 * Trust signals of contract §6: identity (seller KYC), listing (moderation of the public revision) and ownership
 * (documents of this listing). Each badge says *what* was checked — never a bare "Đã xác thực" — and can show its
 * scope note, check date and expiry.
 */
export type IdentityStatus = 'VERIFIED' | 'PENDING' | 'REJECTED' | 'EXPIRED' | 'NOT_SUBMITTED';
export type ListingCheckStatus = 'CHECKED' | 'NOT_CHECKED';
export type OwnershipStatus = 'VERIFIED' | 'PENDING' | 'REJECTED' | 'REVOKED' | 'EXPIRED' | 'NOT_SUBMITTED';

export interface Trust {
  identity: { status: IdentityStatus; checkedAt?: string | null; expiresAt?: string | null };
  listing: { status: ListingCheckStatus; checkedAt?: string | null };
  ownership: {
    status: OwnershipStatus;
    checkedAt?: string | null;
    expiresAt?: string | null;
    documentType?: string | null;
  };
}

export type TrustKind = keyof Trust;
type Tone = 'success' | 'info' | 'warning' | 'neutral';

interface Copy {
  label: string;
  tone: Tone;
  icon: LucideIcon;
}

/*
 * Public copy. A rejected check is shown like "not verified": the reason belongs to the owner and the staff,
 * not to every visitor.
 */
const COPY: { [K in TrustKind]: Record<Trust[K]['status'], Copy> } = {
  identity: {
    VERIFIED: { label: 'Đã xác minh danh tính người đăng', tone: 'success', icon: ShieldCheck },
    PENDING: { label: 'Đang chờ xác minh danh tính người đăng', tone: 'info', icon: Clock },
    REJECTED: { label: 'Chưa xác minh danh tính người đăng', tone: 'neutral', icon: CircleDashed },
    EXPIRED: { label: 'Xác minh danh tính đã hết hạn', tone: 'warning', icon: ShieldAlert },
    NOT_SUBMITTED: { label: 'Chưa xác minh danh tính người đăng', tone: 'neutral', icon: CircleDashed },
  },
  listing: {
    CHECKED: { label: 'Nội dung tin đã qua kiểm duyệt', tone: 'success', icon: FileCheck2 },
    NOT_CHECKED: { label: 'Nội dung tin chưa qua kiểm duyệt', tone: 'neutral', icon: CircleDashed },
  },
  ownership: {
    VERIFIED: { label: 'Đã đối chiếu giấy tờ chủ sở hữu', tone: 'success', icon: BadgeCheck },
    PENDING: { label: 'Đang chờ đối chiếu giấy tờ chủ sở hữu', tone: 'info', icon: Clock },
    REJECTED: { label: 'Chưa đối chiếu được giấy tờ chủ sở hữu', tone: 'neutral', icon: CircleDashed },
    REVOKED: { label: 'Kết quả đối chiếu giấy tờ đã bị thu hồi', tone: 'warning', icon: FileWarning },
    EXPIRED: { label: 'Kết quả đối chiếu giấy tờ đã hết hạn', tone: 'warning', icon: FileWarning },
    NOT_SUBMITTED: { label: 'Chưa đối chiếu giấy tờ chủ sở hữu', tone: 'neutral', icon: CircleDashed },
  },
};

/** What each check does *not* guarantee (shown with the badge in panels). */
export const TRUST_SCOPE_NOTES: Record<TrustKind, string> = {
  identity: 'Không bảo đảm quyền sở hữu hay pháp lý giao dịch.',
  listing: 'Kiểm duyệt nội dung hiển thị; không xác nhận hiện trạng hay pháp lý của tài sản.',
  ownership: 'Đối chiếu tại thời điểm kiểm tra; không thay thế thẩm định pháp lý khi giao dịch.',
};

const tones: Record<Tone, string> = {
  success: 'border-success/25 bg-success-container text-success-on-container',
  info: 'border-info/25 bg-info-container text-info-on-container',
  warning: 'border-warning/25 bg-warning-container text-warning-on-container',
  neutral: 'border-outline-variant bg-surface-container text-on-surface-variant',
};

const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'Asia/Ho_Chi_Minh',
});

function formatDate(value?: string | null): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : dateFormat.format(date);
}

export function trustLabel<K extends TrustKind>(kind: K, status: Trust[K]['status']): string {
  return (COPY[kind] as Record<string, Copy>)[status]?.label ?? '';
}

interface TrustBadgeProps<K extends TrustKind> {
  kind: K;
  status: Trust[K]['status'];
  checkedAt?: string | null;
  expiresAt?: string | null;
  /** Adds the scope note and dates under the badge (detail pages, panels). */
  detailed?: boolean;
  className?: string;
}

export function TrustBadge<K extends TrustKind>({
  kind,
  status,
  checkedAt,
  expiresAt,
  detailed = false,
  className,
}: TrustBadgeProps<K>) {
  const copy = (COPY[kind] as Record<string, Copy>)[status] ?? COPY.listing.NOT_CHECKED;
  const Icon = copy.icon;
  const checked = formatDate(checkedAt);
  const expires = formatDate(expiresAt);
  const badge = (
    <span
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 rounded-pill border px-2.5 py-1 text-xs font-semibold',
        tones[copy.tone],
        !detailed && className,
      )}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0">{copy.label}</span>
    </span>
  );
  if (!detailed) return badge;
  return (
    <div className={cn('flex flex-col items-start gap-1', className)}>
      {badge}
      <p className="text-label font-normal text-on-surface-variant">
        {TRUST_SCOPE_NOTES[kind]}
        {checked && <> Kiểm tra ngày {checked}.</>}
        {expires && <> Hiệu lực đến {expires}.</>}
      </p>
    </div>
  );
}

/** The three trust signals of a listing, each with its scope, for detail pages (DS-08 TrustPanel). */
export function TrustPanel({ trust, className }: { trust: Trust; className?: string }) {
  return (
    <section
      aria-label="Mức độ xác minh"
      className={cn('rounded-card border border-outline-variant bg-surface-container-lowest p-4', className)}
    >
      <h3 className="text-body-sm font-semibold text-on-surface">Những gì đã được kiểm tra</h3>
      <ul className="mt-3 flex flex-col gap-3">
        <li>
          <TrustBadge
            kind="identity"
            status={trust.identity.status}
            checkedAt={trust.identity.checkedAt}
            expiresAt={trust.identity.expiresAt}
            detailed
          />
        </li>
        <li>
          <TrustBadge kind="listing" status={trust.listing.status} checkedAt={trust.listing.checkedAt} detailed />
        </li>
        <li>
          <TrustBadge
            kind="ownership"
            status={trust.ownership.status}
            checkedAt={trust.ownership.checkedAt}
            expiresAt={trust.ownership.expiresAt}
            detailed
          />
        </li>
      </ul>
    </section>
  );
}
