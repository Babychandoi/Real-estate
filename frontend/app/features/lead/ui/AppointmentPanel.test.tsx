import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AppointmentView } from '@/entities/lead/model/types';
import { AppointmentPanel } from './AppointmentPanel';

const proposal: AppointmentView = {
  id: 'a-1',
  leadId: 'l-1',
  listingId: 's-1',
  status: 'PROPOSED',
  proposedBySide: 'OWNER_SIDE',
  startsAt: null,
  endsAt: null,
  confirmedAt: null,
  cancelledAt: null,
  cancelReason: null,
  outcomeAt: null,
  noShowParty: null,
  note: null,
  version: 3,
  createdAt: '2026-09-28T01:00:00Z',
  slots: [
    { id: 'slot-1', startsAt: '2030-01-02T02:00:00Z', endsAt: '2030-01-02T03:00:00Z' },
    { id: 'slot-2', startsAt: '2030-01-03T02:00:00Z', endsAt: '2030-01-03T03:00:00Z' },
  ],
  awaitingMe: true,
};

function stubApi(handler: (url: string, init?: RequestInit) => Response) {
  const calls: Array<{ url: string; body?: unknown }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      return handler(url, init);
    }),
  );
  return calls;
}
const ok = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

describe('AppointmentPanel', () => {
  it('lets the requester confirm one of the owner slots with the version it showed', async () => {
    const calls = stubApi((url) =>
      url.endsWith('/confirm') ? ok({ ...proposal, status: 'CONFIRMED' }) : ok([proposal]),
    );
    const onChanged = vi.fn();
    render(<AppointmentPanel side="REQUESTER" leadId="l-1" canPropose onChanged={onChanged} />);

    const confirm = await screen.findByRole('button', { name: 'Xác nhận khung giờ' });
    expect(confirm).toBeDisabled();
    fireEvent.click(screen.getAllByRole('radio')[1]);
    fireEvent.click(confirm);
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    const post = calls.find((call) => call.url.endsWith('/appointments/a-1/confirm'));
    expect(post?.body).toEqual({ slotId: 'slot-2', expectedVersion: 3 });
    expect(calls[0].url).toContain('/me/inquiries/l-1/appointments');
  });

  it('explains a version conflict instead of overwriting', async () => {
    stubApi((url) =>
      url.endsWith('/confirm')
        ? new Response(JSON.stringify({ title: 'x', status: 409, detail: 'x', code: 'APPOINTMENT_VERSION_CONFLICT' }), {
            status: 409,
            headers: { 'Content-Type': 'application/problem+json' },
          })
        : ok([proposal]),
    );
    render(<AppointmentPanel side="REQUESTER" leadId="l-1" canPropose />);
    fireEvent.click((await screen.findAllByRole('radio'))[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận khung giờ' }));
    expect(await screen.findByText(/vừa được bên kia cập nhật/)).toBeInTheDocument();
  });

  it('offers a proposal form on the owner side when nothing is open', async () => {
    stubApi(() => ok([]));
    render(<AppointmentPanel side="OWNER_SIDE" leadId="l-1" canPropose />);
    fireEvent.click(await screen.findByRole('button', { name: 'Đề xuất lịch hẹn xem' }));
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }));
    expect(await screen.findByText('Chọn ít nhất một khung giờ.')).toBeInTheDocument();
    expect(screen.getByLabelText('Ngày của khung giờ 1')).toBeInTheDocument();
  });
});
