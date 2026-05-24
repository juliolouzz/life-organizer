import {
  monthRangeForBoundary,
  previousMonthRangeForBoundary,
  rangeForPreset,
  rangeForPresetWithBoundary,
  toIso
} from './period';

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

  describe('monthRangeForBoundary', () => {
    it('boundary 1 collapses to a calendar month', () => {
      const r = monthRangeForBoundary(may22, 1);
      expect(toIso(r.from)).toBe('2026-05-01');
      expect(toIso(r.to)).toBe('2026-05-31');
    });

    it('boundary 28, today is mid-cycle -> previous-anchor cycle', () => {
      // May 15: anchor 28 falls on Apr 28 (Tue) -> [Apr 28, May 27]
      const r = monthRangeForBoundary(new Date(2026, 4, 15), 28);
      expect(toIso(r.from)).toBe('2026-04-28');
      expect(toIso(r.to)).toBe('2026-05-27');
    });

    it('boundary 28, today equals anchor -> new cycle starts today', () => {
      // May 28 is the anchor; next anchor Jun 28 falls on Sunday, snaps back to
      // Fri Jun 26 -> cycle ends Thu Jun 25.
      const r = monthRangeForBoundary(new Date(2026, 4, 28), 28);
      expect(toIso(r.from)).toBe('2026-05-28');
      expect(toIso(r.to)).toBe('2026-06-25');
    });

    it('boundary 28, today past anchor -> current cycle from anchor', () => {
      const r = monthRangeForBoundary(new Date(2026, 4, 30), 28);
      expect(toIso(r.from)).toBe('2026-05-28');
      expect(toIso(r.to)).toBe('2026-06-25');
    });

    it('anchor lands on Saturday -> snaps back to Friday', () => {
      // Feb 28 2026 is a Saturday -> snap to Fri Feb 27.
      // For "today = Mar 5 2026", Mar 28 is also Saturday -> snap to Fri Mar 27.
      const r = monthRangeForBoundary(new Date(2026, 2, 5), 28);
      expect(toIso(r.from)).toBe('2026-02-27');
      expect(toIso(r.to)).toBe('2026-03-26'); // Mar 27 - 1
    });

    it('anchor lands on Sunday -> snaps back to Friday', () => {
      // Mar 1 2026 is a Sunday. boundary=1 -> snap to Fri Feb 27.
      const r = monthRangeForBoundary(new Date(2026, 2, 10), 1);
      // Cycle anchored at Sun Mar 1 -> Fri Feb 27, next anchor Sun Apr 1 -> ?
      // Actually April 1 2026 is a Wednesday so no snap; cycle = [Feb 27, Mar 31].
      expect(toIso(r.from)).toBe('2026-02-27');
      expect(toIso(r.to)).toBe('2026-03-31');
    });

    it('boundary 31 clamps to last day of short months', () => {
      // April has 30 days; boundary 31 clamps to Apr 30.
      // For "today = Apr 15", current cycle starts at Mar 31 (which is a Tue).
      const r = monthRangeForBoundary(new Date(2026, 3, 15), 31);
      expect(toIso(r.from)).toBe('2026-03-31');
      expect(toIso(r.to)).toBe('2026-04-29'); // Apr 30 - 1
    });
  });

  describe('previousMonthRangeForBoundary', () => {
    it('returns the cycle immediately preceding the current one', () => {
      // May 15, boundary 28 -> current [Apr 28, May 27]. The previous anchor
      // (Mar 28) is a Saturday, snaps to Fri Mar 27 -> previous [Mar 27, Apr 27].
      const r = previousMonthRangeForBoundary(new Date(2026, 4, 15), 28);
      expect(toIso(r.from)).toBe('2026-03-27');
      expect(toIso(r.to)).toBe('2026-04-27');
    });
  });

  describe('rangeForPresetWithBoundary', () => {
    it('routes this_month / last_month through the boundary helpers', () => {
      const t = rangeForPresetWithBoundary('this_month', 28, new Date(2026, 4, 15));
      expect(toIso(t.from)).toBe('2026-04-28');
      const l = rangeForPresetWithBoundary('last_month', 28, new Date(2026, 4, 15));
      expect(toIso(l.from)).toBe('2026-03-27'); // snapped from Sat Mar 28
    });

    it('delegates other presets to the calendar helpers', () => {
      const r = rangeForPresetWithBoundary('this_year', 28, new Date(2026, 4, 15));
      expect(toIso(r.from)).toBe('2026-01-01');
    });
  });
});
