import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { AccountService } from '../../../core/account/account.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-deletion-pending-banner',
  standalone: true,
  imports: [DatePipe, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shouldShow()) {
      <div class="banner" role="alert">
        <span class="material-symbols-outlined banner-icon">schedule</span>
        <div class="banner-text">
          <strong>Account scheduled for deletion</strong>
          @if (scheduledAt(); as when) {
            <small>We will permanently delete your data on {{ when | date: 'longDate' }}. You can still cancel.</small>
          } @else {
            <small>We will permanently delete your data soon. You can still cancel.</small>
          }
        </div>
        <div class="banner-actions">
          <button mat-flat-button color="primary" (click)="cancel()" [disabled]="cancelling()">
            {{ cancelling() ? 'Cancelling...' : 'Cancel deletion' }}
          </button>
        </div>
      </div>
    }
  `,
  styles: [
    `
      :host { display: block; margin-bottom: 16px; }
      .banner {
        display: flex;
        align-items: center;
        gap: 14px;
        padding: 12px 18px;
        border-radius: var(--radius-md);
        background: color-mix(in srgb, #b91c1c 10%, var(--surface-card));
        border: 1px solid color-mix(in srgb, #b91c1c 40%, transparent);
      }
      .banner-icon { font-size: 24px; color: #b91c1c; flex: 0 0 auto; }
      .banner-text { flex: 1 1 auto; display: flex; flex-direction: column; }
      .banner-text strong { font-size: 0.95rem; font-weight: 600; }
      .banner-text small { color: var(--text-muted); font-size: 0.82rem; }
      .banner-actions { flex: 0 0 auto; }
      @media (max-width: 640px) {
        .banner { flex-wrap: wrap; }
        .banner-text { flex-basis: 100%; }
      }
    `
  ]
})
export class DeletionPendingBannerComponent {
  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly cancelling = signal(false);
  protected readonly scheduledAt = computed(() => this.auth.currentUser()?.deletionScheduledAt ?? null);
  protected readonly shouldShow = computed(() => !!this.scheduledAt());

  protected cancel(): void {
    if (this.cancelling()) return;
    this.cancelling.set(true);
    this.accountService.restoreAccount().subscribe({
      next: () => {
        this.cancelling.set(false);
        this.snackBar.open('Deletion cancelled. Your account is active.', 'Dismiss', { duration: 4000 });
      },
      error: () => {
        this.cancelling.set(false);
        this.snackBar.open('Could not cancel deletion. Please log in again.', 'Dismiss', { duration: 4000 });
        // The user's access token may now be rejected by the deletion gate -
        // sign them out so they can use the email restore link instead.
        this.auth.logout();
        this.router.navigate(['/login']);
      }
    });
  }
}
