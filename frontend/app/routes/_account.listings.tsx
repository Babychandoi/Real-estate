import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { PlusCircle, Clock, Eye, Send, Building2, Pencil } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { apiClient } from '@/shared/api/client';
import { formatPriceVnd } from '@/entities/listing/model/types';
import { listingPath } from '@/entities/listing/model/seo';

interface MyListingItem {
  id: string;
  slug: string;
  status: string;
  revisionNumber: number;
  revisionStatus: string;
  title: string;
  purpose: string;
  propertyType: string;
  priceVnd: number;
  areaM2: number;
  addressSummary: string;
  isVerified: boolean;
  imageUrls: string[];
  createdAt: string;
  updatedAt: string;
}

export const MyListingsPage: React.FC = () => {
  const [filterTab, setFilterTab] = useState<'ALL' | 'ACTIVE' | 'PENDING_REVIEW' | 'DRAFT'>('ALL');
  const [listings, setListings] = useState<MyListingItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchMyListings = async () => {
    setIsLoading(true);
    try {
      const data = await apiClient<MyListingItem[]>('/listings/my-listings');
      setListings(data);
    } catch (err) {
      console.error('Lỗi khi tải kho tin cá nhân:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMyListings();
  }, []);

  const handleQuickSubmit = async (listingId: string) => {
    try {
      await apiClient(`/listings/${listingId}/submit`, { method: 'POST' });
      fetchMyListings();
    } catch (err) {
      console.error('Không thể nộp duyệt:', err);
    }
  };

  const filteredListings = listings.filter((item) => {
    if (filterTab === 'ALL') return true;
    return item.status === filterTab;
  });

  const activeCount = listings.filter((l) => l.status === 'ACTIVE').length;
  const pendingCount = listings.filter((l) => l.status === 'PENDING_REVIEW').length;
  const draftCount = listings.filter((l) => l.status === 'DRAFT').length;

  return (
    <div className="max-w-6xl mx-auto px-4 md:px-8 py-8 flex flex-col gap-6">
      {/* Header Dashboard & Nút Tạo Tin Mới */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-on-surface">Quản lý kho tin đăng</h1>
          <p className="text-xs md:text-sm text-on-surface-variant mt-0.5">
            Theo dõi trạng thái kiểm duyệt, chỉnh sửa bản nháp và nộp duyệt phiên bản mới.
          </p>
        </div>
        <Link to="/listings/new">
          <Button variant="primary" size="md" leftIcon={<PlusCircle className="w-4 h-4" />}>
            Đăng tin mới
          </Button>
        </Link>
      </div>

      {/* Thẻ thống kê nhanh */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Card className="p-3.5 flex flex-col">
          <span className="text-xs text-on-surface-variant font-medium">Tổng tin đăng</span>
          <span className="text-2xl font-extrabold text-on-surface mt-1">{listings.length}</span>
        </Card>
        <Card className="p-3.5 flex flex-col">
          <span className="text-xs text-secondary font-medium">Đang hiển thị</span>
          <span className="text-2xl font-extrabold text-secondary mt-1">{activeCount}</span>
        </Card>
        <Card className="p-3.5 flex flex-col">
          <span className="text-xs text-tertiary-container font-medium">Chờ kiểm duyệt</span>
          <span className="text-2xl font-extrabold text-tertiary-container mt-1">{pendingCount}</span>
        </Card>
        <Card className="p-3.5 flex flex-col">
          <span className="text-xs text-outline font-medium">Bản nháp đang soạn</span>
          <span className="text-2xl font-extrabold text-on-surface-variant mt-1">{draftCount}</span>
        </Card>
      </div>

      {/* Tabs lọc trạng thái */}
      <div className="flex border-b border-outline-variant/40 text-sm gap-6 overflow-x-auto no-scrollbar">
        {[
          { id: 'ALL', label: 'Tất cả tin', count: listings.length },
          { id: 'ACTIVE', label: 'Đang hiển thị', count: activeCount },
          { id: 'PENDING_REVIEW', label: 'Chờ duyệt', count: pendingCount },
          { id: 'DRAFT', label: 'Bản nháp', count: draftCount },
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setFilterTab(tab.id as 'ALL' | 'ACTIVE' | 'PENDING_REVIEW' | 'DRAFT')}
            className={`pb-3 font-semibold flex items-center gap-1.5 transition-all relative ${
              filterTab === tab.id
                ? 'text-primary border-b-2 border-primary'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            {tab.label}
            <span className="px-1.5 py-0.2 rounded-full text-[11px] bg-surface-container-high">
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {/* Danh sách tin */}
      {isLoading ? (
        <div className="flex flex-col gap-3">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-28 bg-surface-container rounded-xl animate-pulse"></div>
          ))}
        </div>
      ) : filteredListings.length > 0 ? (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {filteredListings.map((item) => (
            <Card key={item.id} className="flex min-w-0 flex-col overflow-hidden p-0">
              <div className="relative aspect-[16/9] bg-surface-container">
                  {item.imageUrls[0] ? <img
                    src={item.imageUrls[0]}
                    alt={item.title}
                    className="h-full w-full object-cover"
                  /> : <div className="grid h-full place-items-center text-on-surface-variant" role="img" aria-label="Tin đăng chưa có ảnh">
                    <Building2 className="h-7 w-7" aria-hidden="true" />
                  </div>}
                  <div className="absolute bottom-2 right-2 rounded bg-surface-container-lowest/90 px-1.5 py-0.5 text-[10px] font-bold">
                    v{item.revisionNumber}
                  </div>
                </div>
              <div className="flex flex-1 flex-col p-4">
                  <div className="flex items-center gap-2 flex-wrap">
                    {item.status === 'ACTIVE' && <Badge variant="verified">Đang hiển thị</Badge>}
                    {item.status === 'PENDING_REVIEW' && (
                      <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-tertiary/10 text-tertiary-container flex items-center gap-1">
                        <Clock className="w-3 h-3" /> Chờ thẩm định
                      </span>
                    )}
                    {item.status === 'DRAFT' && <Badge variant="neutral">Bản nháp</Badge>}
                    <span className="text-xs font-semibold text-primary">{formatPriceVnd(item.priceVnd)}</span><span className="text-xs text-on-surface-variant">· {item.areaM2} m²</span>
                  </div>
                  <h3 className="mt-3 text-base font-bold text-on-surface line-clamp-2 hover:text-primary">
                    <Link to={listingPath(item)}>{item.title || 'Tin đăng chưa đặt tiêu đề'}</Link>
                  </h3>
                  <p className="mt-1 text-xs text-on-surface-variant line-clamp-1">{item.addressSummary}</p>
                <div className="mt-4 flex items-center gap-2 border-t border-outline-variant/30 pt-3">
                  <Link to={listingPath(item)}><Button variant="ghost" size="sm" leftIcon={<Eye className="w-4 h-4" />}>Xem</Button></Link>
                  {item.status !== 'PENDING_REVIEW' && <Link to={`/listings/new?edit=${item.id}`}><Button variant="outline" size="sm" leftIcon={<Pencil className="w-4 h-4" />}>Chỉnh sửa</Button></Link>}
                  {item.status === 'DRAFT' && <Button variant="primary" size="sm" onClick={() => handleQuickSubmit(item.id)} leftIcon={<Send className="w-4 h-4" />}>Nộp duyệt</Button>}
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <div className="text-center py-16 bg-surface-container-low rounded-2xl flex flex-col items-center gap-3">
          <Building2 className="w-10 h-10 text-outline" />
          <h3 className="font-bold text-base text-on-surface">Chưa có tin đăng nào trong mục này</h3>
          <p className="text-xs text-on-surface-variant max-w-sm">
            Bắt đầu tạo tin đăng mới để tiếp cận hàng ngàn khách hàng tiềm năng tìm kiếm bất động sản.
          </p>
          <Link to="/listings/new" className="mt-2">
            <Button variant="primary" size="md" leftIcon={<PlusCircle className="w-4 h-4" />}>
              Tạo tin đăng ngay
            </Button>
          </Link>
        </div>
      )}
    </div>
  );
};
