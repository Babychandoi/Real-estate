import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  FolderKanban, CheckCircle2, Clock, History,
  TrendingUp, Zap, UserCheck, PhoneCall, ShieldCheck,
  PlusCircle, Download, Sliders, ExternalLink, RefreshCw,
  Search, Lock
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { formatPriceVnd } from '@/entities/listing/model/types';

interface BrokerListingItem {
  id: string;
  title: string;
  propertyType: string;
  district: string;
  priceVnd: number;
  areaM2: number;
  status: 'ACTIVE' | 'PENDING_REVIEW' | 'CHANGES_REQUESTED' | 'EXPIRED';
  imageUrl: string;
  verifiedOwner: boolean;
  daysRemaining: number;
  leadsCount: number;
  updatedAt: string;
}

interface MatchedBuyer {
  id: string;
  fullName: string;
  phoneMasked: string;
  targetBudget: string;
  targetArea: string;
  preferredDistrict: string;
  matchScore: number;
  pipelineStatus: 'NEW' | 'CONSULTING' | 'VIEWING_SCHEDULED' | 'DEPOSIT_ESCROW';
}

export const BrokerWorkspacePage: React.FC = () => {
  // Tab lọc kho tin
  const [selectedTab, setSelectedTab] = useState<'ALL' | 'ACTIVE' | 'PENDING' | 'CHANGES' | 'EXPIRED'>('ALL');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [notification, setNotification] = useState<string | null>(null);

  // Danh sách kho tin đăng mẫu của môi giới
  const [listings, setListings] = useState<BrokerListingItem[]>([
    {
      id: 'lst-101',
      title: 'Căn hộ The Matrix One 2PN view hồ Mễ Trì',
      propertyType: 'Căn hộ chung cư',
      district: 'Nam Từ Liêm',
      priceVnd: 4200000000,
      areaM2: 73,
      status: 'ACTIVE',
      imageUrl: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: true,
      daysRemaining: 24,
      leadsCount: 5,
      updatedAt: '2 giờ trước',
    },
    {
      id: 'lst-102',
      title: 'Nhà phố thương mại Shophouse Vinhomes Smart City',
      propertyType: 'Nhà riêng / Phố',
      district: 'Nam Từ Liêm',
      priceVnd: 9800000000,
      areaM2: 120,
      status: 'ACTIVE',
      imageUrl: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: true,
      daysRemaining: 18,
      leadsCount: 8,
      updatedAt: 'Hôm qua',
    },
    {
      id: 'lst-103',
      title: 'Căn góc 3PN Golden Palace Mễ Trì full nội thất',
      propertyType: 'Căn hộ chung cư',
      district: 'Nam Từ Liêm',
      priceVnd: 5100000000,
      areaM2: 115,
      status: 'ACTIVE',
      imageUrl: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: false,
      daysRemaining: 12,
      leadsCount: 3,
      updatedAt: '3 ngày trước',
    },
    {
      id: 'lst-104',
      title: 'Biệt thự Đơn lập KĐT Mỹ Đình 2 diện tích lớn',
      propertyType: 'Biệt thự',
      district: 'Nam Từ Liêm',
      priceVnd: 38000000000,
      areaM2: 240,
      status: 'ACTIVE',
      imageUrl: 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: true,
      daysRemaining: 29,
      leadsCount: 2,
      updatedAt: 'Vừa xong',
    },
    {
      id: 'lst-105',
      title: 'Căn 2PN Moonlight 1 An Lạc Green Symphony',
      propertyType: 'Căn hộ chung cư',
      district: 'Hoài Đức',
      priceVnd: 3350000000,
      areaM2: 68,
      status: 'PENDING_REVIEW',
      imageUrl: 'https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: true,
      daysRemaining: 30,
      leadsCount: 0,
      updatedAt: '4 giờ trước',
    },
    {
      id: 'lst-106',
      title: 'Đất thổ cư ô tô vào nhà Phùng Khoang Trung Văn',
      propertyType: 'Đất thổ cư',
      district: 'Nam Từ Liêm',
      priceVnd: 6200000000,
      areaM2: 55,
      status: 'PENDING_REVIEW',
      imageUrl: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: false,
      daysRemaining: 30,
      leadsCount: 0,
      updatedAt: '1 ngày trước',
    },
    {
      id: 'lst-107',
      title: 'Căn hộ Keangnam Landmark 72 tầng 35 panorama',
      propertyType: 'Căn hộ chung cư',
      district: 'Nam Từ Liêm',
      priceVnd: 8500000000,
      areaM2: 156,
      status: 'CHANGES_REQUESTED',
      imageUrl: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: false,
      daysRemaining: 30,
      leadsCount: 1,
      updatedAt: '5 giờ trước',
    },
    {
      id: 'lst-108',
      title: 'Liền kề KĐT Nam Cường Cổ Nhuế 1',
      propertyType: 'Nhà riêng / Phố',
      district: 'Bắc Từ Liêm',
      priceVnd: 14500000000,
      areaM2: 85,
      status: 'EXPIRED',
      imageUrl: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80',
      verifiedOwner: true,
      daysRemaining: 0,
      leadsCount: 4,
      updatedAt: '31 ngày trước',
    },
  ]);

  // Danh sách khách mua khớp nhu cầu tự động (Auto-matched Buyers)
  const [matchedBuyers] = useState<MatchedBuyer[]>([
    {
      id: 'mb-1',
      fullName: 'Vũ Quốc Hưng',
      phoneMasked: '0912****89',
      targetBudget: '4.0 - 4.5 Tỷ',
      targetArea: '70 - 80 m²',
      preferredDistrict: 'Nam Từ Liêm',
      matchScore: 98,
      pipelineStatus: 'VIEWING_SCHEDULED',
    },
    {
      id: 'mb-2',
      fullName: 'Đoàn Thị Mai Phương',
      phoneMasked: '0988****22',
      targetBudget: '9.0 - 10.5 Tỷ',
      targetArea: '100 - 130 m²',
      preferredDistrict: 'Nam Từ Liêm',
      matchScore: 95,
      pipelineStatus: 'CONSULTING',
    },
    {
      id: 'mb-3',
      fullName: 'Hoàng Anh Tuấn',
      phoneMasked: '0903****66',
      targetBudget: '3.0 - 3.8 Tỷ',
      targetArea: '65 - 75 m²',
      preferredDistrict: 'Hoài Đức / Nam Từ Liêm',
      matchScore: 91,
      pipelineStatus: 'NEW',
    },
    {
      id: 'mb-4',
      fullName: 'Trần Trọng Đạt',
      phoneMasked: '0944****15',
      targetBudget: '35 - 40 Tỷ',
      targetArea: '200 - 300 m²',
      preferredDistrict: 'Mỹ Đình',
      matchScore: 89,
      pipelineStatus: 'DEPOSIT_ESCROW',
    },
  ]);

  // Xác nhận còn hàng để gia hạn 30 ngày (FR10)
  const handleRenewListing = (id: string) => {
    setListings((prev) =>
      prev.map((item) => {
        if (item.id === id) {
          return {
            ...item,
            status: 'ACTIVE',
            daysRemaining: 30,
            updatedAt: 'Vừa xác nhận còn hàng',
          };
        }
        return item;
      })
    );
    setNotification('Đã xác nhận tin BĐS còn hàng! Hệ thống đã gia hạn 30 ngày hiển thị trên bản đồ (FR10).');
  };

  const filteredListings = listings.filter((item) => {
    if (selectedTab === 'ACTIVE' && item.status !== 'ACTIVE') return false;
    if (selectedTab === 'PENDING' && item.status !== 'PENDING_REVIEW') return false;
    if (selectedTab === 'CHANGES' && item.status !== 'CHANGES_REQUESTED') return false;
    if (selectedTab === 'EXPIRED' && item.status !== 'EXPIRED') return false;
    if (searchKeyword && !item.title.toLowerCase().includes(searchKeyword.toLowerCase())) return false;
    return true;
  });

  return (
    <div className="min-h-screen bg-slate-50 py-6">
      <div className="container mx-auto px-4 max-w-7xl">
        {/* TOP COMMAND BAR */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight">
                Không gian Môi giới & Kho tin BĐS
              </h1>
              <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-700" />
                Môi giới Cấp 1 • Pro Agent (Verified eKYC)
              </span>
            </div>
            <p className="text-xs md:text-sm text-slate-500 mt-1">
              Theo dõi vòng đời kiểm định tin đăng (FR05/FR10), phân phối Lead khách nét chống trùng 24h (FR18) và quản lý cọc Escrow bảo đảm.
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <Button
              variant="outline"
              size="sm"
              onClick={() => alert('Đang xuất báo cáo kho hàng & chuyển đổi Lead dạng CSV (BR12)...')}
              className="bg-white text-xs font-semibold"
            >
              <Download className="w-4 h-4 mr-1" />
              Xuất CSV (BR12)
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => alert('Cấu hình cam kết SLA phản hồi cuộc gọi trong 15 phút đang kích hoạt.')}
              className="bg-white text-xs font-semibold"
            >
              <Sliders className="w-4 h-4 mr-1" />
              Cấu hình SLA
            </Button>
            <Link to="/listings/new">
              <Button
                variant="primary"
                size="sm"
                className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold shadow-sm text-xs"
              >
                <PlusCircle className="w-4 h-4 mr-1.5" />
                + Soạn tin đăng mới (UC02)
              </Button>
            </Link>
          </div>
        </div>

        {/* NOTIFICATION */}
        {notification && (
          <div className="mb-6 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="font-semibold">{notification}</span>
            </div>
            <button onClick={() => setNotification(null)} className="text-slate-400 hover:text-slate-700 font-bold">
              ✕
            </button>
          </div>
        )}

        {/* 6 THẺ KPI SUMMARY CARDS */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
          {/* KPI 1: Tổng kho tin */}
          <Card className="p-4 bg-white shadow-xs flex flex-col justify-between">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-[11px] font-bold uppercase tracking-wider">Tổng tin đăng</span>
              <FolderKanban className="w-4 h-4 text-slate-400" />
            </div>
            <div className="my-2">
              <span className="text-2xl font-black text-slate-900">8</span>
              <span className="text-xs text-slate-500 ml-1">căn hộ/nhà phố</span>
            </div>
            <span className="text-[11px] font-bold text-emerald-600 flex items-center gap-0.5">
              <TrendingUp className="w-3 h-3" /> +2 tin mới tuần này
            </span>
          </Card>

          {/* KPI 2: Đang hiển thị */}
          <Card className="p-4 bg-white shadow-xs flex flex-col justify-between border-l-4 border-emerald-500">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-700">Đang hiển thị</span>
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            </div>
            <div className="my-2">
              <span className="text-2xl font-black text-emerald-700">4</span>
              <span className="text-xs text-slate-500 ml-1">/ 8 tin (50%)</span>
            </div>
            <span className="text-[11px] text-slate-400">100% khớp PostGIS</span>
          </Card>

          {/* KPI 3: Chờ duyệt / Cần sửa */}
          <Card className="p-4 bg-white shadow-xs flex flex-col justify-between border-l-4 border-amber-500">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-[11px] font-bold uppercase tracking-wider text-amber-700">Chờ duyệt / Sửa</span>
              <Clock className="w-4 h-4 text-amber-500" />
            </div>
            <div className="my-2">
              <span className="text-2xl font-black text-amber-700">3</span>
              <span className="text-[10px] font-bold bg-amber-100 text-amber-900 px-1.5 py-0.5 rounded ml-1">1 cần sửa gấp</span>
            </div>
            <span className="text-[11px] text-slate-400">SLA Mod: ≤ 8h</span>
          </Card>

          {/* KPI 4: Hết hạn 30 ngày (FR10) */}
          <Card className="p-4 bg-white shadow-xs flex flex-col justify-between border-l-4 border-rose-500">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-[11px] font-bold uppercase tracking-wider text-rose-700">Hết hạn FR10</span>
              <History className="w-4 h-4 text-rose-500" />
            </div>
            <div className="my-2">
              <span className="text-2xl font-black text-rose-700">1</span>
              <span className="text-xs text-rose-600 font-semibold ml-1">Tạm ẩn</span>
            </div>
            <span className="text-[11px] text-slate-400">Cần xác nhận còn hàng</span>
          </Card>

          {/* KPI 5 & 6: Tốc độ & Tỷ lệ xử lý Lead */}
          <Card className="col-span-2 p-4 bg-gradient-to-br from-emerald-900 to-slate-900 text-white shadow-sm flex flex-col justify-between">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <Zap className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-bold text-emerald-300">Tốc độ & Tỷ lệ xử lý Lead (BR03)</span>
              </div>
              <span className="text-[10px] font-bold bg-emerald-500/30 text-emerald-300 border border-emerald-400/40 px-2 py-0.5 rounded-full">
                92% trong 24h
              </span>
            </div>
            <div className="grid grid-cols-3 gap-2 my-2 pt-2 border-t border-slate-800 text-center">
              <div>
                <span className="text-[10px] text-slate-400 block">Lead mới 24h</span>
                <span className="text-base font-bold text-amber-400">3 khách</span>
              </div>
              <div>
                <span className="text-[10px] text-slate-400 block">Đang tư vấn</span>
                <span className="text-base font-bold text-emerald-400">4 khách</span>
              </div>
              <div>
                <span className="text-[10px] text-slate-400 block">Lịch hẹn xem</span>
                <span className="text-base font-bold text-blue-400">5 lịch</span>
              </div>
            </div>
            <div className="flex items-center justify-between text-[11px] text-slate-400">
              <span>Chống trùng Lead 24h (FR18)</span>
              <span className="text-emerald-400 font-semibold">Bảo mật OTP Active</span>
            </div>
          </Card>
        </div>

        {/* MAIN 12-COLUMN WORKSPACE GRID */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* CỘT TRÁI (8 cols): Kho Tin Đăng & Bảng Tương Tác */}
          <div className="lg:col-span-8 flex flex-col gap-4">
            <Card className="p-4">
              {/* Filter Tabs Bar */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
                <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
                  <button
                    onClick={() => setSelectedTab('ALL')}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      selectedTab === 'ALL'
                        ? 'bg-slate-900 text-white shadow-xs'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Tất cả (8)
                  </button>
                  <button
                    onClick={() => setSelectedTab('ACTIVE')}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      selectedTab === 'ACTIVE'
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Đang hiển thị (4)
                  </button>
                  <button
                    onClick={() => setSelectedTab('PENDING')}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      selectedTab === 'PENDING'
                        ? 'bg-amber-600 text-white shadow-xs'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Chờ duyệt (2)
                  </button>
                  <button
                    onClick={() => setSelectedTab('CHANGES')}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      selectedTab === 'CHANGES'
                        ? 'bg-rose-600 text-white shadow-xs'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Yêu cầu sửa (1)
                  </button>
                  <button
                    onClick={() => setSelectedTab('EXPIRED')}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      selectedTab === 'EXPIRED'
                        ? 'bg-slate-700 text-white shadow-xs'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Hết hạn 30 ngày (1)
                  </button>
                </div>

                {/* Tìm kiếm */}
                <div className="relative w-full sm:w-48">
                  <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Lọc tiêu đề..."
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                    className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                </div>
              </div>

              {/* Bảng Kho Tin Đăng */}
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-200 text-slate-400 uppercase text-[10px] tracking-wider">
                      <th className="pb-2 font-bold">Tin BĐS & Thông số</th>
                      <th className="pb-2 font-bold">Mức giá</th>
                      <th className="pb-2 font-bold">Trạng thái / Nhãn</th>
                      <th className="pb-2 font-bold">Hiệu lực (FR10)</th>
                      <th className="pb-2 font-bold text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredListings.map((item) => (
                      <tr key={item.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 pr-3">
                          <div className="flex items-start gap-3">
                            <img
                              src={item.imageUrl}
                              alt={item.title}
                              className="w-16 h-12 rounded-lg object-cover border border-slate-200 shrink-0"
                            />
                            <div>
                              <Link
                                to={`/listings/${item.id}`}
                                className="font-bold text-slate-900 hover:text-emerald-700 line-clamp-1 text-xs block"
                              >
                                {item.title}
                              </Link>
                              <span className="text-[11px] text-slate-500 block mt-0.5">
                                {item.propertyType} • {item.district} • {item.areaM2} m²
                              </span>
                              <span className="text-[10px] text-slate-400">
                                Cập nhật {item.updatedAt} • <strong className="text-emerald-700">{item.leadsCount} leads</strong>
                              </span>
                            </div>
                          </div>
                        </td>

                        <td className="py-3 pr-3 font-bold text-emerald-800 whitespace-nowrap">
                          {formatPriceVnd(item.priceVnd)}
                        </td>

                        <td className="py-3 pr-3 whitespace-nowrap">
                          <div className="flex flex-col gap-1 items-start">
                            {item.status === 'ACTIVE' && (
                              <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold text-[10px]">
                                Đang hiển thị
                              </span>
                            )}
                            {item.status === 'PENDING_REVIEW' && (
                              <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold text-[10px]">
                                Chờ duyệt SLA 8h
                              </span>
                            )}
                            {item.status === 'CHANGES_REQUESTED' && (
                              <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 font-bold text-[10px]">
                                Cần sửa ảnh/sổ
                              </span>
                            )}
                            {item.status === 'EXPIRED' && (
                              <span className="px-2 py-0.5 rounded-full bg-slate-200 text-slate-700 font-bold text-[10px]">
                                Hết hạn 30 ngày
                              </span>
                            )}

                            {item.verifiedOwner && (
                              <span className="text-[9px] font-bold text-emerald-700 flex items-center gap-0.5">
                                <ShieldCheck className="w-3 h-3 text-emerald-600" /> Sổ hồng chính chủ
                              </span>
                            )}
                          </div>
                        </td>

                        <td className="py-3 pr-3 whitespace-nowrap">
                          {item.status === 'EXPIRED' ? (
                            <span className="text-rose-600 font-bold text-[11px]">Đã hết hạn</span>
                          ) : (
                            <span className="text-slate-600 font-mono text-[11px]">
                              Còn {item.daysRemaining} ngày
                            </span>
                          )}
                        </td>

                        <td className="py-3 text-right whitespace-nowrap">
                          <div className="flex items-center justify-end gap-1.5">
                            {item.status === 'EXPIRED' ? (
                              <Button
                                size="sm"
                                onClick={() => handleRenewListing(item.id)}
                                className="bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold px-2 py-1 h-auto"
                              >
                                <RefreshCw className="w-3 h-3 mr-1" />
                                Còn hàng (Gia hạn)
                              </Button>
                            ) : (
                              <>
                                <Link to={`/listings/${item.id}`}>
                                  <button
                                    title="Xem chi tiết"
                                    className="p-1.5 rounded hover:bg-slate-100 text-slate-500 hover:text-slate-800"
                                  >
                                    <ExternalLink className="w-3.5 h-3.5" />
                                  </button>
                                </Link>
                                <Link to={`/contracts/dc-${item.id}`}>
                                  <button
                                    title="Tạo cọc Escrow"
                                    className="p-1.5 rounded hover:bg-emerald-50 text-emerald-700"
                                  >
                                    <Lock className="w-3.5 h-3.5" />
                                  </button>
                                </Link>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>

          {/* CỘT PHẢI (4 cols): KHÁCH MUA KHỚP NHU CẦU & LEAD PIPELINE */}
          <div className="lg:col-span-4 flex flex-col gap-6">
            {/* Widget Khách Mua Khớp Nhu Cầu Tự Động */}
            <Card className="p-4 border-2 border-emerald-500/20">
              <div className="flex items-center justify-between pb-2 border-b border-slate-100 mb-3">
                <div className="flex items-center gap-1.5">
                  <UserCheck className="w-4 h-4 text-emerald-600" />
                  <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Khách Mua Khớp Nhu Cầu (AI Matching)
                  </h3>
                </div>
                <span className="text-[10px] font-bold text-emerald-800 bg-emerald-100 px-1.5 py-0.5 rounded">
                  4 Khách nét
                </span>
              </div>

              <div className="space-y-3">
                {matchedBuyers.map((b) => (
                  <div key={b.id} className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-bold text-slate-900">{b.fullName}</span>
                      <span className="text-[10px] font-bold text-emerald-700 bg-emerald-100 px-1.5 py-0.2 rounded">
                        Khớp {b.matchScore}%
                      </span>
                    </div>

                    <div className="text-[11px] text-slate-500 space-y-0.5 mb-2">
                      <p>Tài chính: <strong className="text-slate-800">{b.targetBudget}</strong></p>
                      <p>Nhu cầu: {b.targetArea} • {b.preferredDistrict}</p>
                      <p>SĐT: <span className="font-mono">{b.phoneMasked}</span> (Bảo mật OTP)</p>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-slate-200/60">
                      <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                        b.pipelineStatus === 'VIEWING_SCHEDULED' ? 'bg-blue-100 text-blue-800' :
                        b.pipelineStatus === 'DEPOSIT_ESCROW' ? 'bg-emerald-100 text-emerald-800' :
                        'bg-amber-100 text-amber-800'
                      }`}>
                        {b.pipelineStatus === 'VIEWING_SCHEDULED' ? 'Đã hẹn xem nhà' :
                         b.pipelineStatus === 'DEPOSIT_ESCROW' ? 'Chờ ký cọc Escrow' : 'Đang tư vấn'}
                      </span>

                      <div className="flex gap-1">
                        <button
                          onClick={() => alert(`Đang kết nối cuộc gọi thoại an toàn đến khách hàng ${b.fullName}...`)}
                          className="p-1 rounded bg-emerald-600 text-white hover:bg-emerald-700 text-[10px] flex items-center gap-0.5 px-2"
                        >
                          <PhoneCall className="w-3 h-3" /> Gọi
                        </button>
                        <Link to={`/contracts/dc-mb-${b.id}`}>
                          <button className="p-1 rounded bg-slate-900 text-white hover:bg-slate-800 text-[10px] px-2">
                            Ký Cọc
                          </button>
                        </Link>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </Card>

            {/* Thông tin hỗ trợ môi giới */}
            <div className="p-4 rounded-xl bg-slate-900 text-white shadow-md">
              <div className="flex items-center gap-2 mb-2">
                <Zap className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                  Quy chế Chống cướp khách & Bảo đảm Lead
                </span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Mỗi lead khách hàng gửi từ tin đăng chính chủ sẽ được mã hóa và bảo hộ độc quyền cho môi giới phụ trách trong 24 giờ. Hệ thống tự động cảnh báo nếu phát hiện trùng lặp cuộc gọi.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BrokerWorkspacePage;
