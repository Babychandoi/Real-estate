import React, { useState } from 'react';
import {
  TrendingUp, BarChart3, Users, Eye, PhoneCall,
  ShieldCheck, Clock, Download, CheckCircle2,
  Calendar, MapPin
} from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';

export const ProductAnalyticsPage: React.FC = () => {
  const [period, setPeriod] = useState<'7d' | '30d' | 'q3' | 'custom'>('7d');

  // Dữ liệu phễu chuyển đổi 5 tầng (FR29)
  const funnelSteps = [
    { step: 1, name: 'Lượt tìm kiếm GIS trên Bản đồ', count: 124500, percentOfPrevious: 100, color: 'bg-blue-600' },
    { step: 2, name: 'Xem trang chi tiết tin đăng (Detail Views)', count: 84320, percentOfPrevious: 67.7, color: 'bg-emerald-600' },
    { step: 3, name: 'Bấm xem số điện thoại (Contact Click)', count: 8430, percentOfPrevious: 10.0, color: 'bg-amber-600' },
    { step: 4, name: 'Nộp form tư vấn xác minh OTP (Lead Submit)', count: 1686, percentOfPrevious: 20.0, color: 'bg-purple-600' },
    { step: 5, name: 'Đặt cọc Ký quỹ Escrow bảo đảm (Deposit Locked)', count: 135, percentOfPrevious: 8.0, color: 'bg-rose-600' },
  ];

  // Phân bổ nguồn tin đăng
  const sourceBreakdown = [
    { name: 'Chủ nhà chính chủ (eKYC Verified)', percent: 42, count: 524, color: 'bg-emerald-500' },
    { name: 'Môi giới Pro Agent (Chứng chỉ hành nghề)', percent: 58, count: 724, color: 'bg-blue-500' },
  ];

  // Phân bổ địa bàn trọng điểm
  const districtBreakdown = [
    { district: 'Nam Từ Liêm', listings: 480, leads: 620, slaHours: 3.8, growth: '+18%' },
    { district: 'Cầu Giấy', listings: 350, leads: 480, slaHours: 4.1, growth: '+12%' },
    { district: 'Bắc Từ Liêm', listings: 240, leads: 310, slaHours: 4.5, growth: '+15%' },
    { district: 'Tây Hồ', listings: 178, leads: 276, slaHours: 4.6, growth: '+9%' },
  ];

  const handleExportReport = () => {
    alert('Đang kết xuất tệp Báo cáo Sản phẩm & Phân tích Chuyển đổi FR29 định dạng PDF/CSV...');
  };

  return (
    <div className="min-h-screen bg-slate-50 py-6">
      <div className="container mx-auto px-4 max-w-7xl">
        {/* Top Header & Metadata */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-800 text-xs font-bold uppercase tracking-wider">
                FR29 Analytics
              </span>
              <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 text-xs font-bold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
                Waterfall 0.9.1
              </span>
            </div>
            <h1 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight">
              Báo cáo Sản phẩm & Phân tích Chuyển đổi
            </h1>
            <p className="text-xs md:text-sm text-slate-500 mt-1">
              Đo lường nguồn tin, thời gian duyệt SLA, phễu chuyển đổi và chất lượng Lead an toàn theo chuẩn Waterfall 0.9.1.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              onClick={handleExportReport}
              variant="outline"
              size="sm"
              className="bg-white text-xs font-semibold shadow-xs"
            >
              <Download className="w-4 h-4 mr-1.5" />
              Xuất Báo cáo (PDF / CSV)
            </Button>
          </div>
        </div>

        {/* Period Filter Pills */}
        <div className="flex items-center gap-2 mb-6 overflow-x-auto pb-1">
          <button
            onClick={() => setPeriod('7d')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              period === '7d'
                ? 'bg-slate-900 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            7 ngày qua
          </button>
          <button
            onClick={() => setPeriod('30d')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              period === '30d'
                ? 'bg-slate-900 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            30 ngày qua
          </button>
          <button
            onClick={() => setPeriod('q3')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              period === 'q3'
                ? 'bg-slate-900 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            Quý 3/2026
          </button>
          <button
            onClick={() => setPeriod('custom')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all flex items-center gap-1 ${
              period === 'custom'
                ? 'bg-slate-900 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <Calendar className="w-3.5 h-3.5" />
            Tùy chọn ngày
          </button>
        </div>

        {/* Compliance Banner (FR29 & NFR06) */}
        <div className="p-4 rounded-xl bg-blue-50/80 border border-blue-200/80 mb-6 flex items-start gap-3">
          <ShieldCheck className="w-5 h-5 text-blue-700 shrink-0 mt-0.5" />
          <div className="text-xs text-blue-950">
            <div className="flex items-center gap-2 mb-1">
              <strong className="font-bold">Quy chuẩn đo lường FR29 & NFR06 Bảo vệ PII</strong>
              <Badge variant="neutral" className="bg-blue-200 text-blue-900 text-[10px] py-0 px-1.5">
                Chuẩn hóa
              </Badge>
            </div>
            <p className="leading-relaxed text-blue-800">
              Hệ thống phân biệt rạch ròi giữa <strong>contact_click</strong> (lượt bấm hiển thị số điện thoại) và <strong>lead_submit</strong> (gửi form xác minh OTP 6 số). Toàn bộ dữ liệu định danh PII của khách hàng và người bán đều được ẩn danh hóa và loại trừ 100% các yêu cầu từ bot/crawler.
            </p>
          </div>
        </div>

        {/* 4 CORE KPI CARDS GRID */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <Card className="p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between text-slate-500 mb-2">
              <span className="text-xs font-bold uppercase tracking-wider">Tin còn hiệu lực</span>
              <span className="p-1.5 rounded-lg bg-slate-100 text-slate-700">
                <BarChart3 className="w-4 h-4" />
              </span>
            </div>
            <div className="text-2xl font-black text-slate-900">1.248</div>
            <div className="mt-1 flex items-center justify-between text-xs text-emerald-600 font-bold">
              <span className="flex items-center gap-0.5">
                <TrendingUp className="w-3.5 h-3.5" /> +14% kỳ trước
              </span>
              <span className="text-slate-400 font-normal">100% GIS PostGIS</span>
            </div>
          </Card>

          <Card className="p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between text-slate-500 mb-2">
              <span className="text-xs font-bold uppercase tracking-wider">Lượt xem tin chi tiết</span>
              <span className="p-1.5 rounded-lg bg-slate-100 text-slate-700">
                <Eye className="w-4 h-4" />
              </span>
            </div>
            <div className="text-2xl font-black text-slate-900">84.320</div>
            <div className="mt-1 flex items-center justify-between text-xs text-emerald-600 font-bold">
              <span>98.4% người dùng thật</span>
              <span className="text-slate-400 font-normal">Lọc bot NFR06</span>
            </div>
          </Card>

          <Card className="p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between text-slate-500 mb-2">
              <span className="text-xs font-bold uppercase tracking-wider">Lượt bấm gọi (Click)</span>
              <span className="p-1.5 rounded-lg bg-slate-100 text-slate-700">
                <PhoneCall className="w-4 h-4" />
              </span>
            </div>
            <div className="text-2xl font-black text-amber-700">8.430</div>
            <div className="mt-1 flex items-center justify-between text-xs text-slate-500">
              <span className="font-bold text-amber-800">Tỷ lệ: 10.0% views</span>
              <span>contact_click</span>
            </div>
          </Card>

          <Card className="p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between text-slate-500 mb-2">
              <span className="text-xs font-bold uppercase tracking-wider">Lead OTP Xác thực</span>
              <span className="p-1.5 rounded-lg bg-emerald-100 text-emerald-800">
                <Users className="w-4 h-4" />
              </span>
            </div>
            <div className="text-2xl font-black text-emerald-800">1.686</div>
            <div className="mt-1 flex items-center justify-between text-xs text-emerald-600 font-bold">
              <span>20.0% click chuyển Lead</span>
              <span className="text-slate-400 font-normal">lead_submit</span>
            </div>
          </Card>
        </div>

        {/* 2 CỘT CHÍNH: PHỄU CHUYỂN ĐỔI (FUNNEL) & HIỆU SUẤT DUYỆT SLA */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start mb-6">
          {/* CỘT TRÁI (7 cols): Phễu Chuyển Đổi 5 Tầng (Conversion Funnel) */}
          <div className="lg:col-span-7 flex flex-col gap-6">
            <Card className="p-6">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
                <div className="flex items-center gap-2">
                  <BarChart3 className="w-5 h-5 text-emerald-600" />
                  <h3 className="font-bold text-slate-900 text-sm md:text-base">
                    Phễu Chuyển Đổi 5 Tầng Hành Trình Khách Hàng (FR29)
                  </h3>
                </div>
                <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-200">
                  Tỷ lệ cọc: 8.0%
                </span>
              </div>

              <div className="space-y-4">
                {funnelSteps.map((step) => {
                  const widthPercent = Math.max((step.count / funnelSteps[0].count) * 100, 12);
                  return (
                    <div key={step.step} className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs">
                      <div className="flex items-center justify-between font-bold mb-1.5">
                        <span className="text-slate-800 flex items-center gap-2">
                          <span className="w-5 h-5 rounded-full bg-slate-200 text-slate-700 flex items-center justify-center text-[10px]">
                            {step.step}
                          </span>
                          {step.name}
                        </span>
                        <span className="text-slate-900 font-mono text-sm">
                          {step.count.toLocaleString('vi-VN')}
                        </span>
                      </div>

                      <div className="w-full bg-slate-200 h-2.5 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${step.color}`}
                          style={{ width: `${widthPercent}%` }}
                        />
                      </div>

                      <div className="flex items-center justify-between text-[11px] text-slate-500 mt-1.5">
                        <span>Chuyển đổi từ bước trước: <strong>{step.percentOfPrevious}%</strong></span>
                        <span>Đạt chuẩn Waterfall</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </Card>

            {/* Bảng phân tích theo Quận / Địa bàn trọng điểm */}
            <Card className="p-6">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
                <div className="flex items-center gap-2">
                  <MapPin className="w-5 h-5 text-blue-600" />
                  <h3 className="font-bold text-slate-900 text-sm">
                    Hiệu Suất Theo Địa Bàn Khu Vực Trọng Điểm
                  </h3>
                </div>
                <span className="text-xs text-slate-400">Hà Nội Pilot</span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-200 text-slate-400 uppercase text-[10px] tracking-wider">
                      <th className="pb-2 font-bold">Quận / Huyện</th>
                      <th className="pb-2 font-bold">Tin đăng</th>
                      <th className="pb-2 font-bold">Leads tiếp nhận</th>
                      <th className="pb-2 font-bold">SLA duyệt TB</th>
                      <th className="pb-2 font-bold text-right">Tăng trưởng</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {districtBreakdown.map((d, i) => (
                      <tr key={i} className="hover:bg-slate-50">
                        <td className="py-2.5 font-bold text-slate-900">{d.district}</td>
                        <td className="py-2.5 font-mono">{d.listings}</td>
                        <td className="py-2.5 font-mono font-bold text-emerald-700">{d.leads}</td>
                        <td className="py-2.5 font-mono text-slate-600">{d.slaHours} giờ</td>
                        <td className="py-2.5 text-right font-bold text-emerald-600">{d.growth}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>

          {/* CỘT PHẢI (5 cols): SLA KIỂM DUYỆT & CƠ CẤU NGUỒN TIN */}
          <div className="lg:col-span-5 flex flex-col gap-6">
            {/* Thẻ SLA Kiểm Duyệt (FR08 / BR03) */}
            <Card className="p-6 border-2 border-emerald-500/20">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
                <div className="flex items-center gap-2">
                  <Clock className="w-5 h-5 text-emerald-600" />
                  <h3 className="font-bold text-slate-900 text-sm">
                    Cam Kết Tốc Độ Kiểm Duyệt (SLA)
                  </h3>
                </div>
                <span className="text-[10px] font-bold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded">
                  SLA ≤ 8h
                </span>
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-center mb-4">
                <span className="text-xs text-slate-500 block">Thời gian xử lý trung bình</span>
                <div className="text-3xl font-black text-emerald-700 my-1">4.2 giờ</div>
                <span className="text-xs font-bold text-emerald-600 flex items-center justify-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5" /> 98.2% tin đăng đạt chuẩn cam kết SLA
                </span>
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex justify-between p-2 rounded-lg bg-slate-50">
                  <span className="text-slate-600">Tổng tin đã xử lý:</span>
                  <span className="font-bold text-slate-800">1.420 tin</span>
                </div>
                <div className="flex justify-between p-2 rounded-lg bg-slate-50">
                  <span className="text-slate-600">Tỷ lệ duyệt thành công:</span>
                  <span className="font-bold text-emerald-700">82.4%</span>
                </div>
                <div className="flex justify-between p-2 rounded-lg bg-slate-50">
                  <span className="text-slate-600">Yêu cầu bổ sung ảnh/sổ:</span>
                  <span className="font-bold text-amber-700">14.1%</span>
                </div>
                <div className="flex justify-between p-2 rounded-lg bg-slate-50">
                  <span className="text-slate-600">Từ chối do sai lệch giá:</span>
                  <span className="font-bold text-rose-700">3.5%</span>
                </div>
              </div>
            </Card>

            {/* Cơ Cấu Nguồn Tin Đăng */}
            <Card className="p-6">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
                <div className="flex items-center gap-2">
                  <Users className="w-5 h-5 text-blue-600" />
                  <h3 className="font-bold text-slate-900 text-sm">
                    Cơ Cấu Nguồn Cung Tin Đăng
                  </h3>
                </div>
                <span className="text-xs text-slate-400">100% Xác thực</span>
              </div>

              <div className="space-y-4">
                {sourceBreakdown.map((s, i) => (
                  <div key={i} className="text-xs">
                    <div className="flex items-center justify-between font-bold mb-1">
                      <span className="text-slate-800">{s.name}</span>
                      <span className="text-slate-900">{s.percent}% ({s.count} tin)</span>
                    </div>
                    <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${s.color}`} style={{ width: `${s.percent}%` }} />
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-500 leading-relaxed">
                *Hệ thống tự động gắn nhãn phân loại rõ ràng giữa Chủ nhà eKYC và Môi giới chuyên nghiệp để người mua chủ động lựa chọn.
              </div>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductAnalyticsPage;
