import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div class="titles">
        <h1>{{ title }}</h1>
        @if (subtitle) {
          <p class="subtitle">{{ subtitle }}</p>
        }
      </div>
      <div class="actions">
        <ng-content />
      </div>
    </header>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 24px;
        margin-bottom: 24px;
        flex-wrap: wrap;
      }
      .titles {
        flex: 1 1 auto;
        min-width: 0;
      }
      h1 {
        font-size: 2rem;
        font-weight: 600;
        letter-spacing: -0.02em;
        line-height: 1.15;
        margin: 0;
        color: var(--text-primary);
      }
      .subtitle {
        margin: 6px 0 0 0;
        color: var(--text-muted);
        font-size: 0.95rem;
      }
      .actions {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    `
  ]
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
}
