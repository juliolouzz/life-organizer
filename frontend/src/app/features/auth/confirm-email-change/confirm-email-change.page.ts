import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PasswordResetService } from '../../../core/auth/password-reset.service';

type ViewState = 'verifying' | 'success' | 'error' | 'no-token';

@Component({
  selector: 'app-confirm-email-change-page',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-page">
      <mat-card class="auth-card" appearance="outlined">
        @switch (state()) {
          @case ('verifying') {
            <div class="block">
              <mat-progress-spinner mode="indeterminate" diameter="48" />
              <h2>Applying your new email...</h2>
            </div>
          }
          @case ('success') {
            <div class="block">
              <span class="material-symbols-outlined ok">check_circle</span>
              <h2>Email updated</h2>
              <p>Your new email is now your sign-in address. Use it next time you log in.</p>
              <a mat-flat-button color="primary" routerLink="/login">Back to login</a>
            </div>
          }
          @case ('error') {
            <div class="block">
              <span class="material-symbols-outlined err">error</span>
              <h2>This link is invalid or expired</h2>
              <p>Request another change from your account page.</p>
              <a mat-flat-button color="primary" routerLink="/login">Back to login</a>
            </div>
          }
          @case ('no-token') {
            <div class="block">
              <span class="material-symbols-outlined err">error</span>
              <h2>Missing confirmation token</h2>
              <a mat-flat-button color="primary" routerLink="/login">Back to login</a>
            </div>
          }
        }
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
      .auth-card { width: 100%; max-width: 460px; padding: 40px 32px; border-radius: var(--radius-lg); }
      .block { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 12px; }
      .block .material-symbols-outlined { font-size: 56px; }
      .ok { color: var(--money-positive); }
      .err { color: var(--money-negative); }
      h2 { font-size: 1.25rem; font-weight: 600; margin: 0; letter-spacing: -0.01em; }
      p { color: var(--text-muted); margin: 0; }
      a { margin-top: 12px; }
    `
  ]
})
export class ConfirmEmailChangePage implements OnInit {
  private readonly api = inject(PasswordResetService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly state = signal<ViewState>('verifying');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token || token.trim().length === 0) {
      this.state.set('no-token');
      return;
    }
    this.router.navigate([], {
      queryParams: { token: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
    this.api.confirmEmailChange(token).subscribe({
      next: () => this.state.set('success'),
      error: () => this.state.set('error')
    });
  }
}
