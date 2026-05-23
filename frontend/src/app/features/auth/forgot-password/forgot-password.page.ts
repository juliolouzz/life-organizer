import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { PasswordResetService } from '../../../core/auth/password-reset.service';

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-page">
      <div class="brand-block">
        <div class="brand-mark" aria-hidden="true">
          <span class="material-symbols-outlined">lock_reset</span>
        </div>
        <h1>Reset your password</h1>
        <p class="subtitle">We'll send you a link to set a new one.</p>
      </div>

      <mat-card class="auth-card" appearance="outlined">
        @if (submitted()) {
          <div class="confirmation">
            <span class="material-symbols-outlined check">mark_email_read</span>
            <h2>Check your email</h2>
            <p>
              If <strong>{{ submittedEmail() }}</strong> is registered, a reset link has been
              sent. (Self-hosted users: the link is also written to the backend log.)
            </p>
            <a mat-flat-button color="primary" routerLink="/login" class="back-btn">Back to login</a>
          </div>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" required />
              @if (form.controls.email.touched && form.controls.email.hasError('required')) {
                <mat-error>Email is required.</mat-error>
              }
              @if (form.controls.email.touched && form.controls.email.hasError('email')) {
                <mat-error>Enter a valid email.</mat-error>
              }
            </mat-form-field>

            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || submitting()"
              class="submit-btn"
            >
              {{ submitting() ? 'Sending...' : 'Send reset link' }}
            </button>

            <p class="hint">
              Remembered your password?
              <a routerLink="/login">Back to login</a>
            </p>
          </form>
        }
      </mat-card>
    </div>
  `,
  styles: [
    `
      .auth-page {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 32px 16px;
        background: var(--surface-bg);
      }
      .brand-block { text-align: center; margin-bottom: 32px; }
      .brand-mark {
        width: 56px; height: 56px; border-radius: 14px;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white; display: grid; place-items: center; margin: 0 auto 16px;
      }
      .brand-mark .material-symbols-outlined { font-size: 30px; }
      h1 { font-size: 1.6rem; font-weight: 600; letter-spacing: -0.02em; margin: 0; }
      .subtitle { margin: 8px 0 0 0; color: var(--text-muted); }
      .auth-card { width: 100%; max-width: 420px; padding: 32px; border-radius: var(--radius-lg); }
      .submit-btn { height: 48px; font-weight: 600; }
      .hint { text-align: center; color: var(--text-muted); font-size: 0.9rem; margin: 4px 0 0 0; }
      .hint a { color: var(--mat-sys-primary, #7c3aed); font-weight: 500; text-decoration: none; }
      .hint a:hover { text-decoration: underline; }
      .confirmation {
        display: flex; flex-direction: column; align-items: center;
        text-align: center; gap: 12px;
      }
      .confirmation .check {
        font-size: 48px; color: var(--money-positive); margin-bottom: 8px;
      }
      .confirmation h2 { font-size: 1.2rem; margin: 0; font-weight: 600; }
      .confirmation p { color: var(--text-muted); margin: 0; }
      .back-btn { margin-top: 16px; }
    `
  ]
})
export class ForgotPasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PasswordResetService);

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly submittedEmail = signal('');

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    const email = this.form.controls.email.value.trim();
    this.submitting.set(true);
    // The endpoint always returns 200 to prevent enumeration - same outcome on UI.
    this.api.forgotPassword(email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submittedEmail.set(email);
        this.submitted.set(true);
      },
      error: () => {
        this.submitting.set(false);
        // Even on error (network), pretend success - we don't want to leak info.
        this.submittedEmail.set(email);
        this.submitted.set(true);
      }
    });
  }
}
