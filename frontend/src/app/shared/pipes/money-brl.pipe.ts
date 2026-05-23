import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a money string ("42.50") as "R$ 42,50" using BRL conventions.
 * Always renders exactly two decimal places and inserts a thin space after R$.
 */
@Pipe({ name: 'moneyBrl', standalone: true })
export class MoneyBrlPipe implements PipeTransform {
  transform(
    value: string | number | null | undefined,
    withSign: 'INCOME' | 'EXPENSE' | 'SAVINGS' | null = null
  ): string {
    if (value === null || value === undefined || value === '') return '';
    const num = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(num)) return '';

    const formatted = new Intl.NumberFormat('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(num);

    switch (withSign) {
      case 'INCOME': return `+ R$ ${formatted}`;
      case 'EXPENSE': return `- R$ ${formatted}`;
      case 'SAVINGS': return `>> R$ ${formatted}`;
      default: return `R$ ${formatted}`;
    }
  }
}
