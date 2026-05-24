import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';

import { AuthService } from '../../core/auth/auth.service';
import {
  CategoryTrendsReport,
  ReportsService,
  SummaryReport,
  YearOverYearReport
} from '../../core/reports/reports.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

interface Period {
  year: number;
  month: number;
}

const MONTHS: { value: number; label: string }[] = [
  { value: 1, label: 'January' }, { value: 2, label: 'February' },
  { value: 3, label: 'March' }, { value: 4, label: 'April' },
  { value: 5, label: 'May' }, { value: 6, label: 'June' },
  { value: 7, label: 'July' }, { value: 8, label: 'August' },
  { value: 9, label: 'September' }, { value: 10, label: 'October' },
  { value: 11, label: 'November' }, { value: 12, label: 'December' }
];

@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Reports" subtitle="Monthly summary, year-over-year, and category trends." />

    <div class="period-row">
      <mat-form-field appearance="outline" class="period-field">
        <mat-label>Month</mat-label>
        <mat-select [value]="period().month" (selectionChange)="setMonth($event.value)">
          @for (m of months; track m.value) {
            <mat-option [value]="m.value">{{ m.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="period-field">
        <mat-label>Year</mat-label>
        <mat-select [value]="period().year" (selectionChange)="setYear($event.value)">
          @for (y of years; track y) {
            <mat-option [value]="y">{{ y }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <mat-tab-group [(selectedIndex)]="tabIndexProxy" preserveContent>
      <!-- Summary tab -->
      <mat-tab label="Monthly summary">
        <ng-template matTabContent>
          <section class="export-actions">
            <button mat-stroked-button (click)="downloadCsv()" [disabled]="exporting()">
              <span class="material-symbols-outlined">file_download</span>
              Download CSV
            </button>
            <button mat-stroked-button (click)="downloadPdf()" [disabled]="exporting()">
              <span class="material-symbols-outlined">picture_as_pdf</span>
              Download PDF
            </button>
          </section>

          @if (summaryLoading()) {
            <div class="loading"><mat-progress-spinner mode="indeterminate" diameter="32" /></div>
          }
          @if (summary(); as s) {
            <section class="stats-row">
              <mat-card appearance="outlined"><div class="stat-label">Income</div><div class="stat-value positive">{{ brl(s.totals.income) }}</div></mat-card>
              <mat-card appearance="outlined"><div class="stat-label">Expense</div><div class="stat-value negative">{{ brl(s.totals.expense) }}</div></mat-card>
              <mat-card appearance="outlined"><div class="stat-label">Savings</div><div class="stat-value">{{ brl(s.totals.savings) }}</div></mat-card>
              <mat-card appearance="outlined"><div class="stat-label">Net</div><div class="stat-value" [class.positive]="s.totals.net >= 0" [class.negative]="s.totals.net < 0">{{ brl(s.totals.net) }}</div></mat-card>
            </section>

            <mat-card class="block" appearance="outlined">
              <h3>Top categories</h3>
              @if (s.topCategories.length === 0) {
                <p class="muted">No transactions in this period.</p>
              } @else {
                <table>
                  <thead><tr><th>Category</th><th>Type</th><th class="num">Amount</th></tr></thead>
                  <tbody>
                    @for (c of s.topCategories; track c.name + '-' + c.type) {
                      <tr>
                        <td>{{ c.name }}</td>
                        <td>{{ c.type }}</td>
                        <td class="num">{{ brl(c.amount) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </mat-card>

            <mat-card class="block" appearance="outlined">
              <h3>Daily breakdown</h3>
              <div class="bars">
                @for (d of s.daily; track d.date) {
                  <div class="bar-col">
                    <div class="bar-track">
                      <span class="bar income" [style.height.%]="pct(d.income, dailyMax())"></span>
                      <span class="bar expense" [style.height.%]="pct(d.expense, dailyMax())"></span>
                      <span class="bar savings" [style.height.%]="pct(d.savings, dailyMax())"></span>
                    </div>
                    <div class="day-label">{{ dayOf(d.date) }}</div>
                  </div>
                }
              </div>
              <div class="legend">
                <span><i class="swatch sw-income"></i>Income</span>
                <span><i class="swatch sw-expense"></i>Expense</span>
                <span><i class="swatch sw-savings"></i>Savings</span>
              </div>
            </mat-card>
          }
        </ng-template>
      </mat-tab>

      <!-- YoY tab -->
      <mat-tab label="Year-over-year">
        <ng-template matTabContent>
          @if (yoyLoading()) {
            <div class="loading"><mat-progress-spinner mode="indeterminate" diameter="32" /></div>
          }
          @if (yoy(); as y) {
            <div class="yoy-grid">
              <mat-card appearance="outlined">
                <h3>This year ({{ y.thisYear.year }})</h3>
                <div class="kv">Income <strong>{{ brl(y.thisYear.totals.income) }}</strong></div>
                <div class="kv">Expense <strong>{{ brl(y.thisYear.totals.expense) }}</strong></div>
                <div class="kv">Savings <strong>{{ brl(y.thisYear.totals.savings) }}</strong></div>
                <div class="kv">Net <strong>{{ brl(y.thisYear.totals.net) }}</strong></div>
              </mat-card>
              <mat-card appearance="outlined">
                <h3>Last year ({{ y.lastYear.year }})</h3>
                <div class="kv">Income <strong>{{ brl(y.lastYear.totals.income) }}</strong></div>
                <div class="kv">Expense <strong>{{ brl(y.lastYear.totals.expense) }}</strong></div>
                <div class="kv">Savings <strong>{{ brl(y.lastYear.totals.savings) }}</strong></div>
                <div class="kv">Net <strong>{{ brl(y.lastYear.totals.net) }}</strong></div>
              </mat-card>
              <mat-card appearance="outlined">
                <h3>Delta</h3>
                <div class="kv">Income {{ deltaLine(y.deltas.income) }}</div>
                <div class="kv">Expense {{ deltaLine(y.deltas.expense) }}</div>
                <div class="kv">Savings {{ deltaLine(y.deltas.savings) }}</div>
                <div class="kv">Net {{ deltaLine(y.deltas.net) }}</div>
              </mat-card>
            </div>
            <mat-card class="block" appearance="outlined">
              <h3>Top expense category shifts</h3>
              @if (y.topCategoryDeltas.length === 0) {
                <p class="muted">No expenses in either period.</p>
              } @else {
                <table>
                  <thead><tr><th>Category</th><th class="num">This year</th><th class="num">Last year</th><th class="num">Delta</th></tr></thead>
                  <tbody>
                    @for (row of y.topCategoryDeltas; track row.name) {
                      <tr>
                        <td>{{ row.name }}</td>
                        <td class="num">{{ brl(row.thisYear) }}</td>
                        <td class="num">{{ brl(row.lastYear) }}</td>
                        <td class="num">{{ deltaLine(row.delta) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </mat-card>
          }
        </ng-template>
      </mat-tab>

      <!-- Trends tab -->
      <mat-tab label="Category trends">
        <ng-template matTabContent>
          <div class="trends-toolbar">
            <button mat-button [class.active-pill]="trendMonths() === 6" (click)="setTrendMonths(6)">6 months</button>
            <button mat-button [class.active-pill]="trendMonths() === 12" (click)="setTrendMonths(12)">12 months</button>
          </div>
          @if (trendsLoading()) {
            <div class="loading"><mat-progress-spinner mode="indeterminate" diameter="32" /></div>
          } @else {
            @if (trends(); as t) {
              @if (t.series.length === 0) {
                <p class="muted">No activity in the last {{ t.monthsBack }} months.</p>
              } @else {
                <div class="trend-grid">
                  @for (series of t.series; track series.name) {
                    <mat-card appearance="outlined" class="trend-card">
                      <header><strong>{{ series.name }}</strong><small>{{ series.type }}</small></header>
                      <div class="trend-bars">
                        @for (p of series.points; track p.year + '-' + p.month) {
                          <div class="trend-bar-col">
                            <div class="trend-bar" [style.height.%]="pct(p.amount, seriesMax(series))"></div>
                            <span class="trend-month-label">{{ p.month }}/{{ shortYear(p.year) }}</span>
                          </div>
                        }
                      </div>
                    </mat-card>
                  }
                </div>
              }
            }
          }
        </ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [
    `
      :host { display: block; padding-bottom: 32px; }
      .period-row { display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
      .period-field { min-width: 140px; }
      .export-actions {
        display: flex; gap: 10px; padding: 16px 0 8px 0;
      }
      .export-actions .material-symbols-outlined { margin-right: 6px; vertical-align: middle; font-size: 18px; }
      .loading { display: grid; place-items: center; padding: 48px; }
      .stats-row {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 12px;
        margin: 16px 0;
      }
      .stats-row mat-card { padding: 14px 16px; border-radius: var(--radius-md); }
      .stat-label { color: var(--text-muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.06em; }
      .stat-value { font-size: 1.4rem; font-weight: 600; margin-top: 4px; }
      .stat-value.positive { color: var(--money-positive); }
      .stat-value.negative { color: var(--money-negative); }
      .block { padding: 18px; margin: 12px 0; border-radius: var(--radius-md); }
      .block h3 { margin: 0 0 12px 0; font-size: 1.05rem; font-weight: 600; }
      .muted { color: var(--text-muted); margin: 0; }
      table { width: 100%; border-collapse: collapse; }
      table th, table td { padding: 8px 6px; border-bottom: 1px solid var(--border-subtle); text-align: left; font-size: 0.92rem; }
      table th { color: var(--text-muted); font-weight: 500; }
      .num { text-align: right; }
      .bars { display: flex; align-items: flex-end; gap: 4px; height: 110px; padding: 8px 0; overflow-x: auto; }
      .bar-col { display: flex; flex-direction: column; align-items: center; min-width: 22px; }
      .bar-track { height: 80px; display: flex; align-items: flex-end; gap: 2px; }
      .bar { width: 4px; border-radius: 1px; display: block; min-height: 1px; }
      .bar.income { background: var(--money-positive); }
      .bar.expense { background: var(--money-negative); }
      .bar.savings { background: #1d4ed8; }
      .day-label { color: var(--text-muted); font-size: 0.7rem; margin-top: 4px; }
      .legend { display: flex; gap: 16px; margin-top: 12px; font-size: 0.82rem; color: var(--text-muted); }
      .legend i.swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; vertical-align: middle; margin-right: 6px; }
      .sw-income { background: var(--money-positive); }
      .sw-expense { background: var(--money-negative); }
      .sw-savings { background: #1d4ed8; }
      .yoy-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0; }
      .yoy-grid mat-card { padding: 16px; border-radius: var(--radius-md); }
      .yoy-grid h3 { margin: 0 0 10px 0; font-size: 1rem; font-weight: 600; }
      .kv { display: flex; justify-content: space-between; padding: 4px 0; font-size: 0.92rem; }
      .trends-toolbar { margin: 14px 0 8px 0; display: flex; gap: 6px; }
      .active-pill { background: color-mix(in srgb, var(--mat-sys-primary, #7c3aed) 12%, transparent); }
      .trend-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
      .trend-card { padding: 14px; border-radius: var(--radius-md); }
      .trend-card header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }
      .trend-card header small { color: var(--text-muted); font-size: 0.72rem; }
      .trend-bars { display: flex; align-items: flex-end; gap: 6px; height: 80px; }
      .trend-bar-col { display: flex; flex-direction: column; align-items: center; flex: 1 1 0; }
      .trend-bar { width: 100%; max-width: 16px; background: var(--money-negative); border-radius: 2px 2px 0 0; min-height: 1px; }
      .trend-month-label { font-size: 0.65rem; color: var(--text-muted); margin-top: 4px; }
      @media (max-width: 900px) {
        .stats-row, .yoy-grid { grid-template-columns: repeat(2, 1fr); }
      }
      @media (max-width: 560px) {
        .stats-row, .yoy-grid { grid-template-columns: 1fr; }
      }
    `
  ]
})
export class ReportsPage implements OnInit {
  private readonly api = inject(ReportsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  protected readonly months = MONTHS;
  protected readonly years = this.buildYears();

  private readonly _period = signal<Period>(this.currentPeriod());
  protected readonly period = this._period.asReadonly();

  protected readonly tabIndex = signal(0);
  protected get tabIndexProxy(): number { return this.tabIndex(); }
  // When the user picks a tab, MatTabGroup writes the new index here. Kick
  // off the matching fetch immediately so the body never renders empty.
  protected set tabIndexProxy(value: number) {
    this.tabIndex.set(value);
    this.loadActive(value, this._period(), this.trendMonths());
  }

  protected readonly summary = signal<SummaryReport | null>(null);
  protected readonly summaryLoading = signal(false);

  protected readonly yoy = signal<YearOverYearReport | null>(null);
  protected readonly yoyLoading = signal(false);

  protected readonly trends = signal<CategoryTrendsReport | null>(null);
  protected readonly trendsLoading = signal(false);
  protected readonly trendMonths = signal<6 | 12>(12);

  protected readonly exporting = signal(false);

  protected readonly dailyMax = computed(() => {
    const s = this.summary();
    if (!s) return 1;
    let max = 0;
    for (const d of s.daily) {
      max = Math.max(max, d.income, d.expense, d.savings);
    }
    return max || 1;
  });

  ngOnInit(): void {
    // Lazy-load the initially selected tab. Tab changes go through
    // the tabIndexProxy setter; period changes go through setMonth/setYear.
    this.loadActive(this.tabIndex(), this._period(), this.trendMonths());
  }

  protected setMonth(month: number): void {
    this._period.update((p) => ({ ...p, month }));
    this.invalidateCaches();
    this.loadActive(this.tabIndex(), this._period(), this.trendMonths());
  }

  protected setYear(year: number): void {
    this._period.update((p) => ({ ...p, year }));
    this.invalidateCaches();
    this.loadActive(this.tabIndex(), this._period(), this.trendMonths());
  }

  protected setTrendMonths(months: 6 | 12): void {
    if (months === this.trendMonths()) return;
    this.trendMonths.set(months);
    this.trends.set(null);
    if (this.tabIndex() === 2) {
      this.loadActive(this.tabIndex(), this._period(), months);
    }
  }

  protected downloadCsv(): void {
    const { year, month } = this._period();
    this.exporting.set(true);
    this.api.downloadSummaryCsv(year, month).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        this.saveBlob(blob, `summary-${year}-${this.pad(month)}.csv`);
      },
      error: () => {
        this.exporting.set(false);
        this.snackBar.open('Could not download CSV.', 'Dismiss', { duration: 4000 });
      }
    });
  }

  protected downloadPdf(): void {
    const { year, month } = this._period();
    this.exporting.set(true);
    this.api.downloadSummaryPdf(year, month).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        this.saveBlob(blob, `summary-${year}-${this.pad(month)}.pdf`);
      },
      error: () => {
        this.exporting.set(false);
        this.snackBar.open('Could not download PDF.', 'Dismiss', { duration: 4000 });
      }
    });
  }

  protected brl(amount: number): string {
    return this.auth.currencySymbol() + ' '
        + amount.toLocaleString(this.auth.currencyLocale(),
            { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  protected pct(value: number, max: number): number {
    if (!max) return 0;
    return Math.min(100, Math.round((value / max) * 100));
  }

  protected dayOf(date: string): number {
    return Number(date.slice(-2));
  }

  protected shortYear(year: number): string {
    return String(year).slice(-2);
  }

  protected seriesMax(series: CategoryTrendsReport['series'][number]): number {
    return Math.max(1, ...series.points.map((p) => p.amount));
  }

  protected deltaLine(delta: { absolute: number; percent: number | null }): string {
    const sign = delta.absolute >= 0 ? '+' : '';
    const pct = delta.percent === null ? '—' : `${sign}${delta.percent.toFixed(1)}%`;
    return `${sign}${this.brl(Math.abs(delta.absolute))} (${pct})`;
  }

  private invalidateCaches(): void {
    this.summary.set(null);
    this.yoy.set(null);
  }

  private loadActive(tab: number, period: Period, trendMonths: 6 | 12): void {
    if (tab === 0 && this.summary() === null && !this.summaryLoading()) {
      this.summaryLoading.set(true);
      this.api.summary(period.year, period.month).subscribe({
        next: (r) => { this.summary.set(r); this.summaryLoading.set(false); },
        error: () => { this.summaryLoading.set(false); }
      });
    } else if (tab === 1 && this.yoy() === null && !this.yoyLoading()) {
      this.yoyLoading.set(true);
      this.api.yoy(period.year, period.month).subscribe({
        next: (r) => { this.yoy.set(r); this.yoyLoading.set(false); },
        error: () => { this.yoyLoading.set(false); }
      });
    } else if (tab === 2 && this.trends() === null && !this.trendsLoading()) {
      this.trendsLoading.set(true);
      this.api.trends(trendMonths).subscribe({
        next: (r) => { this.trends.set(r); this.trendsLoading.set(false); },
        error: () => { this.trendsLoading.set(false); }
      });
    }
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  private pad(n: number): string {
    return n < 10 ? `0${n}` : String(n);
  }

  private currentPeriod(): Period {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() + 1 };
  }

  private buildYears(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current - 5; y <= current + 1; y++) years.push(y);
    return years;
  }
}
