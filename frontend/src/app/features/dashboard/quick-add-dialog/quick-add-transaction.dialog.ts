import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import {
  MatDialogActions,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';

import { TransactionsService } from '../../transactions/transactions.service';

interface QuickAddResult {
  created: true;
}

function toIso(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

@Component({
  selector: 'app-quick-add-transaction-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatDatepickerModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      <span class="material-symbols-outlined title-icon">add_circle</span>
      Quick add transaction
    </h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="submit()" class="quick-form" novalidate>
        <mat-button-toggle-group formControlName="type" hideSingleSelectionIndicator class="type-toggle">
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

        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <span matTextPrefix>R$&nbsp;</span>
          <input
            matInput
            type="number"
            step="0.01"
            min="0.01"
            formControlName="amount"
            data-testid="quick-amount"
            required
            #amountInput
          />
          @if (controlError('amount'); as msg) { <mat-error>{{ msg }}</mat-error> }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Category</mat-label>
          <input
            matInput
            formControlName="category"
            maxlength="50"
            data-testid="quick-category"
            placeholder="e.g. Groceries"
            required
          />
          @if (controlError('category'); as msg) { <mat-error>{{ msg }}</mat-error> }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description (optional)</mat-label>
          <input
            matInput
            formControlName="description"
            maxlength="255"
            data-testid="quick-description"
            placeholder="What was it for?"
          />
          @if (controlError('description'); as msg) { <mat-error>{{ msg }}</mat-error> }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input
            matInput
            [matDatepicker]="picker"
            formControlName="transactionDate"
            data-testid="quick-date"
            required
          />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
          @if (controlError('transactionDate'); as msg) { <mat-error>{{ msg }}</mat-error> }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="form.invalid || submitting()"
        (click)="submit()"
        data-testid="quick-save"
      >
        @if (submitting()) {
          Saving...
        } @else {
          Save
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      :host { display: block; }
      h2 {
        display: flex;
        align-items: center;
        gap: 10px;
        margin: 0;
      }
      .title-icon {
        color: var(--mat-sys-primary, #7c3aed);
      }
      .quick-form {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding-top: 6px;
        min-width: 360px;
      }
      .type-toggle {
        align-self: stretch;
      }
      .type-toggle ::ng-deep .mat-button-toggle {
        flex: 1;
      }
      .type-toggle .material-symbols-outlined {
        font-size: 18px;
        margin-right: 6px;
        vertical-align: middle;
      }
    `
  ]
})
export class QuickAddTransactionDialog {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(TransactionsService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly ref = inject(MatDialogRef<QuickAddTransactionDialog, QuickAddResult>);

  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    type: ['EXPENSE' as 'EXPENSE' | 'INCOME' | 'SAVINGS', [Validators.required]],
    category: ['', [Validators.required, Validators.maxLength(50)]],
    description: ['', [Validators.maxLength(255)]],
    transactionDate: this.fb.control<Date | null>(new Date(), [Validators.required])
  });

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
    this.api
      .create({
        amount: raw.amount as number,
        type: raw.type,
        category: raw.category.trim(),
        description: raw.description.trim(),
        transactionDate: toIso(raw.transactionDate as Date)
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.snackBar.open('Transaction added', 'Dismiss', { duration: 2500 });
          this.ref.close({ created: true });
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.handleError(err);
        }
      });
  }

  private handleError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 400) {
      const meta = err.error?.meta as Record<string, string> | undefined;
      if (meta && typeof meta === 'object') {
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
