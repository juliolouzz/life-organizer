import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ErrorCode } from '../../../core/api/error-codes';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TransactionsService } from '../transactions.service';

@Component({
  selector: 'app-transaction-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatDatepickerModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      [title]="mode() === 'edit' ? 'Edit transaction' : 'New transaction'"
      [subtitle]="mode() === 'edit' ? 'Update the fields below.' : 'Record a new income or expense.'"
    >
      <a mat-stroked-button routerLink="/transactions">
        <span class="material-symbols-outlined">arrow_back</span>
        Back to list
      </a>
    </app-page-header>

    @if (mode() === 'edit' && !loaded()) {
      <div class="loading">
        <mat-progress-spinner mode="indeterminate" diameter="40" />
      </div>
    } @else {
      <mat-card class="form-card" appearance="outlined">
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
          <div class="row gap-2">
            <mat-button-toggle-group
              formControlName="type"
              hideSingleSelectionIndicator
              class="type-toggle"
            >
              <mat-button-toggle value="EXPENSE">
                <span class="material-symbols-outlined">trending_down</span>
                Expense
              </mat-button-toggle>
              <mat-button-toggle value="INCOME">
                <span class="material-symbols-outlined">trending_up</span>
                Income
              </mat-button-toggle>
              <mat-button-toggle value="SAVINGS">
                <span class="material-symbols-outlined">savings</span>
                Savings
              </mat-button-toggle>
            </mat-button-toggle-group>
          </div>

          <mat-form-field appearance="outline">
            <mat-label>Amount</mat-label>
            <span matTextPrefix>R$&nbsp;</span>
            <input
              matInput
              type="number"
              step="0.01"
              min="0.01"
              formControlName="amount"
              data-testid="form-amount"
              required
            />
            @if (controlError('amount'); as msg) { <mat-error>{{ msg }}</mat-error> }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Category</mat-label>
            <input
              matInput
              formControlName="category"
              maxlength="50"
              data-testid="form-category"
              required
            />
            <mat-hint align="end">{{ form.controls.category.value.length }}/50</mat-hint>
            @if (controlError('category'); as msg) { <mat-error>{{ msg }}</mat-error> }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Description (optional)</mat-label>
            <textarea
              matInput
              formControlName="description"
              rows="2"
              maxlength="255"
              data-testid="form-description"
            ></textarea>
            <mat-hint align="end">{{ form.controls.description.value.length }}/255</mat-hint>
            @if (controlError('description'); as msg) { <mat-error>{{ msg }}</mat-error> }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Date</mat-label>
            <input
              matInput
              [matDatepicker]="picker"
              formControlName="transactionDate"
              data-testid="form-date"
              required
            />
            <mat-datepicker-toggle matIconSuffix [for]="picker" />
            <mat-datepicker #picker />
            @if (controlError('transactionDate'); as msg) { <mat-error>{{ msg }}</mat-error> }
          </mat-form-field>

          <div class="actions">
            <a mat-stroked-button routerLink="/transactions" type="button">Cancel</a>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || submitting()"
              data-testid="form-submit"
            >
              {{ submitting() ? saveLabel() + '...' : saveLabel() }}
            </button>
          </div>
        </form>
      </mat-card>
    }
  `,
  styles: [
    `
      .form-card {
        max-width: 640px;
        padding: 32px;
        border-radius: var(--radius-lg);
      }
      .type-toggle {
        border-radius: var(--radius-md);
      }
      .type-toggle .material-symbols-outlined {
        font-size: 18px;
        margin-right: 6px;
        vertical-align: middle;
      }
      .actions {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 12px;
        padding-top: 24px;
        border-top: 1px solid var(--border-subtle);
      }
      .loading {
        padding: 64px;
        display: grid;
        place-items: center;
      }
    `
  ]
})
export class TransactionFormPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(TransactionsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly mode = signal<'create' | 'edit'>('create');
  protected readonly editingId = signal<number | null>(null);
  protected readonly loaded = signal(false);
  protected readonly submitting = signal(false);

  protected readonly saveLabel = computed(() => (this.mode() === 'edit' ? 'Save' : 'Create'));

  protected readonly form = this.fb.nonNullable.group({
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    type: ['EXPENSE' as 'EXPENSE' | 'INCOME' | 'SAVINGS', [Validators.required]],
    category: ['', [Validators.required, Validators.maxLength(50)]],
    description: ['', [Validators.maxLength(255)]],
    transactionDate: this.fb.control<Date | null>(new Date(), [Validators.required])
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.mode.set('edit');
      this.editingId.set(id);
      this.api.getById(id).subscribe({
        next: (tx) => {
          this.form.patchValue({
            amount: Number(tx.amount),
            type: tx.type,
            category: tx.category,
            description: tx.description,
            transactionDate: new Date(tx.transactionDate + 'T00:00:00')
          });
          this.loaded.set(true);
        },
        error: () => {
          this.snackBar.open('Transaction not found', 'Dismiss', { duration: 4000 });
          this.router.navigate(['/transactions']);
        }
      });
    } else {
      this.loaded.set(true);
    }
  }

  protected controlError(field: string): string | null {
    const c = this.form.get(field);
    if (!c || !c.touched || !c.errors) return null;
    if (c.errors['required']) return 'Required.';
    if (c.errors['maxlength']) return `Maximum ${c.errors['maxlength'].requiredLength} characters.`;
    if (c.errors['min']) return `Must be greater than ${c.errors['min'].min}.`;
    if (typeof c.errors['server'] === 'string') return c.errors['server'];
    return 'Invalid value.';
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);

    const raw = this.form.getRawValue();
    const payload = {
      amount: raw.amount as number,
      type: raw.type,
      category: raw.category.trim(),
      description: raw.description.trim(),
      transactionDate: toIso(raw.transactionDate as Date)
    };

    const obs =
      this.mode() === 'edit'
        ? this.api.update(this.editingId() as number, payload)
        : this.api.create(payload);

    obs.subscribe({
      next: () => {
        this.submitting.set(false);
        const msg = this.mode() === 'edit' ? 'Transaction updated' : 'Transaction created';
        this.snackBar.open(msg, 'Dismiss', { duration: 3000 });
        this.router.navigate(['/transactions']);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.handleError(err);
      }
    });
  }

  private handleError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const meta = err.error?.meta as Record<string, string> | undefined;
      if (meta?.['code'] === ErrorCode.TRANSACTION_NOT_FOUND) {
        this.snackBar.open('Transaction not found', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/transactions']);
        return;
      }
      if (err.status === 400 && meta && typeof meta === 'object') {
        for (const [field, message] of Object.entries(meta)) {
          if (field === 'code') continue;
          const control = this.form.get(field);
          if (control) control.setErrors({ server: message });
        }
        return;
      }
    }
    this.snackBar.open('Could not save. Please try again.', 'Dismiss', { duration: 4000 });
  }
}

function toIso(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}
