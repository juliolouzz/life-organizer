import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';

import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyBrlPipe } from '../../shared/pipes/money-brl.pipe';
import { AuthService } from '../../core/auth/auth.service';
import { Category, CategoriesService } from '../categories/categories.service';
import { Recurring, RecurringService } from './recurring.service';

function toIso(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

@Component({
  selector: 'app-recurring-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatMenuModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    PageHeaderComponent,
    EmptyStateComponent,
    MoneyBrlPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Recurring"
      subtitle="Set up transactions that repeat - salary, rent, subscriptions."
    />

    <mat-card appearance="outlined" class="create-card">
      <h3>Add a recurring transaction</h3>
      <form [formGroup]="form" (ngSubmit)="submit()" class="form-grid">
        <mat-button-toggle-group formControlName="type" hideSingleSelectionIndicator>
          <mat-button-toggle value="EXPENSE">Expense</mat-button-toggle>
          <mat-button-toggle value="INCOME">Income</mat-button-toggle>
          <mat-button-toggle value="SAVINGS">Savings</mat-button-toggle>
        </mat-button-toggle-group>

        <mat-form-field appearance="outline">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId">
            @for (c of categories(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <span matTextPrefix>{{ currencySymbol() }}&nbsp;</span>
          <input matInput type="number" step="0.01" min="0.01" formControlName="amount" required />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Frequency</mat-label>
          <mat-select formControlName="frequency">
            <mat-option value="DAILY">Daily</mat-option>
            <mat-option value="WEEKLY">Weekly</mat-option>
            <mat-option value="MONTHLY">Monthly</mat-option>
            <mat-option value="YEARLY">Yearly</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description (optional)</mat-label>
          <input matInput formControlName="description" maxlength="255" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Start date</mat-label>
          <input matInput [matDatepicker]="startPicker" formControlName="startDate" required />
          <mat-datepicker-toggle matIconSuffix [for]="startPicker" />
          <mat-datepicker #startPicker />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>End date (optional)</mat-label>
          <input matInput [matDatepicker]="endPicker" formControlName="endDate" />
          <mat-datepicker-toggle matIconSuffix [for]="endPicker" />
          <mat-datepicker #endPicker />
        </mat-form-field>

        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || submitting()" class="submit-btn">
          Save
        </button>
      </form>
    </mat-card>

    @if (rows().length === 0) {
      <app-empty-state
        icon="event_repeat"
        title="No recurring transactions yet"
        description="Set one up above. Each due occurrence is automatically created when you next view your transactions."
      />
    } @else {
      <div class="grid">
        @for (r of rows(); track r.id) {
          <mat-card appearance="outlined" class="row-card" [class.paused]="r.paused">
            <header class="row-head">
              <div>
                <strong>{{ r.description || r.categoryName }}</strong>
                <small>{{ r.categoryName }} · {{ r.frequency.toLowerCase() }}</small>
              </div>
              <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Actions">
                <span class="material-symbols-outlined">more_vert</span>
              </button>
              <mat-menu #menu="matMenu">
                @if (r.paused) {
                  <button mat-menu-item (click)="resume(r)">Resume</button>
                } @else {
                  <button mat-menu-item (click)="pause(r)">Pause</button>
                }
                <button mat-menu-item (click)="confirmDelete(r)">Delete</button>
              </mat-menu>
            </header>
            <div class="row-amount" [class.income]="r.type === 'INCOME'" [class.expense]="r.type === 'EXPENSE'" [class.savings]="r.type === 'SAVINGS'">
              {{ r.amount | moneyBrl: r.type }}
            </div>
            <div class="row-foot">
              @if (r.paused) {
                <span class="badge paused">Paused</span>
              } @else {
                <span class="muted">Next: {{ r.nextDueDate }}</span>
              }
              @if (r.endDate) {
                <span class="muted">Until {{ r.endDate }}</span>
              }
            </div>
          </mat-card>
        }
      </div>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .create-card { padding: 20px 24px; margin-bottom: 24px; border-radius: var(--radius-md); }
      h3 { margin: 0 0 16px 0; font-size: 1rem; font-weight: 600; }
      .form-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 12px;
        align-items: start;
      }
      .form-grid mat-button-toggle-group { grid-column: 1 / -1; }
      .submit-btn { grid-column: 1 / -1; justify-self: end; height: 40px; }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 16px;
      }
      .row-card {
        padding: 18px 20px;
        border-radius: var(--radius-md);
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .row-card.paused { opacity: 0.55; }
      .row-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
      }
      .row-head strong { display: block; font-size: 1rem; font-weight: 600; }
      .row-head small { color: var(--text-muted); font-size: 0.78rem; }
      .row-amount {
        font-family: 'JetBrains Mono', monospace;
        font-size: 1.2rem;
        font-weight: 600;
      }
      .row-amount.income { color: var(--money-positive); }
      .row-amount.expense { color: var(--money-negative); }
      .row-amount.savings { color: #d97706; }
      .row-foot {
        display: flex;
        gap: 12px;
        font-size: 0.82rem;
      }
      .muted { color: var(--text-muted); }
      .badge { font-size: 0.72rem; font-weight: 600; padding: 2px 8px; border-radius: 999px; }
      .badge.paused { background: color-mix(in srgb, var(--text-muted) 20%, transparent); color: var(--text-muted); }
    `
  ]
})
export class RecurringPage implements OnInit {
  private readonly api = inject(RecurringService);
  private readonly categoriesApi = inject(CategoriesService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);

  protected readonly currencySymbol = this.auth.currencySymbol;

  protected readonly rows = signal<Recurring[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    type: ['EXPENSE' as 'EXPENSE' | 'INCOME' | 'SAVINGS', [Validators.required]],
    categoryId: this.fb.control<number | null>(null, [Validators.required]),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    description: ['', [Validators.maxLength(255)]],
    frequency: ['MONTHLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY', [Validators.required]],
    startDate: this.fb.control<Date | null>(new Date(), [Validators.required]),
    endDate: this.fb.control<Date | null>(null)
  });

  ngOnInit(): void {
    this.refresh();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    const raw = this.form.getRawValue();
    this.submitting.set(true);
    this.api
      .create({
        categoryId: raw.categoryId as number,
        amount: raw.amount as number,
        type: raw.type,
        description: raw.description.trim(),
        frequency: raw.frequency,
        startDate: toIso(raw.startDate as Date),
        endDate: raw.endDate ? toIso(raw.endDate) : null
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.snackBar.open('Recurring transaction saved', 'Dismiss', { duration: 2500 });
          this.form.reset({
            type: 'EXPENSE', categoryId: null, amount: null, description: '',
            frequency: 'MONTHLY', startDate: new Date(), endDate: null
          });
          this.refresh();
        },
        error: () => this.submitting.set(false)
      });
  }

  protected pause(r: Recurring): void {
    this.api.pause(r.id).subscribe(() => this.refresh());
  }
  protected resume(r: Recurring): void {
    this.api.resume(r.id).subscribe(() => this.refresh());
  }

  protected confirmDelete(r: Recurring): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Delete this recurring rule?',
        message: 'Already-created transactions stay. No new ones will be made.',
        confirmLabel: 'Delete',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.delete(r.id).subscribe(() => {
        this.snackBar.open('Removed', 'Dismiss', { duration: 2500 });
        this.refresh();
      });
    });
  }

  private refresh(): void {
    forkJoin({ rows: this.api.list(), categories: this.categoriesApi.list() }).subscribe({
      next: (res) => {
        this.rows.set(res.rows);
        this.categories.set(res.categories);
      }
    });
  }
}
