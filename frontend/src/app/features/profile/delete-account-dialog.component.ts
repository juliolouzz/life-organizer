import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { AuthService } from '../../core/auth/auth.service';

export interface DeleteAccountDialogResult {
  confirmed: boolean;
  password: string;
}

@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      <span class="material-symbols-outlined warn">warning</span>
      Delete your account?
    </h2>
    <mat-dialog-content class="content">
      <p>
        Your account, transactions, categories, budgets and recurring rules will be
        scheduled for deletion. You have <strong>30 days</strong> to cancel - we'll
        email a restore link to <strong>{{ email() }}</strong>.
      </p>
      <p>To confirm, type your email and password below.</p>
      <form [formGroup]="form" class="stack">
        <mat-form-field appearance="outline">
          <mat-label>Type your email to confirm</mat-label>
          <input matInput formControlName="emailEcho" autocomplete="off" />
          @if (form.controls.emailEcho.touched && !emailMatches()) {
            <mat-error>Must exactly match {{ email() }}.</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Password</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button
        mat-flat-button
        color="warn"
        [disabled]="form.invalid || !emailMatches()"
        (click)="confirm()"
      >
        Delete account
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .content { max-width: 480px; }
      .stack { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
      .stack mat-form-field { width: 100%; }
      .warn { color: #b91c1c; font-size: 22px; vertical-align: middle; margin-right: 6px; }
      h2 { display: flex; align-items: center; }
    `
  ]
})
export class DeleteAccountDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ref = inject(MatDialogRef<DeleteAccountDialogComponent, DeleteAccountDialogResult>);
  private readonly auth = inject(AuthService);

  protected readonly email = signal(this.auth.currentUser()?.email ?? '');
  protected readonly form = this.fb.nonNullable.group({
    emailEcho: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  protected readonly emailMatches = computed(
    () => this.form.controls.emailEcho.value.trim().toLowerCase() === this.email().toLowerCase()
  );

  protected cancel(): void {
    this.ref.close({ confirmed: false, password: '' });
  }

  protected confirm(): void {
    if (this.form.invalid || !this.emailMatches()) return;
    this.ref.close({ confirmed: true, password: this.form.controls.password.value });
  }
}
