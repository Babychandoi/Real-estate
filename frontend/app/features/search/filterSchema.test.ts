// @vitest-environment node
import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import {
  activeFilterCount,
  canonicalJson,
  DEFAULT_FILTERS,
  effectiveSort,
  filterHash,
  normalizeKeyword,
  parseSearchParams,
  pricePresets,
  serializeFilters,
  toApiParams,
  withoutParams,
  withPurpose,
  type SearchFilters,
} from './filterSchema';

const parse = (query: string) => parseSearchParams(new URLSearchParams(query));

describe('filterSchema (contract §7, F03.1)', () => {
  it('parses a complete URL and serializes it back to the same canonical query', () => {
    const query =
      'purpose=RENT&type=HOUSE,APARTMENT&priceMin=5000000&priceMax=15000000&areaMin=50.5&bedsMin=2&legal=PINK_BOOK&' +
      'furnishing=FULL,BASIC&verified=IDENTITY&district=006,005&q=C%E1%BA%A7u+Gi%E1%BA%A5y&' +
      'bbox=105.78,21.02,105.82,21.05&sort=PRICE_ASC&view=split&place=C%E1%BA%A7u+Gi%E1%BA%A5y';
    const { filters, errors } = parse(query);
    expect(errors).toEqual([]);
    expect(filters).toMatchObject({
      purpose: 'RENT',
      types: ['APARTMENT', 'HOUSE'],
      priceMin: 5_000_000,
      priceMax: 15_000_000,
      areaMin: 50.5,
      bedsMin: 2,
      legal: ['PINK_BOOK'],
      furnishing: ['BASIC', 'FULL'],
      verified: 'IDENTITY',
      districts: ['005', '006'],
      q: 'Cầu Giấy',
      bbox: [105.78, 21.02, 105.82, 21.05],
      sort: 'PRICE_ASC',
      view: 'split',
      place: 'Cầu Giấy',
    });
    const again = parseSearchParams(serializeFilters(filters)).filters;
    expect(again).toEqual(filters);
    expect(serializeFilters(again).toString()).toBe(serializeFilters(filters).toString());
  });

  it('drops invalid values and reports each of them instead of guessing', () => {
    const { filters, errors } = parse(
      'purpose=BUY&type=CASTLE,HOUSE&priceMin=abc&areaMin=-1&bedsMin=12&legal=BLUE&verified=YES&district=5&' +
        'bbox=100,10,110,20&sort=RELEVANCE&view=grid&foo=1&priceMax=1&priceMin=9',
    );
    expect(filters.purpose).toBe('SALE');
    expect(filters.types).toEqual(['HOUSE']);
    expect(filters.bbox).toBeUndefined();
    expect(filters.sort).toBeUndefined();
    expect(errors.map((e) => e.param)).toEqual(
      expect.arrayContaining([
        'purpose',
        'type',
        'areaMin',
        'bedsMin',
        'legal',
        'verified',
        'district',
        'bbox',
        'sort',
        'view',
        'foo',
      ]),
    );
  });

  it('rejects a price range whose minimum exceeds the maximum', () => {
    const { filters, errors } = parse('priceMin=9000000000&priceMax=1000000000');
    expect(filters.priceMin).toBe(9_000_000_000);
    expect(filters.priceMax).toBeUndefined();
    expect(errors).toEqual([{ param: 'priceMax', message: expect.stringContaining('lớn hơn') }]);
  });

  it('normalises keywords exactly like the backend', () => {
    expect(normalizeKeyword('  Căn hộ  CẦU GIẤY, 3PN ')).toBe('can ho cau giay 3pn');
    expect(normalizeKeyword('Đường Nguyễn Trãi – Thanh Xuân')).toBe('duong nguyen trai thanh xuan');
    expect(normalizeKeyword('Hoà Bình'.normalize('NFD'))).toBe('hoa binh');
    expect(normalizeKeyword(' - ')).toBeNull();
  });

  it('keeps the API query free of UI-only parameters and adds paging', () => {
    const { filters } = parse('purpose=SALE&q=nha&view=map&place=X&bbox=105.7,21,105.9,21.1');
    const api = toApiParams(filters, { size: 24, cursor: 'abc.def' });
    expect(api.get('view')).toBeNull();
    expect(api.get('place')).toBeNull();
    expect(api.get('size')).toBe('24');
    expect(api.get('cursor')).toBe('abc.def');
    expect(api.get('bbox')).toBe('105.7,21,105.9,21.1');
    expect(serializeFilters(filters).get('cursor')).toBeNull();
  });

  it('computes the same filter hash as the backend (shared test vector)', async () => {
    const filters: SearchFilters = { ...DEFAULT_FILTERS, purpose: 'RENT', priceMax: 15_000_000, bedsMin: 2 };
    const json = '{"bedsMin":"2","priceMax":"15000000","purpose":"RENT","sort":"NEWEST"}';
    expect(canonicalJson(filters)).toBe(json);
    // SearchFilterParserTests.hashOfTheCanonicalJsonIsStable asserts the same JSON on the backend.
    expect(await filterHash(filters)).toBe(createHash('sha256').update(json).digest('hex').slice(0, 32));
  });

  it('ignores order, CSV order, view and place in the hash; keyword accents do not matter', async () => {
    const a = parse('type=HOUSE,APARTMENT&district=006&district=005&q=C%E1%BA%A7u+Gi%E1%BA%A5y&view=map').filters;
    const b = parse('q=cau+giay&district=005,006&type=APARTMENT,HOUSE').filters;
    expect(await filterHash(a)).toBe(await filterHash(b));
    expect(effectiveSort(a)).toBe('RELEVANCE');
    expect(await filterHash({ ...a, purpose: 'RENT' })).not.toBe(await filterHash(a));
  });

  it('changing purpose clears the price range (F04.2)', () => {
    const sale = parse('purpose=SALE&priceMin=2000000000&priceMax=4999999999&sort=PRICE_ASC').filters;
    const rent = withPurpose(sale, 'RENT');
    expect(rent.priceMin).toBeUndefined();
    expect(rent.priceMax).toBeUndefined();
    expect(rent.sort).toBe('PRICE_ASC');
  });

  it('offers per-month rent bands and sale bands that never overlap', () => {
    const rent = pricePresets('RENT', []);
    expect(rent[0].label).toContain('triệu/tháng');
    expect(rent.every((preset) => (preset.max ?? 0) < 1_000_000_000)).toBe(true);
    for (const presets of [
      rent,
      pricePresets('SALE', []),
      pricePresets('SALE', ['APARTMENT']),
      pricePresets('RENT', ['VILLA']),
    ]) {
      for (let i = 1; i < presets.length; i++) expect(presets[i].min).toBe(presets[i - 1].max! + 1);
    }
    expect(pricePresets('SALE', ['VILLA'])[0].label).toBe('Dưới 10 tỷ');
    expect(pricePresets('SALE', ['APARTMENT'])[1].label).toBe('2 – dưới 4 tỷ');
  });

  it('drops the parameters of a zero-result suggestion', () => {
    const filters = parse(
      'q=abc&priceMin=1&priceMax=2&bbox=105.7,21,105.9,21.1&place=P&sort=RELEVANCE&bedsMin=3',
    ).filters;
    const relaxed = withoutParams(filters, ['priceMin', 'priceMax', 'q', 'bbox']);
    expect(relaxed.priceMin).toBeUndefined();
    expect(relaxed.q).toBeUndefined();
    expect(relaxed.sort).toBeUndefined();
    expect(relaxed.place).toBeUndefined();
    expect(relaxed.bedsMin).toBe(3);
    expect(activeFilterCount(relaxed)).toBe(1);
  });
});
