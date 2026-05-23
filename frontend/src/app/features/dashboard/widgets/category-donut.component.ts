import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  computed,
  inject,
  signal
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { NgChartsModule } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';

import { CategoryTotal } from '../insights.service';
import { MoneyBrlPipe } from '../../../shared/pipes/money-brl.pipe';
import { ThemeService } from '../../../core/theme/theme.service';

const TOP_N = 8;

// 9-colour palette (top 8 + Other). Picked for distinguishability on both light + dark.
const PALETTE = [
  '#7c3aed', '#06b6d4', '#ef4444', '#f59e0b', '#10b981',
  '#3b82f6', '#ec4899', '#8b5cf6', '#a3a3a3'
];

@Component({
  selector: 'app-category-donut',
  standalone: true,
  imports: [MatCardModule, NgChartsModule, MoneyBrlPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="donut-card">
      <header class="donut-head">
        <div>
          <h3>Spending by category</h3>
          <small class="subtitle">Expenses only · top {{ TOP_N }} + Other</small>
        </div>
      </header>

      @if (slices().length === 0) {
        <div class="empty">
          <span class="material-symbols-outlined">pie_chart</span>
          <p>No expenses in this period.</p>
        </div>
      } @else {
        <div class="donut-body">
          <div class="canvas-wrap">
            <canvas
              baseChart
              [type]="'doughnut'"
              [data]="chartData()"
              [options]="chartOptions()"
            ></canvas>
            <div class="centre">
              <small>Total</small>
              <strong class="numeric">{{ totalExpense() | moneyBrl }}</strong>
            </div>
          </div>
          <ul class="legend">
            @for (s of slices(); track s.category; let i = $index) {
              <li>
                <span class="dot" [style.background]="palette[i]"></span>
                <span class="cat-name">{{ s.category }}</span>
                <span class="cat-amt numeric">{{ s.total | moneyBrl }}</span>
              </li>
            }
          </ul>
        </div>
      }
    </mat-card>
  `,
  styles: [
    `
      :host { display: block; }
      .donut-card {
        padding: 22px 24px;
        border-radius: var(--radius-md);
        background: var(--surface-card);
      }
      .donut-head { margin-bottom: 16px; }
      h3 { margin: 0; font-size: 1.05rem; font-weight: 600; letter-spacing: -0.01em; }
      .subtitle { color: var(--text-muted); font-size: 0.8rem; }
      .donut-body {
        display: grid;
        grid-template-columns: 220px 1fr;
        gap: 32px;
        align-items: center;
      }
      .canvas-wrap {
        position: relative;
        width: 220px;
        height: 220px;
      }
      .centre {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        pointer-events: none;
      }
      .centre small {
        color: var(--text-muted);
        font-size: 0.72rem;
        text-transform: uppercase;
        letter-spacing: 0.06em;
      }
      .centre strong { font-size: 1.05rem; }
      .legend {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .legend li {
        display: grid;
        grid-template-columns: 14px 1fr auto;
        gap: 12px;
        align-items: center;
        font-size: 0.88rem;
        color: var(--text-primary);
      }
      .dot {
        width: 12px; height: 12px; border-radius: 50%;
      }
      .cat-name { color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .cat-amt { color: var(--text-muted); }
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        padding: 48px 0;
        color: var(--text-muted);
      }
      .empty .material-symbols-outlined { font-size: 36px; opacity: 0.6; margin-bottom: 8px; }
      @media (max-width: 720px) {
        .donut-body { grid-template-columns: 1fr; }
        .canvas-wrap { margin: 0 auto; }
      }
    `
  ]
})
export class CategoryDonutComponent implements OnChanges {
  @Input() categories: CategoryTotal[] = [];

  private readonly theme = inject(ThemeService);
  private readonly tick = signal(0);

  protected readonly TOP_N = TOP_N;
  protected readonly palette = PALETTE;

  protected readonly slices = computed(() => {
    void this.tick();
    const expenses = this.categories
      .filter((c) => c.type === 'EXPENSE')
      .map((c) => ({ category: c.category, total: Number(c.total) }))
      .filter((c) => c.total > 0)
      .sort((a, b) => b.total - a.total);

    if (expenses.length <= TOP_N) return expenses;
    const top = expenses.slice(0, TOP_N);
    const other = expenses.slice(TOP_N).reduce((s, c) => s + c.total, 0);
    return [...top, { category: 'Other', total: other }];
  });

  protected readonly totalExpense = computed(() =>
    this.slices().reduce((s, c) => s + c.total, 0).toFixed(2)
  );

  protected chartData(): ChartConfiguration<'doughnut'>['data'] {
    void this.tick();
    return {
      labels: this.slices().map((s) => s.category),
      datasets: [
        {
          data: this.slices().map((s) => s.total),
          backgroundColor: this.slices().map((_, i) => PALETTE[i % PALETTE.length]),
          borderColor: readVar('--surface-card', '#ffffff'),
          borderWidth: 2,
          hoverOffset: 6
        }
      ]
    };
  }

  protected chartOptions(): ChartConfiguration<'doughnut'>['options'] {
    void this.tick();
    void this.theme.mode();
    return {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '68%',
      animation: { animateRotate: true, duration: 600 },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: readVar('--surface-elevated', '#ffffff'),
          titleColor: readVar('--text-primary', '#111827'),
          bodyColor: readVar('--text-primary', '#111827'),
          borderColor: readVar('--border-subtle', '#e5e7eb'),
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) =>
              `${ctx.label}: R$ ${Number(ctx.parsed).toLocaleString('pt-BR', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
              })}`
          }
        }
      }
    };
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['categories']) this.tick.update((v) => v + 1);
  }
}

function readVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}
