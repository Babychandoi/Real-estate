import { apiClient } from '@/shared/api/client';
import { DepositContract, CreateDepositRequest, SignDepositRequest } from '../model/types';

export const transactionApi = {
  createDepositContract: (payload: CreateDepositRequest) => {
    return apiClient<DepositContract>('/transactions/deposits', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  signBuyer: (contractId: string, payload: SignDepositRequest) => {
    return apiClient<DepositContract>(`/transactions/deposits/${contractId}/sign-buyer`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  signSeller: (contractId: string, payload: SignDepositRequest) => {
    return apiClient<DepositContract>(`/transactions/deposits/${contractId}/sign-seller`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  releaseEscrow: (contractId: string) => {
    return apiClient<DepositContract>(`/transactions/deposits/${contractId}/release`, {
      method: 'POST',
      body: JSON.stringify({}),
    });
  },

  refundEscrow: (contractId: string, reason: string) => {
    return apiClient<DepositContract>(`/transactions/deposits/${contractId}/refund`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  },

  getContract: (contractId: string) => {
    return apiClient<DepositContract>(`/transactions/deposits/${contractId}`);
  },

  getContractsByListing: (listingId: string) => {
    return apiClient<DepositContract[]>(`/transactions/deposits/listing/${listingId}`);
  },
};
