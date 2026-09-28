import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AUTOSAVE_BLOCKING,
  OfflineError,
  saveDraft,
  serverFieldErrors,
  validateDraft,
  VersionConflictError,
  type DraftFields,
  type FieldErrors,
} from './api';
import { validationMessage } from '@/shared/types/problem-details';

export type SaveState =
  | { kind: 'idle' }
  | { kind: 'saving' }
  | { kind: 'saved'; at: Date }
  | { kind: 'error'; message: string }
  | { kind: 'offline' }
  | { kind: 'conflict'; currentVersion: number | null };

interface Options {
  fields: DraftFields;
  /** Initial ids (read once); later changes go through {@link reset}. */
  listingId: string | null;
  version: number | null;
  /** Called after the first save created the listing (the page puts the id in the URL so a reload keeps it). */
  onCreated: (listingId: string) => void;
  delayMs?: number;
  enabled: boolean;
}

/**
 * Debounced autosave with explicit states for the UI: saving, saved at, offline (retries when the browser is back
 * online), error with retry, and a version conflict that the page resolves in a dialog. One save runs at a time; edits
 * made while it runs are saved right after with the new version.
 */
export function useDraftAutosave({ fields, listingId, version, onCreated, delayMs = 1500, enabled }: Options) {
  const [state, setState] = useState<SaveState>({ kind: 'idle' });
  const [serverErrors, setServerErrors] = useState<FieldErrors>({});
  const ids = useRef({ listingId, version });
  const inFlight = useRef<Promise<boolean> | null>(null);
  const dirty = useRef(false);
  const latest = useRef(fields);
  const firstRender = useRef(true);
  latest.current = fields;

  const blocked = AUTOSAVE_BLOCKING.some((key) => validateDraft(fields)[key]);

  const saveNow = useCallback(async (): Promise<boolean> => {
    if (inFlight.current) {
      await inFlight.current;
    }
    if (AUTOSAVE_BLOCKING.some((key) => validateDraft(latest.current)[key])) return false;
    const snapshot = latest.current;
    dirty.current = false;
    setState({ kind: 'saving' });
    const run = (async () => {
      try {
        const saved = await saveDraft(ids.current.listingId, ids.current.version, snapshot);
        const created = ids.current.listingId == null;
        ids.current = { listingId: saved.listingId, version: saved.version };
        if (created) onCreated(saved.listingId);
        setServerErrors({});
        setState({ kind: 'saved', at: new Date() });
        return true;
      } catch (error) {
        dirty.current = true;
        if (error instanceof VersionConflictError) setState({ kind: 'conflict', currentVersion: error.currentVersion });
        else if (error instanceof OfflineError || (typeof navigator !== 'undefined' && !navigator.onLine))
          setState({ kind: 'offline' });
        else {
          setServerErrors(serverFieldErrors(error));
          setState({
            kind: 'error',
            message: validationMessage(error, 'Chưa lưu được bản nháp. Nội dung vẫn còn trên trang này.'),
          });
        }
        return false;
      } finally {
        inFlight.current = null;
      }
    })();
    inFlight.current = run;
    return run;
  }, [onCreated]);

  // Debounced save after each change.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    dirty.current = true;
    if (!enabled || blocked || state.kind === 'conflict') return;
    const timer = window.setTimeout(() => void saveNow(), delayMs);
    return () => window.clearTimeout(timer);
    // `state` is read only to pause during a conflict; re-running on every state change would loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, enabled, blocked, delayMs, saveNow]);

  // Back online: retry the pending save.
  useEffect(() => {
    const retry = () => {
      if (dirty.current && enabled) void saveNow();
    };
    window.addEventListener('online', retry);
    return () => window.removeEventListener('online', retry);
  }, [enabled, saveNow]);

  // Warn before leaving with unsaved edits.
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty.current && !blocked) event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [blocked]);

  /** After a conflict: adopt the server version so the next save overwrites it (the user chose to keep their text). */
  const adoptVersion = useCallback((next: number) => {
    ids.current = { ...ids.current, version: next };
    setState({ kind: 'idle' });
  }, []);

  /** A draft was (re)loaded from the server: its fields are the saved state. */
  const reset = useCallback((nextListingId: string | null, nextVersion: number | null) => {
    ids.current = { listingId: nextListingId, version: nextVersion };
    dirty.current = false;
    firstRender.current = true;
    setState({ kind: 'idle' });
  }, []);

  return { state, serverErrors, saveNow, adoptVersion, reset, isDirty: () => dirty.current, blocked };
}
