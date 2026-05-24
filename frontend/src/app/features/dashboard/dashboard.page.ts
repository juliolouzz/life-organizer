import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, forkJoin } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyBrlPipe } from '../../shared/pipes/money-brl.pipe';
import { CategoryDonutComponent } from './widgets/category-donut.component';
import { BudgetsWidgetComponent } from './widgets/budgets-widget.component';
import { IncomeExpenseChartComponent } from './widgets/income-expense-chart.component';
import { StatCardComponent } from './widgets/stat-card.component';
import {
  BucketTotal,
  CategoryTotal,
  Granularity,
  InsightsService,
  Summary
} from './insights.service';
import { DateRange, PeriodPreset, rangeForPresetWithBoundary, toIso } from './period';
import { PeriodChange, PeriodSelectorComponent } from './period-selector/period-selector.component';
import { QuickAddTransactionDialog } from './quick-add-dialog/quick-add-transaction.dialog';
import { DeletionPendingBannerComponent } from '../../shared/components/deletion-pending-banner/deletion-pending-banner.component';
import { VerifyEmailBannerComponent } from '../../shared/components/verify-email-banner/verify-email-banner.component';
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
    BudgetsWidgetComponent,
    VerifyEmailBannerComponent,
    DeletionPendingBannerComponent,
    PeriodSelectorComponent,
    MoneyBrlPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-deletion-pending-banner />
    <app-verify-email-banner />

    <app-page-header
      title="Dashboard"
      subtitle="How your money moved this period."
    >
      <app-period-selector
        [initial]="initialPreset"
        [boundaryDay]="boundaryDay()"
        (rangeChange)="onPeriodChange($event)"
      />
      <a
        mat-stroked-button
        routerLink="/transactions"
        class="cross-nav"
        data-testid="goto-transactions"
      >
        View all transactions
        <span class="material-symbols-outlined nav-arrow">arrow_forward</span>
      </a>
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
        tooltip="Income minus expenses and savings transfers for the selected period. A positive net means you ended the period with more money than you started; negative means you spent or transferred more than you earned."
        [accent]="netAccent()"
        [value]="summary()?.net ?? null"
        [caption]="periodCaption()"
        [deltaPct]="netDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="trending_up"
        label="Income"
        tooltip="Sum of all transactions tagged INCOME in the selected period (salary, refunds, gifts received, etc.)."
        accent="var(--money-positive)"
        [value]="summary()?.totalIncome ?? null"
        [caption]="(summary()?.incomeCount ?? 0) + ' transaction(s)'"
        [deltaPct]="incomeDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="trending_down"
        label="Expenses"
        tooltip="Sum of all transactions tagged EXPENSE in the selected period (purchases, bills, fees). Does not include savings transfers."
        accent="var(--money-negative)"
        [value]="summary()?.totalExpense ?? null"
        [caption]="(summary()?.expenseCount ?? 0) + ' transaction(s)'"
        [deltaPct]="expenseDelta()"
        [loading]="loading()"
      />
      <app-stat-card
        icon="savings"
        label="Saved"
        tooltip="Sum of transfers tagged SAVINGS in the selected period - money moved out of spending and into long-term reserves. Tracked separately from expenses so net = income - expenses - savings."
        accent="#d97706"
        [value]="summary()?.totalSavings ?? null"
        [caption]="(summary()?.savingsCount ?? 0) + ' transfer(s)'"
        [deltaPct]="savingsDelta()"
        [loading]="loading()"
      />
    </section>

    @if (summary() && hasNoData()) {
      <app-empty-state
        icon="insights"
        title="Nothing here yet"
        description="There are no transactions in this period. Add one to see your numbers come alive."
      >
        <button mat-flat-button color="primary" (click)="openQuickAdd()" data-testid="empty-state-add">
          <span class="material-symbols-outlined">add</span>
          Add a transaction
        </button>
      </app-empty-state>
    } @else {
      <section class="charts-row">
        <app-income-expense-chart
          [buckets]="bucketsView()"
          [granularity]="granularity()"
        />
        <app-category-donut [categories]="categories()" />
      </section>

      <section class="budgets-section">
        <app-budgets-widget
          [year]="currentYear()"
          [month]="currentMonth()"
          [boundaryDay]="boundaryDay()"
        />
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
        margin-bottom: var(--space-section);
      }
      .charts-row {
        display: grid;
        grid-template-columns: 1.4fr 1fr;
        gap: 20px;
        margin-bottom: var(--space-section);
      }
      .budgets-section { margin-bottom: var(--space-section); }
      .recent-section { margin-bottom: var(--space-section); }
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
      .cross-nav .nav-arrow {
        font-size: 18px;
        margin-left: 6px;
        vertical-align: middle;
      }
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
export class DashboardPage {
  private readonly insights = inject(InsightsService);
  private readonly txs = inject(TransactionsService);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly router = inject(Router);

