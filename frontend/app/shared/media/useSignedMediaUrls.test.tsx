import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { isOwnMedia, rememberSignedMediaUrl, resetSignedMediaCache, useSignedMediaUrls } from './useSignedMediaUrls';

const OWN = '/api/v1/public/media/8f0c4b8e-1111-4a5b-9c3d-000000000001.jpg';
const OTHER = '/api/v1/public/media/8f0c4b8e-1111-4a5b-9c3d-000000000002.jpg';
const EXTERNAL = 'https://images.unsplash.com/photo-1';

function Probe({ urls }: { urls: string[] }) {
  const display = useSignedMediaUrls(urls);
  return (
    <ul>
      {urls.map((url) => (
        <li key={url} data-testid={url}>
          {display(url) ?? 'pending'}
        </li>
      ))}
    </ul>
  );
}

describe('useSignedMediaUrls', () => {
  beforeEach(() => resetSignedMediaCache());
  afterEach(() => vi.useRealTimers());

  it('recognises only uploaded media URLs', () => {
    expect(isOwnMedia(OWN)).toBe(true);
    expect(isOwnMedia('/api/v1/public/media/8f0c4b8e-1111-4a5b-9c3d-000000000001__w320.webp')).toBe(true);
    expect(isOwnMedia(EXTERNAL)).toBe(false);
    expect(isOwnMedia('/api/v1/media/kyc/8f0c4b8e-1111-4a5b-9c3d-000000000001.jpg')).toBe(false);
  });

  it('signs own media in one batch, keeps external URLs and falls back to the plain URL when not signed', async () => {
    const expiresAt = new Date(Date.now() + 15 * 60_000).toISOString();
    const fetchMock = vi.fn(
      async () =>
        new Response(JSON.stringify({ urls: { [OWN]: '/api/v1/media/signed/x.jpg?exp=1&sig=a' }, expiresAt }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<Probe urls={[OWN, OTHER, EXTERNAL]} />);
    // Until signed, an own-media URL renders nothing (no request that would 404 on the public endpoint).
    expect(screen.getByTestId(OWN)).toHaveTextContent('pending');
    expect(screen.getByTestId(EXTERNAL)).toHaveTextContent(EXTERNAL);

    await waitFor(() => expect(screen.getByTestId(OWN)).toHaveTextContent('/api/v1/media/signed/x.jpg?exp=1&sig=a'));
    expect(screen.getByTestId(OTHER)).toHaveTextContent(OTHER);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/v1/media/signed-urls');
    expect(JSON.parse(String(init.body))).toEqual({ urls: [OTHER, OWN].sort() });
  });

  it('uses a remembered upload preview without asking the server', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    rememberSignedMediaUrl(OWN, '/api/v1/media/signed/p.jpg?exp=1&sig=b', new Date(Date.now() + 600_000).toISOString());
    render(<Probe urls={[OWN]} />);
    expect(screen.getByTestId(OWN)).toHaveTextContent('/api/v1/media/signed/p.jpg');
    await act(async () => {});
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
