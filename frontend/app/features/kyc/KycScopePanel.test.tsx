import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { KYC_RETENTION_UNSET, KycScopePanel } from './KycScopePanel';

// UI-12: the KYC page discloses how long document photos are kept. The period is configured at build time
// (VITE_KYC_RETENTION_NOTICE); the wording is the product owner's decision, so the UI must render it verbatim and,
// while it is unset, say so plainly instead of inventing a period.

const notice = () => screen.getByTestId('kyc-retention-notice');

describe('KycScopePanel retention notice (UI-12)', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('says the period has not been published yet when the notice is not configured', () => {
    vi.stubEnv('VITE_KYC_RETENTION_NOTICE', '');
    render(<KycScopePanel status={null} />);
    expect(notice()).toHaveTextContent(KYC_RETENTION_UNSET);
    expect(notice()).toHaveTextContent('chưa được công bố');
    // No invented duration in the fallback.
    expect(notice().textContent).not.toMatch(/\d+\s*(ngày|tháng|năm)/);
    expect(screen.getByRole('link', { name: 'Xem chính sách quyền riêng tư' })).toHaveAttribute('href', '/privacy');
  });

  it('treats a blank (whitespace) notice as not configured', () => {
    vi.stubEnv('VITE_KYC_RETENTION_NOTICE', '   ');
    render(<KycScopePanel status={null} />);
    expect(notice()).toHaveTextContent(KYC_RETENTION_UNSET);
  });

  it('renders a configured notice verbatim, with the privacy link, and no fallback', () => {
    const configured = 'Ảnh giấy tờ được lưu tối đa 24 tháng sau lần xác minh gần nhất, sau đó được xóa.';
    vi.stubEnv('VITE_KYC_RETENTION_NOTICE', `  ${configured}  `);
    render(<KycScopePanel status={null} />);
    expect(notice()).toHaveTextContent(configured);
    expect(notice()).not.toHaveTextContent('chưa được công bố');
    expect(screen.getByRole('link', { name: 'Xem chính sách quyền riêng tư' })).toBeInTheDocument();
  });
});
