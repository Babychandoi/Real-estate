export type KycStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface UserKycProfile {
  id: string;
  userId: string;
  maskedIdNumber: string;
  fullName: string;
  dob?: string;
  address?: string;
  idCardFrontUrl?: string;
  idCardBackUrl?: string;
  selfieUrl?: string;
  faceMatchScore?: number;
  status: KycStatus;
  rejectionReason?: string;
  createdAt: string;
  verifiedAt?: string;
}

export type VerificationType = 'CERTIFICATE_OF_OWNERSHIP' | 'POWER_OF_ATTORNEY' | 'PROJECT_PURCHASE_CONTRACT';

export type VerificationStatus = 'PENDING' | 'VERIFIED_OWNER' | 'REJECTED' | 'REVOKED';

export interface ListingVerification {
  id: string;
  listingId: string;
  userKycId?: string;
  verificationType: VerificationType;
  certificateNumber?: string;
  documentUrls?: string;
  ownerNameOnDoc: string;
  status: VerificationStatus;
  verifierNote?: string;
  createdAt: string;
  verifiedAt?: string;
  userKyc?: UserKycProfile;
  // Dữ liệu mở rộng mô tả tin đăng để hiển thị đối soát
  listingTitle?: string;
  listingPrice?: string;
  listingAddress?: string;
  landPlotNumber?: string;
  mapSheetNumber?: string;
  landArea?: string;
}
