import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ErrorCode } from '../../../core/api/error-codes';
import { PasswordResetService } from '../../../core/auth/password-reset.service';

@Component({
  selector: 'app-reset-password-page',
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
          <span class="material-symbols-outlined">password</span>
        </div>
        <h1>Set a new password</h1>
        <p class="subtitle">Choose a password and you'll be logged in.</p>
      </div>

      <mat-card class="auth-card" appearance="outlined">
        @if (!token()) {
          <div class="error-block">
            <span class="material-symbols-outlined">error</span>
            <p>This reset link is missing the token. Request a new one.</p>
            <a mat-stroked-button routerLink="/forgot-password">Request new link</a>
          </div>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
            <mat-form-field appearance="outline">
              <mat-label>New password</mat-label>
              <input
                matInput
                [type]="showPassword() ? 'text' : 'password'"
                formControlName="newPassword"
                autocomplete="new-password"
                required
              />
              <button
                type="button"
                mat-icon-button
                matSuffix
                (click)="toggleVisibility()"
                [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
              >
                <span class="material-symbols-outlined">
                  {{ showPassword() ? 'visibility_off' : 'visibility' }}
                </span>
              </button>
              <mat-hint>At least 8 characters, one letter and one digit.</mat-hint>
              @if (form.controls.newPassword.touched && form.controls.newPassword.hasError('required')) {
                <mat-error>Password is required.</mat-error>
              }
              @if (form.controls.newPassword.touched && form.controls.newPassword.hasError('minlength')) {
                <mat-error>Use at least 8 characters.</mat-error>
              }
              @if (form.controls.newPassword.touched && form.controls.newPassword.hasError('pattern')) {
                <mat-error>Include at least one letter and one digit.</mat-error>
              }
              @if (form.controls.newPassword.hasError('server')) {
                <mat-error>{{ form.controls.newPassword.getError('server') }}</mat-error>
              }
            </mat-form-field>

            @if (formError(); as msg) {
              <div class="form-error" role="alert">{{ msg }}</div>
            }

            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || submitting()"
              class="submit-btn"
            >
              {{ submitting() ? 'Setting password...' : 'Set password' }}
            </button>

            <p class="hint">
              Token expired?
              <a routerLink="/forgot-password">Request a new link</a>
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
        display: flex; flex-direction: column;
        align-items: center; justify-content: center;
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
      .auth-card { width: 100%; max-width: 460px; padding: 32px; border-radius: var(--radius-lg); }
      .submit-btn { height: 48px; font-weight: 600; }
      .hint { text-align: center; color: var(--text-muted); font-size: 0.9rem; margin: 4px 0 0 0; }
      .hint a { color: var(--mat-sys-primary, #7c3aed); font-weight: 500; }
      .form-error {
        padding: 12px 14px;
        border-radius: var(--radius-sm);
        background: rgba(185, 28, 28, 0.08);
        color: var(--money-negative);
        font-size: 0.875rem;
        border: 1px solid rgba(185, 28, 28, 0.2);
      }
      .error-block {
        display: flex; flex-direction: column; align-items: center; text-align: center; gap: 12px;
      }
      .error-block .material-symbols-outlined {
        font-size: 40px; color: var(--money-negative);
      }
    `
  ]
})
export class ResetPasswordPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PasswordResetService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly token = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(100),
        Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/)
      ]
    ]
  });

  ngOnInit(): void {
    const t = this.route.snapshot.queryParamMap.get('token');
    this.token.set(t && t.trim().length > 0 ? t : null);
  }

  protected toggleVisibility(): void { this.showPassword.update((v) => !v); }

  protected submit(): void {
    const token = this.token();
    if (!token || this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);
    this.api.resetPassword(token, this.form.controls.newPassword.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.snackBar.open('Password updated. Please log in.', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/login']);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.formError.set(this.messageFor(err));
      }
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const meta = err.error?.meta as Record<string, string> | undefined;
      const code = meta?.['code'];
      if (code === ErrorCode.INVALID_TOKEN) {
        return 'This link is invalid or has expired. Request a new one.';
      }
      if (code === ErrorCode.USER_NOT_FOUND) {
        return 'No account matches this link. Request a new one.';
      }
      if (err.status === 400 && meta?.['newPassword']) {
        this.form.controls.newPassword.setErrors({ server: meta['newPassword'] });
        return '';
      }
    }
    return 'Could not set the new password. Please try again.';
  }
}
