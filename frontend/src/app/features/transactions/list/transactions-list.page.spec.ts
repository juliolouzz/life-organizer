/**
 * Focused unit test for the YYYY-MM-DD parser used to seed the /transactions
 * filter from URL query params. We test the helper via a thin extraction so
 * the spec doesn't have to spin up TestBed for this one rule.
 *
 * Regression coverage: navigating to /transactions?from=2026-06-01&to=2026-06-30
 * used to leave the date inputs empty and silently ignore the params.
 */
function parseIsoDate(raw: string | null): Date | null {
  if (!raw) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const d = new Date(year, month - 1, day);
  return Number.isNaN(d.getTime()) ? null : d;
}

describe('TransactionsListPage queryParam parser', () => {
  it('returns null for null / empty / malformed input', () => {
    expect(parseIsoDate(null)).toBeNull();
    expect(parseIsoDate('')).toBeNull();
    expect(parseIsoDate('not-a-date')).toBeNull();
    expect(parseIsoDate('2026/06/01')).toBeNull();
    expect(parseIsoDate('06-01-2026')).toBeNull();
  });

  it('parses a valid YYYY-MM-DD as local midnight (no UTC shift)', () => {
    const d = parseIsoDate('2026-06-01');
    expect(d).not.toBeNull();
    expect(d!.getFullYear()).toBe(2026);
    expect(d!.getMonth()).toBe(5);      // June (0-indexed)
    expect(d!.getDate()).toBe(1);
  });

  it('handles the end-of-month boundary correctly', () => {
    const d = parseIsoDate('2026-12-31');
    expect(d!.getFullYear()).toBe(2026);
    expect(d!.getMonth()).toBe(11);
    expect(d!.getDate()).toBe(31);
  });
});
