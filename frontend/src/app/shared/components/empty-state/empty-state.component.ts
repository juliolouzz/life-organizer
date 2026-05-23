import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <div class="emoji-circle" aria-hidden="true">
        <span class="material-symbols-outlined">{{ icon }}</span>
      </div>
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
      <div class="actions">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        padding: 64px 24px;
        gap: 12px;
      }
      .emoji-circle {
        width: 80px;
        height: 80px;
        border-radius: 50%;
        background: var(--surface-elevated);
        border: 1px solid var(--border-subtle);
        display: grid;
        place-items: center;
        margin-bottom: 8px;
      }
      .emoji-circle .material-symbols-outlined {
        font-size: 36px;
        color: var(--text-muted);
      }
      h3 {
        font-size: 1.15rem;
        font-weight: 600;
        margin: 0;
        color: var(--text-primary);
      }
      p {
        margin: 0;
        color: var(--text-muted);
        max-width: 360px;
      }
      .actions {
        margin-top: 16px;
      }
    `
  ]
})
export class EmptyStateComponent {
  @Input({ required: true }) icon!: string;
  @Input({ required: true }) title!: string;
  @Input({ required: true }) description!: string;
}
