import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { MoneyBrlPipe } from '../../../shared/pipes/money-brl.pipe';
import { BudgetStatusItem, BudgetsService } from '../../budgets/budgets.service';
import { monthRangeForBoundary, toIso } from '../period';

@Component({
  selector: 'app-budgets-widget',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatProgressBarModule, MoneyBrlPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="widget-card">
      <header class="widget-head">
        <div>
          <h3>Budgets</h3>
          <small class="subtitle">Spending vs limits for {{ monthLabel() }}</small>
        </div>
        <a mat-button color="primary" routerLink="/budgets">
          Manage
          <span class="material-symbols-outlined arrow">arrow_forward</span>
        </a>
      </header>

      @if (status().length === 0) {
        <div class="empty">
          <span class="material-symbols-outlined">flag</span>
          <p>No budgets set for this month.</p>
          <a mat-stroked-button routerLink="/budgets">Create a budget</a>
        </div>
      } @else {
        <ul class="budget-list">
          @for (b of status(); track b.budgetId) {
            <li>
              <div class="row-head">
                <strong>{{ b.categoryName }}</strong>
                <span class="amount">
                  {{ b.spent | moneyBrl }} / {{ b.budgeted | moneyBrl }}
                </span>
              </div>
              <mat-progress-bar
                mode="determinate"
                [value]="capped(b.percent)"
                [class.over]="b.percent >= 100"
                [class.near]="b.percent >= 80 && b.percent < 100"
              />
              <div class="row-foot">
                <span class="percent">{{ b.percent }}%</span>
                <span
                  class="remaining"
                  [class.negative]="numericRemaining(b) < 0"
                >
                  {{ numericRemaining(b) >= 0 ? 'left' : 'over' }}:
                  {{ absRemaining(b) | moneyBrl }}
                </span>
              </div>
            </li>
          }
        </ul>
      }
    </mat-card>
  `,
  styles: [
    `
      :host { display: block; }
      .widget-card {
        padding: 22px 24px;
        border-radius: var(--radius-md);
        background: var(--surface-card);
      }
      .widget-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 14px;
      }
      h3 { margin: 0; font-size: 1.05rem; font-weight: 600; letter-spacing: -0.01em; }
      .subtitle { color: var(--text-muted); font-size: 0.8rem; }
      .arrow { font-size: 16px; margin-left: 6px; vertical-align: middle; }
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        padding: 32px 0;
        gap: 12px;
        color: var(--text-muted);
      }
      .empty .material-symbols-outlined { font-size: 32px; opacity: 0.6; }
      .empty p { margin: 0; }
      .budget-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 14px;
      }
      .budget-list li {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .row-head {
        display: flex;
        justify-content: space-between;
        font-size: 0.92rem;
      }
      .row-head strong { font-weight: 600; color: var(--text-primary); }
      .amount {
        font-family: 'JetBrains Mono', monospace;
        color: var(--text-muted);
        font-size: 0.82rem;
      }
      .row-foot {
        display: flex;
        justify-content: space-between;
        font-size: 0.78rem;
      }
      .percent { font-family: 'JetBrains Mono', monospace; color: var(--text-muted); }
      .remaining { color: var(--text-muted); }
      .remaining.negative { color: var(--money-negative); font-weight: 600; }
      ::ng-deep .mat-mdc-progress-bar.near .mdc-linear-progress__bar-inner {
        border-color: #d97706 !important;
      }
      ::ng-deep .mat-mdc-progress-bar.over .mdc-linear-progress__bar-inner {
        border-color: var(--money-negative) !important;
      }
    `
  ]
})
export class BudgetsWidgetComponent implements OnChanges {
  @Input() year!: number;
  @Input() month!: number;
  /** Slice 14: when > 1, show the cycle range alongside the calendar month label. */
  @Input() boundaryDay = 1;

  private readonly api = inject(BudgetsService);
  protected readonly status = signal<BudgetStatusItem[]>([]);

  private static readonly MONTHS = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  protected monthLabel(): string {
    const base = `${BudgetsWidgetComponent.MONTHS[(this.month ?? 1) - 1]} ${this.year ?? ''}`;
    if (!this.boundaryDay || this.boundaryDay === 1) return base;
    // The "current cycle" is anchored at boundaryDay relative to a reference
    // date inside the selected calendar month. Use mid-month so we always
    // land on the cycle that "owns" that calendar month.
    const ref = new Date(this.year, (this.month ?? 1) - 1, 15);
    const cycle = monthRangeForBoundary(ref, this.boundaryDay);
    return `${base} (${toIso(cycle.from)} - ${toIso(cycle.to)})`;
  }

  protected capped(p: number): number { return Math.min(100, Math.max(0, p)); }
  protected numericRemaining(b: BudgetStatusItem): number { return Number(b.remaining); }
  protected absRemaining(b: BudgetStatusItem): string { return Math.abs(Number(b.remaining)).toFixed(2); }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['year'] || changes['month']) {
      if (this.year && this.month) {
        this.api.status(this.year, this.month).subscribe((rows) => this.status.set(rows));
      }
    }
  }
}
