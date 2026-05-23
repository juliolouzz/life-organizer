import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { LoadingService } from './core/ui/loading.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading.busy()) {
      <mat-progress-bar mode="indeterminate" class="app-loading-bar" />
    }
    <router-outlet />
  `
})
export class AppComponent {
  protected readonly loading = inject(LoadingService);
}
