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
