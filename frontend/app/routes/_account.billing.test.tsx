import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { BillingOrder } from '@/entities/admin/model/types';
import { ApiProblemException } from '@/shared/types/problem-details';

const api = vi.hoisted(() => ({
  apiClient: vi.fn(),
  myOrders: vi.fn(),
  cancel: vi.fn(),
  report: vi.fn(),
}));

vi.mock('@/shared/api/client', () => ({ apiClient: api.apiClient }));
vi.mock('@/entities/admin/api/adminApi', () => ({
  billingApi: {
    myOrders: api.myOrders,
    cancel: api.cancel,
    report: api.report,
    createOrder: vi.fn(),
    myOrder: vi.fn(),
  },
  newIdempotencyKey: () => 'key',
}));

import { BillingPage } from './_account.billing';

const order = (status: BillingOrder['status']): BillingOrder =>
  ({
    id: 'order-1',
    planCode: 'STANDARD',
    planName: 'Tiêu chuẩn',
    amountVnd: 199000,
    reference: 'BDS123',
    status,
    createdAt: '2026-10-01T00:00:00Z',
    qrUrl: null,
    accountNumberSnapshot: '0123456789',
    accountNameSnapshot: 'CONG TY',
    reviewNote: null,
  }) as unknown as BillingOrder;

describe('account billing: an action that lost a race', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.apiClient.mockResolvedValue([]);
  });

  it('reloads the orders and explains the new state in words after a 409, without a raw status code', async () => {
    api.myOrders
      .mockResolvedValueOnce({ items: [order('CREATED')], total: 1, page: 0, size: 10 })
      .mockResolvedValue({ items: [order('APPROVED')], total: 1, page: 0, size: 10 });
    api.cancel.mockRejectedValue(
      new ApiProblemException({
        status: 409,
        code: 'ORDER_STATE_CHANGED',
        title: 'Conflict',
        detail: 'Đơn đang ở trạng thái APPROVED, không thể thực hiện thao tác này.',
      }),
    );

    render(<BillingPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Hủy yêu cầu' }));

    expect(
      await screen.findByText(
        'Yêu cầu vừa được xử lý nên thao tác chưa được thực hiện. Trạng thái hiện tại: Đã kích hoạt.',
      ),
    ).toBeTruthy();
    expect(api.myOrders).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(/APPROVED|409/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hủy yêu cầu' })).toBeNull();
  });
});
