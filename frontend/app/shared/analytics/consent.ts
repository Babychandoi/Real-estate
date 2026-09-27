/**
 * Analytics consent (contract §5/§12). The choice lives in localStorage under `bds.consent.analytics`
 * ("granted" | "denied"). No stored choice counts as denied: events are then sent without anonymous/session ids
 * and without UTM (the server stores them as consent "denied"). S8 owns the consent banner; this module is the
 * single place that reads and writes the choice.
 */
export const CONSENT_STORAGE_KEY = 'bds.consent.analytics';
export const ANONYMOUS_ID_KEY = 'bds.analytics.anonymousId';
export const SESSION_KEY = 'bds.analytics.session';
export const UTM_KEY = 'bds.analytics.utm';
export const CONSENT_CHANGE_EVENT = 'bds:consent-change';

export type AnalyticsConsent = 'granted' | 'denied';

function safeGet(storage: Storage | undefined, key: string): string | null {
  try {
    return storage?.getItem(key) ?? null;
  } catch {
    return null;
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

/** The visitor's explicit choice, or `null` when they have not decided yet (a banner should ask). */
export function getStoredAnalyticsConsent(): AnalyticsConsent | null {
  const value = safeGet(local(), CONSENT_STORAGE_KEY);
  return value === 'granted' || value === 'denied' ? value : null;
}

/** Effective consent: only an explicit "granted" allows identifiers. */
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
  try {
    local()?.setItem(CONSENT_STORAGE_KEY, consent);
  } catch {
    // Without storage the choice cannot persist; effective consent stays "denied".
  }
  if (consent === 'denied') clearAnalyticsIdentifiers();
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent<AnalyticsConsent>(CONSENT_CHANGE_EVENT, { detail: consent }));
  }
}

/** Notifies on changes in this tab and in other tabs (storage event). Returns an unsubscribe function. */
export function onAnalyticsConsentChange(listener: (consent: AnalyticsConsent) => void): () => void {
  if (typeof window === 'undefined') return () => undefined;
  const onLocal = () => listener(getAnalyticsConsent());
  const onStorage = (event: StorageEvent) => {
    if (event.key === CONSENT_STORAGE_KEY) listener(getAnalyticsConsent());
  };
  window.addEventListener(CONSENT_CHANGE_EVENT, onLocal);
  window.addEventListener('storage', onStorage);
  return () => {
    window.removeEventListener(CONSENT_CHANGE_EVENT, onLocal);
    window.removeEventListener('storage', onStorage);
  };
}
