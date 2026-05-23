import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyBrlPipe } from '../../shared/pipes/money-brl.pipe';
import { Category, CategoriesService } from '../categories/categories.service';
import { BudgetStatusItem, BudgetsService } from './budgets.service';

const MONTH_LABELS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

@Component({
  selector: 'app-budgets-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    PageHeaderComponent,
    EmptyStateComponent,
    MoneyBrlPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Budgets"
      subtitle="Set a monthly spending limit per category."
    >
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="period-field">
        <mat-label>Month</mat-label>
        <mat-select [value]="month()" (selectionChange)="setMonth($event.value)">
          @for (m of monthOptions; track m.value) {
            <mat-option [value]="m.value">{{ m.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="period-field">
        <mat-label>Year</mat-label>
        <mat-select [value]="year()" (selectionChange)="setYear($event.value)">
          @for (y of yearOptions(); track y) {
            <mat-option [value]="y">{{ y }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </app-page-header>

    <mat-card appearance="outlined" class="create-card">
      <h3>Add a budget for {{ MONTH_LABELS[month() - 1] }} {{ year() }}</h3>
      <form [formGroup]="form" (ngSubmit)="submit()" class="form-row">
        <mat-form-field appearance="outline">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId">
            @for (c of categoriesForBudget(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Monthly limit</mat-label>
          <span matTextPrefix>R$&nbsp;</span>
          <input matInput type="number" step="0.01" min="0.01" formControlName="amount" required />
        </mat-form-field>
        <button mat-flat-button color="primary" [disabled]="form.invalid || submitting()">
          Save budget
        </button>
      </form>
      @if (categoriesForBudget().length === 0) {
        <p class="hint">
          Create a category first to set a budget.
          <a routerLink="/categories">Go to categories</a>.
        </p>
      }
    </mat-card>

    @if (statusItems().length === 0) {
      <app-empty-state
        icon="payments"
        title="No budgets for this period"
        description="Add a budget above to start tracking."
      />
    } @else {
      <div class="status-grid">
        @for (item of statusItems(); track item.budgetId) {
          <mat-card appearance="outlined" class="status-card">
            <header class="status-head">
              <div>
                <strong>{{ item.categoryName }}</strong>
                <small>{{ item.spent | moneyBrl }} of {{ item.budgeted | moneyBrl }}</small>
              </div>
              <button mat-icon-button (click)="confirmDelete(item)" aria-label="Remove budget">
                <span class="material-symbols-outlined">delete</span>
              </button>
            </header>
            <mat-progress-bar
              [mode]="'determinate'"
              [value]="cappedPercent(item.percent)"
              [class.over-budget]="item.percent >= 100"
              [class.near-budget]="item.percent >= 80 && item.percent < 100"
            />
            <div class="status-foot">
              <span class="percent">{{ item.percent }}%</span>
              <span
                class="remaining"
                [class.negative]="numericRemaining(item.remaining) < 0"
              >
                {{ numericRemaining(item.remaining) >= 0 ? 'Remaining' : 'Over' }}:
                {{ absRemaining(item.remaining) | moneyBrl }}
              </span>
            </div>
          </mat-card>
        }
      </div>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .period-field { width: 130px; }
      .period-field ::ng-deep .mat-mdc-form-field-infix { padding-top: 10px; padding-bottom: 10px; min-height: 40px; }
      .create-card { padding: 20px 24px; margin-bottom: 24px; border-radius: var(--radius-md); }
      h3 { margin: 0 0 12px 0; font-size: 1rem; font-weight: 600; }
      .form-row {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        flex-wrap: wrap;
      }
      .form-row mat-form-field { min-width: 220px; }
      .hint { color: var(--text-muted); font-size: 0.9rem; margin: 8px 0 0 0; }
      .hint a { color: var(--mat-sys-primary, #7c3aed); }
      .status-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 16px;
      }
      .status-card {
        padding: 18px 20px;
        border-radius: var(--radius-md);
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .status-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
      }
      .status-head strong {
        display: block;
        font-size: 1rem;
        font-weight: 600;
      }
      .status-head small {
        color: var(--text-muted);
        font-size: 0.82rem;
      }
      .status-foot {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 0.85rem;
      }
      .percent {
        font-weight: 600;
        font-family: 'JetBrains Mono', monospace;
      }
      .remaining { color: var(--text-muted); }
      .remaining.negative { color: var(--money-negative); font-weight: 600; }
      ::ng-deep .mat-mdc-progress-bar.near-budget .mdc-linear-progress__bar-inner {
        border-color: #d97706 !important;
      }
      ::ng-deep .mat-mdc-progress-bar.over-budget .mdc-linear-progress__bar-inner {
        border-color: var(--money-negative) !important;
      }
    `
  ]
})
export class BudgetsPage implements OnInit {
  private readonly api = inject(BudgetsService);
  private readonly categoriesApi = inject(CategoriesService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected readonly MONTH_LABELS = MONTH_LABELS;

  protected readonly statusItems = signal<BudgetStatusItem[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly submitting = signal(false);

  private readonly today = new Date();
  protected readonly month = signal(this.today.getMonth() + 1);
  protected readonly year = signal(this.today.getFullYear());

  protected readonly monthOptions = MONTH_LABELS.map((label, i) => ({ label, value: i + 1 }));
  protected readonly yearOptions = computed(() => {
    const y = this.today.getFullYear();
    return [y - 1, y, y + 1];
  });

  // Categories that can have a budget: EXPENSE or BOTH (matches how budgets are matched
  // against EXPENSE transactions).
  protected readonly categoriesForBudget = computed(() =>
    this.categories().filter((c) => c.kind === 'EXPENSE' || c.kind === 'BOTH')
  );

  protected readonly form = this.fb.nonNullable.group({
    categoryId: this.fb.control<number | null>(null, [Validators.required]),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)])
  });

  ngOnInit(): void {
    this.refresh();
  }

  protected setMonth(m: number): void {
    this.month.set(m);
    this.refresh();
  }
  protected setYear(y: number): void {
    this.year.set(y);
    this.refresh();
  }

  protected cappedPercent(p: number): number {
    return Math.min(100, Math.max(0, p));
  }
  protected numericRemaining(r: string | number): number {
    return Number(r);
  }
  protected absRemaining(r: string | number): string {
    return Math.abs(Number(r)).toFixed(2);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    const raw = this.form.getRawValue();
    this.submitting.set(true);
    this.api
      .create({
        categoryId: raw.categoryId as number,
        amount: raw.amount as number,
        month: this.month(),
        year: this.year()
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.snackBar.open('Budget saved', 'Dismiss', { duration: 2500 });
          this.form.reset({ categoryId: null, amount: null });
          this.refresh();
        },
        error: () => this.submitting.set(false)
      });
  }

  protected confirmDelete(item: BudgetStatusItem): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Remove this budget?',
        message: `The ${item.categoryName} budget for this month will be deleted. Transactions are unaffected.`,
        confirmLabel: 'Remove',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.delete(item.budgetId).subscribe(() => {
        this.snackBar.open('Budget removed', 'Dismiss', { duration: 2500 });
        this.refresh();
      });
    });
  }

  private refresh(): void {
    forkJoin({
      status: this.api.status(this.year(), this.month()),
      categories: this.categoriesApi.list()
    }).subscribe({
      next: (res) => {
        this.statusItems.set(res.status);
        this.categories.set(res.categories);
      }
    });
  }
}
