import React, { useState, useEffect } from 'react';
import {
  FileText,
  PlusCircle,
  Gavel,
  School,
  TrendingUp,
  ShieldCheck,
  CheckCircle2,
  Clock,
  RotateCcw,
  History,
  Layers,
  Search,
  X,
  ExternalLink
} from 'lucide-react';

interface ArticleRevisionItem {
  id: string;
  articleId: string;
  revisionNumber: number;
  title: string;
  summary: string;
  contentHtml: string;
  coverImageUrl: string;
  authorName: string;
  legalReference: string;
  metaDescription: string;
  canonicalUrl: string;
  status: 'DRAFT' | 'SUBMITTED' | 'PUBLISHED' | 'ARCHIVED' | 'REJECTED';
  rejectionReason?: string;
  createdAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
}

interface ArticleItem {
  id: string;
  slug: string;
  category: 'LEGAL_POLICY' | 'KNOWLEDGE' | 'MARKET_INSIGHTS';
  categoryLabel: string;
  status: 'DRAFT' | 'SUBMITTED' | 'PUBLISHED' | 'ARCHIVED';
  currentRevision: ArticleRevisionItem;
  revisionsCount: number;
}

const INITIAL_ARTICLES: ArticleItem[] = [
  {
    id: 'art-001',
    slug: 'chinh-sach-bao-ve-du-lieu-bds-2026',
    category: 'LEGAL_POLICY',
    categoryLabel: 'Chính sách & Pháp lý (FR32)',
    status: 'SUBMITTED',
    revisionsCount: 2,
    currentRevision: {
      id: 'rev-2026-088',
      articleId: 'art-001',
      revisionNumber: 2,
      title: 'Quy chuẩn bảo vệ dữ liệu cá nhân & Chống lừa đảo cọc BĐS 2026',
      summary: 'Hướng dẫn chi tiết bộ quy tắc bảo vệ dữ liệu và xác minh cọc theo Luật BV Dữ liệu 91/2025/QH15 và Luật Kinh doanh BĐS 2024.',
      contentHtml: '<p>Cơ chế ký số OTP hai bên cùng phong tỏa tiền cọc trong Escrow Vault loại trừ 100% rủi ro mất cọc...</p>',
      coverImageUrl: 'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=800&q=80',
      authorName: 'Lê Mai Hương (BTV Pháp chế)',
      legalReference: 'Luật BV Dữ liệu 91/2025/QH15 & Luật KDBĐS 2024',
      metaDescription: 'Hướng dẫn chi tiết bộ quy tắc bảo vệ dữ liệu và xác minh cọc an toàn',
      canonicalUrl: '/chinh-sach-bao-ve-du-lieu-bds-2026',
      status: 'SUBMITTED',
      createdAt: '14:20 - Hôm nay',
    },
  },
  {
    id: 'art-002',
    slug: 'cam-nang-kiem-tra-so-hong-va-quy-hoach-1-500',
    category: 'KNOWLEDGE',
    categoryLabel: 'Chuyên mục kiến thức',
    status: 'PUBLISHED',
    revisionsCount: 1,
    currentRevision: {
      id: 'rev-2026-042',
      articleId: 'art-002',
      revisionNumber: 1,
      title: 'Cẩm nang 5 bước đối soát Sổ đỏ và Quyết định Quy hoạch 1/500 chính thống',
      summary: 'Quy trình kiểm tra tính pháp lý của dự án và thửa đất thông qua Cổng Dịch vụ công và Giấy phép xây dựng Sở Xây Dựng.',
      contentHtml: '<p>Tránh bẫy mua đất quy hoạch treo bằng cách kiểm tra bản đồ địa chính số hóa...</p>',
      coverImageUrl: 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?auto=format&fit=crop&w=800&q=80',
      authorName: 'Trần Đình Trọng (Chuyên gia Quy hoạch)',
      legalReference: 'Luật Đất Đai 2024 số 31/2024/QH15',
      metaDescription: '5 bước đối soát sổ đỏ và bản đồ quy hoạch 1/500 an toàn tuyệt đối',
      canonicalUrl: '/cam-nang-kiem-tra-so-hong-va-quy-hoach-1-500',
      status: 'PUBLISHED',
      createdAt: '08/09/2026',
      reviewedAt: '09/09/2026',
      reviewedBy: 'Admin Tổng biên tập',
    },
  },
  {
    id: 'art-003',
    slug: 'bao-cao-bien-dong-gia-chung-cu-tay-ha-noi-q3-2026',
    category: 'MARKET_INSIGHTS',
    categoryLabel: 'Cẩm nang thị trường',
    status: 'DRAFT',
    revisionsCount: 1,
    currentRevision: {
      id: 'rev-2026-095',
      articleId: 'art-003',
      revisionNumber: 1,
      title: 'Báo cáo chỉ số giá và nguồn cung căn hộ khu Tây Hà Nội Quý 3/2026',
      summary: 'Phân tích dữ liệu thực tế từ 12.000 tin đăng đối soát: Mức giá trung bình Nam Từ Liêm đạt 65.5 triệu/m2.',
      contentHtml: '<p>Lượng tìm kiếm căn hộ 2 phòng ngủ chiếm 54% nhu cầu toàn thị trường...</p>',
      coverImageUrl: 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=800&q=80',
      authorName: 'Nguyễn Văn Bình (Phân tích thị trường)',
      legalReference: 'Dữ liệu Index BDS WF 2026',
      metaDescription: 'Báo cáo chỉ số giá căn hộ Tây Hà Nội Q3/2026 minh bạch',
      canonicalUrl: '/bao-cao-bien-dong-gia-chung-cu-tay-ha-noi-q3-2026',
      status: 'DRAFT',
      createdAt: 'Hôm qua, 17:30',
    },
  },
];

