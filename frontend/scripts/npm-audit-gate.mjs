// Dependency audit gate (CI "Audit dependencies"). Fails on any high or critical advisory, except advisories listed in
// audit-exceptions.json with a reason and an unexpired date. Exceptions marked devOnly are honoured only when the
// advisory is absent from the production dependency tree, so a runtime dependency can never be waved through.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BLOCKING = new Set(['high', 'critical']);
const SEVERITIES = new Set(['info', 'low', 'moderate', 'high', 'critical']);

function incomplete() {
  throw new Error('npm audit did not return a complete vulnerability report');
}

function audit(extraArgs) {
  let output;
  try {
    output = execFileSync('npm', ['audit', '--json', ...extraArgs], { cwd: root, encoding: 'utf8' });
  } catch (error) {
    // npm audit exits non-zero when it finds anything; the JSON report is still on stdout.
    output = error.stdout;
    if (!output) throw error;
  }
  return JSON.parse(output);
}

/** Advisories (by GHSA id) reachable at high/critical severity, with the packages they come through. */
export function blockingAdvisories(report) {
  // npm also exits nonzero with JSON for registry failures; that is no evidence of a clean dependency tree.
  if (
    !report ||
    report.error ||
    !report.vulnerabilities ||
    typeof report.vulnerabilities !== 'object' ||
    Array.isArray(report.vulnerabilities)
  ) {
    incomplete();
  }
  const entries = report.vulnerabilities;
  for (const vulnerability of Object.values(entries)) {
    if (
      !vulnerability ||
      typeof vulnerability !== 'object' ||
      Array.isArray(vulnerability) ||
      !SEVERITIES.has(vulnerability.severity) ||
      !Array.isArray(vulnerability.via)
    )
      incomplete();
    for (const via of vulnerability.via) {
      if (typeof via === 'string') {
        if (!Object.hasOwn(entries, via)) incomplete();
      } else if (
        !via ||
        typeof via !== 'object' ||
        Array.isArray(via) ||
        !SEVERITIES.has(via.severity) ||
        !((typeof via.url === 'string' && via.url.length > 0) || Number.isFinite(via.source))
      )
        incomplete();
    }
  }
  // Aggregate entries legitimately refer to other packages. A blocking entry with no reachable blocking advisory
  // (including an empty list or dependency cycle) is incomplete evidence, never proof of a clean dependency tree.
  for (const [name, vulnerability] of Object.entries(entries)) {
    if (!BLOCKING.has(vulnerability.severity)) continue;
    const pending = [name];
    const visited = new Set();
    let blocking = false;
    while (pending.length && !blocking) {
      const dependency = pending.pop();
      if (visited.has(dependency)) continue;
      visited.add(dependency);
      for (const via of entries[dependency].via) {
        if (typeof via === 'string') pending.push(via);
        else if (BLOCKING.has(via.severity)) blocking = true;
      }
    }
    if (!blocking) incomplete();
  }
  const found = new Map();
  for (const [name, vulnerability] of Object.entries(entries)) {
    for (const via of vulnerability.via) {
      if (typeof via !== 'object' || !BLOCKING.has(via.severity)) continue;
      const id =
        String(via.url ?? '')
          .split('/')
          .pop() || `${via.source}`;
      const entry = found.get(id) ?? { id, title: via.title, severity: via.severity, packages: new Set() };
      entry.packages.add(name);
      found.set(id, entry);
    }
  }
  return found;
}

export function evaluate(all, production, exceptions, today = new Date()) {
  const problems = [];
  for (const advisory of all.values()) {
    const exception = exceptions.find((candidate) => candidate.advisory === advisory.id);
    const where = [...advisory.packages].join(', ');
    if (!exception) {
      problems.push(`${advisory.severity} ${advisory.id} (${where}): ${advisory.title}`);
    } else if (!(new Date(`${exception.expires}T23:59:59Z`) >= today)) {
      problems.push(`${advisory.id}: exception expired on ${exception.expires}; re-assess or remove the dependency`);
    } else if (exception.devOnly && production.has(advisory.id)) {
      problems.push(`${advisory.id}: exception is dev-only but the advisory is in the production dependency tree`);
    } else {
      console.log(`accepted ${advisory.id} (${where}) until ${exception.expires}: ${exception.reason}`);
    }
  }
  return problems;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { exceptions } = JSON.parse(readFileSync(path.join(root, 'audit-exceptions.json'), 'utf8'));
  const problems = evaluate(blockingAdvisories(audit([])), blockingAdvisories(audit(['--omit=dev'])), exceptions);
  if (problems.length) {
    console.error(`Dependency audit failed:\n- ${problems.join('\n- ')}`);
    process.exit(1);
  }
  console.log('Dependency audit: no unaccepted high or critical advisories.');
}
