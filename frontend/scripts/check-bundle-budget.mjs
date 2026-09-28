#!/usr/bin/env node
/**
 * Route bundle budget (F15.2). Reads the Vite manifest of a build made with `vite build --manifest` and computes,
 * per route, the JavaScript a first visit must download before the route renders: the entry chunk, the route's
 * lazy chunk, every chunk they import statically, and any worker/asset script one of those chunks loads eagerly
 * (m7 — e.g. MapLibre's web worker, listed under the chunk's `assets` in the manifest, not its `imports`, but
 * fetched as soon as the map initializes). Dynamic `import()`s inside a route are not counted: they load on
 * demand.
 *
 * Sizes are gzip at the level `frontend/nginx.conf` actually serves with (m7): that file sets `gzip on` without a
 * `gzip_comp_level`, so nginx uses its compiled-in default of **1**, not the maximum (9) an earlier version of
 * this script assumed — level 9 under-reports what a real response weighs by roughly 10-15%. Pass `--gzip-level 9`
 * to compare against the old numbers, or `--gzip-level 0` to compare raw sizes. `frontend/nginx.conf` is owned by
 * another stream (S5-SEC); if it later sets an explicit `gzip_comp_level` or switches to brotli, update the
 * default here (and re-run `npm run check:bundle` to refresh `bundle-budget.json`) to match.
 *
 * Budgets live in bundle-budget.json. The script prints a table and exits 1 when a route is over budget or when a
 * route module is missing from the manifest (renamed file → update the config in the same change).
 *
 *   npm run check:bundle            build with a manifest, then check
 *   node scripts/check-bundle-budget.mjs [--dist dist] [--config bundle-budget.json] [--gzip-level 1] [--json]
 */
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { gzipSync } from 'node:zlib';

/** nginx's compiled-in default when `gzip_comp_level` is not set (frontend/nginx.conf does not set it). */
export const DEFAULT_GZIP_LEVEL = 1;

/** Manifest keys reachable from `keys` through static imports (the chunks loaded together). */
export function staticClosure(manifest, keys) {
  const seen = new Set();
  const stack = [...keys];
  while (stack.length) {
    const key = stack.pop();
    if (seen.has(key)) continue;
    const chunk = manifest[key];
    if (!chunk) throw new Error(`"${key}" is not in the Vite manifest`);
    seen.add(key);
    for (const imported of chunk.imports ?? []) stack.push(imported);
  }
  return seen;
}

/**
 * JS files a first visit of `moduleKey` needs, entry included; `moduleKey` null = the entry alone. Includes each
 * reachable chunk's own file plus any of its `assets` that are themselves JS (worker scripts a chunk starts
 * eagerly; regular non-JS assets — images, CSS — are not part of the "initial JS" metric).
 */
export function initialJsFiles(manifest, moduleKey) {
  const entries = Object.keys(manifest).filter((key) => manifest[key].isEntry);
  if (entries.length !== 1) throw new Error(`expected one entry chunk, found ${entries.length}`);
  const keys = staticClosure(manifest, moduleKey ? [entries[0], moduleKey] : entries);
  const files = new Set();
  for (const key of keys) {
    const chunk = manifest[key];
    if (chunk.file.endsWith('.js')) files.add(chunk.file);
    for (const asset of chunk.assets ?? []) {
      if (asset.endsWith('.js')) files.add(asset);
    }
  }
  return [...files].sort();
}

/** Sizes of the given files in bytes: raw and gzip at `gzipLevel` (0 = raw only, no compression). `read` returns
 * the file contents. */
export function measure(files, read, gzipLevel = DEFAULT_GZIP_LEVEL) {
  let raw = 0;
  let gzip = 0;
  for (const file of files) {
    const content = read(file);
    raw += content.length;
    gzip += gzipLevel > 0 ? gzipSync(content, { level: gzipLevel }).length : content.length;
  }
  return { raw, gzip };
}

/**
 * Route modules that exist in the manifest (app/routes/*.tsx, dynamic entries) but have no entry in
 * `config.routes` (NIT): without this, a newly added route silently gets no budget instead of failing the check,
 * which is easy to miss until its bundle has already grown unchecked.
 */
