import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { AccountService } from '../../core/account/account.service';
import { ErrorCode } from '../../core/api/error-codes';
import { AuthService } from '../../core/auth/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { DeleteAccountDialogComponent, DeleteAccountDialogResult } from './delete-account-dialog.component';
import { LogoutAllDialogComponent, LogoutAllDialogResult } from './logout-all-dialog.component';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatDialogModule,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Account" subtitle="Manage your profile, sign-in details and data." />

    @if (user(); as u) {
      <!-- Identity card -->
      <mat-card class="card" appearance="outlined">
        <div class="profile-row">
          <div class="avatar">{{ initials(u.displayName) }}</div>
          <div class="profile-info">
            <h2>{{ u.displayName }}</h2>
            <p>{{ u.email }}</p>
            <mat-chip-set>
              <mat-chip>{{ humanRole(u.role) }}</mat-chip>
              <mat-chip>ID #{{ u.id }}</mat-chip>
              @if (u.emailVerified === false) {
                <mat-chip class="chip-warn">Email unverified</mat-chip>
              }
            </mat-chip-set>
          </div>
          <button mat-stroked-button color="warn" class="logout-btn" (click)="logout()">
            <span class="material-symbols-outlined">logout</span>
            Log out
          </button>
        </div>
      </mat-card>

      <!-- Profile section -->
      <mat-card class="card" appearance="outlined">
        <h3 class="section-title">Profile</h3>
        <form [formGroup]="profileForm" (ngSubmit)="saveProfile()" class="stack">
          <mat-form-field appearance="outline">
            <mat-label>Display name</mat-label>
            <input matInput formControlName="displayName" autocomplete="name" />
            @if (profileForm.controls.displayName.hasError('required')) {
              <mat-error>Display name is required.</mat-error>
            }
            @if (profileForm.controls.displayName.hasError('minlength')) {
              <mat-error>Must be at least 2 characters.</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Currency</mat-label>
            <mat-select formControlName="currency" data-testid="profile-currency">
              <mat-option value="BRL">Brazilian Real (R$)</mat-option>
              <mat-option value="USD">US Dollar ($)</mat-option>
              <mat-option value="EUR">Euro (€)</mat-option>
            </mat-select>
            <mat-hint>Display only - existing transactions keep their stored amount.</mat-hint>
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="profileForm.invalid || profileSubmitting()"
          >
            {{ profileSubmitting() ? 'Saving...' : 'Save changes' }}
          </button>
        </form>
      </mat-card>

      <!-- Password section -->
      <mat-card class="card" appearance="outlined">
        <h3 class="section-title">Password</h3>
        <form [formGroup]="passwordForm" (ngSubmit)="savePassword()" class="stack">
          <mat-form-field appearance="outline">
            <mat-label>Current password</mat-label>
            <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" />
            @if (passwordForm.controls.currentPassword.hasError('server')) {
              <mat-error>{{ passwordForm.controls.currentPassword.getError('server') }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>New password</mat-label>
            <input matInput type="password" formControlName="newPassword" autocomplete="new-password" />
            <mat-hint>At least 8 characters, one letter and one digit.</mat-hint>
            @if (passwordForm.controls.newPassword.touched && passwordForm.controls.newPassword.hasError('pattern')) {
              <mat-error>Include at least one letter and one digit.</mat-error>
            }
            @if (passwordForm.controls.newPassword.touched && passwordForm.controls.newPassword.hasError('minlength')) {
              <mat-error>Use at least 8 characters.</mat-error>
            }
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="passwordForm.invalid || passwordSubmitting()"
          >
            {{ passwordSubmitting() ? 'Updating...' : 'Update password' }}
          </button>
        </form>
        <div class="divider"></div>
        <div class="sessions-block">
          <h4>Active sessions</h4>
          <p class="muted">
            Signed in on another device and want to revoke access?
            "Sign out everywhere" invalidates every token, including the one on this device.
          </p>
          <button mat-stroked-button color="warn" (click)="openLogoutAllDialog()" [disabled]="loggingOutAll()">
            <span class="material-symbols-outlined">logout</span>
            {{ loggingOutAll() ? 'Signing out...' : 'Sign out of all devices' }}
          </button>
        </div>
      </mat-card>

      <!-- Email section -->
      <mat-card class="card" appearance="outlined">
        <h3 class="section-title">Email</h3>
        <p class="muted">Your sign-in address. We'll verify the new one before applying the change.</p>
        @if (emailSubmitted()) {
          <div class="ok-block">
            <span class="material-symbols-outlined">mark_email_read</span>
            <p>Verification link sent to <strong>{{ emailSubmittedTo() }}</strong>. Click it to apply the change. Your current email still works until then.</p>
            <button mat-stroked-button (click)="resetEmailForm()">Change another</button>
          </div>
        } @else {
          <form [formGroup]="emailForm" (ngSubmit)="saveEmail()" class="stack">
            <mat-form-field appearance="outline">
              <mat-label>New email</mat-label>
              <input matInput type="email" formControlName="newEmail" autocomplete="email" />
              @if (emailForm.controls.newEmail.hasError('server')) {
                <mat-error>{{ emailForm.controls.newEmail.getError('server') }}</mat-error>
              }
              @if (emailForm.controls.newEmail.touched && emailForm.controls.newEmail.hasError('email')) {
                <mat-error>Enter a valid email.</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Current password</mat-label>
              <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" />
              @if (emailForm.controls.currentPassword.hasError('server')) {
                <mat-error>{{ emailForm.controls.currentPassword.getError('server') }}</mat-error>
              }
            </mat-form-field>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="emailForm.invalid || emailSubmitting()"
            >
              {{ emailSubmitting() ? 'Sending...' : 'Send verification link' }}
            </button>
          </form>
        }
      </mat-card>

      <!-- Danger zone -->
      <mat-card class="card danger" appearance="outlined">
        <h3 class="section-title">Delete account</h3>
        <p class="muted">
          You'll have 30 days to change your mind. After that, every transaction, category,
          budget and recurring rule you own is removed for good.
        </p>
        <button mat-stroked-button color="warn" (click)="openDeleteDialog()">
          <span class="material-symbols-outlined">delete_forever</span>
          Delete my account
        </button>
      </mat-card>
    }
  `,
  styles: [
    `
      .card {
        padding: 28px;
        border-radius: var(--radius-lg);
        max-width: 640px;
        margin-bottom: 20px;
      }
      .section-title {
        margin: 0 0 16px 0;
        font-size: 1.05rem;
        font-weight: 600;
        letter-spacing: -0.01em;
      }
      .muted { color: var(--text-muted); margin: 0 0 16px 0; }
      .profile-row {
        display: flex;
        align-items: center;
        gap: 20px;
      }
      .avatar {
        width: 64px; height: 64px; border-radius: 50%;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white; display: grid; place-items: center;
        font-size: 1.4rem; font-weight: 600; flex: 0 0 auto;
      }
      .profile-info { flex: 1 1 auto; }
      .profile-info h2 { margin: 0; font-size: 1.25rem; font-weight: 600; }
      .profile-info p { margin: 4px 0 10px 0; color: var(--text-muted); }
      .logout-btn { flex: 0 0 auto; }
      .stack { display: flex; flex-direction: column; gap: 10px; }
      .stack mat-form-field { width: 100%; }
      .stack button { align-self: flex-start; }
      .chip-warn {
        background: color-mix(in srgb, #d97706 20%, var(--surface-card));
        color: #d97706;
      }
      .danger { border-color: color-mix(in srgb, #b91c1c 30%, transparent); }
      .divider { height: 1px; background: var(--border-subtle); margin: 18px 0; }
      .sessions-block h4 { margin: 0 0 6px 0; font-size: 0.95rem; font-weight: 600; }
      .sessions-block .muted { font-size: 0.85rem; margin-bottom: 12px; }
      .ok-block {
        display: flex; flex-direction: column; align-items: flex-start;
        gap: 10px; padding: 14px;
        background: color-mix(in srgb, var(--money-positive) 8%, var(--surface-card));
        border-radius: var(--radius-md);
      }
      .ok-block .material-symbols-outlined { color: var(--money-positive); font-size: 28px; }
      .material-symbols-outlined { font-size: 18px; margin-right: 4px; vertical-align: middle; }
      @media (max-width: 640px) {
        .profile-row { flex-wrap: wrap; }
        .logout-btn { width: 100%; margin-top: 12px; }
      }
    `
  ]
})
export class ProfilePage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;
  protected readonly currentEmail = computed(() => this.user()?.email ?? '');

  protected readonly profileSubmitting = signal(false);
  protected readonly passwordSubmitting = signal(false);
  protected readonly emailSubmitting = signal(false);
  protected readonly emailSubmitted = signal(false);
  protected readonly emailSubmittedTo = signal('');
  protected readonly loggingOutAll = signal(false);

  protected readonly profileForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    currency: ['BRL' as 'BRL' | 'USD' | 'EUR', [Validators.required]]
  });

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
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

  protected readonly emailForm = this.fb.nonNullable.group({
    newEmail: ['', [Validators.required, Validators.email]],
    currentPassword: ['', [Validators.required]]
  });

  ngOnInit(): void {
    if (!this.user()) {
      this.auth.fetchMe().subscribe(() => this.seedProfile());
    } else {
      this.seedProfile();
    }
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  protected saveProfile(): void {
    if (this.profileForm.invalid || this.profileSubmitting()) return;
    this.profileSubmitting.set(true);
    const { displayName, currency } = this.profileForm.getRawValue();
    this.accountService.updateProfile({ displayName: displayName.trim(), currency })
      .subscribe({
        next: () => {
          this.profileSubmitting.set(false);
          this.snackBar.open('Profile updated.', 'Dismiss', { duration: 3000 });
        },
        error: () => {
          this.profileSubmitting.set(false);
          this.snackBar.open('Could not update profile.', 'Dismiss', { duration: 4000 });
        }
      });
  }

  protected savePassword(): void {
    if (this.passwordForm.invalid || this.passwordSubmitting()) return;
    this.passwordSubmitting.set(true);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.accountService.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.passwordSubmitting.set(false);
        this.passwordForm.reset({ currentPassword: '', newPassword: '' });
        this.snackBar.open('Password updated.', 'Dismiss', { duration: 4000 });
      },
      error: (err: unknown) => {
        this.passwordSubmitting.set(false);
        this.applyPasswordError(err);
      }
    });
  }

  protected saveEmail(): void {
    if (this.emailForm.invalid || this.emailSubmitting()) return;
    this.emailSubmitting.set(true);
    const { newEmail, currentPassword } = this.emailForm.getRawValue();
    this.accountService.requestEmailChange({ newEmail: newEmail.trim(), currentPassword }).subscribe({
      next: () => {
        this.emailSubmitting.set(false);
        this.emailSubmittedTo.set(newEmail.trim());
        this.emailSubmitted.set(true);
      },
      error: (err: unknown) => {
        this.emailSubmitting.set(false);
        this.applyEmailError(err);
      }
    });
  }

  protected resetEmailForm(): void {
    this.emailForm.reset({ newEmail: '', currentPassword: '' });
    this.emailSubmitted.set(false);
  }

  protected openLogoutAllDialog(): void {
    const ref = this.dialog.open<LogoutAllDialogComponent, void, LogoutAllDialogResult>(
      LogoutAllDialogComponent
    );
    ref.afterClosed().subscribe((result) => {
      if (!result?.confirmed) return;
      this.loggingOutAll.set(true);
      this.accountService.logoutAllSessions({ password: result.password }).subscribe({
        next: () => {
          this.loggingOutAll.set(false);
          this.snackBar.open('Signed out of all devices.', 'Dismiss', { duration: 4000 });
          this.auth.logout();
          this.router.navigate(['/login']);
        },
        error: (err: unknown) => {
          this.loggingOutAll.set(false);
          if (err instanceof HttpErrorResponse && err.error?.meta?.code === ErrorCode.INVALID_CREDENTIALS) {
            this.snackBar.open('Wrong password. No sessions were signed out.', 'Dismiss', { duration: 4000 });
          } else {
            this.snackBar.open('Could not sign out of all devices.', 'Dismiss', { duration: 4000 });
          }
        }
      });
    });
  }

  protected openDeleteDialog(): void {
    const ref = this.dialog.open<DeleteAccountDialogComponent, void, DeleteAccountDialogResult>(
      DeleteAccountDialogComponent
    );
    ref.afterClosed().subscribe((result) => {
      if (!result?.confirmed) return;
      this.accountService.deleteAccount({ password: result.password }).subscribe({
        next: (resp) => {
          this.snackBar.open('Account scheduled for deletion.', 'Dismiss', { duration: 3000 });
          this.auth.logout();
          this.router.navigate(['/deletion-pending'], {
            queryParams: { date: resp.deletionScheduledAt }
          });
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.error?.meta?.code === ErrorCode.INVALID_CREDENTIALS) {
            this.snackBar.open('Wrong password. Account not deleted.', 'Dismiss', { duration: 4000 });
          } else {
            this.snackBar.open('Could not delete account.', 'Dismiss', { duration: 4000 });
          }
        }
      });
    });
  }

  protected initials(name: string): string {
    return (
      name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((p) => p[0]?.toUpperCase() ?? '')
        .join('') || 'U'
    );
  }

  protected humanRole(role: string): string {
    return role === 'ROLE_ADMIN' ? 'Admin' : 'User';
  }

  private seedProfile(): void {
    const u = this.user();
    if (u) {
      this.profileForm.controls.displayName.setValue(u.displayName);
      this.profileForm.controls.currency.setValue(u.currency ?? 'BRL');
    }
  }

  private applyPasswordError(err: unknown): void {
    if (!(err instanceof HttpErrorResponse)) {
      this.snackBar.open('Could not update password.', 'Dismiss', { duration: 4000 });
      return;
    }
    const code = err.error?.meta?.code as string | undefined;
    if (code === ErrorCode.INVALID_CREDENTIALS) {
      this.passwordForm.controls.currentPassword.setErrors({ server: 'Current password is incorrect.' });
      return;
    }
    const fieldErrors = err.error?.meta as Record<string, string> | undefined;
    if (err.status === 400 && fieldErrors?.['newPassword']) {
      this.passwordForm.controls.newPassword.setErrors({ server: fieldErrors['newPassword'] });
      return;
    }
    this.snackBar.open('Could not update password.', 'Dismiss', { duration: 4000 });
  }

  private applyEmailError(err: unknown): void {
    if (!(err instanceof HttpErrorResponse)) {
      this.snackBar.open('Could not change email.', 'Dismiss', { duration: 4000 });
      return;
    }
    const code = err.error?.meta?.code as string | undefined;
    if (code === ErrorCode.INVALID_CREDENTIALS) {
      this.emailForm.controls.currentPassword.setErrors({ server: 'Current password is incorrect.' });
      return;
    }
    if (code === ErrorCode.USER_EMAIL_EXISTS) {
      this.emailForm.controls.newEmail.setErrors({ server: 'This email is already in use.' });
      return;
    }
    if (code === ErrorCode.USER_EMAIL_UNCHANGED) {
      this.emailForm.controls.newEmail.setErrors({ server: 'That is already your email.' });
      return;
    }
    const fieldErrors = err.error?.meta as Record<string, string> | undefined;
    if (err.status === 400 && fieldErrors?.['newEmail']) {
      this.emailForm.controls.newEmail.setErrors({ server: fieldErrors['newEmail'] });
      return;
    }
    this.snackBar.open('Could not change email.', 'Dismiss', { duration: 4000 });
  }
}
