// Dependency audit gate (CI "Audit dependencies"). Fails on any high or critical advisory, except advisories listed in
// audit-exceptions.json with a reason and an unexpired date. Exceptions marked devOnly are honoured only when the
// advisory is absent from the production dependency tree, so a runtime dependency can never be waved through.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BLOCKING = new Set(['high', 'critical']);

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
  if (!report || report.error || !report.vulnerabilities || typeof report.vulnerabilities !== 'object') {
    throw new Error('npm audit did not return a complete vulnerability report');
  }
  const found = new Map();
  for (const [name, vulnerability] of Object.entries(report.vulnerabilities ?? {})) {
    for (const via of vulnerability.via ?? []) {
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