export function unbudgetedRoutes(manifest, config) {
  const budgeted = new Set(Object.values(config.routes).map((spec) => spec.module));
  return Object.keys(manifest)
    .filter((key) => /^app\/routes\/.*\.tsx$/.test(key) && manifest[key].isDynamicEntry)
    .filter((key) => !budgeted.has(key))
    .sort();
}

/** Checks every route of `config` against its budget; returns rows for the report. */
export function evaluateBudgets(manifest, config, read, gzipLevel = DEFAULT_GZIP_LEVEL) {
  const rows = [];
  const shell = measure(initialJsFiles(manifest, null), read, gzipLevel);
  rows.push({
    route: '(shell: index.html)',
    module: 'index.html',
    ...shell,
    budgetKb: config.shellBudgetKb,
    chunks: null,
  });
  for (const [route, spec] of Object.entries(config.routes)) {
    if (!manifest[spec.module]) {
      rows.push({ route, module: spec.module, raw: 0, gzip: 0, budgetKb: spec.budgetKb, chunks: 0, missing: true });
      continue;
    }
    const files = initialJsFiles(manifest, spec.module);
    rows.push({
      route,
      module: spec.module,
      ...measure(files, read, gzipLevel),
      budgetKb: spec.budgetKb,
      chunks: files.length,
    });
  }
  return rows.map((row) => ({
    ...row,
    gzipKb: Math.round((row.gzip / 1024) * 10) / 10,
    rawKb: Math.round((row.raw / 1024) * 10) / 10,
    over: Boolean(row.missing) || (row.budgetKb != null && row.gzip / 1024 > row.budgetKb),
  }));
}

function main(argv) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const option = (name, fallback) => {
    const index = argv.indexOf(name);
    return index >= 0 ? argv[index + 1] : fallback;
  };
  const dist = path.resolve(root, option('--dist', 'dist'));
  const configPath = path.resolve(root, option('--config', 'bundle-budget.json'));
  const gzipLevel = Number(option('--gzip-level', DEFAULT_GZIP_LEVEL));
  const manifestPath = path.join(dist, '.vite', 'manifest.json');
  if (!existsSync(manifestPath)) {
    console.error(`No manifest at ${manifestPath}. Build with: npx vite build --manifest`);
    return 1;
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const config = JSON.parse(readFileSync(configPath, 'utf8'));
  const rows = evaluateBudgets(manifest, config, (file) => readFileSync(path.join(dist, file)), gzipLevel);
  const unbudgeted = unbudgetedRoutes(manifest, config);

  if (argv.includes('--json')) {
    console.log(JSON.stringify(rows, null, 2));
  } else {
    console.log(
      gzipLevel > 0
        ? `Compression: gzip level ${gzipLevel} (nginx default when gzip_comp_level is unset; frontend/nginx.conf does not set it).`
        : 'Compression: none (raw sizes).',
    );
    const header = ['route', 'initial JS gzip', 'budget', 'raw', 'chunks', 'status'];
    const lines = rows.map((row) => [
      row.route,
      `${row.gzipKb.toFixed(1)} kB`,
      row.budgetKb != null ? `${row.budgetKb} kB` : '—',
      `${row.rawKb.toFixed(1)} kB`,
      row.chunks ?? '—',
      row.missing ? `MISSING (${row.module})` : row.over ? 'OVER BUDGET' : 'ok',
    ]);
    const widths = header.map((title, column) =>
      Math.max(title.length, ...lines.map((line) => String(line[column]).length)),
    );
    const format = (line) => line.map((cell, column) => String(cell).padEnd(widths[column])).join('  ');
    console.log(format(header));
    console.log(widths.map((width) => '-'.repeat(width)).join('  '));
    lines.forEach((line) => console.log(format(line)));
  }
  if (unbudgeted.length) {
    console.error(
      `\n${unbudgeted.length} route module(s) have no budget entry: ${unbudgeted.join(', ')}`,
    );
    console.error(`Add each one to "routes" in ${path.relative(root, configPath)} with an explicit budgetKb.`);
  }
  const failures = rows.filter((row) => row.over);
  if (failures.length) {
    console.error(
      `\n${failures.length} route(s) over budget or missing: ${failures.map((row) => row.route).join(', ')}`,
    );
    console.error(
      'Split the route (dynamic import) or, if the growth is intended, raise the budget in bundle-budget.json.',
    );
  }
  return failures.length || unbudgeted.length ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
