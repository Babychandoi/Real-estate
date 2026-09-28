import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EMPTY_DRAFT } from './api';

const calls: Array<{ version: number | null }> = [];
let release: Array<() => void> = [];

vi.mock('./api', async (original) => {
  const actual = (await original()) as Record<string, unknown>;
  return {
    ...actual,
    saveDraft: vi.fn((_id: string | null, version: number | null) => {
      calls.push({ version });
      return new Promise((resolve) => {
        release.push(() => resolve({ listingId: 'l1', version: (version ?? -1) + 1 }));
      });
    }),
  };
});

const { useDraftAutosave } = await import('./useDraftAutosave');

describe('useDraftAutosave', () => {
  it('never sends the same version twice when three saves overlap', async () => {
    const fields = { ...EMPTY_DRAFT, title: 'Căn hộ hai phòng ngủ', priceVnd: 1, areaM2: 50 };
    const { result } = renderHook(() =>
      useDraftAutosave({ fields, listingId: 'l1', version: 3, onCreated: () => undefined, enabled: false }),
    );
    let first!: Promise<boolean>;
    let second!: Promise<boolean>;
    let third!: Promise<boolean>;
    act(() => {
      first = result.current.saveNow();
      second = result.current.saveNow();
      third = result.current.saveNow();
    });
    await act(async () => {
      release.shift()?.();
      await first;
    });
    await act(async () => {
      release.shift()?.();
      await second;
    });
    await act(async () => {
      release.shift()?.();
      await third;
    });
    // The second caller waited: either it had nothing left to save or it used the new version.
    expect(calls.map((call) => call.version)).not.toEqual([3, 3]);
    expect(new Set(calls.map((call) => call.version)).size).toBe(calls.length);
    release = [];
  });
});
