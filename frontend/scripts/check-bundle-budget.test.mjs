import { randomBytes } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { evaluateBudgets, initialJsFiles, staticClosure } from './check-bundle-budget.mjs';

// A miniature Vite manifest: entry + shared chunk, one light route, one route that statically imports a map chunk,
// and a dynamic import that must not count.
const manifest = {
  'index.html': { file: 'assets/index.js', isEntry: true, imports: ['_react.js'], css: ['assets/index.css'] },
  '_react.js': { file: 'assets/react.js' },
  '_shared.js': { file: 'assets/shared.js', imports: ['_react.js'] },
  '_map.js': { file: 'assets/map.js' },
  'app/routes/home.tsx': { file: 'assets/home.js', isDynamicEntry: true, imports: ['index.html', '_shared.js'] },
  'app/routes/search.tsx': {
    file: 'assets/search.js',
    isDynamicEntry: true,
    imports: ['index.html', '_shared.js', '_map.js'],
    dynamicImports: ['app/routes/lazy-panel.tsx'],
  },
  'app/routes/lazy-panel.tsx': { file: 'assets/lazy-panel.js', isDynamicEntry: true, imports: ['_shared.js'] },
};

const sizes = {
  'assets/index.js': 60_000,
  'assets/react.js': 40_000,
  'assets/shared.js': 10_000,
  'assets/map.js': 300_000,
  'assets/home.js': 5_000,
  'assets/search.js': 8_000,
  'assets/lazy-panel.js': 50_000,
};
// Random bytes do not compress, so gzip size ≈ raw size and the assertions stay simple.
const contents = Object.fromEntries(Object.entries(sizes).map(([file, size]) => [file, randomBytes(size)]));
const read = (file) => contents[file];

describe('route initial JS', () => {
  it('follows static imports once and ignores dynamic imports', () => {
    expect([...staticClosure(manifest, ['app/routes/search.tsx'])].sort()).toEqual([
      '_map.js',
      '_react.js',
      '_shared.js',
      'app/routes/search.tsx',
      'index.html',
    ]);
    expect(initialJsFiles(manifest, 'app/routes/home.tsx')).toEqual([
      'assets/home.js',
      'assets/index.js',
      'assets/react.js',
      'assets/shared.js',
    ]);
    expect(initialJsFiles(manifest, null)).toEqual(['assets/index.js', 'assets/react.js']);
  });

  it('flags routes over budget and routes missing from the manifest', () => {
    const rows = evaluateBudgets(
      manifest,
      {
        shellBudgetKb: 120,
        routes: {
          '/': { module: 'app/routes/home.tsx', budgetKb: 120 },
          '/search': { module: 'app/routes/search.tsx', budgetKb: 200 },
          '/renamed': { module: 'app/routes/old-name.tsx', budgetKb: 100 },
        },
      },
      read,
    );
    const byRoute = Object.fromEntries(rows.map((row) => [row.route, row]));
    expect(byRoute['/'].over).toBe(false);
    expect(byRoute['/'].chunks).toBe(4);
    expect(byRoute['/search'].over).toBe(true);
    expect(byRoute['/search'].gzipKb).toBeGreaterThan(350);
    expect(byRoute['/renamed']).toMatchObject({ missing: true, over: true });
    expect(byRoute['(shell: index.html)'].over).toBe(false);
  });
});
