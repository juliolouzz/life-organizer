import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { Router, RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyBrlPipe } from '../../shared/pipes/money-brl.pipe';
import { CategoryDonutComponent } from './widgets/category-donut.component';
import { IncomeExpenseChartComponent } from './widgets/income-expense-chart.component';
import { StatCardComponent } from './widgets/stat-card.component';
import {
  BucketTotal,
  CategoryTotal,
  Granularity,
  InsightsService,
  Summary
} from './insights.service';
import { DateRange, PeriodPreset, rangeForPreset, toIso } from './period';
import { PeriodSelectorComponent } from './period-selector/period-selector.component';
import { QuickAddTransactionDialog } from './quick-add-dialog/quick-add-transaction.dialog';
import { Transaction, TransactionsService } from '../transactions/transactions.service';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatCardComponent,
    IncomeExpenseChartComponent,
    CategoryDonutComponent,
    PeriodSelectorComponent,
    MoneyBrlPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Dashboard"
      subtitle="How your money moved this period."
    >
      <app-period-selector [initial]="initialPreset" (rangeChange)="onRange($event)" />
    </app-page-header>

    <button
      mat-fab
      extended
      color="primary"
      class="quick-add-fab"
      (click)="openQuickAdd()"
      matTooltip="Add a transaction without leaving this page"
      data-testid="dashboard-quick-add"
    >
      <span class="material-symbols-outlined">add</span>
      Add transaction
    </button>

    <section class="stats-row">
      <app-stat-card
        icon="account_balance_wallet"
        label="Net"
        [accent]="netAccent()"
        [value]="summary()?.net ?? null"
        [caption]="periodCaption()"
        [deltaPct]="netDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="trending_up"
        label="Income"
        accent="var(--money-positive)"
        [value]="summary()?.totalIncome ?? null"
        [caption]="(summary()?.incomeCount ?? 0) + ' transaction(s)'"
        [deltaPct]="incomeDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="trending_down"
        label="Expenses"
        accent="var(--money-negative)"
        [value]="summary()?.totalExpense ?? null"
        [caption]="(summary()?.expenseCount ?? 0) + ' transaction(s)'"
        [deltaPct]="expenseDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="savings"
        label="Savings rate"
        accent="var(--mat-sys-primary, #7c3aed)"
        [formattedValue]="savingsRateLabel()"
        [hidden]="summary() === null || Number(summary()?.totalIncome ?? 0) === 0"
        [caption]="'Net / Income'"
        [loading]="loading()"
      />
    </section>

    @if (summary() && hasNoData()) {
      <app-empty-state
        icon="insights"
        title="Nothing here yet"
        description="There are no transactions in this period. Add one to see your numbers come alive."
      >
        <a mat-flat-button color="primary" routerLink="/transactions/new">
          <span class="material-symbols-outlined">add</span>
          Add a transaction
        </a>
      </app-empty-state>
    } @else {
      <section class="charts-row">
        <app-income-expense-chart
          [buckets]="bucketsView()"
          [granularity]="granularity()"
        />
        <app-category-donut [categories]="categories()" />
      </section>

      <section class="recent-section">
        <mat-card appearance="outlined" class="recent-card">
          <header class="recent-head">
            <div>
              <h3>Recent transactions</h3>
              <small class="subtitle">Latest 5 across all time</small>
            </div>
            <a mat-button color="primary" routerLink="/transactions">
              View all
              <span class="material-symbols-outlined arrow">arrow_forward</span>
            </a>
          </header>
          <ul class="recent-list">
            @for (t of recent(); track t.id) {
              <li>
                <div class="t-date numeric">{{ t.transactionDate }}</div>
                <div class="t-info">
                  <strong>{{ t.description }}</strong>
                  <small>{{ t.category }}</small>
                </div>
                <div
                  class="t-amount numeric"
                  [class.income]="t.type === 'INCOME'"
                  [class.expense]="t.type === 'EXPENSE'"
                >
                  {{ t.amount | moneyBrl: t.type }}
                </div>
              </li>
            } @empty {
              <li class="empty-recent">No transactions yet.</li>
            }
          </ul>
        </mat-card>
      </section>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .stats-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }
      .charts-row {
        display: grid;
        grid-template-columns: 1.4fr 1fr;
        gap: 20px;
        margin-bottom: 24px;
      }
      .quick-add-fab {
        position: fixed;
        right: 32px;
        bottom: 32px;
        z-index: 50;
        box-shadow: var(--shadow-lg);
      }
      .quick-add-fab .material-symbols-outlined {
        font-size: 22px;
        margin-right: 8px;
      }
      @media (max-width: 720px) {
        .quick-add-fab {
          right: 16px;
          bottom: 16px;
        }
      }
      .recent-section { margin-bottom: 16px; }
      .recent-card {
        padding: 22px 24px;
        border-radius: var(--radius-md);
        background: var(--surface-card);
      }
      .recent-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 12px;
      }
      h3 { margin: 0; font-size: 1.05rem; font-weight: 600; letter-spacing: -0.01em; }
      .subtitle { color: var(--text-muted); font-size: 0.8rem; }
      .arrow { font-size: 16px; margin-left: 6px; vertical-align: middle; }
      .recent-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
      }
      .recent-list li {
        display: grid;
        grid-template-columns: 110px 1fr auto;
        align-items: center;
        gap: 16px;
        padding: 12px 0;
        border-bottom: 1px solid var(--border-subtle);
      }
      .recent-list li:last-child { border-bottom: none; }
      .t-date { color: var(--text-muted); font-size: 0.85rem; }
      .t-info strong {
        display: block;
        font-size: 0.95rem;
        font-weight: 500;
        color: var(--text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 360px;
      }
      .t-info small { color: var(--text-muted); font-size: 0.78rem; }
      .t-amount { font-weight: 600; }
      .t-amount.income { color: var(--money-positive); }
      .t-amount.expense { color: var(--money-negative); }
      .empty-recent {
        color: var(--text-muted);
        padding: 24px 0;
        text-align: center;
        font-size: 0.9rem;
      }
      @media (max-width: 980px) {
        .charts-row { grid-template-columns: 1fr; }
      }
    `
  ]
})
export class DashboardPage implements OnInit {
  private readonly insights = inject(InsightsService);
  private readonly txs = inject(TransactionsService);
  private readonly dialog = inject(MatDialog);
  protected readonly router = inject(Router);

  protected readonly initialPreset: PeriodPreset = 'this_month';

  protected readonly summary = signal<Summary | null>(null);
  protected readonly categories = signal<CategoryTotal[]>([]);
  protected readonly buckets = signal<BucketTotal[]>([]);
  protected readonly granularity = signal<Granularity>('DAY');
  protected readonly recent = signal<Transaction[]>([]);
  protected readonly loading = signal(true);
  protected readonly range = signal<DateRange>(rangeForPreset('this_month'));

  protected readonly bucketsView = computed(() => this.buckets());

  protected readonly periodCaption = computed(() => {
    const r = this.range();
    return `${toIso(r.from)} – ${toIso(r.to)}`;
  });

  protected readonly netAccent = computed(() => {
    const net = Number(this.summary()?.net ?? 0);
    return net >= 0 ? 'var(--money-positive)' : 'var(--money-negative)';
  });

  protected readonly hasNoData = computed(() => {
    const s = this.summary();
    if (!s) return false;
    return s.incomeCount + s.expenseCount === 0;
  });

  protected readonly savingsRateLabel = computed(() => {
    const s = this.summary();
    if (!s) return null;
    const income = Number(s.totalIncome);
    if (income === 0) return 'n/a';
    const rate = (Number(s.net) / income) * 100;
    return `${rate.toFixed(0)}%`;
  });

  protected readonly Number = Number;

  protected netDelta(): number | null {
    return this.delta(this.summary()?.net, this.summary()?.previousPeriod?.net);
  }
  protected incomeDelta(): number | null {
    return this.delta(this.summary()?.totalIncome, this.summary()?.previousPeriod?.totalIncome);
  }
  protected expenseDelta(): number | null {
    return this.delta(this.summary()?.totalExpense, this.summary()?.previousPeriod?.totalExpense);
  }

  ngOnInit(): void {
    this.onRange(this.range());
  }

  protected onRange(range: DateRange): void {
    this.range.set(range);
    this.fetchAll(range);
  }

  protected openQuickAdd(): void {
    const ref = this.dialog.open<QuickAddTransactionDialog, void, { created: true } | undefined>(
      QuickAddTransactionDialog,
      { width: '440px', autoFocus: 'first-tabbable', restoreFocus: true }
    );
    ref.afterClosed().subscribe((result) => {
      if (result?.created) {
        this.fetchAll(this.range());
      }
    });
  }

  private fetchAll(range: DateRange): void {
    const from = toIso(range.from);
    const to = toIso(range.to);
    this.loading.set(true);
    forkJoin({
      summary: this.insights.summary(from, to),
      categories: this.insights.byCategory(from, to),
      period: this.insights.byPeriod(from, to),
      recent: this.txs.list({ limit: 5 })
    }).subscribe({
      next: (res) => {
        this.summary.set(res.summary);
        this.categories.set(res.categories);
        this.buckets.set(res.period.data);
        this.granularity.set(res.period.granularity);
        this.recent.set(res.recent.items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  private delta(current: number | string | undefined, previous: number | string | undefined): number | null {
    if (current === undefined || previous === undefined) return null;
    const cur = Number(current);
    const prev = Number(previous);
    if (prev === 0) {
      return cur === 0 ? 0 : null;
    }
    return ((cur - prev) / Math.abs(prev)) * 100;
  }
}
