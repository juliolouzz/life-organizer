import { MoneyBrlPipe } from './money-brl.pipe';

// Normalises NBSP ( ) and other whitespace variants to a plain ' ' so locale-
// dependent test environments (jsdom vs Node vs browser) don't flake on currency
// formatting differences.
function normalize(s: string): string {
  return s.replace(/\s+/g, ' ').trim();
}

describe('MoneyBrlPipe', () => {
  const pipe = new MoneyBrlPipe();

  it('formats a string amount with comma decimal and dot grouping', () => {
    expect(normalize(pipe.transform('1234.5'))).toBe('R$ 1.234,50');
  });

  it('always renders two decimals', () => {
    expect(normalize(pipe.transform('10'))).toBe('R$ 10,00');
    expect(normalize(pipe.transform('10.5'))).toBe('R$ 10,50');
  });

  it('prefixes + for INCOME and - for EXPENSE', () => {
    expect(normalize(pipe.transform('10.00', 'INCOME'))).toBe('+ R$ 10,00');
    expect(normalize(pipe.transform('10.00', 'EXPENSE'))).toBe('- R$ 10,00');
  });

  it('returns empty string for blank / null / NaN', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('')).toBe('');
    expect(pipe.transform('not-a-number')).toBe('');
  });
});
