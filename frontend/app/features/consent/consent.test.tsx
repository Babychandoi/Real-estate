import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import {
  ANONYMOUS_ID_KEY,
  CONSENT_ID_KEY,
  CONSENT_POLICY_VERSION,
  getAnalyticsConsent,
  getStoredAnalyticsConsent,
  setAnalyticsConsent,
} from '@/shared/analytics/consent';
import { ConsentBanner } from './ConsentBanner';
import { decideAnalyticsConsent } from './consentRecord';

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});
afterEach(() => vi.restoreAllMocks());

describe('ConsentBanner', () => {
  it('offers refuse and accept with equal weight and nothing pre-selected', async () => {
    const decide = vi.fn(async () => undefined);
    const onClose = vi.fn();
    render(
      <MemoryRouter>
        <ConsentBanner view="banner" onClose={onClose} decide={decide} />
      </MemoryRouter>,
    );
    const region = screen.getByRole('region', { name: 'Đồng ý phân tích dữ liệu' });
    const refuse = screen.getByRole('button', { name: 'Từ chối' });
    const accept = screen.getByRole('button', { name: 'Đồng ý' });
    expect(region).toBeInTheDocument();
    expect(refuse.className).toBe(accept.className);
    expect(screen.getByRole('link', { name: 'Chính sách quyền riêng tư' })).toHaveAttribute('href', '/privacy');
    expect(getStoredAnalyticsConsent()).toBeNull();

    fireEvent.click(refuse);
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(decide).toHaveBeenCalledWith('denied', 'banner');
  });

  it('preferences show the analytics switch off until the visitor turns it on', async () => {
    const decide = vi.fn(async () => undefined);
    render(
      <MemoryRouter>
        <ConsentBanner view="preferences" onClose={() => undefined} decide={decide} />
      </MemoryRouter>,
    );
    const toggle = screen.getByRole('switch', { name: 'Phân tích sản phẩm và hiệu năng' });
    expect(toggle).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu lựa chọn' }));
    await waitFor(() => expect(decide).toHaveBeenCalledWith('granted', 'preferences'));
  });
});

describe('decideAnalyticsConsent', () => {
  it('applies the choice locally and records it with a stable consent id and the policy version', async () => {
    const send = vi.fn(async (_endpoint: string, _options?: RequestInit) => new Response(null, { status: 201 }));
    await decideAnalyticsConsent('granted', 'banner', send);
    await decideAnalyticsConsent('denied', 'preferences', send);

    expect(getAnalyticsConsent()).toBe('denied');
    expect(localStorage.getItem(ANONYMOUS_ID_KEY)).toBeNull();
    const bodies = send.mock.calls.map(([, options]) => JSON.parse(String(options?.body)));
    expect(bodies.map((body) => body.choice)).toEqual(['granted', 'withdrawn']);
    expect(bodies[0].consentId).toBe(bodies[1].consentId);
    expect(bodies[0].consentId).toBe(localStorage.getItem(CONSENT_ID_KEY));
    expect(bodies[0]).toMatchObject({ purpose: 'analytics', policyVersion: CONSENT_POLICY_VERSION, source: 'banner' });
    expect(send.mock.calls[0][0]).toBe('/events/consent');
  });

  it('keeps the local decision when the server cannot be reached', async () => {
    await decideAnalyticsConsent(
      'granted',
      'banner',
      vi.fn(async () => Promise.reject(new TypeError('offline'))),
    );
    expect(getAnalyticsConsent()).toBe('granted');
  });

  it('writes no consent id before a decision', () => {
    setAnalyticsConsent('denied');
    expect(localStorage.getItem(CONSENT_ID_KEY)).toBeNull();
  });
});
