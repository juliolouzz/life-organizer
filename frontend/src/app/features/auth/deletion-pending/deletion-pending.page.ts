import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-deletion-pending-page',
  standalone: true,
  imports: [DatePipe, RouterLink, MatCardModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-page">
      <mat-card class="auth-card" appearance="outlined">
        <div class="block">
          <span class="material-symbols-outlined warn">schedule</span>
          <h2>Account scheduled for deletion</h2>
          @if (scheduledAt(); as when) {
            <p>
              We will permanently delete your data on
              <strong>{{ when | date: 'longDate' }}</strong>.
            </p>
          } @else {
            <p>We will permanently delete your data after the 30-day grace period.</p>
          }
          <p>
            Changed your mind? Open the restore link we sent to your email
            within 30 days to keep your account.
          </p>
          <a mat-stroked-button routerLink="/login">Back to login</a>
        </div>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .auth-page {
        min-height: 100vh;
        display: flex; align-items: center; justify-content: center;
        padding: 32px 16px;
        background: var(--surface-bg);
      }
      .auth-card { width: 100%; max-width: 480px; padding: 40px 32px; border-radius: var(--radius-lg); }
      .block { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 12px; }
      .warn { font-size: 56px; color: #d97706; }
      h2 { font-size: 1.25rem; font-weight: 600; margin: 0; letter-spacing: -0.01em; }
      p { color: var(--text-muted); margin: 0; }
      a { margin-top: 16px; }
    `
  ]
})
export class DeletionPendingPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  protected readonly scheduledAt = signal<string | null>(null);

  ngOnInit(): void {
    this.scheduledAt.set(this.route.snapshot.queryParamMap.get('date'));
  }
}
