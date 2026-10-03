import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/shared/api/client';
import { ApiProblemException } from '@/shared/types/problem-details';
import { LeadConsultationModal } from './LeadConsultationModal';

vi.mock('@/shared/api/client', () => ({ apiClient: vi.fn() }));
vi.mock('@/shared/analytics/track', () => ({ track: vi.fn() }));

const listing = { id: 'listing-1', title: 'Căn hộ thử', priceVnd: 2_000_000_000, areaM2: 60, address: 'Hà Nội' };

function openForm() {
  render(
    <MemoryRouter>
      <LeadConsultationModal isOpen onClose={vi.fn()} listing={listing} />
    </MemoryRouter>,
  );
  fireEvent.change(screen.getByRole('textbox', { name: /Họ và tên/ }), { target: { value: 'Nguyễn An' } });
  fireEvent.change(screen.getByRole('textbox', { name: /Số điện thoại/ }), { target: { value: '0912345678' } });
  fireEvent.change(screen.getByRole('textbox', { name: /Thời gian hoặc lời nhắn/ }), {
    target: { value: 'Chiều thứ bảy' },
  });
  fireEvent.click(screen.getByRole('checkbox', { name: /Tôi đồng ý/ }));
}

beforeEach(() => {
  vi.mocked(apiClient).mockReset();
});

describe('lead form server outcomes (DS-08)', () => {
  it('retries a lost response with the same key and payload, then shows the replayed request code', async () => {
    let reject!: (error: Error) => void;
    vi.mocked(apiClient)
      .mockImplementationOnce(
        () =>
          new Promise((_, fail) => {
            reject = fail;
          }),
      )
      .mockResolvedValueOnce({
        requestCode: 'YC-ORIGINAL',
        status: 'NEW',
        createdAt: '2026-10-03T10:00:00Z',
        replayed: true,
      });
    openForm();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
    expect(screen.getByRole('button', { name: 'Đang gửi…' })).toBeDisabled();
    await act(async () => reject(new Error('response lost after commit')));
    expect(await screen.findByRole('alert')).toHaveTextContent('Dữ liệu vẫn được giữ');
    expect(screen.getByRole('textbox', { name: /Họ và tên/ })).toHaveValue('Nguyễn An');
    expect(screen.getByRole('textbox', { name: /Số điện thoại/ })).toHaveValue('0912345678');
    expect(screen.getByRole('textbox', { name: /Thời gian hoặc lời nhắn/ })).toHaveValue('Chiều thứ bảy');
    expect(screen.getByRole('checkbox', { name: /Tôi đồng ý/ })).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
    expect(await screen.findByText('YC-ORIGINAL')).toBeInTheDocument();
    expect(screen.getByText('Yêu cầu đã được ghi nhận')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gửi yêu cầu' })).not.toBeInTheDocument();
    const [first, retry] = vi.mocked(apiClient).mock.calls;
    expect(first[0]).toBe('/public/leads');
    expect(retry[0]).toBe(first[0]);
    expect(new Headers(retry[1]?.headers).get('Idempotency-Key')).toBe(
      new Headers(first[1]?.headers).get('Idempotency-Key'),
    );
    expect(new Headers(first[1]?.headers).get('Idempotency-Key')).toBeTruthy();
    expect(retry[1]?.body).toBe(first[1]?.body);
  });

  it('announces an unavailable listing while retaining the form and avoiding the KYC detour', async () => {
    vi.mocked(apiClient).mockRejectedValue(
      new ApiProblemException({
        title: 'Không nhận liên hệ',
        status: 409,
        code: 'LISTING_NOT_ACCEPTING_LEADS',
        detail: 'Tin đăng không còn nhận yêu cầu liên hệ.',
      }),
    );
    openForm();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Tin đăng không còn nhận yêu cầu liên hệ.');
    expect(screen.getByRole('textbox', { name: /Họ và tên/ })).toHaveValue('Nguyễn An');
    expect(screen.getByRole('checkbox', { name: /Tôi đồng ý/ })).toBeChecked();
    expect(screen.queryByRole('link', { name: /Xác minh eKYC/ })).not.toBeInTheDocument();
    expect(screen.queryByText('Yêu cầu đã được ghi nhận')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Gửi yêu cầu' })).toBeEnabled());
  });
});
