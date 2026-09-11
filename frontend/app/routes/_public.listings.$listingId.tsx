import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ShieldCheck, MapPin, Maximize2, Bed, Bath, Compass, ArrowLeft, Send, CheckCircle2, Lock, MessageSquare } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';
import { apiClient } from '@/shared/api/client';
import { type Listing, formatPriceVnd, calculateUnitPrice } from '@/entities/listing/model/types';
import { LeadConsultationModal } from '@/features/lead/ui/LeadConsultationModal';

export const ListingDetailPage: React.FC = () => {
  const { listingId } = useParams<{ listingId: string }>();
  const [listing, setListing] = useState<Listing | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isConsultationModalOpen, setIsConsultationModalOpen] = useState(false);

  // Form liên hệ nhận tư vấn (Lead)
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [note, setNote] = useState('');
  const [consent, setConsent] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    async function loadListing() {
      try {
        const data = await apiClient<Listing>(`/listings/${listingId}`);
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

  const handleLeadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!phone || !fullName) {
      setErrorMessage('Vui lòng điền đầy đủ họ tên và số điện thoại.');
      return;
    }
    if (!consent) {
      setErrorMessage('Bạn cần đồng ý với chính sách xử lý dữ liệu.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      await apiClient('/public/leads', {
        method: 'POST',
        body: JSON.stringify({
          listingId: listing?.id || '11111111-1111-1111-1111-111111111111',
          fullName,
          phone,
          note,
          consentPolicy: consent,
        }),
      });
      setSubmitSuccess(true);
    } catch (err: unknown) {
      setErrorMessage('Không thể gửi yêu cầu tư vấn. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setIsSubmitting(false);
    }
  };

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
          to="/"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant hover:text-primary transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Quay lại danh sách
        </Link>
      </div>

      {/* Hero Gallery ảnh */}
      <div className="relative rounded-2xl overflow-hidden aspect-[16/9] md:aspect-[21/9] bg-surface-container">
        <img
          src={listing.primaryImageUrl}
          alt={listing.title}
          className="w-full h-full object-cover"
        />
        <div className="absolute top-4 left-4 flex gap-2">
          {listing.isVerified && (
            <Badge variant="verified" icon={<ShieldCheck className="w-4 h-4" />}>
              Đã xác thực sổ đỏ/hồng
            </Badge>
          )}
        </div>
      </div>

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
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Card className="flex flex-col items-center text-center p-3">
              <Maximize2 className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Diện tích</span>
              <span className="text-sm font-bold text-on-surface">{listing.areaM2} m²</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Bed className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Phòng ngủ</span>
              <span className="text-sm font-bold text-on-surface">2 Phòng</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Bath className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Vệ sinh</span>
              <span className="text-sm font-bold text-on-surface">2 Phòng</span>
            </Card>
            <Card className="flex flex-col items-center text-center p-3">
              <Compass className="w-5 h-5 text-primary mb-1" />
              <span className="text-xs text-on-surface-variant">Hướng</span>
              <span className="text-sm font-bold text-on-surface">Đông Nam</span>
            </Card>
          </div>

          {/* Mô tả chi tiết */}
          <div className="flex flex-col gap-3">
            <h2 className="text-lg font-bold text-on-surface">Mô tả bất động sản</h2>
            <div className="text-sm text-on-surface leading-relaxed whitespace-pre-line bg-surface-container-lowest p-5 rounded-xl border border-outline-variant/40">
              {`Căn hộ tầng trung view trực diện công viên và hồ điều hòa, ban công Đông Nam mát mẻ quanh năm.
              - Nội thất bàn giao full cao cấp: sàn gỗ nhập khẩu, điều hòa âm trần Daikin, thiết bị vệ sinh Kohler.
              - Pháp lý minh bạch: Sổ hồng lâu dài sẵn sàng công chứng sang tên ngay.
              - Tiện ích nội khu đẳng cấp: bể bơi 4 mùa, sân tennis, trường học Vinschool, siêu thị VinMart ngay khối đế.`}
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
                <h3 className="font-bold text-sm text-on-surface">Chuyên viên tư vấn</h3>
                <p className="text-xs text-secondary font-medium flex items-center gap-1">
                  <ShieldCheck className="w-3.5 h-3.5" /> Môi giới chứng nhận
                </p>
              </div>
            </div>

            {submitSuccess ? (
              <div className="my-6 text-center flex flex-col items-center gap-2">
                <CheckCircle2 className="w-12 h-12 text-secondary" />
                <h4 className="font-bold text-base text-on-surface">Gửi yêu cầu thành công!</h4>
                <p className="text-xs text-on-surface-variant">
                  Chuyên viên tư vấn sẽ liên hệ lại với bạn trong vòng 15 phút.
                </p>
              </div>
            ) : (
              <form onSubmit={handleLeadSubmit} className="mt-4 flex flex-col gap-3">
                <h4 className="font-bold text-sm text-on-surface">Đặt lịch xem & Nhận báo giá</h4>

                {errorMessage && (
                  <div className="p-2.5 rounded-lg bg-error-container text-error-on-container text-xs">
                    {errorMessage}
                  </div>
                )}

                <div>
                  <label className="text-xs font-semibold text-on-surface-variant block mb-1">
                    Họ và tên *
                  </label>
                  <input
                    type="text"
                    required
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    placeholder="Nguyễn Văn A"
                    className="w-full text-sm px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:border-primary bg-surface-container-lowest"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-on-surface-variant block mb-1">
                    Số điện thoại *
                  </label>
                  <input
                    type="tel"
                    required
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="0912345678"
                    className="w-full text-sm px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:border-primary bg-surface-container-lowest"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-on-surface-variant block mb-1">
                    Ghi chú thêm
                  </label>
                  <textarea
                    rows={2}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Muốn xem nhà vào cuối tuần..."
                    className="w-full text-sm px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:border-primary bg-surface-container-lowest"
                  />
                </div>

                <div className="flex items-start gap-2 pt-1">
                  <input
                    type="checkbox"
                    id="consent"
                    checked={consent}
                    onChange={(e) => setConsent(e.target.checked)}
                    className="checkbox checkbox-primary checkbox-xs mt-0.5"
                  />
                  <label htmlFor="consent" className="text-xs text-on-surface-variant cursor-pointer">
                    Tôi đồng ý để hệ thống liên hệ và bảo mật thông tin theo chính sách quyền riêng tư.
                  </label>
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  isLoading={isSubmitting}
                  leftIcon={<Send className="w-4 h-4" />}
                  className="w-full mt-2"
                >
                  Gửi yêu cầu tư vấn nhanh
                </Button>

                <Button
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={() => setIsConsultationModalOpen(true)}
                  leftIcon={<MessageSquare className="w-4 h-4 text-primary" />}
                  className="w-full border-primary/30 text-primary hover:bg-primary/10 font-bold"
                >
                  Tư vấn & Hẹn xem nhà (OTP)
                </Button>
              </form>
            )}

            {/* Khối Giao dịch Đặt cọc Trực tuyến Bảo đảm Escrow (FR28, FR30, UC05) */}
            <div className="mt-6 pt-5 border-t border-outline-variant/30 flex flex-col gap-3">
              <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-start gap-2.5">
                <Lock className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
                <div className="text-xs">
                  <span className="font-bold text-emerald-900 block">Ký quỹ Escrow Bảo đảm</span>
                  <span className="text-emerald-700 leading-snug block mt-0.5">
                    Đặt cọc online qua Két Escrow an toàn 100%, phong tỏa tiền cho tới khi ký công chứng.
                  </span>
                </div>
              </div>

              <Link to={`/contracts/dc-${listing.id}`}>
                <Button
                  variant="outline"
                  size="md"
                  className="w-full border-emerald-600 text-emerald-700 hover:bg-emerald-50 font-bold flex items-center justify-center gap-2 shadow-sm"
                >
                  <ShieldCheck className="w-4 h-4" />
                  Ký số Hợp đồng Cọc Trực tuyến
                </Button>
              </Link>
            </div>
          </Card>
        </div>
      </div>

      {/* Modal Đăng Ký Tư Vấn & Xác Minh OTP Khách Hàng (FR18, FR20, UC04) */}
      {listing && (
        <LeadConsultationModal
          isOpen={isConsultationModalOpen}
          onClose={() => setIsConsultationModalOpen(false)}
          listing={{
            id: listing.id,
            title: listing.title,
            priceVnd: listing.priceVnd,
            areaM2: listing.areaM2,
            address: listing.addressSummary,
            imageUrl: listing.primaryImageUrl,
            code: '#GB-8824',
            isCertifiedOwner: true,
            ownerName: 'Anh Hoàng (Chính chủ)',
          }}
        />
      )}
    </div>
  );
};
