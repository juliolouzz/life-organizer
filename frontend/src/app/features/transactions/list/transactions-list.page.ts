import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import {
  ConfirmDialogComponent,
  ConfirmDialogData
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyBrlPipe } from '../../../shared/pipes/money-brl.pipe';
import { Transaction, TransactionsService } from '../transactions.service';

@Component({
  selector: 'app-transactions-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
    MoneyBrlPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Transactions"
      subtitle="Income and expenses, ordered by date."
    >
      <a
        mat-stroked-button
        routerLink="/dashboard"
        class="cross-nav"
        data-testid="goto-dashboard"
      >
        <span class="material-symbols-outlined nav-arrow">arrow_back</span>
        Back to dashboard
      </a>
      <a mat-flat-button color="primary" routerLink="/transactions/new" data-testid="new-transaction">
        <span class="material-symbols-outlined">add</span>
        New transaction
      </a>
    </app-page-header>

    <mat-card class="filter-card" appearance="outlined">
      <form [formGroup]="filterForm" class="filter-form" (ngSubmit)="apply()">
        <mat-form-field appearance="outline" class="dense">
          <mat-label>From</mat-label>
          <input matInput [matDatepicker]="fromPicker" formControlName="from" />
          <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
          <mat-datepicker #fromPicker />
        </mat-form-field>
        <mat-form-field appearance="outline" class="dense">
          <mat-label>To</mat-label>
          <input matInput [matDatepicker]="toPicker" formControlName="to" />
          <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
          <mat-datepicker #toPicker />
        </mat-form-field>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          [disabled]="fromAfterTo()"
        >
          Apply
        </button>
        <button mat-stroked-button type="button" (click)="clearFilter()">
          Clear
        </button>
        @if (fromAfterTo()) {
          <small class="filter-warn">From must be on or before To.</small>
        }
      </form>
    </mat-card>

    @if (loading() && rows().length === 0) {
      <div class="loading">
        <mat-progress-spinner mode="indeterminate" diameter="40" />
      </div>
    } @else if (rows().length === 0) {
      <app-empty-state
        icon="receipt_long"
        title="No transactions yet"
        description="Start by adding your first income or expense."
      >
        <a mat-flat-button color="primary" routerLink="/transactions/new">
          <span class="material-symbols-outlined">add</span>
          New transaction
        </a>
      </app-empty-state>
    } @else {
      <mat-card appearance="outlined" class="table-card">
        <table
          mat-table
          [dataSource]="rows()"
          class="tx-table"
          data-testid="transactions-table"
        >
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r" class="mono">{{ r.transactionDate }}</td>
          </ng-container>

          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let r">
              <span
                class="type-badge"
                [class.income]="r.type === 'INCOME'"
                [class.expense]="r.type === 'EXPENSE'"
                [class.savings]="r.type === 'SAVINGS'"
              >
                {{ typeLabel(r.type) }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="category">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let r">{{ r.category }}</td>
          </ng-container>

          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>Description</th>
            <td mat-cell *matCellDef="let r" class="description-cell">{{ r.description }}</td>
          </ng-container>

          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="text-right">Amount</th>
            <td mat-cell *matCellDef="let r" class="numeric amount-cell"
                [class.amount-income]="r.type === 'INCOME'"
                [class.amount-expense]="r.type === 'EXPENSE'"
                [class.amount-savings]="r.type === 'SAVINGS'">
              {{ r.amount | moneyBrl: r.type }}
            </td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let r">
              <button
                mat-icon-button
                [matMenuTriggerFor]="rowMenu"
                aria-label="Row actions"
                [attr.data-testid]="'row-menu-' + r.id"
              >
                <span class="material-symbols-outlined">more_vert</span>
              </button>
              <mat-menu #rowMenu="matMenu">
                <button mat-menu-item (click)="edit(r)">
                  <span class="material-symbols-outlined">edit</span>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="confirmDelete(r)">
                  <span class="material-symbols-outlined">delete</span>
                  <span>Delete</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        </table>

        <div class="table-footer">
          <small class="muted">Showing {{ rows().length }} transaction(s)</small>
          <button
            mat-stroked-button
            (click)="loadMore()"
            [disabled]="!hasMore() || loading()"
          >
            @if (loading()) { Loading... } @else { Load more }
          </button>
        </div>
      </mat-card>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .cross-nav .nav-arrow {
        font-size: 18px;
        margin-right: 6px;
        vertical-align: middle;
      }
      .filter-card {
        padding: 16px 20px;
        margin-bottom: 24px;
        border-radius: var(--radius-md);
      }
      .filter-form {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
      }
      .filter-form mat-form-field {
        min-width: 180px;
        max-width: 220px;
      }
      .filter-form .dense ::ng-deep .mat-mdc-form-field-infix {
        padding-top: 10px;
        padding-bottom: 10px;
        min-height: 40px;
      }
      .filter-warn { color: var(--money-negative); font-size: 0.8rem; }
      .table-card { padding: 0; overflow: hidden; border-radius: var(--radius-md); }
      .tx-table {
        width: 100%;
      }
      .tx-table .mat-mdc-header-row {
        background: var(--surface-bg);
      }
      .tx-table .mat-mdc-header-cell {
        font-weight: 600;
        font-size: 0.78rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--text-muted);
      }
      .text-right { text-align: right !important; }
      .description-cell {
        max-width: 320px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .amount-cell { text-align: right; font-weight: 600; }
      .amount-income { color: var(--money-positive); }
      .amount-expense { color: var(--money-negative); }
      .amount-savings { color: #d97706; }
      .type-badge {
        display: inline-block;
        font-size: 0.75rem;
        font-weight: 600;
        padding: 4px 10px;
        border-radius: 999px;
        letter-spacing: 0.01em;
      }
      .type-badge.income {
        background: color-mix(in srgb, var(--money-positive) 15%, transparent);
        color: var(--money-positive);
      }
      .type-badge.expense {
        background: color-mix(in srgb, var(--text-muted) 15%, transparent);
        color: var(--text-muted);
      }
      .type-badge.savings {
        background: color-mix(in srgb, #d97706 18%, transparent);
        color: #d97706;
      }
      .table-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px 20px;
        border-top: 1px solid var(--border-subtle);
      }
      .muted { color: var(--text-muted); }
      .loading {
        padding: 64px;
        display: grid;
        place-items: center;
      }
    `
  ]
})
export class TransactionsListPage implements OnInit {
  private readonly api = inject(TransactionsService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly displayedColumns = ['date', 'type', 'category', 'description', 'amount', 'actions'];

  protected readonly rows = signal<Transaction[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly filterForm = this.fb.nonNullable.group({
    from: this.fb.control<Date | null>(null),
    to: this.fb.control<Date | null>(null)
  });

  protected readonly hasMore = computed(() => this.nextCursor() !== null);

  protected fromAfterTo(): boolean {
    const { from, to } = this.filterForm.getRawValue();
    return !!(from && to && from > to);
  }

  protected typeLabel(type: 'INCOME' | 'EXPENSE' | 'SAVINGS'): string {
    return type === 'INCOME' ? 'Income' : type === 'EXPENSE' ? 'Expense' : 'Saved';
  }

  ngOnInit(): void {
    this.fetchInitial();
  }

  protected apply(): void {
    if (this.fromAfterTo()) return;
    this.fetchInitial();
  }

  protected clearFilter(): void {
    this.filterForm.reset({ from: null, to: null });
    this.fetchInitial();
  }

  protected loadMore(): void {
    if (!this.hasMore() || this.loading()) return;
    this.loading.set(true);
    const { from, to } = this.filterForm.getRawValue();
    this.api
      .list({
        cursor: this.nextCursor(),
        from: from ? toIso(from) : null,
        to: to ? toIso(to) : null
      })
      .subscribe({
        next: (page) => {
          this.rows.update((existing) => [...existing, ...page.items]);
          this.nextCursor.set(page.nextCursor);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }

  protected edit(tx: Transaction): void {
    this.router.navigate(['/transactions', tx.id, 'edit']);
  }

  protected confirmDelete(tx: Transaction): void {
    const data: ConfirmDialogData = {
      title: 'Delete transaction?',
      message: `"${tx.description}" will be removed. This cannot be undone.`,
      confirmLabel: 'Delete',
      destructive: true
    };
    const ref = this.dialog.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      { data, width: '400px' }
    );
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) this.deleteTx(tx);
    });
  }

  private deleteTx(tx: Transaction): void {
    // Optimistic removal.
    const original = this.rows();
    this.rows.set(original.filter((r) => r.id !== tx.id));
    this.api.delete(tx.id).subscribe({
      next: () => {
        this.snackBar.open('Transaction deleted', 'Dismiss', { duration: 3000 });
      },
      error: () => {
        // Race - it might have already been deleted in another tab. Treat as success.
        this.snackBar.open('Transaction not found', 'Dismiss', { duration: 3000 });
      }
    });
  }

  private fetchInitial(): void {
    this.loading.set(true);
    const { from, to } = this.filterForm.getRawValue();
    this.api
      .list({
        from: from ? toIso(from) : null,
        to: to ? toIso(to) : null
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.items);
          this.nextCursor.set(page.nextCursor);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }
}

function toIso(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}
