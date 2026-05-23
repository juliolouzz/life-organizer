import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { ErrorCode } from '../../../core/api/error-codes';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
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
          <span class="material-symbols-outlined">orbit</span>
        </div>
        <h1>Welcome back</h1>
        <p class="subtitle">Sign in to manage your transactions.</p>
      </div>

      <mat-card class="auth-card" appearance="outlined">
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input
              matInput
              type="email"
              formControlName="email"
              autocomplete="email"
              required
              data-testid="login-email"
            />
            @if (form.controls.email.touched && form.controls.email.hasError('required')) {
              <mat-error>Email is required.</mat-error>
            }
            @if (form.controls.email.touched && form.controls.email.hasError('email')) {
              <mat-error>Enter a valid email.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input
              matInput
              [type]="showPassword() ? 'text' : 'password'"
              formControlName="password"
              autocomplete="current-password"
              required
              data-testid="login-password"
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
            @if (form.controls.password.touched && form.controls.password.hasError('required')) {
              <mat-error>Password is required.</mat-error>
            }
          </mat-form-field>

          @if (formError(); as err) {
            <div class="form-error" role="alert">{{ err }}</div>
          }

          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="form.invalid || submitting()"
            data-testid="login-submit"
            class="submit-btn"
          >
            {{ submitting() ? 'Signing in...' : 'Log in' }}
          </button>

          <p class="hint">
            Don't have an account?
            <a routerLink="/register">Register</a>
          </p>
        </form>
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
      .brand-block {
        text-align: center;
        margin-bottom: 32px;
      }
      .brand-mark {
        width: 56px;
        height: 56px;
        border-radius: 14px;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white;
        display: grid;
        place-items: center;
        margin: 0 auto 16px;
      }
      .brand-mark .material-symbols-outlined { font-size: 32px; }
      h1 {
        font-size: 1.75rem;
        font-weight: 600;
        letter-spacing: -0.02em;
        margin: 0;
      }
      .subtitle {
        margin: 8px 0 0 0;
        color: var(--text-muted);
      }
      .auth-card {
        width: 100%;
        max-width: 420px;
        padding: 32px;
        border-radius: var(--radius-lg);
        background: var(--surface-card);
      }
      .submit-btn {
        height: 48px;
        font-weight: 600;
        font-size: 0.95rem;
      }
      .form-error {
        padding: 12px 14px;
        border-radius: var(--radius-sm);
        background: rgba(185, 28, 28, 0.08);
        color: var(--money-negative);
        font-size: 0.875rem;
        border: 1px solid rgba(185, 28, 28, 0.2);
      }
      .hint {
        text-align: center;
        color: var(--text-muted);
        margin: 4px 0 0 0;
        font-size: 0.9rem;
      }
      .hint a {
        color: var(--mat-sys-primary, #7c3aed);
        font-weight: 500;
        text-decoration: none;
      }
      .hint a:hover { text-decoration: underline; }
    `
  ]
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  protected toggleVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.formError.set(this.messageFor(err));
      }
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const meta = err.error?.meta as { code?: string } | undefined;
      if (meta?.code === ErrorCode.INVALID_CREDENTIALS) {
        return 'Invalid email or password';
      }
    }
    return 'Could not sign in. Please try again.';
  }
}
