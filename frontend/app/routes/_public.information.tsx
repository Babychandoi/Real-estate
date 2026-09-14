import { Link, useLocation } from 'react-router-dom';

const CONTENT: Record<string, { title: string; body: string }> = {
  '/about': { title: 'Giới thiệu Nhà Đất Chuẩn', body: 'Nền tảng hỗ trợ đăng, tìm kiếm và kiểm duyệt nội dung tin bất động sản. Các bên tự liên hệ và chịu trách nhiệm xác minh trước khi giao dịch.' },
  '/terms': { title: 'Điều khoản sử dụng', body: 'Người đăng chịu trách nhiệm về tính chính xác của nội dung. Nền tảng có quyền từ chối hoặc gỡ nội dung vi phạm và không nhận tiền giao dịch bất động sản.' },
  '/privacy': { title: 'Chính sách quyền riêng tư', body: 'Thông tin tài khoản, eKYC và thông tin liên hệ được giới hạn truy cập theo vai trò. Không đưa giấy tờ định danh vào dữ liệu công khai.' },
  '/contact': { title: 'Liên hệ hỗ trợ', body: 'Vui lòng gửi email tới nhadatchuan.online@gmail.com. Báo cáo một tin cụ thể bằng nút “Báo cáo tin vi phạm” tại trang chi tiết.' },
};

export function InformationPage() {
  const location = useLocation();
  const content = CONTENT[location.pathname];
  if (!content) return <NotFoundPage />;
  return <main className="mx-auto max-w-3xl px-4 py-14"><h1 className="text-3xl font-bold">{content.title}</h1><p className="mt-5 leading-7 text-slate-700">{content.body}</p><Link to="/" className="mt-8 inline-flex min-h-11 items-center font-bold text-primary">Về trang chủ</Link></main>;
}

export function NotFoundPage() {
  return <main className="mx-auto max-w-xl px-4 py-20 text-center"><p className="text-sm font-bold text-slate-500">404</p><h1 className="mt-2 text-3xl font-bold">Không tìm thấy trang</h1><Link to="/" className="mt-8 inline-flex min-h-11 items-center font-bold text-primary">Về trang chủ</Link></main>;
}
