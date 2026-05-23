import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatListModule,
    MatDividerModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="shell" autosize>
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="brand">
          <div class="brand-mark" aria-hidden="true">
            <span class="material-symbols-outlined">orbit</span>
          </div>
          <div class="brand-text">
            <strong>Life Organizer</strong>
            <small>v0.3 · Slice 3</small>
          </div>
        </div>

        <nav class="nav-section">
          <a
            mat-list-item
            routerLink="/dashboard"
            routerLinkActive="active"
            class="nav-item"
          >
            <span class="material-symbols-outlined nav-icon">dashboard</span>
            Dashboard
          </a>
          <a
            mat-list-item
            routerLink="/transactions"
            routerLinkActive="active"
            class="nav-item"
          >
            <span class="material-symbols-outlined nav-icon">payments</span>
            Transactions
          </a>
          <a
            mat-list-item
            routerLink="/profile"
            routerLinkActive="active"
            class="nav-item"
          >
            <span class="material-symbols-outlined nav-icon">account_circle</span>
            Profile
          </a>
        </nav>

        <div class="spacer"></div>

        <div class="user-card">
          <div class="avatar" aria-hidden="true">{{ initials() }}</div>
          <div class="user-meta">
            <strong>{{ auth.currentUser()?.displayName ?? 'You' }}</strong>
            <small>{{ auth.currentUser()?.email ?? '' }}</small>
          </div>
          <button
            mat-icon-button
            [matMenuTriggerFor]="userMenu"
            aria-label="Account menu"
          >
            <span class="material-symbols-outlined">more_vert</span>
          </button>
          <mat-menu #userMenu="matMenu">
            <button mat-menu-item (click)="logout()">
              <span class="material-symbols-outlined">logout</span>
              <span>Log out</span>
            </button>
          </mat-menu>
        </div>
      </mat-sidenav>

      <mat-sidenav-content class="content">
        <header class="topbar">
          <span class="spacer"></span>
          <button
            mat-icon-button
            (click)="theme.toggle()"
            [matTooltip]="theme.mode() === 'dark' ? 'Switch to light' : 'Switch to dark'"
            aria-label="Toggle theme"
          >
            <span class="material-symbols-outlined">
              {{ theme.mode() === 'dark' ? 'light_mode' : 'dark_mode' }}
            </span>
          </button>
        </header>

        <main class="main-area">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
      .shell {
        height: 100vh;
        background: var(--surface-bg);
      }
      .sidenav {
        width: 264px;
        padding: 20px 12px;
        background: var(--surface-card);
        border-right: 1px solid var(--border-subtle);
        display: flex;
        flex-direction: column;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 4px 8px 20px 8px;
        border-bottom: 1px solid var(--border-subtle);
        margin-bottom: 16px;
      }
      .brand-mark {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        display: grid;
        place-items: center;
        color: white;
      }
      .brand-mark .material-symbols-outlined {
        font-size: 24px;
      }
      .brand-text {
        display: flex;
        flex-direction: column;
      }
      .brand-text strong {
        font-size: 0.95rem;
        letter-spacing: -0.01em;
      }
      .brand-text small {
        font-size: 0.75rem;
        color: var(--text-muted);
      }
      .nav-section {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .nav-item {
        --mat-list-list-item-leading-icon-start-space: 12px;
        --mat-list-list-item-leading-icon-end-space: 12px;
        border-radius: 10px;
        font-weight: 500;
        font-size: 0.9rem;
      }
      .nav-item.active {
        background: color-mix(in srgb, var(--mat-sys-primary, #7c3aed) 12%, transparent);
        color: var(--mat-sys-primary, #7c3aed);
      }
      .nav-item .nav-icon {
        font-size: 20px;
        margin-right: 12px;
        vertical-align: middle;
      }
      .spacer { flex: 1 1 auto; }
      .user-card {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px;
        margin-top: 12px;
        border: 1px solid var(--border-subtle);
        border-radius: 12px;
        background: var(--surface-bg);
      }
      .avatar {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        color: white;
        display: grid;
        place-items: center;
        font-size: 0.85rem;
        font-weight: 600;
        flex: 0 0 auto;
      }
      .user-meta {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1 1 auto;
      }
      .user-meta strong {
        font-size: 0.85rem;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .user-meta small {
        font-size: 0.72rem;
        color: var(--text-muted);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .content { background: var(--surface-bg); }
      .topbar {
        display: flex;
        align-items: center;
        padding: 14px 24px;
        border-bottom: 1px solid var(--border-subtle);
        background: var(--surface-bg);
        position: sticky;
        top: 0;
        z-index: 5;
      }
      .main-area {
        padding: 32px 40px;
        max-width: 1280px;
        margin: 0 auto;
      }
      @media (max-width: 720px) {
        .sidenav { width: 220px; padding: 12px 8px; }
        .main-area { padding: 20px 16px; }
      }
    `
  ]
})
export class AppShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  protected initials(): string {
    const name = this.auth.currentUser()?.displayName ?? '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('') || 'U';
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
