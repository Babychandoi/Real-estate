import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom';
import { AdminAlias, adminAliasTarget, ADMIN_PAGES, ADMIN_PREFIX } from './AdminAlias';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search + location.hash}</p>;
}

function landOn(url: string): string {
  const router = createMemoryRouter(
    [
      { path: `${ADMIN_PREFIX}/*`, element: <Where /> },
      { path: ADMIN_PREFIX, element: <Where /> },
      { path: '/admin/*', element: <AdminAlias /> },
      { path: '/2026/nhadatchua/admin/*', element: <AdminAlias /> },
    ],
    { initialEntries: [url] },
  );
  render(<RouterProvider router={router} />);
  return screen.getByTestId('where').textContent ?? '';
}

describe('admin aliases (UI-26)', () => {
  it.each(ADMIN_PAGES.map((page) => [page]))('/admin/%s and the misspelt prefix map to the same page', (page) => {
    expect(adminAliasTarget(page)).toBe(`${ADMIN_PREFIX}/${page}`);
    expect(adminAliasTarget(`${page}/extra/segments`)).toBe(`${ADMIN_PREFIX}/${page}`);
  });

  it('unknown or empty paths land on the admin prefix (which opens moderation), never on a dead end', () => {
    expect(adminAliasTarget(undefined)).toBe(ADMIN_PREFIX);
    expect(adminAliasTarget('')).toBe(ADMIN_PREFIX);
    expect(adminAliasTarget('wp-login.php')).toBe(ADMIN_PREFIX);
    expect(adminAliasTarget('..%2Flogin')).toBe(ADMIN_PREFIX);
  });

  it('keeps the query string and hash', () => {
    expect(landOn('/admin/users?role=BROKER#top')).toBe(`${ADMIN_PREFIX}/users?role=BROKER#top`);
  });

  it('redirects both legacy prefixes in the router', () => {
    expect(landOn('/2026/nhadatchua/admin/billing')).toBe(`${ADMIN_PREFIX}/billing`);
  });

  it('sends a bare /admin to the prefix', () => {
    expect(landOn('/admin')).toBe(ADMIN_PREFIX);
  });
});
