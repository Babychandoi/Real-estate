import React, { useState, useEffect } from 'react';
import { Search, Building2, Key, Shield, Sparkles, Filter } from 'lucide-react';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { type Listing } from '@/entities/listing/model/types';
import { Button } from '@/shared/ui/Button';
import { apiClient } from '@/shared/api/client';

export const HomePage: React.FC = () => {
  const [purpose, setPurpose] = useState<'SALE' | 'RENT'>('SALE');
  const [keyword, setKeyword] = useState('');
  const [listings, setListings] = useState<Listing[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      setIsLoading(true);
      try {
        const data = await apiClient<Listing[]>(`/listings/search?purpose=${purpose}`);
        if (isMounted) {
          setListings(data);
        }
      } catch (err) {
        console.error('Không thể tải danh sách tin đăng từ API backend:', err);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, [purpose]);

  return (
    <div className="flex flex-col gap-8 pb-16">
      {/* Hero Banner & Thanh tìm kiếm chính */}
      <section className="relative overflow-hidden bg-gradient-to-b from-primary-fixed/40 via-surface to-surface pt-10 pb-12 px-4 md:px-8 border-b border-outline-variant/30">
        <div className="max-w-4xl mx-auto flex flex-col items-center text-center gap-4">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-container-high border border-outline-variant text-xs font-semibold text-primary">
            <Shield className="w-3.5 h-3.5 text-secondary" />
            Nền tảng Bất động sản Kiểm duyệt & Xác thực Pháp lý
          </div>

          <h1 className="text-3xl md:text-5xl font-bold tracking-tight text-on-surface">
            Minh bạch từng căn nhà, <br className="hidden sm:inline" />
            <span className="text-primary">An tâm từng giao dịch</span>
          </h1>

          <p className="text-sm md:text-base text-on-surface-variant max-w-xl">
            100% tin đăng được kiểm duyệt hồ sơ pháp lý, cập nhật trạng thái còn/hết thực tế và bảo vệ dữ liệu khách hàng.
          </p>

          {/* Toggle Mua bán / Cho thuê */}
          <div className="mt-4 p-1.5 bg-surface-container rounded-xl flex gap-1 w-full max-w-xs shadow-inner">
            <button
              type="button"
              onClick={() => setPurpose('SALE')}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold flex items-center justify-center gap-1.5 transition-all ${
                purpose === 'SALE'
                  ? 'bg-surface-container-lowest text-primary shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <Building2 className="w-4 h-4" />
              Mua bán
            </button>
            <button
              type="button"
              onClick={() => setPurpose('RENT')}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold flex items-center justify-center gap-1.5 transition-all ${
                purpose === 'RENT'
                  ? 'bg-surface-container-lowest text-primary shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <Key className="w-4 h-4" />
              Cho thuê
            </button>
          </div>

          {/* Input Tìm kiếm thông minh */}
          <div className="w-full max-w-2xl bg-surface-container-lowest rounded-2xl p-2.5 shadow-[0_10px_25px_-5px_rgba(15,76,129,0.12)] border border-outline-variant/60 flex flex-col sm:flex-row gap-2 mt-2">
            <div className="flex items-center gap-2 flex-1 px-3">
              <Search className="w-5 h-5 text-outline" />
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm theo khu vực, dự án, phường mới (QĐ 19/2025)..."
                className="w-full bg-transparent text-sm text-on-surface placeholder:text-outline focus:outline-none py-1.5"
              />
            </div>
            <Button variant="primary" size="md" className="rounded-xl px-6">
              Tìm kiếm
            </Button>
          </div>

          {/* Filter Chips gợi ý nhanh */}
          <div className="flex items-center gap-2 overflow-x-auto max-w-full py-1 text-xs no-scrollbar text-on-surface-variant">
            <span className="font-semibold text-outline flex items-center gap-1">
              <Sparkles className="w-3.5 h-3.5 text-tertiary-container" /> Gợi ý:
            </span>
            <button className="px-3 py-1 rounded-full bg-surface-container hover:bg-surface-container-high transition-colors whitespace-nowrap">
              Vinhomes Green Bay
            </button>
            <button className="px-3 py-1 rounded-full bg-surface-container hover:bg-surface-container-high transition-colors whitespace-nowrap">
              Cầu Giấy 2PN
            </button>
            <button className="px-3 py-1 rounded-full bg-surface-container hover:bg-surface-container-high transition-colors whitespace-nowrap">
              Nhà phố Đống Đa
            </button>
          </div>
        </div>
      </section>

      {/* Danh sách tin đăng nổi bật */}
      <section className="max-w-6xl mx-auto w-full px-4 md:px-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-on-surface">
              Bất động sản nổi bật đã kiểm duyệt
            </h2>
            <p className="text-xs md:text-sm text-on-surface-variant mt-0.5">
              Đầy đủ thông tin sổ hồng, diện tích thực và hình ảnh thực tế
            </p>
          </div>
          <Button variant="outline" size="sm" leftIcon={<Filter className="w-4 h-4" />}>
            Bộ lọc
          </Button>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-80 bg-surface-container-high rounded-xl animate-pulse"></div>
            ))}
          </div>
        ) : listings.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {listings.map((item) => (
              <ListingCard key={item.id} listing={item} />
            ))}
          </div>
        ) : (
          <div className="text-center py-12 bg-surface-container-low rounded-xl">
            <p className="text-on-surface-variant">Không tìm thấy tin đăng phù hợp.</p>
          </div>
        )}
      </section>
    </div>
  );
};
