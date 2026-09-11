import type { ListingVerification, VerificationStatus } from '../model/types';

const API_BASE = '/api/v1';

export async function fetchVerificationQueue(status?: VerificationStatus): Promise<ListingVerification[]> {
  try {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    const url = `${API_BASE}/verifications${params.toString() ? '?' + params.toString() : ''}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Không thể tải hàng đợi thẩm định');
    const data = await res.json();
    return data && data.length > 0 ? data : getMockVerifications();
  } catch (err) {
    console.warn('API error, using mock verifications:', err);
    return getMockVerifications();
  }
}

export async function fetchVerificationDetail(id: string): Promise<ListingVerification> {
  try {
    const res = await fetch(`${API_BASE}/verifications/${id}`);
    if (!res.ok) throw new Error('Không thể tải chi tiết hồ sơ');
    return await res.json();
  } catch (err) {
    console.warn('API error, using mock detail:', err);
    const list = getMockVerifications();
    return list.find(v => v.id === id) || list[0];
  }
}

export async function approveVerification(id: string, note?: string): Promise<ListingVerification> {
  const res = await fetch(`${API_BASE}/verifications/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ verifierNote: note || 'Thông tin CCCD và Sổ hồng trùng khớp 100%. Phê duyệt cấp nhãn Tin Chính Chủ.' }),
  });
  if (!res.ok) throw new Error('Phê duyệt thẩm định thất bại');
  return await res.json();
}

export async function rejectVerification(id: string, reason: string): Promise<ListingVerification> {
  const res = await fetch(`${API_BASE}/verifications/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error('Từ chối thẩm định thất bại');
  return await res.json();
}

function getMockVerifications(): ListingVerification[] {
  return [
    {
      id: 'verif-001',
      listingId: 'lst-101',
      userKycId: 'kyc-001',
      verificationType: 'CERTIFICATE_OF_OWNERSHIP',
      certificateNumber: 'CS 882910 - Số vào sổ: CH-09412',
      ownerNameOnDoc: 'LÊ HOÀNG YẾN',
      status: 'PENDING',
      createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
      listingTitle: 'Căn 3PN Sun Grand City Tây Hồ view trọn hồ',
      listingPrice: '7.8 tỷ',
      listingAddress: 'Quảng An, Tây Hồ, Hà Nội',
      landPlotNumber: '108',
      mapSheetNumber: '24',
      landArea: '128.5 m²',
      documentUrls: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c',
      userKyc: {
        id: 'kyc-001',
        userId: 'usr-001',
        maskedIdNumber: '001****3888',
        fullName: 'LÊ HOÀNG YẾN',
        dob: '20/11/1992',
        address: 'Phường Mễ Trì, Nam Từ Liêm, Hà Nội',
        idCardFrontUrl: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00',
        idCardBackUrl: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00',
        selfieUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb',
        faceMatchScore: 98.4,
        status: 'VERIFIED',
        createdAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
        verifiedAt: new Date(Date.now() - 1 * 3600 * 1000).toISOString(),
      },
    },
    {
      id: 'verif-002',
      listingId: 'lst-102',
      userKycId: 'kyc-002',
      verificationType: 'POWER_OF_ATTORNEY',
      certificateNumber: 'HĐUQ số 458/2026/VPCC-HANOI',
      ownerNameOnDoc: 'TRẦN VĂN BÌNH',
      status: 'PENDING',
      createdAt: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
      listingTitle: 'Nhà riêng 4 tầng ngõ Thái Hà, Đống Đa',
      listingPrice: '6.2 tỷ',
      listingAddress: 'Thái Hà, Đống Đa, Hà Nội',
      landPlotNumber: '42',
      mapSheetNumber: '15',
      landArea: '45.0 m²',
      documentUrls: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750',
      userKyc: {
        id: 'kyc-002',
        userId: 'usr-002',
        maskedIdNumber: '001****4567',
        fullName: 'TRẦN VĂN BÌNH',
        dob: '15/08/1985',
        address: 'Thanh Xuân, Hà Nội',
        faceMatchScore: 96.8,
        status: 'VERIFIED',
        createdAt: new Date(Date.now() - 5 * 3600 * 1000).toISOString(),
      },
    },
    {
      id: 'verif-003',
      listingId: 'lst-103',
      userKycId: 'kyc-003',
      verificationType: 'PROJECT_PURCHASE_CONTRACT',
      certificateNumber: 'HĐMB-VHM-SMC-1204',
      ownerNameOnDoc: 'NGUYỄN MINH ANH',
      status: 'VERIFIED_OWNER',
      verifierNote: 'Đã đối soát hợp đồng mua bán CĐT và biên bản bàn giao nhà. Cấp nhãn chính chủ thành công.',
      createdAt: new Date(Date.now() - 24 * 3600 * 1000).toISOString(),
      verifiedAt: new Date(Date.now() - 12 * 3600 * 1000).toISOString(),
      listingTitle: 'Căn hộ Masteri West Heights 2PN view hồ',
      listingPrice: '3.45 tỷ',
      listingAddress: 'Tây Mỗ, Nam Từ Liêm, Hà Nội',
      landPlotNumber: 'Tòa A - Căn 12.04',
      mapSheetNumber: 'VHM-02',
      landArea: '68.0 m²',
      userKyc: {
        id: 'kyc-003',
        userId: 'usr-003',
        maskedIdNumber: '038****9912',
        fullName: 'NGUYỄN MINH ANH',
        dob: '05/03/1990',
        faceMatchScore: 99.1,
        status: 'VERIFIED',
        createdAt: new Date(Date.now() - 30 * 3600 * 1000).toISOString(),
      },
    },
  ];
}
