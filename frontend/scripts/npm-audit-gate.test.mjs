import { describe, expect, it } from 'vitest';
import { blockingAdvisories, evaluate } from './npm-audit-gate.mjs';

const report = (entries) => ({
  vulnerabilities: Object.fromEntries(
    entries.map(([name, severity, id]) => [
      name,
      { via: [{ severity, title: `${id} title`, url: `https://github.com/advisories/${id}` }] },
    ]),
  ),
});

const braces = { advisory: 'GHSA-aaaa', reason: 'build-time only', devOnly: true, expires: '2026-12-31' };
const today = new Date('2026-10-03T00:00:00Z');

describe('npm audit gate', () => {
  it('ignores moderate and low advisories', () => {
    expect(
      blockingAdvisories(
        report([
          ['x', 'moderate', 'GHSA-m'],
          ['y', 'low', 'GHSA-l'],
        ]),
      ).size,
    ).toBe(0);
  });

  it('blocks a high advisory without an exception', () => {
    const all = blockingAdvisories(report([['lodash', 'high', 'GHSA-zzzz']]));
    expect(evaluate(all, new Map(), [braces], today)).toEqual([expect.stringContaining('GHSA-zzzz')]);
  });

  it('accepts a listed dev-only advisory that is absent from the production tree', () => {
    const all = blockingAdvisories(
      report([
        ['braces', 'high', 'GHSA-aaaa'],
        ['micromatch', 'high', 'GHSA-aaaa'],
      ]),
    );
    expect(evaluate(all, new Map(), [braces], today)).toEqual([]);
  });

  it('blocks a dev-only exception once the advisory reaches the production tree', () => {
    const all = blockingAdvisories(report([['braces', 'high', 'GHSA-aaaa']]));
    const production = blockingAdvisories(report([['braces', 'high', 'GHSA-aaaa']]));
    expect(evaluate(all, production, [braces], today)).toEqual([expect.stringContaining('production')]);
  });

  it('blocks an expired exception', () => {
    const all = blockingAdvisories(report([['braces', 'high', 'GHSA-aaaa']]));
    expect(evaluate(all, new Map(), [braces], new Date('2027-01-01T00:00:00Z'))).toEqual([
      expect.stringContaining('expired'),
    ]);
  });

  it('blocks a critical advisory even when another advisory is accepted', () => {
    const all = blockingAdvisories(
      report([
        ['braces', 'high', 'GHSA-aaaa'],
        ['axios', 'critical', 'GHSA-cccc'],
      ]),
    );
    expect(evaluate(all, new Map(), [braces], today)).toEqual([expect.stringContaining('GHSA-cccc')]);
  });
});
