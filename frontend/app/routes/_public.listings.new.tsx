import React, { useState, useEffect } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import {
  Building2, Key, Home, Castle, MapPin,
  CheckCircle2, ArrowRight, ArrowLeft, Save, Send, Sparkles, AlertCircle,
  ShieldCheck, Image as ImageIcon, Eye, TrendingUp, HelpCircle, Upload, X
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { apiClient } from '@/shared/api/client';
import { formatPriceVnd, calculateUnitPrice } from '@/entities/listing/model/types';
import { useAuth } from '@/shared/auth/AuthContext';
import type { UserKycProfile } from '@/entities/verification/model/types';

export const CreateListingPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  // Trạng thái Wizard 4 bước
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);

  // Form state
  const [listingId, setListingId] = useState<string | null>(null);
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>('SALE');
  const [propertyType, setPropertyType] = useState<string>('APARTMENT');
  const [title, setTitle] = useState('');
  const [priceVnd, setPriceVnd] = useState<number>(0);
  const [areaM2, setAreaM2] = useState<number>(0);
  const [province, setProvince] = useState('');
  const [district, setDistrict] = useState('');
  const [ward, setWard] = useState('');
  const [addressSummary, setAddressSummary] = useState('');
  const [latitude, setLatitude] = useState(0);
  const [longitude, setLongitude] = useState(0);
  const [description, setDescription] = useState('');

  // Media
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [isUploadingImages, setIsUploadingImages] = useState(false);
  const [deletingImage, setDeletingImage] = useState<string | null>(null);

  // AI & Quality State
  const [qualityScore, setQualityScore] = useState<number>(0);

  // UI state
  const [isSaving, setIsSaving] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [autosaveTime, setAutosaveTime] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSuccessSubmitted, setIsSuccessSubmitted] = useState(false);
  const [kycProfile, setKycProfile] = useState<UserKycProfile | null>(null);
  const [isCheckingKyc, setIsCheckingKyc] = useState(true);
  const editingListingId = searchParams.get('edit');

  useEffect(() => {
    if (!user) return;
    apiClient<UserKycProfile>(`/kyc/user/${user.id}`)
      .then(setKycProfile)
      .catch(() => setKycProfile(null))
      .finally(() => setIsCheckingKyc(false));
  }, [user]);

  useEffect(() => {
    if (!editingListingId) return;
    apiClient<{ id: string; purpose: 'SALE' | 'RENT'; propertyType: string; title: string; priceVnd: number; areaM2: number; description: string; provinceCode?: string; districtCode?: string; wardCode?: string; addressSummary: string; publicLatitude?: number; publicLongitude?: number; imageUrls: string[] }>(`/listings/${editingListingId}`)
      .then((listing) => {
        setListingId(listing.id); setPurpose(listing.purpose); setPropertyType(listing.propertyType); setTitle(listing.title);
        setPriceVnd(listing.priceVnd); setAreaM2(listing.areaM2); setDescription(listing.description); setProvince(listing.provinceCode || '');
        setDistrict(listing.districtCode || ''); setWard(listing.wardCode || ''); setAddressSummary(listing.addressSummary);
        setLatitude(listing.publicLatitude || 0); setLongitude(listing.publicLongitude || 0); setImageUrls(listing.imageUrls || []);
      })
      .catch(() => setErrorMessage('Không thể tải dữ liệu tin để chỉnh sửa. Vui lòng quay lại kho tin và thử lại.'));
  }, [editingListingId]);

  // Tính lại điểm chất lượng khi các trường thay đổi
  useEffect(() => {
    let score = 0;
    if (title.length >= 15) score += 20;
    if (description.length >= 80) score += 20;
    if (imageUrls.length >= 5) score += 25;
    else if (imageUrls.length >= 3) score += 15;
    if (latitude && longitude) score += 15;
    setQualityScore(Math.min(score, 100));
  }, [title, description, imageUrls, latitude, longitude]);

  // Tự động lưu nháp
  const handleSaveDraft = async () => {
    setIsSaving(true);
    setErrorMessage(null);
    try {
      if (!listingId) {
        const res = await apiClient<{ listingId: string }>('/listings', {
          method: 'POST',
          body: JSON.stringify({
            purpose,
            propertyType,
            title,
            priceVnd,
            areaM2,
            description,
            provinceCode: province || null,
            districtCode: district || null,
            wardCode: ward || null,
            addressSummary,
            publicLatitude: latitude,
            publicLongitude: longitude,
            imageUrls,
          }),
        });
        setListingId(res.listingId);
      } else {
        await apiClient(`/listings/${listingId}/draft`, {
          method: 'PUT',
          body: JSON.stringify({
            purpose,
            propertyType,
            title,
            priceVnd,
            areaM2,
            description,
            provinceCode: province || null,
            districtCode: district || null,
            wardCode: ward || null,
            addressSummary,
            publicLatitude: latitude || null,
            publicLongitude: longitude || null,
            imageUrls,
          }),
        });
      }
      setAutosaveTime(new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }));
    } catch (err: unknown) {
      console.error('Lỗi khi lưu nháp:', err);
      setErrorMessage('Không thể lưu bản nháp lên máy chủ. Vui lòng giữ trang này và thử lại.');
    } finally {
      setIsSaving(false);
    }
  };

  // Nộp duyệt tin đăng
  const handleSubmitForReview = async () => {
    if (!title || title.length < 10) {
      setErrorMessage('Tiêu đề phải từ 10 ký tự trở lên.');
      setStep(1);
      return;
    }
    if (areaM2 <= 0 || priceVnd <= 0) {
      setErrorMessage('Mức giá và diện tích phải lớn hơn 0.');
      setStep(2);
      return;
    }
    if (imageUrls.length < 3) {
      setErrorMessage('Vui lòng tải lên tối thiểu 3 ảnh chất lượng.');
      setStep(3);
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      let currentId = listingId;
      if (!currentId) {
        const draftRes = await apiClient<{ listingId: string }>('/listings', {
          method: 'POST',
          body: JSON.stringify({
            purpose,
            propertyType,
            title,
            priceVnd,
            areaM2,
            description,
            addressSummary,
            publicLatitude: latitude,
            publicLongitude: longitude,
            imageUrls,
          }),
        });
        currentId = draftRes.listingId;
        setListingId(currentId);
      } else {
        await apiClient(`/listings/${currentId}/draft`, {
          method: 'PUT',
          body: JSON.stringify({
            purpose, propertyType, title, priceVnd, areaM2, description,
            provinceCode: province || null, districtCode: district || null, wardCode: ward || null,
            addressSummary,
            publicLatitude: latitude || null,
            publicLongitude: longitude || null,
            imageUrls,
          }),
        });
      }

      await apiClient(`/listings/${currentId}/submit`, {
        method: 'POST',
      });

      setIsSuccessSubmitted(true);
    } catch (err: unknown) {
      console.error('Lỗi khi nộp duyệt tin:', err);
      setErrorMessage('Chưa thể nộp tin. Bản nháp vẫn được giữ; vui lòng kiểm tra kết nối rồi thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleImageUpload = async (files: FileList | null) => {
    if (!files?.length) return;
    const selected = Array.from(files).slice(0, 20 - imageUrls.length);
    const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/avif']);
    const invalid = selected.find((file) => !allowedTypes.has(file.type) || file.size <= 0 || file.size > 10 * 1024 * 1024);
    if (invalid) {
      setErrorMessage(`Ảnh “${invalid.name}” không hợp lệ. Chỉ nhận JPEG, PNG, WebP hoặc AVIF, tối đa 10 MB.`);
      return;
    }
    if (selected.length < files.length) {
      setErrorMessage('Mỗi tin đăng chỉ được lưu tối đa 20 ảnh.');
    } else {
      setErrorMessage(null);
    }
    setIsUploadingImages(true);
    try {
      for (const file of selected) {
        const formData = new FormData();
        formData.append('file', file);
        const result = await apiClient<{ url: string }>('/media/images', { method: 'POST', body: formData });
        setImageUrls((current) => [...current, result.url]);
      }
    } catch (err) {
      console.error('Không thể tải ảnh lên MinIO:', err);
      setErrorMessage('Một số ảnh chưa tải được lên kho lưu trữ. Các ảnh đã tải thành công vẫn được giữ lại; hãy thử lại phần còn thiếu.');
    } finally {
      setIsUploadingImages(false);
    }
  };

  const handleRemoveImage = async (index: number) => {
    const url = imageUrls[index];
    const objectKey = url.split('/').pop();
    if (!objectKey) return;
    setImageUrls((current) => current.filter((item) => item !== url));
    setDeletingImage(url);
    setErrorMessage(null);
    try {
      await apiClient(`/media/images/${objectKey}`, { method: 'DELETE' });
    } catch (err) {
      console.info('Ảnh đã được gỡ khỏi bản chỉnh sửa; object đang thuộc lịch sử revision hoặc sẽ được dọn nền.', err);
    } finally {
      setDeletingImage(null);
    }
  };

  if (isCheckingKyc) {
    return <main className="mx-auto max-w-5xl px-4 py-10" role="status">Đang kiểm tra điều kiện đăng tin…</main>;
  }

  if (kycProfile?.status !== 'VERIFIED') {
    const pending = kycProfile?.status === 'PENDING';
    return <main className="mx-auto max-w-3xl px-4 py-10 md:px-8">
      <section className="rounded-xl border border-slate-200 bg-white p-6 md:p-8">
        <ShieldCheck className="h-9 w-9 text-slate-900" />
        <h1 className="mt-4 text-2xl font-bold text-slate-950">Xác minh danh tính trước khi đăng tin</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">Để bảo vệ người đăng và người liên hệ, chỉ tài khoản đã được duyệt eKYC mới có thể tạo hoặc gửi tin đăng.</p>
        {pending ? <p className="mt-5 rounded-lg bg-slate-50 p-4 text-sm text-slate-700">Hồ sơ eKYC của bạn đang chờ duyệt thủ công. Bạn sẽ có thể đăng tin ngay khi hồ sơ được xác nhận.</p> : <Link to="/kyc" className="mt-6 inline-flex min-h-11 items-center rounded-lg bg-slate-950 px-4 text-sm font-bold text-white hover:bg-slate-800">Đi tới xác minh eKYC</Link>}
      </section>
    </main>;
  }

  if (isSuccessSubmitted) {
    return (
      <div className="min-h-screen bg-slate-50 py-16 flex items-center justify-center">
        <div className="container mx-auto px-4 max-w-xl">
          <Card className="p-8 text-center bg-white shadow-lg border border-slate-200">
            <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto mb-4">
              <CheckCircle2 className="w-10 h-10" />
            </div>
            <h2 className="text-2xl font-bold text-slate-900 mb-2">
              Nộp duyệt tin đăng thành công!
            </h2>
            <p className="text-slate-600 text-sm mb-6 leading-relaxed">
              Tin đăng đã được tiếp nhận vào hàng đợi kiểm duyệt nội dung. Bạn sẽ nhận được thông báo khi quản trị viên hoàn tất xét duyệt.
            </p>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-left mb-6 text-xs space-y-2">
              <div className="flex justify-between">
                <span className="text-slate-500">Mã tin đăng:</span>
                <span className="font-mono font-bold text-slate-800">{listingId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Điểm chất lượng tin:</span>
                <span className="font-bold text-emerald-700">{qualityScore}/100 (mức độ hoàn thiện biểu mẫu)</span>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <Button onClick={() => navigate('/my-listings')} variant="primary" className="bg-emerald-600 hover:bg-emerald-700">
                Về Quản lý tin của tôi
              </Button>
              <Button onClick={() => navigate('/broker/workspace')} variant="outline">
                Không gian Môi giới
              </Button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 py-8">
      <div className="container mx-auto px-4 max-w-6xl">
        {/* Top Breadcrumb & Autosave info */}
        <div className="flex items-center justify-between mb-6">
          <Link to="/my-listings" className="inline-flex items-center gap-1.5 text-sm text-slate-600 hover:text-slate-900 font-medium transition-colors">
            <ArrowLeft className="w-4 h-4" />
            <span>Quay lại Kho tin của tôi</span>
          </Link>

          <div className="flex items-center gap-3">
            {autosaveTime && (
              <span className="text-xs text-slate-500 flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                Đã tự động lưu lúc {autosaveTime}
              </span>
            )}
            <Button
              onClick={handleSaveDraft}
              disabled={isSaving}
              variant="outline"
              size="sm"
              className="text-xs font-semibold text-slate-700 bg-white"
            >
              <Save className="w-3.5 h-3.5 mr-1" />
              {isSaving ? 'Đang lưu...' : 'Lưu bản nháp'}
            </Button>
          </div>
        </div>

        {/* Header Title */}
        <div className="mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight">
              Soạn thảo & Đăng tin BĐS Chuẩn Minh Bạch
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Quy trình 4 bước có lưu nháp • Gợi ý giá theo quy tắc tham khảo
            </p>
          </div>

          {/* Listing Quality Badge */}
          <div className="flex items-center gap-2 bg-white px-4 py-2.5 rounded-xl border border-slate-200 shadow-sm">
            <Sparkles className="w-5 h-5 text-amber-500" />
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-slate-700">Điểm chất lượng tin:</span>
                <span className="text-sm font-black text-emerald-700">{qualityScore}/100</span>
              </div>
              <div className="w-28 bg-slate-200 h-1.5 rounded-full overflow-hidden mt-1">
                <div
                  className="bg-emerald-600 h-full rounded-full transition-all duration-300"
                  style={{ width: `${qualityScore}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Wizard Stepper 4 Bước */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-4 mb-8">
          {[
            { num: 1, label: 'Loại BĐS & Vị trí GIS', icon: MapPin },
            { num: 2, label: 'Thông số & Gợi ý giá', icon: TrendingUp },
            { num: 3, label: 'Hình ảnh & An toàn', icon: ImageIcon },
            { num: 4, label: 'Xem trước & Gửi duyệt', icon: Eye },
          ].map((s) => {
            const Icon = s.icon;
            const isActive = step === s.num;
            const isCompleted = step > s.num;
            return (
              <button
                key={s.num}
                type="button"
                onClick={() => setStep(s.num as 1 | 2 | 3 | 4)}
                className={`flex items-center gap-3 p-3.5 rounded-xl border text-left transition-all ${
                  isActive
                    ? 'bg-emerald-50 border-emerald-500 text-emerald-900 shadow-sm'
                    : isCompleted
                    ? 'bg-white border-slate-300 text-slate-800'
                    : 'bg-white/60 border-slate-200 text-slate-400'
                }`}
              >
                <div
                  className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs shrink-0 ${
                    isActive
                      ? 'bg-emerald-600 text-white shadow'
                      : isCompleted
                      ? 'bg-emerald-100 text-emerald-800'
                      : 'bg-slate-100 text-slate-500'
                  }`}
                >
                  {isCompleted ? <CheckCircle2 className="w-4 h-4" /> : <Icon className="w-4 h-4" />}
                </div>
                <div className="overflow-hidden">
                  <span className="text-xs font-bold block truncate">{s.label}</span>
                  <span className="text-[11px] text-slate-400 block">Bước {s.num} / 4</span>
                </div>
              </button>
            );
          })}
        </div>

        {errorMessage && (
          <div className="mb-6 p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-sm flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* NỘI DUNG TỪNG BƯỚC WIZARD */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Main Form Column (8 cols) */}
          <div className="lg:col-span-8 flex flex-col gap-6">
            {/* BƯỚC 1: LOẠI BĐS & ĐỊNH VỊ GIS THÔNG MINH */}
            {step === 1 && (
              <Card className="p-6">
                <h3 className="text-base font-bold text-slate-900 mb-4 pb-2 border-b border-slate-100 flex items-center gap-2">
                  <MapPin className="w-5 h-5 text-emerald-600" />
                  Bước 1: Loại Hình & Định vị Địa chỉ GIS
                </h3>

                {/* Mục đích */}
                <div className="mb-6">
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2">
                    Nhu cầu đăng tin *
                  </label>
                  <div className="grid grid-cols-2 gap-4">
                    <button
                      type="button"
                      onClick={() => setPurpose('SALE')}
                      className={`p-4 rounded-xl border flex items-center gap-3 font-bold text-sm transition-all ${
                        purpose === 'SALE'
                          ? 'border-emerald-600 bg-emerald-50 text-emerald-900 shadow-sm'
                          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <Building2 className="w-5 h-5 text-emerald-600" />
                      <div>
                        <span className="block">Cần Bán Bất động sản</span>
                        <span className="text-xs text-slate-400 font-normal">Chuyển nhượng quyền sở hữu</span>
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() => setPurpose('RENT')}
                      className={`p-4 rounded-xl border flex items-center gap-3 font-bold text-sm transition-all ${
                        purpose === 'RENT'
                          ? 'border-emerald-600 bg-emerald-50 text-emerald-900 shadow-sm'
                          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <Key className="w-5 h-5 text-emerald-600" />
                      <div>
                        <span className="block">Cho Thuê Bất động sản</span>
                        <span className="text-xs text-slate-400 font-normal">Hợp đồng thuê theo tháng/năm</span>
                      </div>
                    </button>
                  </div>
                </div>

                {/* Loại hình BĐS */}
                <div className="mb-6">
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2">
                    Loại hình tài sản *
                  </label>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {[
                      { type: 'APARTMENT', label: 'Căn hộ chung cư', icon: Building2 },
                      { type: 'HOUSE', label: 'Nhà riêng / Phố', icon: Home },
                      { type: 'VILLA', label: 'Biệt thự / Liền kề', icon: Castle },
                      { type: 'LAND', label: 'Đất thổ cư / Nền', icon: MapPin },
                    ].map((item) => {
                      const Icon = item.icon;
                      return (
                        <button
                          key={item.type}
                          type="button"
                          onClick={() => setPropertyType(item.type)}
                          className={`p-3 rounded-xl border text-center flex flex-col items-center gap-2 transition-all ${
                            propertyType === item.type
                              ? 'border-emerald-600 bg-emerald-50 text-emerald-900 font-bold'
                              : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                          }`}
                        >
                          <Icon className="w-5 h-5 text-emerald-600" />
                          <span className="text-xs">{item.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Tiêu đề tin đăng */}
                <div className="mb-6">
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                    Tiêu đề tin đăng chuẩn SEO *
                  </label>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Ví dụ: Bán căn hộ The Matrix One 2PN Mễ Trì, Nam Từ Liêm, Sổ hồng sẵn sàng"
                    className="w-full px-4 py-2.5 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 font-medium"
                  />
                  <p className="text-[11px] text-slate-400 mt-1">
                    Tối thiểu 15 ký tự, nêu rõ loại hình, dự án và địa điểm để tăng điểm chất lượng tin.
                  </p>
                </div>

                {/* Địa chỉ hành chính có chuẩn hóa */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-600 mb-1">Tỉnh / Thành phố *</label>
                    <input
                      type="text"
                      value={province}
                      onChange={(e) => setProvince(e.target.value)}
                      className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm bg-slate-50"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-600 mb-1">Quận / Huyện *</label>
                    <input
                      type="text"
                      value={district}
                      onChange={(e) => setDistrict(e.target.value)}
                      className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm bg-slate-50"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-600 mb-1">Phường / Xã *</label>
                    <input
                      type="text"
                      value={ward}
                      onChange={(e) => setWard(e.target.value)}
                      className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm bg-slate-50"
                    />
                  </div>
                </div>

                <div className="mb-6">
                  <label className="block text-xs font-bold text-slate-600 mb-1">Địa chỉ chi tiết / Tên dự án</label>
                  <input
                    type="text"
                    value={addressSummary}
                    onChange={(e) => setAddressSummary(e.target.value)}
                    className="w-full px-4 py-2 rounded-xl border border-slate-300 text-sm"
                  />
                </div>

                {/* Tọa độ hiển thị công khai trên bản đồ */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <MapPin className="w-4 h-4 text-emerald-600" />
                      <span className="text-xs font-bold text-slate-800 uppercase">Tọa độ hiển thị trên bản đồ</span>
                    </div>
                    <span className="text-xs text-amber-700">Chỉ nhập tọa độ bạn đồng ý công khai.</span>
                  </div>
                  <div className="grid grid-cols-2 gap-4 text-xs font-mono">
                    <div>
                      <span className="text-slate-400 block mb-0.5">Vĩ độ (Latitude):</span>
                      <input
                        type="number"
                        step="0.0001"
                        value={latitude}
                        onChange={(e) => setLatitude(parseFloat(e.target.value))}
                        className="w-full px-3 py-1.5 rounded-lg border border-slate-300 bg-white"
                      />
                    </div>
                    <div>
                      <span className="text-slate-400 block mb-0.5">Kinh độ (Longitude):</span>
                      <input
                        type="number"
                        step="0.0001"
                        value={longitude}
                        onChange={(e) => setLongitude(parseFloat(e.target.value))}
                        className="w-full px-3 py-1.5 rounded-lg border border-slate-300 bg-white"
                      />
                    </div>
                  </div>
                </div>

                <div className="mt-6 flex justify-end">
                  <Button onClick={() => setStep(2)} variant="primary" className="bg-emerald-600 hover:bg-emerald-700">
                    Tiếp tục: Thông số & Giá <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </div>
              </Card>
            )}

            {/* BƯỚC 2: THÔNG SỐ KỸ THUẬT & AI ĐỊNH GIÁ THỊ TRƯỜNG */}
            {step === 2 && (
              <Card className="p-6">
                <h3 className="text-base font-bold text-slate-900 mb-4 pb-2 border-b border-slate-100 flex items-center gap-2">
                  <TrendingUp className="w-5 h-5 text-emerald-600" />
                  Bước 2: Thông số chi tiết & Gợi ý giá tham khảo
                </h3>

                {/* Giá & Diện tích */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-6">
                  <div>
                    <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                      Mức giá đề xuất (VNĐ) *
                    </label>
                    <input
                      type="number"
                      value={priceVnd}
                      onChange={(e) => setPriceVnd(Number(e.target.value))}
                      className="w-full px-4 py-2.5 rounded-xl border border-slate-300 text-sm font-bold text-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                    <div className="flex items-center justify-between text-xs text-slate-500 mt-1">
                      <span>Bằng chữ: <strong>{formatPriceVnd(priceVnd)}</strong></span>
                      <span>{calculateUnitPrice(priceVnd, areaM2)}</span>
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                      Diện tích tim tường (m²) *
                    </label>
                    <input
                      type="number"
                      step="0.5"
                      value={areaM2}
                      onChange={(e) => setAreaM2(Number(e.target.value))}
                      className="w-full px-4 py-2.5 rounded-xl border border-slate-300 text-sm font-bold text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>
                </div>

                {/* Mô tả chi tiết */}
                <div className="mb-6">
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                    Mô tả chi tiết tài sản *
                  </label>
                  <textarea
                    rows={5}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Mô tả các ưu điểm về thiết kế, nội thất bàn giao, tầng cao, view, tiện ích..."
                    className="w-full px-4 py-3 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 leading-relaxed font-sans"
                  />
                  <span className="text-[11px] text-slate-400">Đã nhập {description.length} ký tự (Khuyến nghị $\ge 80$ ký tự để đạt điểm chất lượng tối đa)</span>
                </div>

                <div className="mt-6 flex justify-between">
                  <Button onClick={() => setStep(1)} variant="outline">
                    <ArrowLeft className="w-4 h-4 mr-1" /> Quay lại Bước 1
                  </Button>
                  <Button onClick={() => setStep(3)} variant="primary" className="bg-emerald-600 hover:bg-emerald-700">
                    Tiếp tục: Media & Pháp lý <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </div>
              </Card>
            )}

            {/* BƯỚC 3: HÌNH ẢNH */}
            {step === 3 && (
              <Card className="p-6">
                <h3 className="text-base font-bold text-slate-900 mb-4 pb-2 border-b border-slate-100 flex items-center gap-2">
                  <ImageIcon className="w-5 h-5 text-emerald-600" />
                  Bước 3: Quản lý hình ảnh & an toàn dữ liệu
                </h3>

                {/* Quản lý ảnh (3-20 ảnh) */}
                <div className="mb-6">
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                      Hình ảnh BĐS (Đã tải: {imageUrls.length} ảnh, tối thiểu 3 ảnh) *
                    </label>
                    <span className="text-xs text-emerald-600 font-bold">
                      {imageUrls.length >= 5 ? '✓ Đạt 25/25 điểm ảnh phong phú' : 'Thêm >= 5 ảnh để đạt điểm tối đa'}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 mb-4">
                    {imageUrls.map((url, idx) => (
                      <div key={idx} className="relative group rounded-xl overflow-hidden border border-slate-200 aspect-video bg-slate-100">
                        <img src={url} alt={`Ảnh bất động sản ${idx + 1}`} className="w-full h-full object-cover" loading="lazy" />
                        {idx === 0 && (
                          <span className="absolute top-1 left-1 bg-emerald-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow">
                            Ảnh bìa
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={() => handleRemoveImage(idx)}
                          disabled={deletingImage === url}
                          aria-label={`Xóa ảnh ${idx + 1}`}
                          className="absolute top-1 right-1 bg-black/70 text-white w-11 h-11 rounded-full flex items-center justify-center opacity-90 sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100 transition-opacity focus:outline-none focus:ring-2 focus:ring-white disabled:opacity-50"
                        >
                          {deletingImage === url ? <span className="loading loading-spinner loading-xs" /> : <X className="w-4 h-4" />}
                        </button>
                      </div>
                    ))}
                  </div>

                  <label className="flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-emerald-500 bg-emerald-50/50 px-5 py-6 text-center transition-colors hover:bg-emerald-50 focus-within:ring-2 focus-within:ring-emerald-500 focus-within:ring-offset-2">
                    <input
                      type="file"
                      multiple
                      accept="image/jpeg,image/png,image/webp,image/avif"
                      disabled={isUploadingImages || imageUrls.length >= 20}
                      className="sr-only"
                      onChange={(event) => {
                        void handleImageUpload(event.target.files);
                        event.target.value = '';
                      }}
                    />
                    {isUploadingImages ? <span className="loading loading-spinner loading-md text-emerald-700" /> : <Upload className="h-7 w-7 text-emerald-700" />}
                    <span className="text-sm font-bold text-emerald-950">
                      {isUploadingImages ? 'Đang lưu ảnh vào MinIO…' : imageUrls.length >= 20 ? 'Đã đạt giới hạn 20 ảnh' : 'Chọn ảnh từ thiết bị'}
                    </span>
                    <span className="text-xs leading-relaxed text-emerald-800">
                      JPEG, PNG, WebP hoặc AVIF · tối đa 10 MB/ảnh · còn {20 - imageUrls.length} vị trí
                    </span>
                  </label>
                  <p className="mt-2 text-xs text-slate-500" aria-live="polite">
                    Ảnh được lưu trong kho MinIO riêng của hệ thống. Không cần dán liên kết từ website khác.
                  </p>
                </div>

                <div className="mt-6 flex justify-between">
                  <Button onClick={() => setStep(2)} variant="outline">
                    <ArrowLeft className="w-4 h-4 mr-1" /> Quay lại Bước 2
                  </Button>
                  <Button onClick={() => setStep(4)} variant="primary" className="bg-emerald-600 hover:bg-emerald-700">
                    Tiếp tục: Xem trước & Gửi duyệt <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </div>
              </Card>
            )}

            {/* BƯỚC 4: XEM TRƯỚC LIVE PREVIEW & GỬI DUYỆT */}
            {step === 4 && (
              <Card className="p-6">
                <h3 className="text-base font-bold text-slate-900 mb-4 pb-2 border-b border-slate-100 flex items-center gap-2">
                  <Eye className="w-5 h-5 text-emerald-600" />
                  Bước 4: Xem trước Live Preview & Nộp Kiểm duyệt
                </h3>

                {/* Thẻ mô phỏng giao diện tìm kiếm thực tế */}
                <div className="p-4 rounded-2xl bg-slate-100 border border-slate-200 mb-6">
                  <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-3">
                    Hiển thị trên Trang Kết quả Tìm kiếm & Bản đồ:
                  </span>

                  <div className="bg-white rounded-xl overflow-hidden border border-slate-200 shadow-sm max-w-md mx-auto">
                    <div className="relative aspect-video">
                      {imageUrls[0] ? (
                        <img src={imageUrls[0]} alt="Ảnh bìa xem trước" className="w-full h-full object-cover" />
                      ) : (
                        <div className="flex h-full flex-col items-center justify-center gap-2 bg-slate-200 text-slate-600">
                          <ImageIcon className="h-8 w-8" />
                          <span className="text-sm font-medium">Chưa có ảnh để xem trước</span>
                        </div>
                      )}
                      <div className="absolute top-2 left-2 flex flex-col gap-1">
                        <span className="bg-slate-900/80 text-white text-[11px] font-bold px-2 py-0.5 rounded backdrop-blur-sm">
                          {purpose === 'SALE' ? 'Bán' : 'Cho thuê'}
                        </span>
                      </div>
                      {imageUrls.length > 0 && (
                        <span className="absolute bottom-2 right-2 bg-black/70 text-white text-[10px] font-mono px-1.5 py-0.5 rounded">
                          1/{imageUrls.length} ảnh
                        </span>
                      )}
                    </div>

                    <div className="p-4">
                      <div className="flex items-baseline justify-between mb-1">
                        <span className="text-lg font-black text-emerald-700">{formatPriceVnd(priceVnd)}</span>
                        <span className="text-xs text-slate-500 font-medium">{areaM2} m² • {calculateUnitPrice(priceVnd, areaM2)}</span>
                      </div>
                      <h4 className="font-bold text-sm text-slate-900 line-clamp-1 mb-2">
                        {title || 'Tiêu đề tin đăng BĐS'}
                      </h4>
                      <p className="text-xs text-slate-500 flex items-center gap-1 line-clamp-1">
                        <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                        {addressSummary}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Bản cam kết kiểm duyệt */}
                <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 leading-relaxed mb-6">
                  <p className="font-bold mb-1">Cam kết của người đăng tin:</p>
                  Tôi cam đoan thông tin mô tả, mức giá và hồ sơ pháp lý cung cấp là hoàn toàn chính xác. Tôi đồng ý để ban quản trị đối soát, áp dụng bộ lọc trùng lặp và tạm gỡ tin nếu phát hiện hành vi gian lận hoặc đăng khống.
                </div>

                <div className="mt-6 flex justify-between items-center">
                  <Button onClick={() => setStep(3)} variant="outline">
                    <ArrowLeft className="w-4 h-4 mr-1" /> Quay lại Bước 3
                  </Button>
                  <Button
                    onClick={handleSubmitForReview}
                    disabled={isSubmitting}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold px-6 py-2.5 shadow-md flex items-center gap-2"
                  >
                    <Send className="w-4 h-4" />
                    {isSubmitting ? 'Đang gửi duyệt...' : 'Gửi duyệt tin đăng'}
                  </Button>
                </div>
              </Card>
            )}
          </div>

          {/* Right Sidebar Column (4 cols): Bộ tiêu chuẩn chất lượng & Checklist */}
          <div className="lg:col-span-4 flex flex-col gap-6">
            {/* Checklist Tiêu chuẩn chất lượng */}
            <Card className="p-5">
              <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">
                Kiểm tra trước khi gửi duyệt
              </h4>
              <div className="space-y-2.5 text-xs">
                <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200">
                  <span className="text-slate-700">Tiêu đề chuẩn SEO ($\ge 15$ ký tự)</span>
                  {title.length >= 15 ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <span className="text-[10px] text-slate-400 font-mono">{title.length}/15</span>
                  )}
                </div>

                <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200">
                  <span className="text-slate-700">Mô tả đầy đủ ($\ge 80$ ký tự)</span>
                  {description.length >= 80 ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <span className="text-[10px] text-slate-400 font-mono">{description.length}/80</span>
                  )}
                </div>

                <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200">
                  <span className="text-slate-700">Hình ảnh thực tế ($\ge 5$ ảnh)</span>
                  {imageUrls.length >= 5 ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <span className="text-[10px] text-amber-600 font-mono">{imageUrls.length}/5</span>
                  )}
                </div>

                <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200">
                  <span className="text-slate-700">Tọa độ định vị GIS PostGIS</span>
                  {latitude && longitude ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <span className="text-[10px] text-slate-400">Chưa có</span>
                  )}
                </div>

              </div>
            </Card>

            {/* Thẻ hỗ trợ người đăng tin */}
            <div className="p-4 rounded-xl bg-slate-900 text-white shadow-md">
              <div className="flex items-center gap-2 mb-2">
                <HelpCircle className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                  Chính sách duyệt tin an toàn
                </span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Mỗi tin đăng sau khi gửi sẽ vào hàng đợi kiểm duyệt. Nếu cần bổ sung nội dung, trạng thái tin sẽ được cập nhật trong Kho tin của tôi.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CreateListingPage;
