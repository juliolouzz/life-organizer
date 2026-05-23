import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';

import { ErrorCode } from '../../../core/api/error-codes';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register-page',
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
        <h1>Create your account</h1>
        <p class="subtitle">Track every income and expense in one place.</p>
      </div>

      <mat-card class="auth-card" appearance="outlined">
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Display name</mat-label>
            <input
              matInput
              formControlName="displayName"
              autocomplete="name"
              required
              data-testid="register-displayName"
            />
            @if (form.controls.displayName.touched && form.controls.displayName.invalid) {
              <mat-error>Use 2 to 100 characters.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input
              matInput
              type="email"
              formControlName="email"
              autocomplete="email"
              required
              data-testid="register-email"
            />
            @if (form.controls.email.touched && form.controls.email.hasError('required')) {
              <mat-error>Email is required.</mat-error>
            }
            @if (form.controls.email.touched && form.controls.email.hasError('email')) {
              <mat-error>Enter a valid email.</mat-error>
            }
            @if (form.controls.email.hasError('duplicate')) {
              <mat-error>Email already registered.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input
              matInput
              [type]="showPassword() ? 'text' : 'password'"
              formControlName="password"
              autocomplete="new-password"
              required
              data-testid="register-password"
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
            @if (form.controls.password.touched && form.controls.password.hasError('required')) {
              <mat-error>Password is required.</mat-error>
            }
            @if (form.controls.password.touched && form.controls.password.hasError('minlength')) {
              <mat-error>Use at least 8 characters.</mat-error>
            }
            @if (form.controls.password.touched && form.controls.password.hasError('pattern')) {
              <mat-error>Include at least one letter and one digit.</mat-error>
            }
          </mat-form-field>

          @if (passwordValue().length > 0) {
            <div class="strength" [class]="'strength-' + strength()">
              <div class="strength-bar">
                <span></span>
                <span></span>
                <span></span>
                <span></span>
              </div>
              <small>Password strength: <strong>{{ strengthLabel() }}</strong></small>
            </div>
          }

          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="form.invalid || submitting()"
            data-testid="register-submit"
            class="submit-btn"
          >
            {{ submitting() ? 'Creating...' : 'Create account' }}
          </button>

          <p class="hint">
            Already have an account?
            <a routerLink="/login">Log in</a>
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
      .brand-block { text-align: center; margin-bottom: 32px; }
      .brand-mark {
        width: 56px; height: 56px; border-radius: 14px;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white; display: grid; place-items: center;
        margin: 0 auto 16px;
      }
      .brand-mark .material-symbols-outlined { font-size: 32px; }
      h1 { font-size: 1.75rem; font-weight: 600; letter-spacing: -0.02em; margin: 0; }
      .subtitle { margin: 8px 0 0 0; color: var(--text-muted); }
      .auth-card {
        width: 100%; max-width: 460px; padding: 32px;
        border-radius: var(--radius-lg);
        background: var(--surface-card);
      }
      .submit-btn { height: 48px; font-weight: 600; font-size: 0.95rem; }
      .strength { display: flex; flex-direction: column; gap: 6px; }
      .strength-bar {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 4px;
      }
      .strength-bar span {
        height: 4px;
        background: var(--border-strong);
        border-radius: 2px;
      }
      .strength-weak .strength-bar span:nth-child(-n+1) { background: #ef4444; }
      .strength-fair .strength-bar span:nth-child(-n+2) { background: #f97316; }
      .strength-good .strength-bar span:nth-child(-n+3) { background: #eab308; }
      .strength-strong .strength-bar span { background: #16a34a; }
      .strength small { color: var(--text-muted); }
      .hint { text-align: center; color: var(--text-muted); margin: 4px 0 0 0; font-size: 0.9rem; }
      .hint a {
        color: var(--mat-sys-primary, #7c3aed); font-weight: 500; text-decoration: none;
      }
      .hint a:hover { text-decoration: underline; }
    `
  ]
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(100),
        Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/)
      ]
    ],
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]]
  });

  // Watch the password control as a signal so the strength meter is reactive.
  protected readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: ''
  });

  protected readonly strength = computed<'weak' | 'fair' | 'good' | 'strong'>(() => {
    const v = this.passwordValue();
    let score = 0;
    if (v.length >= 8) score++;
    if (/[A-Z]/.test(v)) score++;
    if (/\d/.test(v)) score++;
    if (/[^A-Za-z0-9]/.test(v)) score++;
    if (v.length >= 12) score++;
    if (score >= 4) return 'strong';
    if (score === 3) return 'good';
    if (score === 2) return 'fair';
    return 'weak';
  });

  protected readonly strengthLabel = computed(() => {
    return this.strength().replace(/^./, (c) => c.toUpperCase());
  });

  protected toggleVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);

    this.auth.register(this.form.getRawValue()).subscribe({
      next: (user) => {
        this.submitting.set(false);
        this.snackBar.open(`Welcome, ${user.displayName}`, 'Dismiss', { duration: 4000 });
        this.router.navigate(['/dashboard']);
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
      if (meta?.['code'] === ErrorCode.USER_EMAIL_EXISTS) {
        this.form.controls.email.setErrors({ duplicate: true });
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
    this.snackBar.open('Could not create account. Please try again.', 'Dismiss', { duration: 4000 });
  }
}
