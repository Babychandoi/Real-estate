import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { getStoredAnalyticsConsent } from '@/shared/analytics/consent';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { Switch } from '@/shared/ui/Switch';
import { decideAnalyticsConsent, type ConsentSource } from './consentRecord';

export type ConsentView = 'banner' | 'preferences';

interface ConsentBannerProps {
  view: ConsentView;
  onClose: () => void;
  /** Injected in tests. */
  decide?: typeof decideAnalyticsConsent;
}

const PURPOSE =
  'Ghi nhận ẩn danh cách trang được dùng (tìm kiếm, xem tin, mở form liên hệ) và tốc độ tải trang để cải thiện ' +
  'dịch vụ. Không dùng cho quảng cáo, không bán hay chia sẻ cho bên thứ ba.';

/**
 * Analytics consent (Decree 13/2023/NĐ-CP): asked before any analytics runs; "Từ chối" and "Đồng ý" have equal weight;
 * nothing is pre-selected; the choice can be changed or withdrawn at any time from the footer ("Tùy chọn quyền riêng
 * tư"). Loaded lazily, only when a decision is needed or the preferences are opened, so it costs the shell nothing.
 */
export function ConsentBanner({ view, onClose, decide = decideAnalyticsConsent }: ConsentBannerProps) {
  const [mode, setMode] = useState<ConsentView>(view);
  const [analytics, setAnalytics] = useState(() => getStoredAnalyticsConsent() === 'granted');
  const [saving, setSaving] = useState(false);

  const choose = async (granted: boolean, source: ConsentSource) => {
    setSaving(true);
    try {
      await decide(granted ? 'granted' : 'denied', source);
    } finally {
      setSaving(false);
      onClose();
    }
  };

  if (mode === 'preferences') {
    return (
      <Dialog
        open
        onClose={onClose}
        title="Tùy chọn quyền riêng tư"
        description="Bạn có thể thay đổi lựa chọn này bất cứ lúc nào. Rút lại đồng ý không ảnh hưởng tới việc dùng trang."
        footer={
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={onClose} disabled={saving}>
              Hủy
            </Button>
            <Button onClick={() => void choose(analytics, 'preferences')} isLoading={saving}>
              Lưu lựa chọn
            </Button>
          </div>
        }
      >
        <div className="grid gap-4 p-5 text-body-sm text-on-surface-variant">
          <section aria-labelledby="consent-necessary">
            <h3 id="consent-necessary" className="font-semibold text-on-surface">
              Cần thiết (luôn bật)
            </h3>
            <p className="mt-1">
              Đăng nhập, bảo mật, chống lạm dụng và ghi nhớ lựa chọn này. Không thể tắt vì trang không hoạt động được
              nếu thiếu.
            </p>
          </section>
          <section aria-labelledby="consent-analytics" className="rounded-lg border p-4">
            <h3 id="consent-analytics" className="sr-only">
              Phân tích sản phẩm và hiệu năng
            </h3>
            <Switch
              checked={analytics}
              onCheckedChange={setAnalytics}
              label="Phân tích sản phẩm và hiệu năng"
              description={PURPOSE}
            />
            <ul className="mt-2 list-disc space-y-1 pl-5 text-label">
              <li>Dùng mã ngẫu nhiên của trình duyệt, không dùng số điện thoại hay email.</li>
              <li>Mã định danh bị xóa khỏi dữ liệu sau 90 ngày; dữ liệu chi tiết bị xóa sau 180 ngày.</li>
              <li>Lưu lượng của bot và nhân viên nội bộ được loại khỏi số liệu.</li>
            </ul>
          </section>
          <p>
            Xem thêm tại{' '}
            <Link to="/privacy" className="font-medium text-primary underline" onClick={onClose}>
              Chính sách quyền riêng tư
            </Link>
            .
          </p>
        </div>
      </Dialog>
    );
  }

  return (
    <section
      aria-label="Đồng ý phân tích dữ liệu"
      className="fixed inset-x-0 bottom-0 z-40 border-t bg-white shadow-2xl"
      style={{ borderColor: 'var(--ndc-border)' }}
    >
      <div className="ndc-page flex flex-col gap-4 py-4 md:flex-row md:items-center md:justify-between">
        <div className="flex gap-3">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
          <div className="text-body-sm text-on-surface-variant">
            <p className="font-semibold text-on-surface">Bạn có đồng ý cho chúng tôi phân tích cách dùng trang?</p>
            <p className="mt-1 max-w-3xl">
              {PURPOSE} Chỉ bắt đầu khi bạn đồng ý.{' '}
              <Link to="/privacy" className="font-medium text-primary underline">
                Chính sách quyền riêng tư
              </Link>
            </p>
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <Button variant="ghost" onClick={() => setMode('preferences')} disabled={saving}>
            Tùy chỉnh
          </Button>
          <Button variant="outline" onClick={() => void choose(false, 'banner')} disabled={saving}>
            Từ chối
          </Button>
          <Button variant="outline" onClick={() => void choose(true, 'banner')} disabled={saving}>
            Đồng ý
          </Button>
        </div>
      </div>
    </section>
  );
}

export default ConsentBanner;
