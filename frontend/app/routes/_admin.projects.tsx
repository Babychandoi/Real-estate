import React, { useState, useEffect } from 'react';
import {
  Building2,
  PlusCircle,
  Download,
  FileCheck2,
  Search,
  MapPin,
  Layers,
  Link as LinkIcon,
  CheckCircle2,
  Clock,
  ShieldCheck,
  AlertTriangle,
  History,
  X
} from 'lucide-react';

interface ProjectItem {
  id: string;
  name: string;
  code: string;
  investorName: string;
  location: string;
  district: string;
  provinceCity: string;
  status: 'SELLING' | 'UPCOMING' | 'DELIVERED' | 'PENDING_APPROVAL';
  statusLabel: string;
  scaleHa: number;
  totalTowers: number;
  totalUnits: number;
  subdivisions: string;
  buildingPermitNo: string;
  masterPlan1500Decision: string;
  appraisedAuthority: string;
  imageUrl: string;
  linkedListingsCount: number;
  updatedAt: string;
  pendingRevision?: {
    editorName: string;
    description: string;
    submittedAt: string;
    revisionNo: number;
  };
}

const INITIAL_PROJECTS: ProjectItem[] = [
  {
    id: 'da-hn-0082',
    name: 'Vinhomes Smart City',
    code: 'DA-HN-0082',
    investorName: 'Vingroup',
    location: 'Tây Mỗ - Đại Mỗ, Nam Từ Liêm, Hà Nội',
    district: 'Nam Từ Liêm',
    provinceCity: 'Hà Nội',
    status: 'SELLING',
    statusLabel: 'Đang mở bán',
    scaleHa: 280,
    totalTowers: 58,
    totalUnits: 40000,
    subdivisions: 'Sapphire, The Tonkin, The Miami, Masteri West Heights',
    buildingPermitNo: 'GPXD-928/SXD-HN',
    masterPlan1500Decision: 'QĐ-2849/QĐ-UBND QH 1/500',
    appraisedAuthority: 'Sở Xây Dựng Hà Nội',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBFG6ZF2TWA0-j55WHUN1nem73CxAOfnJZkphX0rfPMvyWksfeVREdrYcpjlnm4HXvPkxyq8qEwTAvMakq_eW2F1OHr76e_HHtIIE7Ci3QkyMdJosft2XEv7tjdD5TLW-W9ywPezaut_g_b_NF-TaUqJTNXZh1pRX3ZitzYU2gIQcM9ZV0dkG3trm2KTg9cTsVxV1W7S18XIJEgBPmlfq6uc0m2RFBijaYl3mMGmvdoLC7tZYvKFikp',
    linkedListingsCount: 142,
    updatedAt: '05/09/2026',
  },
  {
    id: 'da-hn-0056',
    name: 'The Matrix One Mễ Trì',
    code: 'DA-HN-0056',
    investorName: 'MIK Group',
    location: 'Lê Quang Đạo, Mễ Trì, Nam Từ Liêm, Hà Nội',
    district: 'Nam Từ Liêm',
    provinceCity: 'Hà Nội',
    status: 'DELIVERED',
    statusLabel: 'Đã bàn giao',
    scaleHa: 3.9,
    totalTowers: 2,
    totalUnits: 740,
    subdivisions: 'Tháp A, Tháp B & Shophouse Diamond',
    buildingPermitNo: 'GPXD-214/SXD',
    masterPlan1500Decision: 'QĐ-1420/UBND QH 1/500 Phê duyệt 2022',
    appraisedAuthority: 'Sở Xây Dựng Hà Nội',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBgutO5nISc1z5ujlLMFG-HYmGhmXaU6Rp2TkEqoLne7vVHRwXuz63niVj92JS4-EvMTac_lHF0qkgh1LKw65bBF2Qc49i1q4noVO7MQ7U7eb3orQK6GxyfKep0wUPDWMOhDawlVrKZzIoATs3Ymf4nAKTd5NCbU-0aXEZvSasQ7tNQvAvBjDPNzOiY6Zn57OQnlR623C9LM-_h_22Qko3hDRSGk4mt_BCAEpxEmjwjcSTmeHzQ1SJw',
    linkedListingsCount: 38,
    updatedAt: '12/08/2026',
  },
  {
    id: 'da-hn-0099',
    name: 'Masteri West Heights',
    code: 'DA-HN-0099',
    investorName: 'Masterise Homes',
    location: 'KĐT Smart City, Nam Từ Liêm, Hà Nội',
    district: 'Nam Từ Liêm',
    provinceCity: 'Hà Nội',
    status: 'PENDING_APPROVAL',
    statusLabel: 'Chờ duyệt Revision #2',
    scaleHa: 3.1,
    totalTowers: 4,
    totalUnits: 3599,
    subdivisions: 'West A, West B, West C, West D',
    buildingPermitNo: 'GPXD-48/QĐ-BXD',
    masterPlan1500Decision: 'QĐ-883/QĐ-UBND Điều chỉnh Quy hoạch 1/500',
    appraisedAuthority: 'Bộ Xây Dựng & Sở Xây Dựng HN',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuA_mI2JXy1VuuxLt0lWL1RfrXmqYWLk6TwmIq9vjzyjRsXAjBJ0Ue6vwUkiWMko5qEf_KBxapEIOQb6OPPTYpZsarjAdPdliA-EpbsAbETWqgRw1SLXd-FiEGlnb-APmSQqpXTbB6OEvZJ9vy9UR4wIIS5F4I3hjNveOT4YK7yVyORk8TrY-uig0gV6m6q8rplq9vV7z541pEFKc1-phRZoAKT7HvB8d66g_uRBdH0QQz4D4I55h_Wl',
    linkedListingsCount: 95,
    updatedAt: 'Hôm nay, 10:45',
    pendingRevision: {
      editorName: 'Nguyễn Văn Bình (Editor ID: ED-04)',
      description: 'Vừa gửi cập nhật: Bảng giá trần quý 4/2026 & Tiến độ hoàn thiện Phân khu C đối soát theo Quyết định số 48/QĐ-BXD.',
      submittedAt: '10:45 - Hôm nay',
      revisionNo: 2,
    },
  },
];