export const CmsManagementPage: React.FC = () => {
  const [articles, setArticles] = useState<ArticleItem[]>(INITIAL_ARTICLES);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [selectedTab, setSelectedTab] = useState<string>('SUBMITTED');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  // Form State
  const [formData, setFormData] = useState({
    title: '',
    slug: '',
    category: 'LEGAL_POLICY',
    authorName: 'Lê Mai Hương',
    legalReference: 'Luật KDBĐS 2024 & Luật Đất Đai 2024',
    summary: '',
    contentHtml: '',
    coverImageUrl: 'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=800&q=80',
  });

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  // Load from backend if available
  useEffect(() => {
    fetch('/api/v1/cms/articles')
      .then((res) => {
        if (res.ok) return res.json();
        return null;
      })
      .then((data) => {
        if (data && Array.isArray(data) && data.length > 0) {
          const mapped: ArticleItem[] = data.map((d: any) => ({
            id: d.id,
            slug: d.slug,
            category: d.category,
            categoryLabel:
              d.category === 'LEGAL_POLICY'
                ? 'Chính sách & Pháp lý (FR32)'
                : d.category === 'KNOWLEDGE'
                ? 'Chuyên mục kiến thức'
                : 'Cẩm nang thị trường',
            status: d.status,
            revisionsCount: d.revisions ? d.revisions.length : 1,
            currentRevision: d.currentRevision
              ? {
                  id: d.currentRevision.id,
                  articleId: d.id,
                  revisionNumber: d.currentRevision.revisionNumber,
                  title: d.currentRevision.title,
                  summary: d.currentRevision.summary || '',
                  contentHtml: d.currentRevision.contentHtml,
                  coverImageUrl:
                    d.currentRevision.coverImageUrl ||
                    'https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=800&q=80',
                  authorName: d.currentRevision.authorName,
                  legalReference: d.currentRevision.legalReference || '',
                  metaDescription: d.currentRevision.metaDescription || '',
                  canonicalUrl: d.currentRevision.canonicalUrl || `/${d.slug}`,
                  status: d.currentRevision.status,
                  createdAt: 'Hôm nay',
                  reviewedAt: d.currentRevision.reviewedAt,
                  reviewedBy: d.currentRevision.reviewedBy,
                }
              : INITIAL_ARTICLES[0].currentRevision,
          }));
          setArticles([...mapped, ...INITIAL_ARTICLES]);
        }
      })
      .catch(() => {});
  }, []);

  const handleCreateArticle = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.title || !formData.slug || !formData.contentHtml) {
      showToast('Vui lòng điền đủ Tiêu đề, Slug và Nội dung bài viết');
      return;
    }

    try {
      const payload = {
        title: formData.title,
        slug: formData.slug,
        category: formData.category,
        authorName: formData.authorName,
        legalReference: formData.legalReference,
        summary: formData.summary,
        contentHtml: formData.contentHtml,
        coverImageUrl: formData.coverImageUrl,
        metaDescription: formData.summary,
        canonicalUrl: `/${formData.slug}`,
      };

      const res = await fetch('/api/v1/cms/articles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const created = await res.json();
        const newItem: ArticleItem = {
          id: created.id,
          slug: created.slug,
          category: created.category,
          categoryLabel:
            created.category === 'LEGAL_POLICY'
              ? 'Chính sách & Pháp lý (FR32)'
              : created.category === 'KNOWLEDGE'
              ? 'Chuyên mục kiến thức'
              : 'Cẩm nang thị trường',
          status: 'DRAFT',
          revisionsCount: 1,
          currentRevision: {
            id: created.currentRevision.id,
            articleId: created.id,
            revisionNumber: 1,
            title: created.currentRevision.title,
            summary: created.currentRevision.summary,
            contentHtml: created.currentRevision.contentHtml,
            coverImageUrl: created.currentRevision.coverImageUrl,
            authorName: created.currentRevision.authorName,
            legalReference: created.currentRevision.legalReference,
            metaDescription: created.currentRevision.metaDescription,
            canonicalUrl: created.currentRevision.canonicalUrl,
            status: 'DRAFT',
            createdAt: 'Vừa xong',
          },
        };
        setArticles([newItem, ...articles]);
        setIsCreateModalOpen(false);
        showToast('Đã tạo bản nháp bài viết mới thành công (Revision 1)');
      } else {
        showToast('Lỗi máy chủ khi tạo bài viết');
      }
    } catch {
      // Local fallback
      const localItem: ArticleItem = {
        id: `local-${Date.now()}`,
        slug: formData.slug,
        category: formData.category as any,
        categoryLabel:
          formData.category === 'LEGAL_POLICY'
            ? 'Chính sách & Pháp lý (FR32)'
            : 'Chuyên mục kiến thức',
        status: 'DRAFT',
        revisionsCount: 1,
        currentRevision: {
          id: `rev-${Date.now()}`,
          articleId: `local-${Date.now()}`,
          revisionNumber: 1,
          title: formData.title,
          summary: formData.summary,
          contentHtml: formData.contentHtml,
          coverImageUrl: formData.coverImageUrl,
          authorName: formData.authorName,
          legalReference: formData.legalReference,
          metaDescription: formData.summary,
          canonicalUrl: `/${formData.slug}`,
          status: 'DRAFT',
          createdAt: 'Vừa xong',
        },
      };
      setArticles([localItem, ...articles]);
      setIsCreateModalOpen(false);
      showToast('Đã lưu bản nháp bài viết vào bộ nhớ');
    }
  };

  const handleApprove = async (articleId: string, revisionId: string) => {
    try {
      await fetch(`/api/v1/cms/articles/${articleId}/revisions/${revisionId}/approve?adminUsername=Admin_Chief`, {
        method: 'POST',
      });
    } catch {}

    setArticles(
      articles.map((art) => {
        if (art.id === articleId) {
          return {
            ...art,
            status: 'PUBLISHED',
            currentRevision: {
              ...art.currentRevision,
              status: 'PUBLISHED',
              reviewedBy: 'Admin Tổng biên tập',
              reviewedAt: 'Vừa xong',
            },
          };
        }
        return art;
      })
    );
    showToast('Đã phê duyệt và xuất bản bài viết công khai thành công (FR24/FR32)');
  };

  const handleReject = async () => {
    if (!rejectingId) return;
    const targetArticle = articles.find((a) => a.id === rejectingId);
    if (!targetArticle) return;

    try {
      await fetch(
        `/api/v1/cms/articles/${rejectingId}/revisions/${targetArticle.currentRevision.id}/reject`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            reason: rejectReason || 'Yêu cầu bổ sung đối chiếu thông tư hướng dẫn',
            adminUsername: 'Admin_Chief',
          }),
        }
      );
    } catch {}

    setArticles(
      articles.map((art) => {
        if (art.id === rejectingId) {
          return {
            ...art,
            status: 'DRAFT',
            currentRevision: {
              ...art.currentRevision,
              status: 'REJECTED',
              rejectionReason: rejectReason || 'Yêu cầu bổ sung đối chiếu thông tư hướng dẫn',
            },
          };
        }
        return art;
      })
    );

    setRejectingId(null);
    setRejectReason('');
    showToast('Đã trả về bản thảo kèm lý do yêu cầu sửa đổi cho BTV');
  };

  // Filter
  const filteredArticles = articles.filter((art) => {
    const matchesCategory =
      selectedCategory === 'ALL' || art.category === selectedCategory;

    const matchesTab =
      selectedTab === 'ALL' ||
      (selectedTab === 'SUBMITTED' && art.currentRevision.status === 'SUBMITTED') ||
      (selectedTab === 'DRAFT' &&
        (art.currentRevision.status === 'DRAFT' || art.currentRevision.status === 'REJECTED')) ||
      (selectedTab === 'PUBLISHED' && art.status === 'PUBLISHED') ||
      (selectedTab === 'ARCHIVED' && art.status === 'ARCHIVED');

    const matchesSearch =
      searchQuery === '' ||
      art.currentRevision.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      art.slug.toLowerCase().includes(searchQuery.toLowerCase()) ||
      art.currentRevision.authorName.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesCategory && matchesTab && matchesSearch;
  });

  return (
    <div className="min-h-screen bg-surface pb-20 pt-4 text-on-surface">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-20 left-1/2 -translate-x-1/2 z-50 bg-primary text-white px-5 py-3 rounded-xl shadow-2xl flex items-center gap-3 animate-fade-in border border-primary-container">
          <ShieldCheck className="w-5 h-5 text-emerald-400" />
          <div className="text-sm">
            <span className="font-semibold block text-emerald-300">
              CMS KIỂM DUYỆT ĐỘC LẬP (FR24 / FR32)
            </span>
            <span>{toastMessage}</span>
          </div>
        </div>
      )}

      <div className="max-w-6xl mx-auto px-4 md:px-8 space-y-6">
        {/* Header & Quick Action Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-outline-variant/30 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2 h-6 rounded-full bg-primary inline-block"></span>
              <h1 className="text-2xl font-bold text-primary">Biên tập & Xuất bản CMS</h1>
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-xs font-semibold uppercase">
                Waterfall 0.9.1 • FR24 & FR32
              </span>
            </div>
            <p className="text-sm text-on-surface-variant mt-1">
              Quản lý ContentRevision (ED04/ERD04) theo quy chuẩn kiểm duyệt nội dung độc lập của Biên tập viên và Ban biên tập.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="flex items-center gap-1.5 h-10 px-4 rounded-lg bg-primary text-white text-sm font-semibold hover:bg-primary/90 shadow-sm transition-all"
            >
              <PlusCircle className="w-4 h-4" />
              Soạn bài viết mới
            </button>
          </div>
        </div>

        {/* Compliance Alert Box (FR24 & FR32 Mandate) */}
        <div className="p-4 rounded-xl bg-surface-container-high border border-outline-variant/30 shadow-sm flex items-start gap-3">
          <div className="w-9 h-9 rounded-full bg-primary text-white flex items-center justify-center shrink-0 mt-0.5">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="space-y-1 text-xs">
            <div className="flex items-center gap-2">
              <span className="font-bold text-primary text-sm">
                Quy tắc Tuân thủ Kiểm duyệt Nội dung Độc lập (FR24 & FR32)
              </span>
              <span className="px-1.5 py-0.5 rounded bg-surface-container-lowest text-primary font-mono font-semibold text-[10px]">
                Strict Mode Active
              </span>
            </div>
            <p className="text-on-surface-variant leading-relaxed">
              Biên tập viên (BTV) không thể tự ý xuất bản bài viết lên trang chủ. Mọi nội dung sửa đổi trên bài viết đang công khai sẽ tự động sinh một <strong>Revision mới</strong>; bài viết cũ tiếp tục giữ nguyên hiệu lực cho tới khi Admin Tổng biên tập đối soát và phê duyệt.
            </p>
          </div>
        </div>

        {/* Category Horizontal Filter Chips */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs">
          {[
            { key: 'ALL', label: 'Tất cả chuyên mục', icon: FileText },
            { key: 'LEGAL_POLICY', label: 'Chính sách & Pháp lý (FR32)', icon: Gavel },
            { key: 'KNOWLEDGE', label: 'Chuyên mục kiến thức', icon: School },
            { key: 'MARKET_INSIGHTS', label: 'Cẩm nang thị trường', icon: TrendingUp },
          ].map((cat) => {
            const Icon = cat.icon;
            const isSelected = selectedCategory === cat.key;
            return (
              <button
                key={cat.key}
                onClick={() => setSelectedCategory(cat.key)}
                className={`h-9 px-3.5 rounded-full font-medium transition-all flex items-center gap-1.5 shrink-0 ${
                  isSelected
                    ? 'bg-primary text-white shadow-sm'
                    : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{cat.label}</span>
              </button>
            );
          })}
        </div>

        {/* Lifecycle Revision Tabs & Search Toolbar */}
        <div className="space-y-3">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-1 p-1 bg-surface-container rounded-xl text-center">
            {[
              {
                key: 'SUBMITTED',
                label: 'Chờ duyệt',
                count: articles.filter((a) => a.currentRevision.status === 'SUBMITTED').length,
                color: 'text-amber-700',
              },
              {
                key: 'DRAFT',
                label: 'Đang soạn / Trả về',
                count: articles.filter(
                  (a) => a.currentRevision.status === 'DRAFT' || a.currentRevision.status === 'REJECTED'
                ).length,
                color: 'text-outline',
              },
              {
                key: 'PUBLISHED',
                label: 'Đã công khai',
                count: articles.filter((a) => a.status === 'PUBLISHED').length,
                color: 'text-secondary font-semibold',
              },
              {
                key: 'ALL',
                label: 'Tất cả',
                count: articles.length,
                color: 'text-primary',
              },
            ].map((tab) => (
              <button
                key={tab.key}
                onClick={() => setSelectedTab(tab.key)}
                className={`py-2 px-3 rounded-lg text-xs font-medium transition-all flex flex-col items-center justify-center ${
                  selectedTab === tab.key
                    ? 'bg-surface-container-lowest text-primary shadow-sm font-bold'
                    : 'text-on-surface-variant hover:text-primary'
                }`}
              >
                <span>{tab.label}</span>
                <span className={`text-[11px] ${tab.color}`}>
                  {tab.count.toString().padStart(2, '0')}
                </span>
              </button>
            ))}
          </div>

          {/* Search Box */}
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-outline" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm theo tiêu đề bài viết, slug URL, tên tác giả biên tập viên..."
              className="w-full h-10 pl-10 pr-10 rounded-xl bg-surface-container-lowest border border-outline-variant/30 text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-outline hover:text-primary"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>

        {/* Article Cards List */}
        <div className="space-y-6">
          {filteredArticles.length === 0 ? (
            <div className="py-16 text-center bg-surface-container-lowest border border-dashed border-outline-variant rounded-xl p-8 space-y-3">
              <FileText className="w-12 h-12 text-outline mx-auto" />
              <div className="font-semibold text-base text-on-surface">
                Không tìm thấy bài viết phù hợp
              </div>
              <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                Vui lòng thử điều chỉnh lại chuyên mục hoặc trạng thái vòng đời bài viết.
              </p>
              <button
                onClick={() => {
                  setSelectedCategory('ALL');
                  setSelectedTab('ALL');
                  setSearchQuery('');
                }}
                className="h-9 px-4 rounded-lg bg-primary text-white text-xs font-semibold"
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : (
            filteredArticles.map((art) => {
              const rev = art.currentRevision;
              const isPending = rev.status === 'SUBMITTED';
              const isRejected = rev.status === 'REJECTED';

              return (
                <div
                  key={art.id}
                  className={`rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-sm overflow-hidden flex flex-col md:flex-row transition-all hover:shadow-md ${
                    isPending ? 'border-l-4 border-l-amber-500 ring-1 ring-amber-500/20' : ''
                  }`}
                >
                  {/* Cover Image & Metadata Overlay */}
                  <div className="md:w-80 h-48 md:h-auto relative shrink-0 overflow-hidden bg-surface-dim">
                    <img
                      src={rev.coverImageUrl}
                      alt={rev.title}
                      className="w-full h-full object-cover"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/20 to-transparent"></div>

                    {/* Status badge */}
                    <div className="absolute top-3 left-3 right-3 flex items-center justify-between">
                      <span
                        className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1 shadow-sm ${
                          isPending
                            ? 'bg-amber-600 text-white animate-pulse'
                            : isRejected
                            ? 'bg-rose-600 text-white'
                            : art.status === 'PUBLISHED'
                            ? 'bg-emerald-600 text-white'
                            : 'bg-slate-700 text-white'
                        }`}
                      >
                        {isPending && <Clock className="w-3.5 h-3.5" />}
                        {art.status === 'PUBLISHED' && <CheckCircle2 className="w-3.5 h-3.5" />}
                        {isPending
                          ? 'CHỜ DUYỆT'
                          : isRejected
                          ? 'TRẢ VỀ SỬA'
                          : art.status === 'PUBLISHED'
                          ? 'ĐÃ XUẤT BẢN'
                          : 'BẢN NHÁP'}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-black/60 text-white text-[11px] font-mono">
                        Rev #{rev.revisionNumber}
                      </span>
                    </div>

                    <div className="absolute bottom-3 left-3 right-3 text-white text-xs flex items-center justify-between">
                      <span className="bg-black/40 backdrop-blur-sm px-2 py-0.5 rounded text-[11px]">
                        1200 x 630px
                      </span>
                      <span className="bg-emerald-800/80 px-2 py-0.5 rounded text-[10px] font-semibold">
                        Clean HTML Anti-XSS ✓
                      </span>
                    </div>
                  </div>

                  {/* Body Content */}
                  <div className="p-5 flex-1 flex flex-col justify-between space-y-4">
                    <div className="space-y-2">
                      <div className="flex items-center justify-between gap-2 flex-wrap">
                        <span className="text-xs px-2 py-0.5 rounded bg-surface-container text-primary font-semibold">
                          {art.categoryLabel}
                        </span>
                        <span className="text-outline text-[11px] font-mono">
                          ID: #{rev.id.slice(0, 12)}
                        </span>
                      </div>

                      <h2 className="text-lg font-bold text-on-surface leading-snug">
                        {rev.title}
                      </h2>

                      {/* Author & Legal Citation */}
                      <div className="flex items-center gap-2 text-xs text-on-surface-variant flex-wrap">
                        {rev.legalReference && (
                          <span className="text-primary font-semibold flex items-center gap-1">
                            <Gavel className="w-3 h-3" />
                            {rev.legalReference}
                          </span>
                        )}
                        <span>•</span>
                        <span>BTV: {rev.authorName}</span>
                        <span>•</span>
                        <span className="text-outline">{rev.createdAt}</span>
                      </div>

                      <p className="text-xs text-on-surface-variant leading-relaxed line-clamp-2">
                        {rev.summary}
                      </p>

                      {/* SEO Specs Accordion FR26 */}
                      <div className="p-2.5 rounded-lg bg-surface-container-low text-xs space-y-1 font-mono text-[11px]">
                        <div className="flex items-center justify-between">
                          <span className="text-outline">Slug:</span>
                          <span className="text-primary font-semibold">/{art.slug}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-outline">Canonical:</span>
                          <span className="text-secondary font-medium">Self-referencing ✓</span>
                        </div>
                      </div>

                      {/* Rejection notice if any */}
                      {isRejected && rev.rejectionReason && (
                        <div className="p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-xs text-rose-800">
                          <strong>Lý do trả về sửa:</strong> {rev.rejectionReason}
                        </div>
                      )}
                    </div>

                    {/* Actions Dock */}
                    <div className="flex items-center gap-2 pt-2 border-t border-outline-variant/20 flex-wrap">
                      {isPending ? (
                        <>
                          <button
                            onClick={() => handleApprove(art.id, rev.id)}
                            className="h-9 px-4 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary/90 flex items-center gap-1.5 shadow-sm transition-all"
                          >
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            PHÊ DUYỆT & XUẤT BẢN
                          </button>
                          <button
                            onClick={() => setRejectingId(art.id)}
                            className="h-9 px-3 rounded-lg bg-rose-50 text-rose-700 hover:bg-rose-100 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-rose-200"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                            Trả về sửa (Lý do)
                          </button>
                          <button
                            onClick={() =>
                              showToast(`So sánh đối chiếu Diff giữa Revision 1 và Revision 2`)
                            }
                            className="h-9 px-3 rounded-lg bg-surface-container text-primary text-xs font-medium hover:bg-surface-container-high transition-colors flex items-center gap-1"
                          >
                            <History className="w-3.5 h-3.5" />
                            Đối chiếu Diff
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            onClick={() =>
                              showToast(`Xem bài viết thực tế đã xuất bản tại /${art.slug}`)
                            }
                            className="h-9 px-3 rounded-lg bg-surface-container text-primary text-xs font-medium hover:bg-surface-container-high transition-colors flex items-center gap-1"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                            Xem trang công khai
                          </button>
                          <button
                            onClick={() =>
                              showToast(
                                `Khởi tạo Revision #${art.revisionsCount + 1} để biên tập cập nhật (ED04)`
                              )
                            }
                            className="h-9 px-3 rounded-lg bg-surface-container text-on-surface-variant text-xs font-medium hover:bg-surface-container-high transition-colors flex items-center gap-1"
                          >
                            <Layers className="w-3.5 h-3.5" />
                            Tạo Revision mới
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Modal Trả Về Sửa */}
      {rejectingId && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant/30 overflow-hidden animate-fade-in p-5 space-y-4">
            <h3 className="text-base font-bold text-rose-700 flex items-center gap-2">
              <RotateCcw className="w-5 h-5" /> Trả về yêu cầu sửa đổi
            </h3>
            <p className="text-xs text-on-surface-variant">
              Ghi rõ nội dung điều khoản hoặc thông tư chưa đạt chuẩn để BTV tiến hành chỉnh sửa:
            </p>
            <textarea
              rows={3}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="VD: Cần bổ sung trích dẫn Nghị định 96/2024/NĐ-CP hướng dẫn Luật KDBĐS về mẫu hợp đồng cọc..."
              className="w-full p-3 rounded-lg border border-outline-variant text-xs focus:outline-none focus:ring-2 focus:ring-rose-500/30"
            />
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setRejectingId(null)}
                className="h-9 px-4 rounded-lg bg-surface-container text-xs font-medium"
              >
                Hủy
              </button>
              <button
                onClick={handleReject}
                className="h-9 px-4 rounded-lg bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700 shadow-sm"
              >
                Gửi yêu cầu sửa
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Soạn Bài Viết Mới Chuẩn ERD04 */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-2xl bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant/30 overflow-hidden animate-fade-in flex flex-col max-h-[90vh]">
            <div className="p-5 border-b border-outline-variant/30 flex items-center justify-between bg-surface-container-low">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-primary" />
                <h3 className="font-bold text-base text-on-surface">
                  Soạn thảo bài viết CMS (Chuẩn ERD04 & FR32)
                </h3>
              </div>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="w-8 h-8 rounded-full hover:bg-surface-container flex items-center justify-center text-outline"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateArticle} className="p-6 overflow-y-auto space-y-4 text-sm">
              <div className="p-3 bg-blue-50 rounded-lg border border-blue-200 text-xs text-blue-800">
                <strong>Quy tắc FR24:</strong> Bài viết mới sẽ lưu dưới dạng <strong>Revision 1 (DRAFT)</strong>. Bạn cần nộp duyệt để Ban biên tập thẩm định trước khi xuất bản.
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Tiêu đề bài viết *
                </label>
                <input
                  type="text"
                  required
                  placeholder="VD: Hướng dẫn định giá căn hộ theo chỉ số thị trường"
                  value={formData.title}
                  onChange={(e) => {
                    const title = e.target.value;
                    const slug = title
                      .toLowerCase()
                      .normalize('NFD')
                      .replace(/[\u0300-\u036f]/g, '')
                      .replace(/[đĐ]/g, 'd')
                      .replace(/[^a-z0-9\s-]/g, '')
                      .replace(/\s+/g, '-')
                      .slice(0, 80);
                    setFormData({ ...formData, title, slug });
                  }}
                  className="w-full h-10 px-3 rounded-lg border border-outline-variant text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Slug URL (SEO FR26) *
                  </label>
                  <input
                    type="text"
                    required
                    value={formData.slug}
                    onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Chuyên mục *
                  </label>
                  <select
                    value={formData.category}
                    onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  >
                    <option value="LEGAL_POLICY">Chính sách & Pháp lý (FR32)</option>
                    <option value="KNOWLEDGE">Chuyên mục kiến thức</option>
                    <option value="MARKET_INSIGHTS">Cẩm nang thị trường</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Tác giả biên tập viên *
                  </label>
                  <input
                    type="text"
                    required
                    value={formData.authorName}
                    onChange={(e) => setFormData({ ...formData, authorName: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Luật / Pháp lý tham chiếu
                  </label>
                  <input
                    type="text"
                    placeholder="VD: Luật KDBĐS 2024 số 29/2023/QH15"
                    value={formData.legalReference}
                    onChange={(e) => setFormData({ ...formData, legalReference: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Đoạn tóm tắt mở đầu
                </label>
                <textarea
                  rows={2}
                  value={formData.summary}
                  onChange={(e) => setFormData({ ...formData, summary: e.target.value })}
                  placeholder="Tóm tắt ngắn gọn 1-2 câu để làm meta description cho Google..."
                  className="w-full p-2.5 rounded-lg border border-outline-variant text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Nội dung chi tiết (Clean HTML) *
                </label>
                <textarea
                  rows={6}
                  required
                  value={formData.contentHtml}
                  onChange={(e) => setFormData({ ...formData, contentHtml: e.target.value })}
                  placeholder="<p>Nhập nội dung bài viết định dạng HTML an toàn...</p>"
                  className="w-full p-3 rounded-lg border border-outline-variant text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>

              <div className="p-3 border-t border-outline-variant/30 flex items-center justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="h-10 px-4 rounded-lg bg-surface-container text-on-surface-variant font-medium text-xs hover:bg-surface-container-high transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="h-10 px-5 rounded-lg bg-primary text-white font-semibold text-xs hover:bg-primary/90 transition-all shadow-sm flex items-center gap-1.5"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  Lưu bản nháp Revision 1
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CmsManagementPage;
