import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatChipsModule, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Profile" subtitle="Your account at a glance." />

    @if (user(); as u) {
      <mat-card class="profile-card" appearance="outlined">
        <div class="profile-row">
          <div class="avatar">{{ initials(u.displayName) }}</div>
          <div class="profile-info">
            <h2>{{ u.displayName }}</h2>
            <p>{{ u.email }}</p>
            <mat-chip-set>
              <mat-chip>{{ humanRole(u.role) }}</mat-chip>
              <mat-chip>ID #{{ u.id }}</mat-chip>
            </mat-chip-set>
          </div>
        </div>
        <div class="actions">
          <button mat-stroked-button color="warn" (click)="logout()">
            <span class="material-symbols-outlined">logout</span>
            Log out
          </button>
        </div>
      </mat-card>
    }
  `,
  styles: [
    `
      .profile-card {
        padding: 32px;
        border-radius: var(--radius-lg);
        max-width: 640px;
      }
      .profile-row {
        display: flex;
        align-items: center;
        gap: 24px;
      }
      .avatar {
        width: 72px;
        height: 72px;
        border-radius: 50%;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white;
        display: grid;
        place-items: center;
        font-size: 1.6rem;
        font-weight: 600;
        flex: 0 0 auto;
      }
      .profile-info h2 {
        margin: 0;
        font-size: 1.4rem;
        font-weight: 600;
        letter-spacing: -0.01em;
      }
      .profile-info p {
        margin: 4px 0 12px 0;
        color: var(--text-muted);
      }
      .actions {
        margin-top: 24px;
        padding-top: 20px;
        border-top: 1px solid var(--border-subtle);
        display: flex;
        justify-content: flex-end;
      }
      .actions .material-symbols-outlined {
        font-size: 18px;
        margin-right: 4px;
        vertical-align: middle;
      }
    `
  ]
})
export class ProfilePage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;

  ngOnInit(): void {
    // Refresh from server in case displayName or role changed externally.
    if (!this.user()) {
      this.auth.fetchMe().subscribe();
    }
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
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
}
