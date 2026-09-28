/**
 * Analytics consent (contract §5/§12, Decree 13/2023/NĐ-CP). Analytics is non-essential processing, so it is opt-in:
 * nothing is sent, and no analytics identifier is written to the browser, until the visitor chooses "granted".
 *
 * The choice lives in localStorage under `bds.consent.analytics` ("granted" | "denied") together with the policy
 * version it was given for (`bds.consent.analytics.version`). A choice made for an older policy version counts as
 * no choice, so the banner asks again when the policy changes. This module is the single place that reads and writes
 * the choice; the banner (features/consent) records each decision on the server as proof of consent.
 */
export const CONSENT_STORAGE_KEY = 'bds.consent.analytics';
export const CONSENT_VERSION_KEY = 'bds.consent.analytics.version';
/** Random id of this browser's consent decisions (proof of consent); created when the visitor decides. */
export const CONSENT_ID_KEY = 'bds.consent.id';
export const ANONYMOUS_ID_KEY = 'bds.analytics.anonymousId';
export const SESSION_KEY = 'bds.analytics.session';
export const UTM_KEY = 'bds.analytics.utm';
export const CONSENT_CHANGE_EVENT = 'bds:consent-change';
/** Dispatched (e.g. by the footer link) to open the privacy preferences dialog. */
export const OPEN_CONSENT_PREFERENCES_EVENT = 'bds:open-consent-preferences';
/** Version of the analytics notice shown in the banner; bump it (YYYY-MM-DD) whenever purposes or retention change. */
export const CONSENT_POLICY_VERSION = '2026-09-28';

export type AnalyticsConsent = 'granted' | 'denied';

function safeGet(storage: Storage | undefined, key: string): string | null {
  try {
    return storage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function safeSet(storage: Storage | undefined, key: string, value: string): void {
  try {
    storage?.setItem(key, value);
  } catch {
    // Without storage the choice cannot persist; effective consent stays "denied".
  }
}

function safeRemove(storage: Storage | undefined, key: string): void {
  try {
    storage?.removeItem(key);
  } catch {
    // Storage can be unavailable (private mode, blocked site data); nothing to clear then.
  }
}

const local = () => (typeof window === 'undefined' ? undefined : window.localStorage);
const session = () => (typeof window === 'undefined' ? undefined : window.sessionStorage);

/** The visitor's explicit choice for the current policy, or `null` when they have not decided yet (the banner asks). */
export function getStoredAnalyticsConsent(): AnalyticsConsent | null {
  const value = safeGet(local(), CONSENT_STORAGE_KEY);
  if (value !== 'granted' && value !== 'denied') return null;
  return safeGet(local(), CONSENT_VERSION_KEY) === CONSENT_POLICY_VERSION ? value : null;
}

/** Effective consent: only an explicit "granted" for the current policy allows analytics. */
export function getAnalyticsConsent(): AnalyticsConsent {
  return getStoredAnalyticsConsent() === 'granted' ? 'granted' : 'denied';
}

/** Removes every analytics identifier kept in the browser. */
export function clearAnalyticsIdentifiers(): void {
  safeRemove(local(), ANONYMOUS_ID_KEY);
  safeRemove(session(), SESSION_KEY);
  safeRemove(session(), UTM_KEY);
}

export function setAnalyticsConsent(consent: AnalyticsConsent): void {
  safeSet(local(), CONSENT_STORAGE_KEY, consent);
  safeSet(local(), CONSENT_VERSION_KEY, CONSENT_POLICY_VERSION);
  if (consent === 'denied') clearAnalyticsIdentifiers();
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent<AnalyticsConsent>(CONSENT_CHANGE_EVENT, { detail: consent }));
  }
}

/** The random consent id, created on the first decision (never before: it is only needed as proof of a decision). */
export function consentDecisionId(randomId: () => string): string {
  const existing = safeGet(local(), CONSENT_ID_KEY);
  if (existing && /^[A-Za-z0-9_-]{8,64}$/.test(existing)) return existing;
  const created = randomId();
  safeSet(local(), CONSENT_ID_KEY, created);
  return created;
}

export function openConsentPreferences(): void {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(OPEN_CONSENT_PREFERENCES_EVENT));
}

/** Notifies on changes in this tab and in other tabs (storage event). Returns an unsubscribe function. */
export function onAnalyticsConsentChange(listener: (consent: AnalyticsConsent) => void): () => void {
  if (typeof window === 'undefined') return () => undefined;
  const onLocal = () => listener(getAnalyticsConsent());
  const onStorage = (event: StorageEvent) => {
    if (event.key === CONSENT_STORAGE_KEY || event.key === CONSENT_VERSION_KEY) listener(getAnalyticsConsent());
  };
  window.addEventListener(CONSENT_CHANGE_EVENT, onLocal);
  window.addEventListener('storage', onStorage);
  return () => {
    window.removeEventListener(CONSENT_CHANGE_EVENT, onLocal);
    window.removeEventListener('storage', onStorage);
  };
}
