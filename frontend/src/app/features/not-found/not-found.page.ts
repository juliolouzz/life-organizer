import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  standalone: true,
  imports: [MatButtonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="not-found">
      <div class="big-404">404</div>
      <h1>Page not found</h1>
      <p>The page you're looking for doesn't exist or has moved.</p>
      <a mat-flat-button color="primary" routerLink="/">Go home</a>
    </div>
  `,
  styles: [
    `
      .not-found {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        padding: 32px;
        text-align: center;
      }
      .big-404 {
        font-size: 6rem;
        font-weight: 700;
        letter-spacing: -0.05em;
        background: linear-gradient(135deg, #7c3aed, #06b6d4);
        -webkit-background-clip: text;
        background-clip: text;
        color: transparent;
        line-height: 1;
        margin-bottom: 16px;
      }
      h1 {
        margin: 0;
        font-size: 1.4rem;
        font-weight: 600;
      }
      p {
        margin: 0 0 24px 0;
        color: var(--text-muted);
      }
    `
  ]
})
export class NotFoundPage {}
