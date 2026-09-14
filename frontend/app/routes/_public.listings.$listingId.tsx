import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Building2, ShieldCheck, MapPin, Maximize2, Home, ArrowLeft, Lock, MessageSquare, Flag } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';
import { apiClient } from '@/shared/api/client';
import { type ListingDetail, formatPriceVnd, calculateUnitPrice, formatPropertyType } from '@/entities/listing/model/types';
import { LeadConsultationModal } from '@/features/lead/ui/LeadConsultationModal';

export const ListingDetailPage: React.FC = () => {
  const { listingId } = useParams<{ listingId: string }>();
  const [listing, setListing] = useState<ListingDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isConsultationModalOpen, setIsConsultationModalOpen] = useState(false);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [reportCategory, setReportCategory] = useState('OTHER');
  const [reportDescription, setReportDescription] = useState('');
  const [reportFeedback, setReportFeedback] = useState<string | null>(null);
  const [reportBusy, setReportBusy] = useState(false);

  const submitReport = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!listingId || reportDescription.trim().length < 10) return;
    setReportBusy(true);
    setReportFeedback(null);
    try {
      await apiClient('/public/reports', { method: 'POST', body: JSON.stringify({ listingId, category: reportCategory, severity: 'MEDIUM', description: reportDescription.trim(), evidenceUrls: '', reporterPhone: '' }) });
      setReportFeedback('Báo cáo đã được ghi nhận trên hệ thống.');
      setReportDescription('');
    } catch {
      setReportFeedback('Không thể gửi báo cáo. Vui lòng thử lại.');
    } finally { setReportBusy(false); }
  };

  useEffect(() => {
    async function loadListing() {
      try {
        const data = await apiClient<ListingDetail>(`/listings/${listingId}`);
        setListing(data);
      } catch (err) {
        console.error('Lỗi khi tải chi tiết tin đăng:', err);
      } finally {
        setIsLoading(false);
      }
    }
    if (listingId) {
      loadListing();
    }
  }, [listingId]);

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-12">
        <div className="h-96 bg-surface-container rounded-2xl animate-pulse"></div>
      </div>
    );
  }

  if (!listing) {
    return (
      <div className="max-w-md mx-auto my-16 text-center">
        <h2 className="text-xl font-bold">Không tìm thấy bất động sản</h2>
        <Link to="/" className="text-primary mt-4 inline-block font-semibold">
          Quay lại trang chủ
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 md:px-8 py-6 flex flex-col gap-6">
      {/* Nút quay lại */}
      <div>
        <Link
          to="/search"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant hover:text-primary transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Quay lại danh sách
        </Link>
        <button type="button" onClick={() => setIsReportOpen(true)} className="ml-4 inline-flex min-h-11 items-center gap-1.5 text-xs font-semibold text-rose-700"><Flag className="h-4 w-4" /> Báo cáo tin vi phạm</button>
      </div>

      {/* Hero Gallery ảnh */}
      <div className="relative rounded-2xl overflow-hidden aspect-[16/9] md:aspect-[21/9] bg-surface-container">
        {listing.imageUrls[0] ? <img
          src={listing.imageUrls[0]}
          alt={listing.title}
          decoding="async"
          fetchPriority="high"
          className="w-full h-full object-cover"
        /> : <div className="grid h-full place-items-center text-on-surface-variant" role="img" aria-label="Tin đăng chưa có ảnh">
          <Building2 className="h-16 w-16" aria-hidden="true" />
        </div>}
        <div className="absolute top-4 left-4 flex gap-2">
          {listing.isVerified && (
            <Badge variant="verified" icon={<ShieldCheck className="w-4 h-4" />}>
              Đã xác thực người đăng
            </Badge>
          )}
          {listing.isShowcase && <Badge variant="neutral">Tin mẫu trải nghiệm</Badge>}
        </div>
      </div>
      {listing.imageUrls.length > 1 && (
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-5" aria-label="Các ảnh của tin đăng">
          {listing.imageUrls.slice(1, 6).map((url, index) => (
            <img key={url} src={url} alt={`Ảnh ${index + 2} của ${listing.title}`} loading="lazy" decoding="async" className="aspect-video w-full rounded-xl object-cover" />
          ))}
        </div>
      )}

      {/* Chi tiết nội dung và Form liên hệ Lead */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        {/* Cột trái: Thông số & Mô tả */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          <div>
            <div className="flex items-baseline gap-3 flex-wrap">
              <span className="text-3xl font-extrabold text-primary tracking-tight">
                {formatPriceVnd(listing.priceVnd)}
              </span>
              <span className="text-sm font-semibold text-on-surface-variant">
                {calculateUnitPrice(listing.priceVnd, listing.areaM2)}
              </span>
            </div>
            <h1 className="text-xl md:text-2xl font-bold text-on-surface mt-2 leading-snug">
              {listing.title}
            </h1>
            <p className="flex items-center gap-1.5 text-sm text-on-surface-variant mt-2">
              <MapPin className="w-4 h-4 text-outline" /> {listing.addressSummary}
            </p>
          </div>

          {/* Ma trận thông số kỹ thuật */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Card className="flex flex-col items-center text-center p-3">
              <Maximize2 className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Diện tích</span>
              <span className="text-sm font-bold text-on-surface">{listing.areaM2} m²</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Home className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Loại hình</span>
              <span className="text-sm font-bold text-on-surface">{formatPropertyType(listing.propertyType)}</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <span className="text-xs text-on-surface-variant">Nhu cầu</span>
              <span className="text-sm font-bold text-on-surface">{listing.purpose === 'SALE' ? 'Cần bán' : 'Cho thuê'}</span>
            </Card>
          </div>

          {/* Mô tả chi tiết */}
          <div className="flex flex-col gap-3">
            <h2 className="text-lg font-bold text-on-surface">Mô tả bất động sản</h2>
            <div className="text-sm text-on-surface leading-relaxed whitespace-pre-line bg-surface-container-lowest p-5 rounded-xl border border-outline-variant/40">
              {listing.description?.trim() || 'Người đăng chưa cung cấp mô tả chi tiết.'}
            </div>
          </div>
        </div>

        {/* Cột phải: Form gửi yêu cầu tư vấn Lead */}
        <div className="lg:col-span-1 sticky top-6">
          <Card className="p-5 border border-primary/20 shadow-lg shadow-primary/5">
            <div className="flex items-center gap-2 pb-4 border-b border-outline-variant/40">
              <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold">
                BDS
              </div>
              <div>
                <h3 className="font-bold text-sm text-on-surface">Liên hệ người đăng</h3>
                <p className="text-xs text-on-surface-variant">Trao đổi trực tiếp về tin đăng</p>
              </div>
            </div>

            <div className="mt-4 flex flex-col gap-3">
                <p className="text-sm text-on-surface-variant">Gửi một yêu cầu ngắn để hẹn thời gian xem nhà. Hệ thống chỉ ghi nhận thành công khi máy chủ trả về mã yêu cầu.</p>
                {listing.isShowcase ? <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Tin này dùng để trải nghiệm tìm kiếm, không tiếp nhận tên hoặc số điện thoại thật.</p> : <Button
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={() => setIsConsultationModalOpen(true)}
                  leftIcon={<MessageSquare className="w-4 h-4 text-primary" />}
                  className="w-full border-primary/30 text-primary hover:bg-primary/10 font-bold"
                >
                  Hẹn xem & nhận tư vấn
                </Button>}
            </div>

            {/* Khối Giao dịch Đặt cọc Trực tuyến Bảo đảm Escrow (FR28, FR30, UC05) */}
            <div className="mt-6 pt-5 border-t border-outline-variant/30 flex flex-col gap-3">
              <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-start gap-2.5">
                <Lock className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
                <div className="text-xs">
                  <span className="font-bold text-emerald-900 block">Liên hệ và trao đổi trực tiếp</span>
                  <span className="text-emerald-700 leading-snug block mt-0.5">
                    Nhà Đất Chuẩn không nhận tiền cọc hoặc ký hợp đồng thay bạn. Hãy xác minh người đăng và hồ sơ pháp lý trước khi giao dịch.
                  </span>
                </div>
              </div>

            </div>
          </Card>
        </div>
      </div>

      {/* Modal Đăng Ký Tư Vấn & Xác Minh OTP Khách Hàng (FR18, FR20, UC04) */}
      {listing && !listing.isShowcase && (
        <LeadConsultationModal
          isOpen={isConsultationModalOpen}
          onClose={() => setIsConsultationModalOpen(false)}
          listing={{
            id: listing.id,
            title: listing.title,
            priceVnd: listing.priceVnd,
            areaM2: listing.areaM2,
            address: listing.addressSummary,
            imageUrl: listing.imageUrls[0] || '',
          }}
        />
      )}
      {isReportOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-labelledby="report-title">
          <form onSubmit={submitReport} className="w-full max-w-lg space-y-4 rounded-2xl bg-white p-6">
            <h2 id="report-title" className="text-xl font-bold">Báo cáo tin vi phạm</h2>
            <label className="block text-sm font-semibold">Loại vi phạm<select value={reportCategory} onChange={(event) => setReportCategory(event.target.value)} className="mt-1 min-h-11 w-full rounded-lg border px-3"><option value="SCAM_DEPOSIT">Có dấu hiệu lừa cọc</option><option value="FAKE_SOLD">Tin không còn đúng hiện trạng</option><option value="INCORRECT_PRICE">Giá không chính xác</option><option value="OTHER">Khác</option></select></label>
            <label className="block text-sm font-semibold">Mô tả<textarea required minLength={10} value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} rows={4} className="mt-1 w-full rounded-lg border p-3" /></label>
            {reportFeedback && <p role="status" className="text-sm">{reportFeedback}</p>}
            <div className="flex justify-end gap-2"><button type="button" onClick={() => setIsReportOpen(false)} className="min-h-11 rounded-lg border px-4">Đóng</button><button disabled={reportBusy || reportDescription.trim().length < 10} className="min-h-11 rounded-lg bg-rose-700 px-4 font-bold text-white disabled:opacity-50">{reportBusy ? 'Đang gửi…' : 'Gửi báo cáo'}</button></div>
          </form>
        </div>
      )}
    </div>
  );
};
