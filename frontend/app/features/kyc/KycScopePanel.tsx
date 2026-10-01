import type { MyKycStatus } from '@/entities/admin/model/types';
import { formatDate, formatDateTime } from '@/shared/admin/adminUi';

const DECISION: Record<string, string> = {
  APPROVED: 'Đã xác minh',
  REJECTED: 'Cần gửi lại',
  REVOKED: 'Bị thu hồi',
  EXPIRED: 'Hết hiệu lực',
};

/** UI-12: what identity verification proves (and does not), why documents are needed, who sees them, the timeline. */
export function KycScopePanel({ status }: { status: MyKycStatus | null }) {
  const retentionNotice = import.meta.env.VITE_KYC_RETENTION_NOTICE?.trim();
  return (
    <div className="mt-6 space-y-4 text-sm">
      <section
        aria-labelledby="kyc-scope"
        className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5"
      >
        <h2 id="kyc-scope" className="text-base font-bold">
          Xác minh danh tính chứng nhận điều gì?
        </h2>
        <ul className="mt-2 list-disc space-y-1 pl-5">
          <li>Chứng nhận: tài khoản đăng tin thuộc về người có CCCD đã được đối chiếu với ảnh chân dung.</li>
          <li>
            Không chứng nhận: quyền sở hữu bất động sản, tình trạng pháp lý của tài sản hay tính hợp pháp của giao dịch.
          </li>
          <li>Hiệu lực 24 tháng kể từ ngày duyệt; bạn được nhắc trước 30 ngày để gửi lại.</li>
        </ul>
        <h3 className="mt-3 font-bold">Vì sao cần giấy tờ, ai xem được?</h3>
        <p className="mt-1">
          Ảnh CCCD và chân dung giúp người mua biết họ đang liên hệ với người thật. Ảnh được lưu riêng tư, không bao giờ
          hiển thị công khai; chỉ nhân sự kiểm duyệt xem được sau khi xác nhận lại mật khẩu và nêu lý do, và mỗi lần xem
          đều được ghi lại. Số CCCD được mã hóa và chỉ hiển thị dạng che.
        </p>
        <p className="mt-1 text-on-surface-variant">
          {retentionNotice || 'Thời hạn lưu trữ ảnh giấy tờ: chưa có dữ liệu chính sách được công bố.'}{' '}
          <a href="/privacy" className="font-semibold text-primary underline">
            Xem chính sách quyền riêng tư
          </a>
        </p>
      </section>
      {status && status.status !== 'NOT_SUBMITTED' && (
        <section
          aria-labelledby="kyc-timeline"
          className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest p-5"
        >
          <h2 id="kyc-timeline" className="text-base font-bold">
            Tiến trình hồ sơ
          </h2>
          <ol className="mt-2 space-y-2">
            {status.submittedAt && <li>Gửi hồ sơ · {formatDateTime(status.submittedAt)}</li>}
            {[...status.timeline].reverse().map((d) => (
              <li key={d.id}>
                <span className="font-semibold">{DECISION[d.decision] ?? d.decision}</span> ·{' '}
                {formatDateTime(d.createdAt)}
                <span className="block text-on-surface-variant">
                  {d.reasonLabel}
                  {d.expiresAt && d.decision === 'APPROVED' ? ` · hiệu lực đến ${formatDate(d.expiresAt)}` : ''}
                </span>
              </li>
            ))}
            {status.status === 'PENDING' && (
              <li className="text-on-surface-variant">Đang chờ nhân sự kiểm duyệt đối chiếu.</li>
            )}
          </ol>
          {status.status === 'EXPIRED' && (
            <p className="mt-2 font-semibold">Xác minh đã hết hạn — hãy gửi lại hồ sơ bên dưới.</p>
          )}
        </section>
      )}
    </div>
  );
}
