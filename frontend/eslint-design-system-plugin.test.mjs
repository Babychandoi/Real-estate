import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

/**
 * m6: the design-system guard rails must catch emoji/symbols wherever they can appear as an "icon" (JSX text,
 * string literals, template literals) across the wider Unicode ranges, and text below 12px expressed as rem/em or
 * an inline `fontSize`, not only bare `text-[Npx]` classes.
 */
async function lintSource(code) {
  const eslint = new ESLint({ overrideConfigFile: 'eslint.config.js' });
  const [result] = await eslint.lintText(code, { filePath: 'app/probe.tsx' });
  return result.messages;
}

describe('design-system ESLint rules (m6)', () => {
  it('flags emoji in a JSX attribute value, a string literal and JSX text', async () => {
    const messages = await lintSource(`
      export const A = () => <span aria-label="⭐">✅ done</span>;
      const label = '🏠 Nhà';
      export const B = () => <p>{label}</p>;
    `);
    const emoji = messages.filter((m) => m.ruleId === 'no-restricted-syntax');
    expect(emoji.length).toBeGreaterThanOrEqual(3);
  });

  it('flags a geometric-shape bullet used as an icon (the ● this rule used to miss)', async () => {
    const messages = await lintSource(`export const A = () => <span>● bullet</span>;`);
    expect(messages.some((m) => m.ruleId === 'no-restricted-syntax')).toBe(true);
  });

  it('does not flag ordinary punctuation such as © or an arrow inside a comment', async () => {
    const messages = await lintSource(`
      // offline -> retried
      export const A = () => <p>© 2026 Nhà Đất Chuẩn</p>;
    `);
    expect(messages.filter((m) => m.ruleId === 'no-restricted-syntax')).toEqual([]);
  });

  it('flags text-[…rem] and text-[…em] below 12px, not only bare px', async () => {
    const messages = await lintSource(`
      export const A = () => <span className="text-[0.625rem]">tiny</span>;
      export const B = (x: boolean) => <p className={\`a \${x ? 'text-[0.7em]' : ''}\`}>x</p>;
    `);
    const tiny = messages.filter((m) => m.ruleId === 'local/no-tiny-text');
    expect(tiny).toHaveLength(2);
  });

  it('allows text-[…rem] at or above 12px', async () => {
    const messages = await lintSource(`export const A = () => <span className="text-[0.75rem]">ok</span>;`);
    expect(messages.filter((m) => m.ruleId === 'local/no-tiny-text')).toEqual([]);
  });

  it('flags an inline style fontSize below 12px (number or string)', async () => {
    const messages = await lintSource(`
      export const A = () => <span style={{ fontSize: 10 }}>tiny</span>;
      export const B = () => <span style={{ fontSize: '0.6em' }}>tiny</span>;
    `);
    expect(messages.filter((m) => m.ruleId === 'local/no-tiny-inline-font-size')).toHaveLength(2);
  });
});
