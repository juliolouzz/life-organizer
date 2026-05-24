export type PeriodPreset =
  | 'this_month'
  | 'last_month'
  | 'last_3_months'
  | 'this_year'
  | 'all_time'
  | 'custom';

export interface DateRange {
  from: Date;
  to: Date;
}

function startOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}
function endOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

/**
 * Returns the date range for a built-in preset using calendar months.
 *
 * For users with a custom month boundary day (Slice 14), prefer
 * {@link rangeForPresetWithBoundary} so the "this_month" / "last_month"
 * presets follow the user's accounting cycle.
 */
export function rangeForPreset(preset: PeriodPreset, today = new Date()): DateRange {
  const start = (y: number, m: number, day: number) => new Date(y, m, day);
  switch (preset) {
    case 'this_month':
      return { from: startOfMonth(today), to: today };
    case 'last_month': {
      const lastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      return { from: startOfMonth(lastMonth), to: endOfMonth(lastMonth) };
    }
    case 'last_3_months': {
      const from = new Date(today.getFullYear(), today.getMonth() - 2, 1);
      return { from, to: today };
    }
    case 'this_year':
      return { from: start(today.getFullYear(), 0, 1), to: today };
    case 'all_time':
      // Spec section 4.2: a custom date range is recommended for "all time"; we use the
      // earliest sensible date and today. The backend has no rows older than user creation
      // so the query naturally returns everything.
      return { from: start(2000, 0, 1), to: today };
    case 'custom':
      return { from: startOfMonth(today), to: today };
  }
}

export function toIso(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

// ----------------------------------------------------------------------
// Slice 14: custom accounting month
// ----------------------------------------------------------------------

/**
 * Day of week as returned by Date.getDay() - 0=Sun, 6=Sat.
 * Snaps weekend dates BACK to the most recent past working day:
 * Saturday -> Friday (-1), Sunday -> Friday (-2). Returns the input
 * unchanged for Monday-Friday.
 */
function snapToPreviousWorkingDay(d: Date): Date {
  const dow = d.getDay();
  if (dow === 6) return new Date(d.getFullYear(), d.getMonth(), d.getDate() - 1);
  if (dow === 0) return new Date(d.getFullYear(), d.getMonth(), d.getDate() - 2);
  return d;
}

/**
 * Returns the anchor date in a given calendar (year, month), clamped to the
 * last day of that month when boundaryDay > last-day-of-month (Feb 30/31 etc).
 * The returned date is then snapped backward to the previous working day if
 * it falls on a weekend.
 */
function anchorIn(year: number, month: number, boundaryDay: number): Date {
  const lastDay = new Date(year, month + 1, 0).getDate();
  const day = Math.min(boundaryDay, lastDay);
  return snapToPreviousWorkingDay(new Date(year, month, day));
}

/**
 * "Previous-anchor" semantics: returns [previousAnchor, nextAnchor - 1] - the
 * accounting month whose start is the most recent past (or today) anchor.
 *
 * Examples (boundaryDay = 28):
 *   today = 2026-05-15 -> [2026-04-28, 2026-05-27]
 *   today = 2026-05-28 -> [2026-05-28, 2026-06-27]
 *   today = 2026-05-30 -> [2026-05-28, 2026-06-27]
 *
 * boundaryDay = 1 collapses to a regular calendar month: [1st, last-of-month].
 */
export function monthRangeForBoundary(today: Date, boundaryDay: number): DateRange {
  const year = today.getFullYear();
  const month = today.getMonth();

  // Snap can push the cycle's actual start date back into the previous calendar
  // month, so we have to track the "anchor month" (the calendar month whose
  // boundaryDay anchors this cycle) separately from the start date itself.
  const thisMonthAnchor = anchorIn(year, month, boundaryDay);
  const todayDateOnly = new Date(year, month, today.getDate());

  let anchorYear: number;
  let anchorMonth: number;
  if (todayDateOnly.getTime() >= thisMonthAnchor.getTime()) {
    anchorYear = year;
    anchorMonth = month;
  } else {
    const prev = new Date(year, month - 1, 1);
    anchorYear = prev.getFullYear();
    anchorMonth = prev.getMonth();
  }

  const cycleStart = anchorIn(anchorYear, anchorMonth, boundaryDay);
  // The cycle ends the day before the NEXT cycle's snapped start. Cycles always
  // partition the timeline without overlaps or gaps - if Jun 28 falls on Sunday
  // and snaps to Fri Jun 26, then the previous cycle ends Thu Jun 25.
  const nextAnchor = anchorIn(anchorYear, anchorMonth + 1, boundaryDay);
  const cycleEnd = new Date(
    nextAnchor.getFullYear(),
    nextAnchor.getMonth(),
    nextAnchor.getDate() - 1
  );

  return { from: cycleStart, to: cycleEnd };
}

/**
 * The accounting month immediately preceding {@link monthRangeForBoundary}.
 */
export function previousMonthRangeForBoundary(today: Date, boundaryDay: number): DateRange {
  const current = monthRangeForBoundary(today, boundaryDay);
  // Take "yesterday relative to the current cycle start" and ask which cycle
  // contains it. That's always the previous one.
  const dayBeforeCurrent = new Date(
    current.from.getFullYear(),
    current.from.getMonth(),
    current.from.getDate() - 1
  );
  return monthRangeForBoundary(dayBeforeCurrent, boundaryDay);
}

/**
 * Drop-in replacement for {@link rangeForPreset} that honours the user's
 * accounting cycle for "this_month" / "last_month". Other presets are
 * delegated to the calendar-month version because they don't make sense
 * relative to a custom boundary (last_3_months, this_year, all_time).
 */
export function rangeForPresetWithBoundary(
  preset: PeriodPreset,
  boundaryDay: number,
  today = new Date()
): DateRange {
  if (preset === 'this_month') return monthRangeForBoundary(today, boundaryDay);
  if (preset === 'last_month') return previousMonthRangeForBoundary(today, boundaryDay);
  return rangeForPreset(preset, today);
}
