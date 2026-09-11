import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  CheckCircle2, XCircle, ShieldCheck, Lock,
  Building2, MapPin, Sliders, ArrowLeft,
  ChevronDown, ChevronUp, RefreshCw, Plus, Sparkles
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Badge } from '@/shared/ui/Badge';
import { formatPriceVnd, calculateUnitPrice } from '@/entities/listing/model/types';

interface CompareProperty {
  id: string;
  title: string;
  propertyType: string;
  purpose: 'SALE' | 'RENT';
  priceVnd: number;
  areaM2: number;
  usableAreaM2: number;
  district: string;
  addressSummary: string;
  bedrooms: number;
  bathrooms: number;
  floor: string;
  balconyDirection: string;
  mainDirection: string;
  legalStatus: string;
  verifiedOwner: boolean;
  ekycDate: string;
  plotNumber: string;
  monthlyServiceFee: string;
  escrowDeposit: number;
  bankLoanSupport: string;
  metroDistance: string;
  hospitalDistance: string;
  schoolDistance: string;
  parkDistance: string;
  imageUrl: string;
  highlights: string[];
}

export const PropertyComparePage: React.FC = () => {
  const navigate = useNavigate();

  // Diff Mode: Chỉ hiển thị các dòng có thông số khác nhau
  const [diffOnly, setDiffOnly] = useState(false);

  // Trạng thái thu gọn các nhóm thông số (Accordion)
  const [expandedSections, setExpandedSections] = useState({
    core: true,
    legal: true,
    financial: true,
    gis: true,
  });

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections(prev => ({ ...prev, [section]: !prev[section] }));
  };

  // 3 Bất động sản cùng mục đích Mua Bán (FR17: Khóa không so lẫn Mua với Thuê)
  const [properties, setProperties] = useState<CompareProperty[]>([
    {
      id: 'lst-101',
      title: 'Căn hộ The Matrix One 2PN view hồ điều hòa',
      propertyType: 'Căn hộ chung cư',
      purpose: 'SALE',
      priceVnd: 4200000000,
      areaM2: 73.0,
      usableAreaM2: 68.5,
      district: 'Nam Từ Liêm',
      addressSummary: 'Lê Quang Đạo, Phường Mễ Trì, Nam Từ Liêm, Hà Nội',
      bedrooms: 2,
      bathrooms: 2,
      floor: 'Tầng 18 (Tầng trung cao)',
      balconyDirection: 'Đông Nam',
      mainDirection: 'Tây Bắc',
      legalStatus: 'Sổ hồng chính chủ lâu dài',
      verifiedOwner: true,
      ekycDate: '10/09/2026',
      plotNumber: 'Thửa 18, Tờ bản đồ số 42',
      monthlyServiceFee: '16.500 đ/m²/tháng',
      escrowDeposit: 100000000,
      bankLoanSupport: '70% giá trị - Vietcombank bảo lãnh',
      metroDistance: '450m (Tuyến Metro số 5)',
      hospitalDistance: '1.2 km (BV Hồng Ngọc Phúc Trường Minh)',
      schoolDistance: '300m (Trường Quốc tế Marie Curie)',
      parkDistance: '150m (Công viên hồ điều hòa 14ha)',
      imageUrl: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
      highlights: ['Sổ hồng chính chủ eKYC', 'View hồ 14ha vĩnh viễn', 'Gần tuyến Metro'],
    },
    {
      id: 'lst-102',
      title: 'Căn hộ Vinhomes Smart City Tonkin 2PN cao cấp',
      propertyType: 'Căn hộ chung cư',
      purpose: 'SALE',
      priceVnd: 3850000000,
      areaM2: 65.5,
      usableAreaM2: 61.0,
      district: 'Nam Từ Liêm',
      addressSummary: 'Phân khu The Tonkin, Tây Mỗ, Nam Từ Liêm, Hà Nội',
      bedrooms: 2,
      bathrooms: 2,
      floor: 'Tầng 12 (Tầng trung)',
      balconyDirection: 'Đông Bắc',
      mainDirection: 'Tây Nam',
      legalStatus: 'Sổ hồng chính chủ lâu dài',
      verifiedOwner: true,
      ekycDate: '11/09/2026',
      plotNumber: 'Thửa 102, Tờ bản đồ số 15',
      monthlyServiceFee: '18.000 đ/m²/tháng',
      escrowDeposit: 100000000,
      bankLoanSupport: '80% giá trị - Techcombank bảo lãnh',
      metroDistance: '800m (Tuyến Metro số 6 & 7)',
      hospitalDistance: '1.8 km (Bệnh viện Vinmec)',
      schoolDistance: '500m (Trường Vinschool)',
      parkDistance: '300m (Công viên trung tâm Central Park)',
      imageUrl: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80',
      highlights: ['Tiện ích Vinhomes trọn gói', 'Bàn giao full nội thất cao cấp', 'Đơn giá/m² cạnh tranh'],
    },
    {
      id: 'lst-103',
      title: 'Căn góc 3PN Golden Palace Mễ Trì diện tích rộng',
      propertyType: 'Căn hộ chung cư',
      purpose: 'SALE',
      priceVnd: 5100000000,
      areaM2: 115.0,
      usableAreaM2: 108.0,
      district: 'Nam Từ Liêm',
      addressSummary: 'Đường Mễ Trì, Phường Mễ Trì, Nam Từ Liêm, Hà Nội',
      bedrooms: 3,
      bathrooms: 2,
      floor: 'Tầng 25 (Tầng cao thoáng)',
      balconyDirection: 'Nam',
      mainDirection: 'Bắc',
      legalStatus: 'Sổ đỏ sẵn sàng giao dịch',
      verifiedOwner: false,
      ekycDate: 'Đang thẩm định',
      plotNumber: 'Thửa 55, Tờ bản đồ số 28',
      monthlyServiceFee: '11.000 đ/m²/tháng',
      escrowDeposit: 100000000,
      bankLoanSupport: '65% giá trị - BIDV bảo lãnh',
      metroDistance: '1.2 km (Tuyến Metro Nhổn - Ga Hà Nội)',
      hospitalDistance: '2.5 km (Bệnh viện 19-8)',
      schoolDistance: '600m (Tiểu học Lomonoxop)',
      parkDistance: '800m (Công viên Cầu Giấy)',
      imageUrl: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80',
      highlights: ['Diện tích lớn 115m²', 'Đơn giá rẻ nhất khu vực', 'Căn góc 3 mặt thoáng'],
    },
  ]);

  const handleRemove = (id: string) => {
    if (properties.length <= 2) {
      alert('Tối thiểu cần giữ lại 2 bất động sản để đối chiếu so sánh!');
      return;
    }
    setProperties(properties.filter(p => p.id !== id));
  };

  const handleReset = () => {
    navigate('/search');
  };

  // Kiểm tra xem dòng có giá trị khác nhau giữa các BĐS không
  const isDifferent = (getter: (p: CompareProperty) => any) => {
    const first = getter(properties[0]);
    return properties.some(p => getter(p) !== first);
  };

  return (
    <div className="min-h-screen bg-slate-50 py-8">
      <div className="container mx-auto px-4 max-w-[1360px]">
        {/* Navigation & Header Breadcrumb */}
        <div className="flex items-center justify-between mb-4">
          <Link to="/search" className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900 font-medium transition-colors">
            <ArrowLeft className="w-4 h-4" />
            <span>Quay lại Bản đồ Tìm kiếm</span>
          </Link>

          <div className="flex items-center gap-2">
            <Badge variant="neutral" className="bg-emerald-50 text-emerald-800 border-emerald-200">
              Chuẩn mục đích: Mua bán • Khóa không so lẫn Mua với Thuê (FR17)
            </Badge>
          </div>
        </div>

        {/* TOP CONTROL BAR (Theo nguyên mẫu) */}
        <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200 mb-6 flex flex-col xl:flex-row xl:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight">
                Đối chiếu thông số bất động sản (FR17)
              </h1>
              <span className="px-3 py-1 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">
                {properties.length}/3 Bất động sản
              </span>
            </div>
            <p className="text-xs md:text-sm text-slate-500 mt-1">
              So sánh đa chiều về giá, đơn giá m², tình trạng pháp lý eKYC, tiện ích GIS và điều kiện đặt cọc Escrow bảo đảm.
            </p>
          </div>

          {/* Quick Actions & Diff Mode Toggle */}
          <div className="flex items-center gap-3 flex-wrap">
            {/* Diff Mode Toggle Switch */}
            <label className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-100 border border-slate-200 cursor-pointer select-none text-xs font-bold text-slate-700 hover:bg-slate-200/70 transition-colors">
              <input
                type="checkbox"
                checked={diffOnly}
                onChange={(e) => setDiffOnly(e.target.checked)}
                className="rounded text-emerald-600 focus:ring-emerald-500"
              />
              <Sliders className="w-3.5 h-3.5 text-slate-600" />
              <span>Chỉ xem điểm khác biệt (Diff Mode)</span>
            </label>

            <Button
              variant="outline"
              size="sm"
              onClick={handleReset}
              className="text-xs text-slate-600 bg-white"
            >
              <RefreshCw className="w-3.5 h-3.5 mr-1" />
              Xóa & Chọn lại
            </Button>

            {properties.length < 3 && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => navigate('/search')}
                className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs"
              >
                <Plus className="w-3.5 h-3.5 mr-1" />
                Thêm BĐS thứ 3
              </Button>
            )}
          </div>
        </div>

        {/* BẢNG SO SÁNH MA TRẬN 4 CỘT (1 CỘT TIÊU CHÍ + 3 CỘT BĐS) */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
          {/* Header BĐS: Sticky Top */}
          <div className="grid grid-cols-1 md:grid-cols-4 border-b border-slate-200 divide-x divide-slate-100 bg-slate-50/50">
            {/* Cột 1: Nhãn tiêu chí */}
            <div className="p-5 flex flex-col justify-end">
              <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block">
                Tiêu chuẩn đối soát
              </span>
              <h3 className="text-lg font-bold text-slate-900 mt-1">
                Danh sách BĐS Đối chiếu
              </h3>
              <p className="text-xs text-slate-500 mt-1">
                Dữ liệu được cập nhật từ hồ sơ kiểm duyệt chính chủ.
              </p>
            </div>

            {/* Các cột BĐS */}
            {properties.map((item) => (
              <div key={item.id} className="p-5 flex flex-col justify-between relative group bg-white">
                {/* Nút xóa khỏi so sánh */}
                <button
                  onClick={() => handleRemove(item.id)}
                  title="Xóa khỏi so sánh"
                  className="absolute top-3 right-3 w-6 h-6 rounded-full bg-slate-100 text-slate-400 hover:bg-rose-100 hover:text-rose-600 flex items-center justify-center text-xs transition-colors"
                >
                  ✕
                </button>

                <div>
                  <div className="relative aspect-video rounded-xl overflow-hidden border border-slate-200 mb-3 shadow-xs">
                    <img src={item.imageUrl} alt={item.title} className="w-full h-full object-cover" />
                    {item.verifiedOwner ? (
                      <span className="absolute top-2 left-2 bg-emerald-600 text-white text-[10px] font-bold px-2 py-0.5 rounded shadow flex items-center gap-1">
                        <ShieldCheck className="w-3 h-3" /> Sổ hồng chính chủ
                      </span>
                    ) : (
                      <span className="absolute top-2 left-2 bg-slate-900/80 text-white text-[10px] font-bold px-2 py-0.5 rounded backdrop-blur-sm">
                        Đang đối soát
                      </span>
                    )}
                  </div>

                  <div className="mb-2">
                    <span className="text-2xl font-black text-emerald-800 block leading-tight">
                      {formatPriceVnd(item.priceVnd)}
                    </span>
                    <span className="text-xs text-slate-500 font-semibold">
                      {calculateUnitPrice(item.priceVnd, item.areaM2)} • {item.areaM2} m²
                    </span>
                  </div>

                  <Link
                    to={`/listings/${item.id}`}
                    className="font-bold text-sm text-slate-900 hover:text-emerald-700 line-clamp-2 mb-2 block leading-snug"
                  >
                    {item.title}
                  </Link>

                  <p className="text-xs text-slate-500 flex items-center gap-1 line-clamp-1 mb-3">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    {item.addressSummary}
                  </p>
                </div>

                {/* Các nút hành động nhanh */}
                <div className="space-y-2 pt-3 border-t border-slate-100">
                  <Link to={`/contracts/dc-${item.id}`} className="block">
                    <Button
                      size="sm"
                      className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs py-2 shadow-xs flex items-center justify-center gap-1.5"
                    >
                      <Lock className="w-3.5 h-3.5" />
                      Đặt cọc Escrow
                    </Button>
                  </Link>
                  <Link to={`/listings/${item.id}`} className="block">
                    <Button
                      size="sm"
                      variant="outline"
                      className="w-full text-xs font-semibold py-1.5 text-slate-700"
                    >
                      Xem chi tiết tin
                    </Button>
                  </Link>
                </div>
              </div>
            ))}
          </div>

          {/* NHÓM 1: THÔNG SỐ CỐT LÕI */}
          <div className="border-b border-slate-200">
            <button
              type="button"
              onClick={() => toggleSection('core')}
              className="w-full px-5 py-3 bg-slate-100 hover:bg-slate-200/60 flex items-center justify-between text-left transition-colors"
            >
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                <Building2 className="w-4 h-4 text-emerald-600" />
                1. Thông số Kỹ thuật Cốt lõi
              </span>
              {expandedSections.core ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
            </button>

            {expandedSections.core && (
              <div className="divide-y divide-slate-100 text-xs">
                {(!diffOnly || isDifferent(p => p.propertyType)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Loại hình BĐS</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-medium">{p.propertyType}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.areaM2)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Diện tích tim tường</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-bold">{p.areaM2} m²</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.usableAreaM2)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Diện tích thông thủy</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.usableAreaM2} m²</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.bedrooms)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Số phòng ngủ</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-bold">{p.bedrooms} PN</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.bathrooms)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Số phòng tắm/WC</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.bathrooms} WC</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.floor)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Vị trí tầng cao</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.floor}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.balconyDirection)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Hướng ban công</div>
                    {properties.map(p => (
                      <div key={p.id} className="p-3.5 text-slate-800 font-semibold">
                        {p.balconyDirection} {p.balconyDirection.includes('Đông Nam') && '★ (Rất mát)'}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* NHÓM 2: PHÁP LÝ & KIỂM ĐỊNH eKYC */}
          <div className="border-b border-slate-200">
            <button
              type="button"
              onClick={() => toggleSection('legal')}
              className="w-full px-5 py-3 bg-slate-100 hover:bg-slate-200/60 flex items-center justify-between text-left transition-colors"
            >
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
                2. Pháp lý & Kiểm định Sổ Hồng eKYC (FR22, NFR12)
              </span>
              {expandedSections.legal ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
            </button>

            {expandedSections.legal && (
              <div className="divide-y divide-slate-100 text-xs">
                {(!diffOnly || isDifferent(p => p.legalStatus)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Tình trạng giấy tờ</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-bold">{p.legalStatus}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.verifiedOwner)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Xác thực danh tính eKYC</div>
                    {properties.map(p => (
                      <div key={p.id} className="p-3.5">
                        {p.verifiedOwner ? (
                          <span className="inline-flex items-center gap-1 text-emerald-700 font-bold bg-emerald-100 px-2 py-0.5 rounded">
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Đã kiểm duyệt CCCD & Sổ
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-slate-500 bg-slate-100 px-2 py-0.5 rounded">
                            <XCircle className="w-3.5 h-3.5 text-slate-400" /> Chưa có nhãn chính chủ
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.plotNumber)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Thửa đất / Tờ bản đồ</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-mono">{p.plotNumber}</div>)}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* NHÓM 3: TÀI CHÍNH & KÝ QUỸ ESCROW */}
          <div className="border-b border-slate-200">
            <button
              type="button"
              onClick={() => toggleSection('financial')}
              className="w-full px-5 py-3 bg-slate-100 hover:bg-slate-200/60 flex items-center justify-between text-left transition-colors"
            >
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                <Lock className="w-4 h-4 text-emerald-600" />
                3. Tài chính, Ký quỹ Escrow & Ngân hàng
              </span>
              {expandedSections.financial ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
            </button>

            {expandedSections.financial && (
              <div className="divide-y divide-slate-100 text-xs">
                {(!diffOnly || isDifferent(p => p.monthlyServiceFee)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Phí dịch vụ quản lý</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.monthlyServiceFee}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.escrowDeposit)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Mức cọc Escrow Vault</div>
                    {properties.map(p => (
                      <div key={p.id} className="p-3.5 text-emerald-800 font-bold">
                        {formatPriceVnd(p.escrowDeposit)} (Bảo đảm 100%)
                      </div>
                    ))}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.bankLoanSupport)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Hỗ trợ vay & Bảo lãnh</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-medium">{p.bankLoanSupport}</div>)}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* NHÓM 4: VỊ TRÍ GIS & TIỆN ÍCH POSTGIS */}
          <div>
            <button
              type="button"
              onClick={() => toggleSection('gis')}
              className="w-full px-5 py-3 bg-slate-100 hover:bg-slate-200/60 flex items-center justify-between text-left transition-colors"
            >
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                <MapPin className="w-4 h-4 text-emerald-600" />
                4. Vị trí GIS & Tiện ích Vùng PostGIS (FR13)
              </span>
              {expandedSections.gis ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
            </button>

            {expandedSections.gis && (
              <div className="divide-y divide-slate-100 text-xs">
                {(!diffOnly || isDifferent(p => p.metroDistance)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Khoảng cách ga Metro</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-medium">{p.metroDistance}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.hospitalDistance)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Bệnh viện gần nhất</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.hospitalDistance}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.schoolDistance)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Trường học liên cấp</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800">{p.schoolDistance}</div>)}
                  </div>
                )}

                {(!diffOnly || isDifferent(p => p.parkDistance)) && (
                  <div className="grid grid-cols-1 md:grid-cols-4 divide-x divide-slate-100 hover:bg-slate-50/50">
                    <div className="p-3.5 font-bold text-slate-600 bg-slate-50/30">Công viên / Hồ điều hòa</div>
                    {properties.map(p => <div key={p.id} className="p-3.5 text-slate-800 font-medium">{p.parkDistance}</div>)}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Tổng kết Khuyến nghị AI (Best Choice Summary) */}
        <div className="mt-8 p-6 rounded-2xl bg-gradient-to-r from-emerald-900 to-slate-900 text-white shadow-md flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-500/20 border border-emerald-400/30 flex items-center justify-center shrink-0">
              <Sparkles className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h4 className="text-base font-bold text-white">Khuyến nghị Đánh giá Tổng thể từ BDS WF AI</h4>
              <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
                Nếu bạn ưu tiên môi trường sống cao cấp, hướng mát và pháp lý sổ hồng chính chủ thì <strong>Căn hộ The Matrix One</strong> là lựa chọn tối ưu. Nếu bạn tìm kiếm căn hộ gia đình diện tích lớn thì <strong>Golden Palace Mễ Trì</strong> có đơn giá m² tốt nhất khu vực.
              </p>
            </div>
          </div>

          <Link to="/contracts/dc-lst-101" className="shrink-0">
            <Button className="bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold px-5 py-2.5 shadow-md">
              Ký cọc Căn ưu tú nhất
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default PropertyComparePage;
