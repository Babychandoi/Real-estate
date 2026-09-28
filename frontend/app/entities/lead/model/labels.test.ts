import { describe, expect, it } from 'vitest';
import { ApiProblemException } from '@/shared/types/problem-details';
import { OWNER_TRANSITIONS, formatMinutes, isVersionConflict, orNoData, responseTime, slotsToPayload } from './labels';

const NOW = new Date('2026-09-28T03:00:00Z'); // 10:00 in Vietnam

describe('slotsToPayload', () => {
  it('turns Vietnam-time drafts into sorted ISO instants', () => {
    const result = slotsToPayload(
      [
        { date: '2026-09-30', start: '15:00', minutes: 60 },
        { date: '2026-09-30', start: '09:30', minutes: 45 },
      ],
      NOW,
    );
    expect(result.error).toBeUndefined();
    expect(result.slots).toEqual([
      { startsAt: '2026-09-30T02:30:00.000Z', endsAt: '2026-09-30T03:15:00.000Z' },
      { startsAt: '2026-09-30T08:00:00.000Z', endsAt: '2026-09-30T09:00:00.000Z' },
    ]);
  });

  it('mirrors the server rules: lead time, horizon, overlap, count', () => {
    expect(slotsToPayload([{ date: '2026-09-28', start: '10:10', minutes: 30 }], NOW).error).toMatch(/30 phút/);
    expect(slotsToPayload([{ date: '2026-12-30', start: '10:00', minutes: 30 }], NOW).error).toMatch(/60 ngày/);
    expect(
      slotsToPayload(
        [
          { date: '2026-09-30', start: '09:00', minutes: 60 },
          { date: '2026-09-30', start: '09:30', minutes: 60 },
        ],
        NOW,
      ).error,
    ).toMatch(/chồng/);
    expect(slotsToPayload([{ date: '', start: '', minutes: 60 }], NOW).error).toMatch(/ít nhất một/);
    const four = Array.from({ length: 4 }, (_, index) => ({ date: '2026-10-01', start: `1${index}:00`, minutes: 30 }));
    expect(slotsToPayload(four, NOW).error).toMatch(/tối đa 3/);
  });
});

describe('labels', () => {
  it('shows unmeasured values as "Chưa có dữ liệu", never 0', () => {
    expect(orNoData(null)).toBe('Chưa có dữ liệu');
    expect(orNoData(0)).toBe('0');
    expect(orNoData(25, formatMinutes)).toBe('25 phút');
  });

  it('formats durations and response times', () => {
    expect(formatMinutes(185)).toBe('3 giờ 5 phút');
    expect(formatMinutes(120)).toBe('2 giờ');
    expect(formatMinutes(2 * 24 * 60)).toBe('2 ngày');
    expect(responseTime('2026-09-28T03:00:00Z', null)).toBeNull();
    expect(responseTime('2026-09-28T03:00:00Z', '2026-09-28T03:42:00Z')).toBe('42 phút');
  });

  it('keeps WITHDRAWN terminal and never offers NEW again', () => {
    expect(OWNER_TRANSITIONS.WITHDRAWN).toEqual([]);
    for (const targets of Object.values(OWNER_TRANSITIONS)) {
      expect(targets).not.toContain('NEW');
      expect(targets).not.toContain('WITHDRAWN');
    }
  });

  it('recognises compare-and-set conflicts', () => {
    const conflict = new ApiProblemException({ title: 'x', status: 409, detail: 'x', code: 'LEAD_VERSION_CONFLICT' });
    const other = new ApiProblemException({ title: 'x', status: 409, detail: 'x', code: 'LEAD_NOT_OPEN' });
    expect(isVersionConflict(conflict)).toBe(true);
    expect(isVersionConflict(other)).toBe(false);
    expect(isVersionConflict(new Error('network'))).toBe(false);
  });
});