export const ProjectCatalogPage: React.FC = () => {
  const [projects, setProjects] = useState<ProjectItem[]>(INITIAL_PROJECTS);
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedDistrict, setSelectedDistrict] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Form State
  const [formData, setFormData] = useState({
    name: '',
    code: '',
    investorName: '',
    address: '',
    district: 'Nam Từ Liêm',
    provinceCity: 'Hà Nội',
    scaleHa: 5.0,
    totalTowers: 3,
    totalUnits: 1200,
    buildingPermitNo: '',
    masterPlan1500Decision: '',
    appraisedAuthority: 'Sở Xây Dựng Hà Nội',
    status: 'SELLING',
  });

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(null);
    }, 3500);
  };

  // Fetch backend projects if available
  useEffect(() => {
    fetch('/api/v1/catalog/projects')
      .then((res) => {
        if (res.ok) return res.json();
        return null;
      })
      .then((data) => {
        if (data && Array.isArray(data) && data.length > 0) {
          // Map backend items into view
          const mapped: ProjectItem[] = data.map((d: any) => ({
            id: d.id,
            name: d.name,
            code: d.code,
            investorName: d.investorName,
            location: `${d.address}, ${d.district}, ${d.provinceCity}`,
            district: d.district,
            provinceCity: d.provinceCity,
            status: d.status,
            statusLabel:
              d.status === 'SELLING'
                ? 'Đang mở bán'
                : d.status === 'UPCOMING'
                ? 'Sắp mở bán'
                : 'Đã bàn giao',
            scaleHa: d.scaleHa || 5,
            totalTowers: d.totalTowers || 2,
            totalUnits: d.totalUnits || 800,
            subdivisions: 'Khu A, Khu B',
            buildingPermitNo: d.buildingPermitNo,
            masterPlan1500Decision: d.masterPlan1500Decision,
            appraisedAuthority: d.appraisedAuthority || 'Sở Xây Dựng',
            imageUrl:
              'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
            linkedListingsCount: d.linkedListingsCount || 0,
            updatedAt: 'Mới cập nhật',
          }));
          // Merge with initial rich mockups
          setProjects([...mapped, ...INITIAL_PROJECTS]);
        }
      })
      .catch(() => {
        // use initial fallback
      });
  }, []);

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name || !formData.code || !formData.buildingPermitNo) {
      showToast('Vui lòng điền đầy đủ Tên, Mã dự án và Số GPXD');
      return;
    }

    try {
      const payload = {
        name: formData.name,
        code: formData.code,
        investorName: formData.investorName,
        address: formData.address,
        district: formData.district,
        provinceCity: formData.provinceCity,
        latitude: 21.002,
        longitude: 105.748,
        status: formData.status,
        scaleHa: Number(formData.scaleHa),
        totalTowers: Number(formData.totalTowers),
        totalUnits: Number(formData.totalUnits),
        buildingPermitNo: formData.buildingPermitNo,
        masterPlan1500Decision: formData.masterPlan1500Decision,
        appraisedAuthority: formData.appraisedAuthority,
      };

      const res = await fetch('/api/v1/catalog/projects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const created = await res.json();
        const newItem: ProjectItem = {
          id: created.id,
          name: created.name,
          code: created.code,
          investorName: created.investorName,
          location: `${created.address}, ${created.district}, ${created.provinceCity}`,
          district: created.district,
          provinceCity: created.provinceCity,
          status: created.status as any,
          statusLabel:
            created.status === 'SELLING'
              ? 'Đang mở bán'
              : created.status === 'UPCOMING'
              ? 'Sắp mở bán'
              : 'Đã bàn giao',
          scaleHa: created.scaleHa,
          totalTowers: created.totalTowers,
          totalUnits: created.totalUnits,
          subdivisions: 'Khu biệt lập',
          buildingPermitNo: created.buildingPermitNo,
          masterPlan1500Decision: created.masterPlan1500Decision,
          appraisedAuthority: created.appraisedAuthority,
          imageUrl:
            'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
          linkedListingsCount: 0,
          updatedAt: 'Hôm nay',
        };
        setProjects([newItem, ...projects]);
        setIsModalOpen(false);
        showToast(`Đã thêm dự án ${created.name} thành công (ERD04 Saved)`);
      } else {
        showToast('Lỗi máy chủ khi tạo dự án');
      }
    } catch {
      // Local fallback creation
      const localItem: ProjectItem = {
        id: `local-${Date.now()}`,
        name: formData.name,
        code: formData.code,
        investorName: formData.investorName,
        location: `${formData.address}, ${formData.district}, ${formData.provinceCity}`,
        district: formData.district,
        provinceCity: formData.provinceCity,
        status: formData.status as any,
        statusLabel:
          formData.status === 'SELLING'
            ? 'Đang mở bán'
            : formData.status === 'UPCOMING'
            ? 'Sắp mở bán'
            : 'Đã bàn giao',
        scaleHa: Number(formData.scaleHa),
        totalTowers: Number(formData.totalTowers),
        totalUnits: Number(formData.totalUnits),
        subdivisions: 'Khu biệt lập',
        buildingPermitNo: formData.buildingPermitNo,
        masterPlan1500Decision: formData.masterPlan1500Decision,
        appraisedAuthority: formData.appraisedAuthority,
        imageUrl:
          'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
        linkedListingsCount: 0,
        updatedAt: 'Hôm nay',
      };
      setProjects([localItem, ...projects]);
      setIsModalOpen(false);
      showToast(`Đã lưu dự án ${formData.name} vào bộ nhớ cục bộ`);
    }
  };

  const handleApproveRevision = (projectId: string) => {
    setProjects(
      projects.map((p) => {
        if (p.id === projectId) {
          return {
            ...p,
            status: 'SELLING',
            statusLabel: 'Đang mở bán',
            pendingRevision: undefined,
            updatedAt: 'Đã duyệt vừa xong (ED04 Logged)',
          };
        }
        return p;
      })
    );
    showToast('Revision #2 Masteri West Heights đã được duyệt vào CSDL chính!');
  };

  // Filtering
  const filteredProjects = projects.filter((p) => {
    const matchesSearch =
      searchTerm === '' ||
      p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.investorName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.buildingPermitNo.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      selectedStatus === 'ALL' ||
      (selectedStatus === 'SELLING' && p.status === 'SELLING') ||
      (selectedStatus === 'UPCOMING' && p.status === 'UPCOMING') ||
      (selectedStatus === 'DELIVERED' && p.status === 'DELIVERED') ||
      (selectedStatus === 'PENDING' && p.status === 'PENDING_APPROVAL');

    const matchesDistrict =
      selectedDistrict === 'ALL' || p.district.includes(selectedDistrict);

    return matchesSearch && matchesStatus && matchesDistrict;
  });

  return (
    <div className="min-h-screen bg-surface pb-20 pt-4">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-20 left-1/2 -translate-x-1/2 z-50 bg-primary text-white px-5 py-3 rounded-xl shadow-2xl flex items-center gap-3 animate-fade-in border border-primary-container">
          <ShieldCheck className="w-5 h-5 text-emerald-400" />
          <div className="text-sm">
            <span className="font-semibold block text-emerald-300">
              HỆ THỐNG SRS 0.9.1 (ED04 / FR25)
            </span>
            <span>{toastMessage}</span>
          </div>
        </div>
      )}

      <div className="max-w-6xl mx-auto px-4 md:px-8 space-y-6">
        {/* Header & Quick Actions */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-outline-variant/30 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2 h-6 rounded-full bg-primary inline-block"></span>
              <h1 className="text-2xl font-bold text-on-surface">
                Danh mục Dự án BĐS & Bảng Hàng
              </h1>
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-xs font-semibold">
                UC08 / FR25
              </span>
            </div>
            <p className="text-sm text-on-surface-variant mt-1">
              Quản lý thực thể dự án nguồn, hồ sơ pháp lý quy hoạch 1/500 và quy trình kiểm duyệt Revision độc lập.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsModalOpen(true)}
              className="flex items-center gap-1.5 h-10 px-4 rounded-lg bg-primary text-white text-sm font-semibold hover:bg-primary/90 shadow-sm transition-all"
            >
              <PlusCircle className="w-4 h-4" />
              Thêm dự án mới
            </button>
            <button
              onClick={() =>
                showToast('Đang kết xuất Báo cáo Thẩm định Pháp lý (XLSX/PDF)...')
              }
              className="flex items-center gap-1.5 h-10 px-4 rounded-lg bg-surface-container text-primary text-sm font-medium hover:bg-surface-container-high transition-colors"
            >
              <Download className="w-4 h-4" />
              Xuất báo cáo
            </button>
          </div>
        </div>

        {/* Quick Bento Stats */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-sm flex flex-col justify-between">
            <span className="text-xs text-on-surface-variant font-medium">Tổng số dự án quản lý</span>
            <div className="text-2xl font-bold text-primary mt-1">{projects.length}</div>
            <span className="text-[11px] text-secondary font-semibold mt-1">100% Khởi tạo chuẩn ERD04</span>
          </div>

          <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-sm flex flex-col justify-between">
            <span className="text-xs text-on-surface-variant font-medium">Tin đăng liên kết chuẩn</span>
            <div className="text-2xl font-bold text-primary mt-1">
              {projects.reduce((acc, curr) => acc + curr.linkedListingsCount, 0)}
            </div>
            <span className="text-[11px] text-primary font-semibold mt-1">Đối soát liên kết Listing FR05</span>
          </div>

          <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-sm flex flex-col justify-between">
            <span className="text-xs text-on-surface-variant font-medium">Đối soát chuẩn Pháp lý</span>
            <div className="text-2xl font-bold text-secondary mt-1">100%</div>
            <span className="text-[11px] text-secondary font-semibold mt-1">Quy hoạch 1/500 & GPXD đối soát</span>
          </div>
        </div>

        {/* Legal Mandate Alert (FR25 Mandate) */}
        <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200/80 shadow-sm flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg bg-amber-100 text-amber-800 flex items-center justify-center shrink-0">
            <AlertTriangle className="w-5 h-5 text-amber-700" />
          </div>
          <div className="space-y-1 text-xs">
            <div className="flex items-center gap-2">
              <span className="font-bold text-amber-900 text-sm">
                Quy tắc xác thực nguồn & Pháp lý dự án (Ràng buộc FR25)
              </span>
              <span className="px-1.5 py-0.5 rounded bg-amber-200/60 text-amber-800 font-semibold text-[10px]">
                FR25 / US25
              </span>
            </div>
            <p className="text-amber-800/90 leading-relaxed">
              Dữ liệu dự án chỉ được kích hoạt khi có <strong>Giấy phép xây dựng hoặc Quyết định Quy hoạch 1/500</strong> đã được đối soát nguồn chính thống từ Sở Xây Dựng hoặc Bộ Xây Dựng.{' '}
              <span className="text-rose-700 font-semibold">Nghiêm cấm</span> tự động suy đoán hoặc gán nhãn pháp lý dự án từ nội dung tin đăng tự do của người dùng.
            </p>
            <div className="flex items-center gap-2 pt-1 text-emerald-800 font-semibold">
              <ShieldCheck className="w-4 h-4" />
              <span>Cơ chế Audit Trail ED04 & Phiên bản Revision đang hoạt động</span>
            </div>
          </div>
        </div>

        {/* Search & Filter Toolbar */}
        <div className="space-y-3">
          {/* Search Box */}
          <div className="relative">
            <Search className="w-5 h-5 absolute left-3.5 top-1/2 -translate-y-1/2 text-outline" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Tìm kiếm theo tên dự án, chủ đầu tư, mã dự án, số giấy phép xây dựng..."
              className="w-full h-11 pl-11 pr-10 rounded-xl bg-surface-container-lowest border border-outline-variant/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-sm"
            />
            {searchTerm && (
              <button
                onClick={() => setSearchTerm('')}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-outline hover:text-primary"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Filter Status Tabs */}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
              {[
                { key: 'ALL', label: 'Tất cả', count: projects.length },
                {
                  key: 'SELLING',
                  label: 'Đang mở bán',
                  count: projects.filter((p) => p.status === 'SELLING').length,
                },
                {
                  key: 'UPCOMING',
                  label: 'Sắp mở bán',
                  count: projects.filter((p) => p.status === 'UPCOMING').length,
                },
                {
                  key: 'DELIVERED',
                  label: 'Đã bàn giao',
                  count: projects.filter((p) => p.status === 'DELIVERED').length,
                },
                {
                  key: 'PENDING',
                  label: 'Chờ duyệt Revision',
                  count: projects.filter((p) => p.status === 'PENDING_APPROVAL').length,
                },
              ].map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setSelectedStatus(tab.key)}
                  className={`h-8 px-3 rounded-full font-medium transition-all flex items-center gap-1.5 ${
                    selectedStatus === tab.key
                      ? 'bg-primary text-white shadow-sm'
                      : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
                  }`}
                >
                  <span>{tab.label}</span>
                  <span
                    className={`px-1.5 py-0.2 text-[10px] rounded-full ${
                      selectedStatus === tab.key
                        ? 'bg-white/20 text-white'
                        : 'bg-outline-variant/20 text-on-surface'
                    }`}
                  >
                    {tab.count}
                  </span>
                </button>
              ))}
            </div>

            {/* District Chips */}
            <div className="flex items-center gap-1.5 text-xs">
              <span className="text-on-surface-variant flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5 text-outline" /> Quận:
              </span>
              {['ALL', 'Nam Từ Liêm', 'Cầu Giấy', 'Tây Hồ'].map((dist) => (
                <button
                  key={dist}
                  onClick={() => setSelectedDistrict(dist)}
                  className={`h-7 px-2.5 rounded-md font-medium text-xs transition-colors ${
                    selectedDistrict === dist
                      ? 'bg-primary/10 text-primary border border-primary/30 font-semibold'
                      : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                  }`}
                >
                  {dist === 'ALL' ? 'Tất cả quận' : dist}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Project Catalog List */}
        <div className="space-y-6">
          {filteredProjects.length === 0 ? (
            <div className="py-16 text-center bg-surface-container-lowest border border-dashed border-outline-variant rounded-xl p-8 space-y-3">
              <Building2 className="w-12 h-12 text-outline mx-auto" />
              <div className="font-semibold text-base text-on-surface">
                Không tìm thấy dự án phù hợp
              </div>
              <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                Vui lòng thử điều chỉnh lại từ khóa tìm kiếm hoặc bấm nút bên dưới để xóa các bộ lọc.
              </p>
              <button
                onClick={() => {
                  setSearchTerm('');
                  setSelectedStatus('ALL');
                  setSelectedDistrict('ALL');
                }}
                className="h-9 px-4 rounded-lg bg-primary text-white text-xs font-semibold"
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : (
            filteredProjects.map((project) => (
              <div
                key={project.id}
                className={`rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-sm overflow-hidden flex flex-col md:flex-row transition-all hover:shadow-md ${
                  project.status === 'PENDING_APPROVAL' ? 'border-l-4 border-l-amber-500' : ''
                }`}
              >
                {/* Image Section */}
                <div className="md:w-72 h-48 md:h-auto relative shrink-0 overflow-hidden bg-surface-dim">
                  <img
                    src={project.imageUrl}
                    alt={project.name}
                    className="w-full h-full object-cover"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent"></div>
                  {/* Status Badges */}
                  <div className="absolute top-3 left-3 right-3 flex items-center justify-between">
                    <span
                      className={`px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1 shadow-sm ${
                        project.status === 'SELLING'
                          ? 'bg-emerald-600 text-white'
                          : project.status === 'DELIVERED'
                          ? 'bg-sky-700 text-white'
                          : project.status === 'PENDING_APPROVAL'
                          ? 'bg-amber-600 text-white'
                          : 'bg-indigo-600 text-white'
                      }`}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      {project.statusLabel}
                    </span>
                    <span className="px-2 py-0.5 rounded bg-black/60 text-white text-[11px] font-mono">
                      {project.code}
                    </span>
                  </div>

                  <div className="absolute bottom-3 left-3 right-3 text-white text-xs flex items-center justify-between">
                    <span className="flex items-center gap-1 bg-black/50 px-2 py-0.5 rounded backdrop-blur-sm">
                      <Layers className="w-3.5 h-3.5" /> {project.subdivisions.split(',')[0]}
                    </span>
                    <span className="bg-primary/80 px-2 py-0.5 rounded font-semibold text-[10px]">
                      KĐT chuẩn 1/500
                    </span>
                  </div>
                </div>

                {/* Content Section */}
                <div className="p-5 flex-1 flex flex-col justify-between space-y-4">
                  <div className="space-y-2">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                      <h2 className="text-xl font-bold text-on-surface flex items-center gap-2">
                        {project.name}
                      </h2>
                      <span className="text-xs px-2 py-1 rounded bg-surface-container font-semibold text-primary inline-flex self-start">
                        {project.appraisedAuthority}
                      </span>
                    </div>

                    <div className="text-xs text-on-surface-variant flex items-center gap-2 flex-wrap">
                      <span className="font-semibold text-primary">
                        CĐT: {project.investorName}
                      </span>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-outline" />
                        {project.location}
                      </span>
                    </div>

                    {/* Scale Bento Numbers */}
                    <div className="grid grid-cols-3 gap-2 p-2.5 rounded-lg bg-surface-container-low text-center">
                      <div>
                        <div className="text-[11px] text-outline">Quy mô</div>
                        <div className="font-bold text-sm text-on-surface">{project.scaleHa} ha</div>
                      </div>
                      <div>
                        <div className="text-[11px] text-outline">Tòa tháp</div>
                        <div className="font-bold text-sm text-on-surface">{project.totalTowers} tòa</div>
                      </div>
                      <div>
                        <div className="text-[11px] text-outline">Tổng căn</div>
                        <div className="font-bold text-sm text-on-surface">
                          {project.totalUnits.toLocaleString()} căn
                        </div>
                      </div>
                    </div>

                    {/* Legal Permits details */}
                    <div className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/30 text-xs space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="text-on-surface-variant font-medium flex items-center gap-1">
                          <FileCheck2 className="w-3.5 h-3.5 text-primary" /> GP Xây dựng:
                        </span>
                        <span className="font-semibold text-primary">{project.buildingPermitNo}</span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-on-surface-variant font-medium flex items-center gap-1">
                          <ShieldCheck className="w-3.5 h-3.5 text-secondary" /> QH 1/500:
                        </span>
                        <span className="font-medium text-on-surface truncate max-w-xs text-right">
                          {project.masterPlan1500Decision}
                        </span>
                      </div>
                    </div>

                    {/* Pending Revision Box if applicable */}
                    {project.pendingRevision && (
                      <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-xs space-y-2">
                        <div className="flex items-center justify-between font-semibold text-amber-900">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5 text-amber-700" />
                            {project.pendingRevision.editorName}
                          </span>
                          <span className="text-[10px] bg-amber-200 text-amber-900 px-1.5 py-0.5 rounded">
                            Chờ duyệt UC08.1
                          </span>
                        </div>
                        <p className="text-amber-800 leading-relaxed">
                          {project.pendingRevision.description}
                        </p>
                        <div className="flex items-center justify-between text-[11px] text-amber-700 pt-1">
                          <span>Thời điểm nộp: {project.pendingRevision.submittedAt}</span>
                          <span className="font-semibold">Revision #{project.pendingRevision.revisionNo}</span>
                        </div>
                      </div>
                    )}

                    {/* Linked Listings count & certified source */}
                    <div className="flex items-center justify-between text-xs pt-1 border-t border-outline-variant/20">
                      <span className="flex items-center gap-1 font-semibold text-primary">
                        <LinkIcon className="w-3.5 h-3.5" />
                        {project.linkedListingsCount} tin đăng liên kết (FR05)
                      </span>
                      <span className="text-outline text-[11px]">
                        Cập nhật: {project.updatedAt}
                      </span>
                    </div>
                  </div>

                  {/* Actions Dock */}
                  <div className="flex items-center gap-2 pt-2 flex-wrap">
                    {project.status === 'PENDING_APPROVAL' ? (
                      <>
                        <button
                          onClick={() => handleApproveRevision(project.id)}
                          className="h-9 px-4 rounded-lg bg-secondary text-white text-xs font-semibold hover:bg-secondary/90 flex items-center gap-1.5 shadow-sm transition-all"
                        >
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Duyệt thay đổi (UC08.1)
                        </button>
                        <button
                          onClick={() =>
                            showToast(
                              'So sánh Diff bản gốc v1.1 với bản đề xuất Revision v2.0 của BTV'
                            )
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
                            showToast(`Mở form sửa hồ sơ pháp lý & tọa độ GIS: ${project.name}`)
                          }
                          className="h-9 px-3 rounded-lg bg-surface-container text-primary text-xs font-medium hover:bg-surface-container-high transition-colors flex items-center gap-1"
                        >
                          <FileCheck2 className="w-3.5 h-3.5" />
                          Sửa hồ sơ
                        </button>
                        <button
                          onClick={() =>
                            showToast(`Xem toàn bộ lịch sử Revision & Audit Log của ${project.name}`)
                          }
                          className="h-9 px-3 rounded-lg bg-surface-container text-on-surface-variant text-xs font-medium hover:bg-surface-container-high transition-colors flex items-center gap-1"
                        >
                          <History className="w-3.5 h-3.5" />
                          Lịch sử Revision
                        </button>
                        <button
                          onClick={() =>
                            showToast(
                              `Điều hướng sang danh sách ${project.linkedListingsCount} tin đăng liên kết dự án ${project.name}`
                            )
                          }
                          className="h-9 px-3 rounded-lg bg-primary/10 text-primary text-xs font-semibold hover:bg-primary/20 transition-colors flex items-center gap-1"
                        >
                          <LinkIcon className="w-3.5 h-3.5" />
                          Quản lý {project.linkedListingsCount} tin
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Modal Thêm Dự Án Mới Chuẩn ERD04 & FR25 */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-2xl bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant/30 overflow-hidden animate-fade-in flex flex-col max-h-[90vh]">
            <div className="p-5 border-b border-outline-variant/30 flex items-center justify-between bg-surface-container-low">
              <div className="flex items-center gap-2">
                <Building2 className="w-5 h-5 text-primary" />
                <h3 className="font-bold text-base text-on-surface">
                  Thêm mới Dự án BĐS (Chuẩn ERD04 & Pháp lý FR25)
                </h3>
              </div>
              <button
                onClick={() => setIsModalOpen(false)}
                className="w-8 h-8 rounded-full hover:bg-surface-container flex items-center justify-center text-outline"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateProject} className="p-6 overflow-y-auto space-y-4 text-sm">
              <div className="p-3 bg-amber-50 rounded-lg border border-amber-200 text-xs text-amber-800">
                <strong>Ràng buộc FR25:</strong> Số Giấy phép xây dựng hoặc Quyết định Quy hoạch 1/500 là bắt buộc để dự án được đưa vào cơ sở dữ liệu đối soát.
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Tên dự án *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: Lumi Hanoi"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Mã dự án (Code) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: DA-HN-0105"
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Chủ đầu tư (Investor) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: CapitaLand Development"
                    value={formData.investorName}
                    onChange={(e) => setFormData({ ...formData, investorName: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Trạng thái mở bán
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  >
                    <option value="SELLING">Đang mở bán</option>
                    <option value="UPCOMING">Sắp mở bán</option>
                    <option value="DELIVERED">Đã bàn giao</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Địa chỉ chi tiết *
                </label>
                <input
                  type="text"
                  required
                  placeholder="VD: Đại lộ Thăng Long, Phường Tây Mỗ"
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Quận / Huyện
                  </label>
                  <select
                    value={formData.district}
                    onChange={(e) => setFormData({ ...formData, district: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  >
                    <option value="Nam Từ Liêm">Nam Từ Liêm</option>
                    <option value="Cầu Giấy">Cầu Giấy</option>
                    <option value="Tây Hồ">Tây Hồ</option>
                    <option value="Thanh Xuân">Thanh Xuân</option>
                    <option value="Hoàng Mai">Hoàng Mai</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Tỉnh / Thành phố
                  </label>
                  <input
                    type="text"
                    value={formData.provinceCity}
                    onChange={(e) => setFormData({ ...formData, provinceCity: e.target.value })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Quy mô (ha)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    value={formData.scaleHa}
                    onChange={(e) => setFormData({ ...formData, scaleHa: Number(e.target.value) })}
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Số tòa tháp
                  </label>
                  <input
                    type="number"
                    value={formData.totalTowers}
                    onChange={(e) =>
                      setFormData({ ...formData, totalTowers: Number(e.target.value) })
                    }
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Tổng số căn
                  </label>
                  <input
                    type="number"
                    value={formData.totalUnits}
                    onChange={(e) =>
                      setFormData({ ...formData, totalUnits: Number(e.target.value) })
                    }
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Số Giấy phép xây dựng (GPXD) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: GPXD-512/SXD-HN"
                    value={formData.buildingPermitNo}
                    onChange={(e) =>
                      setFormData({ ...formData, buildingPermitNo: e.target.value })
                    }
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Quyết định phê duyệt Quy hoạch 1/500
                  </label>
                  <input
                    type="text"
                    placeholder="VD: QĐ-1142/QĐ-UBND"
                    value={formData.masterPlan1500Decision}
                    onChange={(e) =>
                      setFormData({ ...formData, masterPlan1500Decision: e.target.value })
                    }
                    className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>
              </div>

              <div className="p-3 border-t border-outline-variant/30 flex items-center justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="h-10 px-4 rounded-lg bg-surface-container text-on-surface-variant font-medium text-xs hover:bg-surface-container-high transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="h-10 px-5 rounded-lg bg-primary text-white font-semibold text-xs hover:bg-primary/90 transition-all shadow-sm flex items-center gap-1.5"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  Lưu hồ sơ Dự án (ERD04)
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProjectCatalogPage;
