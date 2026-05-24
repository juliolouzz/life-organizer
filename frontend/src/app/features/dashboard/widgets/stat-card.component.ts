import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../../../core/auth/auth.service';

import { MoneyBrlPipe } from '../../../shared/pipes/money-brl.pipe';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [MatCardModule, MatTooltipModule, MoneyBrlPipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card
      class="stat"
      appearance="outlined"
      [class.empty]="loading || hidden"
      [matTooltip]="tooltip ?? ''"
      [matTooltipDisabled]="!tooltip"
      [matTooltipShowDelay]="2000"
      matTooltipPosition="above"
    >
      <div class="head">
        <span class="material-symbols-outlined icon" [style.color]="accent">{{ icon }}</span>
        <small class="label">{{ label }}</small>
      </div>

      @if (loading) {
        <div class="skeleton-bar"></div>
        <div class="skeleton-bar small"></div>
      } @else {
        <div class="value" [class.muted]="hidden">
          @if (rendered() !== null) {
            <span class="numeric">{{ rendered() }}</span>
          } @else if (hidden) {
            <span class="muted">—</span>
          }
        </div>
        @if (caption) {
          <small class="caption">{{ caption }}</small>
        }
        @if (deltaPct !== null && deltaPct !== undefined && !hidden) {
          <small class="delta" [class.up]="deltaPct >= 0" [class.down]="deltaPct < 0">
            <span class="material-symbols-outlined">
              {{ deltaPct >= 0 ? 'trending_up' : 'trending_down' }}
            </span>
            {{ deltaPct >= 0 ? '+' : '' }}{{ deltaPct | number : '1.0-0' }}% vs previous
          </small>
        }
      }
    </mat-card>
  `,
  styles: [
    `
      :host { display: block; }
      .stat {
        padding: 20px 22px;
        border-radius: var(--radius-md);
        background: var(--surface-card);
        height: 100%;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .head {
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .icon {
        font-size: 22px;
      }
      .label {
        text-transform: uppercase;
        letter-spacing: 0.06em;
        font-size: 0.72rem;
        font-weight: 600;
        color: var(--text-muted);
      }
      .value {
        font-size: 1.85rem;
        font-weight: 600;
        letter-spacing: -0.02em;
        line-height: 1.1;
        color: var(--text-primary);
      }
      .value.muted { color: var(--text-muted); font-size: 1.4rem; }
      .caption {
        color: var(--text-muted);
        font-size: 0.82rem;
      }
      .delta {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 0.78rem;
        margin-top: 4px;
        color: var(--text-muted);
      }
      .delta .material-symbols-outlined { font-size: 16px; }
      .delta.up { color: var(--money-positive); }
      .delta.down { color: var(--money-negative); }
      .skeleton-bar {
        height: 28px;
        border-radius: 6px;
        background: var(--border-subtle);
        animation: pulse 1.4s ease-in-out infinite;
      }
      .skeleton-bar.small { height: 14px; margin-top: 6px; width: 60%; }
      @keyframes pulse {
        0%, 100% { opacity: 0.7; }
        50% { opacity: 0.35; }
      }
    `
  ]
})
export class StatCardComponent {
  @Input({ required: true }) icon!: string;
  @Input({ required: true }) label!: string;
  /** Numeric value as string or number. Pass null with hidden=true to show a dash. */
  @Input() value: number | string | null = null;
  /** Optional sign treatment for money formatting. */
  @Input() sign: 'INCOME' | 'EXPENSE' | null = null;
  @Input() caption: string | null = null;
  @Input() deltaPct: number | null = null;
  @Input() hidden = false;
  @Input() loading = false;
  @Input() accent: string = 'var(--text-muted)';
  /** Pre-formatted value (e.g. a percentage). When set, takes precedence over value+sign. */
  @Input() formattedValue: string | null = null;
  /** Hover explanation; shown after a 2s delay. Omit to disable the tooltip. */
  @Input() tooltip: string | null = null;

  private readonly auth = inject(AuthService);
  private readonly pipe = new MoneyBrlPipe(this.auth);
  protected readonly _ = signal(null);

  protected rendered = computed<string | null>(() => {
    if (this.formattedValue !== null) return this.formattedValue;
    if (this.value === null || this.value === undefined) return null;
    return this.pipe.transform(this.value as number | string, this.sign);
  });
}
