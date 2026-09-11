import type { ListingReport, LeadItem, FunnelAnalytics, LeadStatus, ReportSeverity, ReportStatus } from '../model/types';

const API_BASE = '/api/v1';

export async function fetchReports(status?: ReportStatus, severity?: ReportSeverity): Promise<ListingReport[]> {
  try {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (severity) params.append('severity', severity);
    const url = `${API_BASE}/reports${params.toString() ? '?' + params.toString() : ''}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Không thể tải danh sách báo xấu');
    return await res.json();
  } catch (err) {
    console.warn('API error, using mock reports:', err);
    return getMockReports();
  }
}

export async function emergencyHideListing(reportId: string, reason?: string): Promise<ListingReport> {
  const res = await fetch(`${API_BASE}/reports/${reportId}/emergency-hide`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason: reason || 'Khẩn cấp: Tạm ẩn tin ngăn chặn lừa cọc' }),
  });
  if (!res.ok) throw new Error('Thao tác tạm ẩn thất bại');
  return await res.json();
}

export async function resolveReport(reportId: string, note: string, permanentlyLock: boolean): Promise<ListingReport> {
  const res = await fetch(`${API_BASE}/reports/${reportId}/resolve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resolutionNote: note, permanentlyLockListing: permanentlyLock }),
  });
  if (!res.ok) throw new Error('Thao tác xử lý vi phạm thất bại');
  return await res.json();
}

export async function dismissReport(reportId: string, note: string, resumeListing: boolean): Promise<ListingReport> {
  const res = await fetch(`${API_BASE}/reports/${reportId}/dismiss`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dismissNote: note, resumeListing }),
  });
  if (!res.ok) throw new Error('Thao tác bác bỏ thất bại');
  return await res.json();
}

export async function fetchLeads(listingId?: string, brokerId?: string): Promise<LeadItem[]> {
  try {
    const params = new URLSearchParams();
    if (listingId) params.append('listingId', listingId);
    if (brokerId) params.append('brokerId', brokerId);
    const url = `${API_BASE}/leads${params.toString() ? '?' + params.toString() : ''}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Không thể tải danh sách Lead');
    return await res.json();
  } catch (err) {
    console.warn('API error, using mock leads:', err);
    return getMockLeads();
  }
}

export async function updateLeadStatus(leadId: string, status: LeadStatus): Promise<LeadItem> {
  const res = await fetch(`${API_BASE}/leads/${leadId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
  if (!res.ok) throw new Error('Không thể cập nhật trạng thái Lead');
  return await res.json();
}

export async function fetchFunnelAnalytics(): Promise<FunnelAnalytics> {
  try {
    const res = await fetch(`${API_BASE}/analytics/funnel`);
    if (!res.ok) throw new Error('Không thể tải số liệu phễu chuyển đổi');
    return await res.json();
  } catch (err) {
    console.warn('API error, using default funnel:', err);
    return {
      impressions: 142500,
      detailViews: 84320,
      leadsSubmitted: 1410,
      contactedCount: 1298,
      dealsClosed: 82,
      conversionRatePercent: 5.71,
      steps: [
        { stepIndex: 1, stepName: 'Xem Danh Sách & Bản Đồ Mini-GIS', count: 142500, percentage: 100 },
        { stepIndex: 2, stepName: 'Xem Chi Tiết Tin Đăng (PDP)', count: 84320, percentage: 59.2 },
        { stepIndex: 3, stepName: 'Bấm Nút Gọi / Chat Zalo', count: 3410, percentage: 4.04 },
        { stepIndex: 4, stepName: 'Gửi Lead Xác Thực OTP', count: 1410, percentage: 1.67 },
        { stepIndex: 5, stepName: 'Môi Giới Tiếp Nhận < 24h (BR03)', count: 1298, percentage: 92.1 },
      ],
    };
  }
}

function getMockReports(): ListingReport[] {
  return [
    {
      id: 'rep-001',
      listingId: 'lst-8802',
      caseNumber: 'CASE-8802',
      reporterType: 'ANONYMOUS',
      reporterPhone: '098****211',
      category: 'SCAM_DEPOSIT',
      severity: 'P0_EMERGENCY',
      status: 'PENDING',
      description: 'Môi giới yêu cầu chuyển cọc giữ chỗ 50 triệu trước khi dẫn xem nhà với lý do đang có 3 người khác cùng tranh mua. Sau khi tra cứu số tài khoản thụ hưởng thì bị ngắt liên lạc và khóa Zalo ngay lập tức.',
      evidenceUrls: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00;https://images.unsplash.com/photo-1600585154340-be6161a56a0c',
      createdAt: new Date(Date.now() - 18 * 60 * 1000).toISOString(),
    },
    {
      id: 'rep-002',
      listingId: 'lst-8795',
      caseNumber: 'CASE-8795',
      reporterType: 'VERIFIED_BUYER',
      reporterPhone: '091****889',
      category: 'FAKE_SOLD',
      severity: 'MEDIUM',
      status: 'WAITING_REPLY',
      description: 'Tôi gọi điện đến thì môi giới báo căn này đã bán từ tuần trước rồi và gạ dẫn sang xem căn khác ở Hoàng Cầu với giá đắt hơn 1.2 tỷ.',
      createdAt: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
    },
    {
      id: 'rep-003',
      listingId: 'lst-8740',
      caseNumber: 'CASE-8740',
      reporterType: 'ANONYMOUS',
      category: 'OTHER',
      severity: 'LOW',
      status: 'APPEALED',
      description: 'Tin đăng bị báo mạo danh chủ nhà nhưng môi giới đã gửi hợp đồng ủy quyền và trích lục sổ hồng.',
      evidenceUrls: 'https://cdn.example.com/hop_dong_uy_quyen.pdf',
      createdAt: new Date(Date.now() - 6 * 3600 * 1000).toISOString(),
    },
  ];
}

function getMockLeads(): LeadItem[] {
  return [
    {
      id: 'lead-001',
      listingId: 'lst-001',
      fullName: 'Hoàng Minh Quân',
      maskedPhone: '098****678',
      note: 'Quan tâm căn 3PN view hồ Tây, cần tư vấn thủ tục vay ngân hàng 70%',
      consentPolicy: true,
      status: 'NEW',
      createdAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
    },
    {
      id: 'lead-002',
      listingId: 'lst-002',
      fullName: 'Trần Thị Thu Hà',
      maskedPhone: '091****334',
      note: 'Muốn xem nhà vào 9h sáng Chủ nhật này, dẫn theo người nhà xem hướng phong thủy',
      consentPolicy: true,
      status: 'CONTACTED',
      createdAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
    },
    {
      id: 'lead-003',
      listingId: 'lst-003',
      fullName: 'Vũ Đức Long',
      maskedPhone: '097****912',
      note: 'Đã cọc thiện chí, hẹn ký hợp đồng mua bán sang tuần',
      consentPolicy: true,
      status: 'APPOINTED',
      createdAt: new Date(Date.now() - 24 * 3600 * 1000).toISOString(),
    },
    {
      id: 'lead-004',
      listingId: 'lst-004',
      fullName: 'Nguyễn Bích Ngọc',
      maskedPhone: '093****556',
      note: 'Giao dịch thành công, đã bàn giao nhà',
      consentPolicy: true,
      status: 'CLOSED',
      createdAt: new Date(Date.now() - 48 * 3600 * 1000).toISOString(),
    },
  ];
}
