import type { UiAllowRule } from './targetAudit';

/**
 * Accepted DS-03 exceptions, each with its reason (ux-audit.spec.ts, ux-dialogs.spec.ts). Keep this list short: fix
 * the component instead wherever possible. Every entry must say why the exception is acceptable.
 */
export const ALLOW: UiAllowRule[] = [];
