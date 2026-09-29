import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import TotpQrCode from './TotpQrCode';

const URI = 'otpauth://totp/Nha%20Dat%20Chuan:admin%40example.invalid?secret=JBSWY3DPEHPK3PXP&issuer=Nha%20Dat%20Chuan';

describe('TotpQrCode', () => {
  it('draws an SVG data URL for the otpauth link without any network request', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    render(<TotpQrCode uri={URI} />);
    const img = await screen.findByRole('img', { name: /Mã QR thiết lập ứng dụng xác thực/ });
    expect(img.getAttribute('src')).toMatch(/^data:image\/svg\+xml/);
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('draws a new code when the link changes', async () => {
    const { rerender } = render(<TotpQrCode uri={URI} />);
    const first = (await screen.findByRole('img')).getAttribute('src');
    rerender(<TotpQrCode uri={URI.replace('JBSWY3DPEHPK3PXP', 'KRSXG5CTMVRXEZLU')} />);
    await waitFor(() => expect(screen.getByRole('img').getAttribute('src')).not.toBe(first));
  });

  it('falls back to the typed key when the QR cannot be drawn', async () => {
    vi.resetModules();
    vi.doMock('uqr', () => ({ renderSVG: () => Promise.reject(new Error('boom')) }));
    const { default: Broken } = await import('./TotpQrCode');
    render(<Broken uri={URI} />);
    expect(await screen.findByRole('status')).toHaveTextContent('Không tạo được mã QR');
    vi.doUnmock('uqr');
  });
});
