import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AuthService } from '../../../core/auth/auth.service';
import { PasswordResetService } from '../../../core/auth/password-reset.service';

const DISMISS_KEY = 'life-organizer.verify-banner-dismissed';

@Component({
  selector: 'app-verify-email-banner',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shouldShow()) {
      <div class="banner" role="status">
        <span class="material-symbols-outlined banner-icon">mark_email_unread</span>
        <div class="banner-text">
          <strong>Verify your email</strong>
          <small>We sent a link to {{ email() }}. Click it to verify - or resend.</small>
        </div>
        <div class="banner-actions">
          <button mat-stroked-button (click)="resend()" [disabled]="resending()">
            {{ resending() ? 'Sending...' : 'Resend link' }}
          </button>
          <button mat-icon-button (click)="dismiss()" aria-label="Dismiss for this session">
            <span class="material-symbols-outlined">close</span>
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
        background: color-mix(in srgb, #d97706 12%, var(--surface-card));
        border: 1px solid color-mix(in srgb, #d97706 35%, transparent);
      }
      .banner-icon {
        font-size: 24px;
        color: #d97706;
        flex: 0 0 auto;
      }
      .banner-text { flex: 1 1 auto; display: flex; flex-direction: column; }
      .banner-text strong { font-size: 0.95rem; font-weight: 600; }
      .banner-text small { color: var(--text-muted); font-size: 0.82rem; }
      .banner-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
      .banner-actions .material-symbols-outlined { font-size: 20px; }
      @media (max-width: 640px) {
        .banner { flex-wrap: wrap; }
        .banner-text { flex-basis: 100%; }
      }
    `
  ]
})
export class VerifyEmailBannerComponent {
  private readonly auth = inject(AuthService);
  private readonly api = inject(PasswordResetService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly resending = signal(false);
  protected readonly dismissed = signal(this.readDismissed());

  protected readonly shouldShow = computed(() => {
    const user = this.auth.currentUser();
    if (!user) return false;
    if (user.emailVerified) return false;
    return !this.dismissed();
  });

  protected readonly email = computed(() => this.auth.currentUser()?.email ?? '');

  protected resend(): void {
    const userEmail = this.email();
    if (!userEmail || this.resending()) return;
    this.resending.set(true);
    this.api.resendVerification(userEmail).subscribe({
      next: () => {
        this.resending.set(false);
        this.snackBar.open('Verification link sent (check your inbox or backend logs).',
            'Dismiss', { duration: 4500 });
      },
      error: () => {
        this.resending.set(false);
        // Same anti-enumeration message regardless.
        this.snackBar.open('Verification link sent (check your inbox or backend logs).',
            'Dismiss', { duration: 4500 });
      }
    });
  }

  protected dismiss(): void {
    this.dismissed.set(true);
    try {
      sessionStorage.setItem(DISMISS_KEY, '1');
    } catch {
      /* ignore - sessionStorage might be unavailable */
    }
  }

  private readDismissed(): boolean {
    try {
      return sessionStorage.getItem(DISMISS_KEY) === '1';
    } catch {
      return false;
    }
  }
}
