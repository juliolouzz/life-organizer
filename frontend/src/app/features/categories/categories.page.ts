import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';

import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Category, CategoriesService, CategoryKind } from './categories.service';

@Component({
  selector: 'app-categories-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Categories"
      subtitle="Name and group the things you spend on or earn from."
    />

    <mat-card appearance="outlined" class="create-card">
      <h3>Add category</h3>
      <form [formGroup]="form" (ngSubmit)="submit()" class="form-row">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="50" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Kind</mat-label>
          <mat-select formControlName="kind">
            <mat-option value="EXPENSE">Expense</mat-option>
            <mat-option value="INCOME">Income</mat-option>
            <mat-option value="SAVINGS">Savings</mat-option>
            <mat-option value="BOTH">Both</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" [disabled]="form.invalid || submitting()">
          Add
        </button>
      </form>
    </mat-card>

    @if (categories().length === 0) {
      <app-empty-state
        icon="folder"
        title="No categories yet"
        description="Categories help group your transactions and set up budgets."
      />
    } @else {
      <mat-card appearance="outlined" class="list-card">
        <table mat-table [dataSource]="categories()" class="cat-table">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let c">{{ c.name }}</td>
          </ng-container>
          <ng-container matColumnDef="kind">
            <th mat-header-cell *matHeaderCellDef>Kind</th>
            <td mat-cell *matCellDef="let c">
              <span class="kind-badge" [class]="'k-' + c.kind.toLowerCase()">
                {{ humanKind(c.kind) }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let c">
              <button mat-icon-button (click)="confirmArchive(c)" aria-label="Archive category">
                <span class="material-symbols-outlined">archive</span>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="['name', 'kind', 'actions']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['name', 'kind', 'actions']"></tr>
        </table>
      </mat-card>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .create-card {
        padding: 20px 24px;
        margin-bottom: 24px;
        border-radius: var(--radius-md);
      }
      h3 { margin: 0 0 12px 0; font-size: 1rem; font-weight: 600; }
      .form-row {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        flex-wrap: wrap;
      }
      .form-row mat-form-field { min-width: 200px; }
      .list-card {
        padding: 0;
        border-radius: var(--radius-md);
        overflow: hidden;
      }
      .cat-table { width: 100%; }
      .kind-badge {
        display: inline-block;
        font-size: 0.75rem;
        font-weight: 600;
        padding: 4px 10px;
        border-radius: 999px;
      }
      .k-income {
        background: color-mix(in srgb, var(--money-positive) 15%, transparent);
        color: var(--money-positive);
      }
      .k-expense {
        background: color-mix(in srgb, var(--money-negative) 15%, transparent);
        color: var(--money-negative);
      }
      .k-savings { background: color-mix(in srgb, #d97706 18%, transparent); color: #d97706; }
      .k-both { background: color-mix(in srgb, #7c3aed 15%, transparent); color: #7c3aed; }
    `
  ]
})
export class CategoriesPage implements OnInit {
  private readonly api = inject(CategoriesService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected readonly categories = signal<Category[]>([]);
  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    kind: ['EXPENSE' as CategoryKind, [Validators.required]]
  });

  ngOnInit(): void {
    this.refresh();
  }

  protected humanKind(kind: CategoryKind): string {
    return kind === 'BOTH' ? 'Both' : kind.charAt(0) + kind.slice(1).toLowerCase();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.api.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.form.reset({ name: '', kind: 'EXPENSE' });
        this.snackBar.open('Category added', 'Dismiss', { duration: 2500 });
        this.refresh();
      },
      error: () => this.submitting.set(false)
    });
  }

  protected confirmArchive(c: Category): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Archive this category?',
        message: `"${c.name}" will be hidden. Existing transactions and budgets are unaffected.`,
        confirmLabel: 'Archive',
        destructive: true
      }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.archive(c.id).subscribe({
        next: () => {
          this.snackBar.open('Category archived', 'Dismiss', { duration: 2500 });
          this.refresh();
        }
      });
    });
  }

  private refresh(): void {
    this.api.list().subscribe((rows) => this.categories.set(rows));
  }
}
