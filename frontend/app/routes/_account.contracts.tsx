import React from 'react';
import { ArrowLeft, LockKeyhole, ShieldAlert } from 'lucide-react';
import { Link } from 'react-router-dom';

export const DepositContractPage: React.FC = () => (
  <div className="max-w-3xl mx-auto px-4 py-12">
    <Link to="/" className="inline-flex items-center gap-2 text-sm font-semibold text-primary min-h-11"><ArrowLeft className="w-4 h-4" />Về trang chủ</Link>
    <section className="mt-5 rounded-2xl bg-surface-container-low p-6 sm:p-10">
      <div className="w-14 h-14 rounded-xl bg-amber-100 text-amber-800 grid place-items-center"><LockKeyhole className="w-7 h-7" /></div>
      <h1 className="text-2xl sm:text-3xl font-bold mt-5">Trao đổi trực tiếp với người đăng</h1>
      <p className="mt-3 text-base leading-relaxed text-on-surface-variant max-w-2xl">Nhà Đất Chuẩn là nền tảng đăng tin có kiểm duyệt, không nhận tiền cọc, không ký hợp đồng thay người dùng và không làm trung gian thanh toán bất động sản.</p>
      <div className="mt-6 flex gap-3 p-4 rounded-xl bg-amber-50 text-amber-950"><ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" /><p className="text-sm">Hãy gặp trực tiếp, kiểm tra giấy tờ và chỉ chuyển tiền khi đã xác minh đầy đủ bên liên quan. Không gửi CCCD hoặc thông tin tài chính qua kênh không đáng tin cậy.</p></div>
    </section>
  </div>
);
