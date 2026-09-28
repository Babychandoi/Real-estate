import { describe, expect, it } from 'vitest';
import { draftPayload, EMPTY_DRAFT, validateDraft, type DraftFields } from './api';

const valid: DraftFields = {
  ...EMPTY_DRAFT,
  title: 'Căn hộ hai phòng ngủ Cầu Giấy',
  priceVnd: 3_950_000_000,
  areaM2: 72.5,
};

describe('listing draft helpers', () => {
  it('requires title, price and area and explains each next to its field', () => {
    const errors = validateDraft(EMPTY_DRAFT);
    expect(Object.keys(errors).sort()).toEqual(['areaM2', 'priceVnd', 'title']);
    expect(validateDraft({ ...valid, purpose: 'RENT', priceVnd: null }).priceVnd).toContain('mỗi tháng');
    expect(validateDraft(valid)).toEqual({});
  });

  it('asks for a legal detail only when the code is OTHER', () => {
    expect(validateDraft({ ...valid, legalStatusCode: 'OTHER' }).legalStatus).toBeDefined();
    expect(validateDraft({ ...valid, legalStatusCode: 'OTHER', legalStatus: 'Giấy phép xây dựng' })).toEqual({});
  });

  it('never sends rent terms for a sale and turns blanks into null', () => {
    const payload = draftPayload({
      ...valid,
      depositVnd: 10,
      monthlyServiceFeeVnd: 5,
      direction: '  ',
      furnishing: '',
    });
    expect(payload.depositVnd).toBeNull();
    expect(payload.monthlyServiceFeeVnd).toBeNull();
    expect(payload.direction).toBeNull();
    expect(payload.furnishing).toBeNull();
    const rent = draftPayload({ ...valid, purpose: 'RENT', depositVnd: 29_000_000 });
    expect(rent.depositVnd).toBe(29_000_000);
  });
});