  protected readonly initialPreset: PeriodPreset = 'this_month';
  protected readonly boundaryDay = computed(() => this.auth.currentUser()?.monthBoundaryDay ?? 1);

  // Whether the user has picked a custom range (in which case we DON'T want to
  // override it when the boundary day finishes loading).
  private readonly isCustom = signal(false);

  protected readonly summary = signal<Summary | null>(null);
  protected readonly categories = signal<CategoryTotal[]>([]);
  protected readonly buckets = signal<BucketTotal[]>([]);
  protected readonly granularity = signal<Granularity>('DAY');
  protected readonly recent = signal<Transaction[]>([]);
  protected readonly loading = signal(true);
  protected readonly range = signal<DateRange>(
    rangeForPresetWithBoundary('this_month', 1)
  );

  // Single stream of "I want data for this range". Pushed into via fetchAll();
  // switchMap cancels any in-flight request when a new range arrives, so the
  // stat cards / chart / donut can never end up showing data from two
  // different fetches (previously: a slow initial "This month" fetch could
  // overwrite a fast "Custom" fetch and produce a half-stale UI).
  private readonly fetchRequest$ = new Subject<DateRange>();

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
    return s.incomeCount + s.expenseCount + (s.savingsCount ?? 0) === 0;
  });

  // Budgets widget always shows the CURRENT calendar month, regardless of the dashboard
  // period selector. People budget by calendar month, not arbitrary date ranges.
  protected readonly currentYear = signal(new Date().getFullYear());
  protected readonly currentMonth = signal(new Date().getMonth() + 1);

  protected readonly Number = Number;

  protected netDelta(): number | null {
    return this.delta(this.summary()?.net, this.summary()?.previousPeriod?.net);
  }
  protected incomeDelta(): number | null {
    return this.delta(this.summary()?.totalIncome, this.summary()?.previousPeriod?.totalIncome);
  }
  protected savingsDelta(): number | null {
    return this.delta(this.summary()?.totalSavings, this.summary()?.previousPeriod?.totalSavings);
  }
  protected expenseDelta(): number | null {
    return this.delta(this.summary()?.totalExpense, this.summary()?.previousPeriod?.totalExpense);
  }

  constructor() {
    // Single subscription for the whole component's lifetime. switchMap
    // cancels the previous in-flight request whenever a new range arrives.
    this.fetchRequest$
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap((range) => {
          const from = toIso(range.from);
          const to = toIso(range.to);
          return forkJoin({
            summary: this.insights.summary(from, to),
            categories: this.insights.byCategory(from, to),
            period: this.insights.byPeriod(from, to),
            recent: this.txs.list({ limit: 5 })
          });
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
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

    // When the user's accounting cycle finishes loading (currentUser arrives
    // after ngOnInit), re-seed the active range from the new boundary - unless
    // the user has already picked a custom range, in which case we leave it.
    effect(() => {
      const day = this.boundaryDay();
      if (this.isCustom()) return;
      this.onRange(rangeForPresetWithBoundary(this.initialPreset, day));
    });
  }

  protected onRange(range: DateRange, custom = false): void {
    this.isCustom.set(custom);
    this.range.set(range);
    this.fetchRequest$.next(range);
  }

  /** Called by the period selector for both presets and custom apply. */
  protected onPeriodChange(change: PeriodChange): void {
    this.onRange(change.range, change.preset === 'custom');
  }

  protected openQuickAdd(): void {
    const ref = this.dialog.open<QuickAddTransactionDialog, void, { created: true } | undefined>(
      QuickAddTransactionDialog,
      { width: '440px', autoFocus: 'first-tabbable', restoreFocus: true }
    );
    ref.afterClosed().subscribe((result) => {
      if (result?.created) {
        this.fetchRequest$.next(this.range());
      }
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
