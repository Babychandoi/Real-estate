export type DepositStatus =
  | 'DRAFT'
  | 'AWAITING_SELLER_SIGN'
  | 'ESCROW_LOCKED'
  | 'COMPLETED'
  | 'REFUNDED'
  | 'DISPUTED';

export type EscrowAction = 'DEPOSIT' | 'LOCK' | 'RELEASE' | 'REFUND' | 'DISPUTE';

export interface EscrowTransaction {
  id: string;
  action: EscrowAction;
  amount: number;
  performedBy: string;
  note: string;
  createdAt: string;
}

export interface DepositContract {
  id: string;
  listingId: string;
  buyerId: string;
  buyerName: string;
  buyerPhone: string;
  buyerIdMasked: string;
  sellerId: string;
  sellerName: string;
  sellerPhone: string;
  depositAmount: number;
  listingPrice: number;
  status: DepositStatus;
  termsConditions: string;
  buyerSignedAt?: string;
  buyerOtpVerified: boolean;
  sellerSignedAt?: string;
  sellerOtpVerified: boolean;
  escrowLockedAt?: string;
  completedAt?: string;
  disputeReason?: string;
  createdAt: string;
  updatedAt: string;
  escrowTransactions?: EscrowTransaction[];
}

export interface CreateDepositRequest {
  listingId: string;
  buyerName: string;
  buyerPhone: string;
  buyerIdNumber: string;
  depositAmount: number;
  termsConditions?: string;
}

export interface SignDepositRequest {
  otpCode: string;
}
