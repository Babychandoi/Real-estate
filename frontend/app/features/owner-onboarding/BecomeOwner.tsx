import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Home } from 'lucide-react';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { apiClient } from '@/shared/api/client';
import { useAuth } from '@/shared/auth/AuthContext';
import { validationMessage } from '@/shared/types/problem-details';

/** Entry point shown to people looking for a home: "I own a property and want to post it". */
export function BecomeOwnerCard() {
  return (
    <section className="rounded-xl border border-outline-variant bg-surface p-4" aria-labelledby="become-owner-card">
      <h2 id="become-owner-card" className="flex items-center gap-2 text-body font-semibold text-on-surface">
        <Home className="h-5 w-5" aria-hidden="true" /> Tôi là chủ nhà muốn đăng tin
      </h2>
      <p className="mt-1 text-body-sm text-on-surface-variant">
        Chuyển tài khoản sang vai trò Chủ nhà để tự đăng bất động sản của mình, không qua môi giới.
      </p>
      <ButtonLink to="/become-owner" variant="outline" size="sm" className="mt-3">
        Tìm hiểu và chuyển vai trò
      </ButtonLink>
    </section>
  );
}

/** USER → OWNER after an explicit statement (P-09); the server records the change. */
export const BecomeOwnerPage: React.FC = () => {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await apiClient('/me/become-owner', { method: 'POST', body: JSON.stringify({ confirmOwnProperty: confirmed }) });
      await refreshUser();
      navigate('/listings/new');
    } catch (err) {
      setError(validationMessage(err, 'Chưa chuyển được vai trò, vui lòng thử lại.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl px-4 py-10" data-ready="true">
      <h1 className="text-headline-md text-on-surface">Đăng tin với vai trò Chủ nhà</h1>
      <p className="mt-2 text-body-sm text-on-surface-variant">
        Tài khoản {user?.email} hiện là người tìm nhà. Vai trò Chủ nhà dành cho người đăng bất động sản của chính mình
        (hoặc được chủ sở hữu ủy quyền), không phải môi giới.
      </p>
      <ul className="mt-4 list-disc pl-5 text-body-sm text-on-surface">
        <li>Tin của bạn hiển thị nhãn “Chủ nhà”.</li>
        <li>Bạn cần xác minh danh tính (eKYC) trước khi gửi tin.</li>
        <li>Mỗi tin được kiểm duyệt trước khi hiển thị và cần xác nhận còn hàng sau mỗi 45 ngày.</li>
        <li>Nếu bạn hành nghề môi giới, hãy đăng ký tài khoản Môi giới thay vì chọn vai trò này.</li>
      </ul>
      <Checkbox
        className="mt-6"
        checked={confirmed}
        onChange={(event) => setConfirmed(event.target.checked)}
        label="Tôi là chủ sở hữu (hoặc được chủ sở hữu ủy quyền) của bất động sản sẽ đăng."
      />
      {error && <InlineFeedback kind="error" title={error} className="mt-4" />}
      <div className="mt-6 flex gap-3">
        <Button disabled={!confirmed} isLoading={busy} onClick={() => void submit()}>
          Chuyển sang Chủ nhà
        </Button>
        <ButtonLink variant="outline" to="/my-inquiries">
          Để sau
        </ButtonLink>
      </div>
    </div>
  );
};

export default BecomeOwnerPage;
