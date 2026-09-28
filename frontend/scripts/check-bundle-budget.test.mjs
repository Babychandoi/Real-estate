import { randomBytes } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { DEFAULT_GZIP_LEVEL, evaluateBudgets, initialJsFiles, measure, staticClosure } from './check-bundle-budget.mjs';

// A miniature Vite manifest: entry + shared chunk, one light route, one route that statically imports a map chunk
// (with a worker script under `assets`, like MapLibre's — m7), a non-JS asset that must not be counted as JS, and
// a dynamic import that must not count.
const manifest = {
  'index.html': { file: 'assets/index.js', isEntry: true, imports: ['_react.js'], css: ['assets/index.css'] },
  '_react.js': { file: 'assets/react.js' },
  '_shared.js': { file: 'assets/shared.js', imports: ['_react.js'] },
  '_map.js': {
    file: 'assets/map.js',
    assets: ['assets/map-worker.js', 'assets/map-marker.png'],
  },
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
  'assets/map-worker.js': 140_000,
  'assets/map-marker.png': 5_000,
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

  it("counts a chunk's own eagerly-loaded worker script but not a non-JS asset (m7)", () => {
    const files = initialJsFiles(manifest, 'app/routes/search.tsx');
    expect(files).toContain('assets/map-worker.js');
    expect(files).not.toContain('assets/map-marker.png');
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
    // The worker script (140 kB) is now part of the route's initial JS, on top of map.js (300 kB) and the rest.
    expect(byRoute['/search'].gzipKb).toBeGreaterThan(500);
    expect(byRoute['/renamed']).toMatchObject({ missing: true, over: true });
    expect(byRoute['(shell: index.html)'].over).toBe(false);
  });
});

describe('compression level matches what nginx actually serves (m7)', () => {
  it('defaults to level 1 (nginx compiled-in default; frontend/nginx.conf sets gzip on without gzip_comp_level)', () => {
    expect(DEFAULT_GZIP_LEVEL).toBe(1);
  });

  it('a lower gzip level compresses compressible content less than a higher one', () => {
    const compressible = Buffer.alloc(50_000, 'a');
    const read = () => compressible;
    const atLevel1 = measure(['x.js'], read, 1);
    const atLevel9 = measure(['x.js'], read, 9);
    expect(atLevel1.raw).toBe(50_000);
    expect(atLevel9.raw).toBe(50_000);
    expect(atLevel9.gzip).toBeLessThan(atLevel1.gzip);
  });

  it('level 0 reports raw sizes (no compression)', () => {
    const content = Buffer.alloc(50_000, 'a');
    const { raw, gzip } = measure(['x.js'], () => content, 0);
    expect(gzip).toBe(raw);
  });
});
