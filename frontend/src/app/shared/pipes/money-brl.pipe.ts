import { Pipe, PipeTransform } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Currency-aware money pipe (Slice 13). Defaults to the signed-in user's
 * preference via {@link AuthService.currencySymbol} + {@link AuthService.currencyLocale},
 * falling back to BRL when no user is authenticated.
 *
 * <p>The pipe is intentionally <strong>impure</strong>: a pure pipe caches by
 * input value, so changing the user's currency without changing the amount
 * would not re-trigger transform(). Money formatting is cheap; running on
 * every change-detection cycle is the right trade-off for "the whole UI
 * reformats the moment I pick a new currency in /profile".
 *
 * <p>The pipe name stays <code>moneyBrl</code> to avoid a sweeping rename
 * across every dashboard / list / form template.
 */
@Pipe({ name: 'moneyBrl', standalone: true, pure: false })
export class MoneyBrlPipe implements PipeTransform {

  constructor(private readonly auth: AuthService) {}

  transform(
    value: string | number | null | undefined,
    withSign: 'INCOME' | 'EXPENSE' | 'SAVINGS' | null = null
  ): string {
    if (value === null || value === undefined || value === '') return '';
    const num = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(num)) return '';

    const symbol = this.auth.currencySymbol();
    const locale = this.auth.currencyLocale();

    const formatted = new Intl.NumberFormat(locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(num);

    switch (withSign) {
      case 'INCOME': return `+ ${symbol} ${formatted}`;
      case 'EXPENSE': return `- ${symbol} ${formatted}`;
      case 'SAVINGS': return `>> ${symbol} ${formatted}`;
      default: return `${symbol} ${formatted}`;
    }
  }
}
