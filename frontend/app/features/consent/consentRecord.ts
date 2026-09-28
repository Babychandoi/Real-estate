import { apiFetch } from '@/shared/api/client';
import {
  CONSENT_POLICY_VERSION,
  consentDecisionId,
  setAnalyticsConsent,
  type AnalyticsConsent,
} from '@/shared/analytics/consent';

export type ConsentSource = 'banner' | 'preferences';

function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  return `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Applies the visitor's decision in the browser and records it on the server as proof of consent (Decree 13/2023
 * Art. 11). The server record is best effort: the decision applies in the browser even when the request fails.
 */
export async function decideAnalyticsConsent(
  consent: AnalyticsConsent,
  source: ConsentSource,
  send: typeof apiFetch = apiFetch,
): Promise<void> {
  setAnalyticsConsent(consent);
  try {
    await send('/events/consent', {
      method: 'POST',
      keepalive: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        consentId: consentDecisionId(randomId),
        purpose: 'analytics',
        choice: consent === 'granted' ? 'granted' : 'withdrawn',
        policyVersion: CONSENT_POLICY_VERSION,
        source,
      }),
    });
  } catch {
    // Offline or blocked: the local decision stands; the next decision is recorded again.
  }
}
