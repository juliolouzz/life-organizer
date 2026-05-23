import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  inject,
  signal
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { NgChartsModule } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';

import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { BucketTotal, Granularity } from '../insights.service';

@Component({
  selector: 'app-income-expense-chart',
  standalone: true,
  imports: [MatCardModule, NgChartsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="chart-card">
      <header class="chart-head">
        <div>
          <h3>Income vs Expenses</h3>
          <small class="subtitle">{{ granularityLabel() }} totals</small>
        </div>
      </header>
      <div class="chart-body">
        <canvas
          baseChart
          [type]="'bar'"
          [data]="chartData()"
          [options]="chartOptions()"
        ></canvas>
      </div>
    </mat-card>
  `,
  styles: [
    `
      :host { display: block; }
      .chart-card {
        padding: 22px 24px;
        border-radius: var(--radius-md);
        background: var(--surface-card);
      }
      .chart-head {
        margin-bottom: 16px;
      }
      h3 {
        margin: 0;
        font-size: 1.05rem;
        font-weight: 600;
        letter-spacing: -0.01em;
      }
      .subtitle { color: var(--text-muted); font-size: 0.8rem; }
      .chart-body {
        position: relative;
        height: 320px;
      }
    `
  ]
})
export class IncomeExpenseChartComponent implements OnChanges {
  @Input() buckets: BucketTotal[] = [];
  @Input() granularity: Granularity = 'DAY';

  private readonly theme = inject(ThemeService);
  private readonly auth = inject(AuthService);
  private readonly tick = signal(0);

  protected granularityLabel(): string {
    return this.granularity === 'DAY' ? 'Daily' : this.granularity === 'WEEK' ? 'Weekly' : 'Monthly';
  }

  protected chartData(): ChartConfiguration<'bar'>['data'] {
    // Read tick so the chart re-renders when theme toggles.
    void this.tick();
    void this.theme.mode();

    const incomeColor = readVar('--money-positive', '#22c55e');
    const expenseColor = readVar('--money-negative', '#ef4444');
    const savingsColor = '#d97706';

    return {
      labels: this.buckets.map((b) => formatBucketLabel(b.bucket, this.granularity)),
      datasets: [
        {
          label: 'Income',
          data: this.buckets.map((b) => Number(b.income)),
          backgroundColor: incomeColor,
          borderRadius: 6,
          maxBarThickness: 28
        },
        {
          label: 'Expenses',
          data: this.buckets.map((b) => Number(b.expense)),
          backgroundColor: expenseColor,
          borderRadius: 6,
          maxBarThickness: 28
        },
        {
          label: 'Saved',
          data: this.buckets.map((b) => Number(b.savings ?? 0)),
          backgroundColor: savingsColor,
          borderRadius: 6,
          maxBarThickness: 28
        }
      ]
    };
  }

  protected chartOptions(): ChartConfiguration<'bar'>['options'] {
    void this.tick();
    void this.theme.mode();

    const gridColor = readVar('--border-subtle', '#e5e7eb');
    const textColor = readVar('--text-muted', '#6b7280');

    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 350 },
      plugins: {
        legend: {
          position: 'top',
          align: 'end',
          labels: {
            color: textColor,
            font: { family: 'Inter, system-ui, sans-serif', size: 12 },
            boxWidth: 12,
            boxHeight: 12,
            usePointStyle: true,
            pointStyle: 'rectRounded'
          }
        },
        tooltip: {
          backgroundColor: readVar('--surface-elevated', '#ffffff'),
          titleColor: readVar('--text-primary', '#111827'),
          bodyColor: readVar('--text-primary', '#111827'),
          borderColor: gridColor,
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => {
              const y = (ctx.parsed.y ?? 0) as number;
              const symbol = this.auth.currencySymbol();
              const locale = this.auth.currencyLocale();
              return `${ctx.dataset.label}: ${symbol} ${y.toLocaleString(locale, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
              })}`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { color: textColor, font: { family: 'Inter, system-ui, sans-serif', size: 11 } }
        },
        y: {
          beginAtZero: true,
          grid: { color: gridColor },
          ticks: {
            color: textColor,
            font: { family: 'JetBrains Mono, monospace', size: 11 },
            callback: (val) =>
              `${this.auth.currencySymbol()} ${Number(val).toLocaleString(
                this.auth.currencyLocale(),
                { maximumFractionDigits: 0 }
              )}`
          }
        }
      }
    };
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['buckets'] || changes['granularity']) {
      this.tick.update((v) => v + 1);
    }
  }
}

function readVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

function formatBucketLabel(iso: string, g: Granularity): string {
  const d = new Date(iso + 'T00:00:00');
  if (g === 'DAY') {
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
  }
  if (g === 'WEEK') {
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
  }
  return d.toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
}
