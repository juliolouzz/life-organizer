import { rangeForPreset, toIso } from './period';

describe('period helpers', () => {
  const may22 = new Date(2026, 4, 22); // months are 0-indexed

  describe('toIso', () => {
    it('formats YYYY-MM-DD with zero padding', () => {
      expect(toIso(new Date(2026, 0, 5))).toBe('2026-01-05');
      expect(toIso(new Date(2026, 11, 31))).toBe('2026-12-31');
    });
  });

  describe('rangeForPreset', () => {
    it('this_month: 1st of the current month to today', () => {
      const r = rangeForPreset('this_month', may22);
      expect(toIso(r.from)).toBe('2026-05-01');
      expect(toIso(r.to)).toBe('2026-05-22');
    });

    it('last_month: full previous month, regardless of today', () => {
      const r = rangeForPreset('last_month', may22);
      expect(toIso(r.from)).toBe('2026-04-01');
      expect(toIso(r.to)).toBe('2026-04-30');
    });

    it('last_3_months: 1st of the month two before, through today', () => {
      const r = rangeForPreset('last_3_months', may22);
      expect(toIso(r.from)).toBe('2026-03-01');
      expect(toIso(r.to)).toBe('2026-05-22');
    });

    it('this_year: Jan 1 to today', () => {
      const r = rangeForPreset('this_year', may22);
      expect(toIso(r.from)).toBe('2026-01-01');
      expect(toIso(r.to)).toBe('2026-05-22');
    });

    it('all_time: 2000-01-01 to today', () => {
      const r = rangeForPreset('all_time', may22);
      expect(toIso(r.from)).toBe('2000-01-01');
      expect(toIso(r.to)).toBe('2026-05-22');
    });
  });
});
