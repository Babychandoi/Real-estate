import { act, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { fetchLead, fetchLeadHistory } from '@/entities/lead/api/leadApi';
import type { LeadItem } from '@/entities/lead/model/types';
import { LeadDetailDialog } from './LeadDetailDialog';
vi.mock('@/entities/lead/api/leadApi', () => ({
  fetchLead: vi.fn(),
  fetchLeadHistory: vi.fn(),
  assignLead: vi.fn(),
  qualifyLead: vi.fn(),
  revealLeadContact: vi.fn(),
  updateLeadStatus: vi.fn(),
}));
it('keeps a late previous-lead response out of the newly opened dialog', async () => {
  let resolve!: (lead: LeadItem) => void;
  vi.mocked(fetchLead)
    .mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    )
    .mockImplementationOnce(() => new Promise(() => {}));
  vi.mocked(fetchLeadHistory).mockResolvedValue([]);
  const props = { onClose: vi.fn(), onChanged: vi.fn(), team: [], currentUserId: 'owner' };
  const { rerender } = render(<LeadDetailDialog {...props} leadId="old" />);
  await waitFor(() => expect(fetchLead).toHaveBeenCalledWith('old'));
  rerender(<LeadDetailDialog {...props} leadId="new" />);
  await waitFor(() => expect(fetchLead).toHaveBeenCalledWith('new'));
  await act(async () => resolve({ id: 'old', fullName: 'Previous private contact' } as LeadItem));
  expect(screen.queryByText('Previous private contact')).not.toBeInTheDocument();
  expect(screen.getByText('Đang tải…')).toBeInTheDocument();
});
