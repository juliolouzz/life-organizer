import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  computed,
  signal
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { FormsModule } from '@angular/forms';

import { DateRange, PeriodPreset, rangeForPresetWithBoundary, toIso } from '../period';

export interface PeriodChange {
  range: DateRange;
  preset: PeriodPreset;
}

@Component({
  selector: 'app-period-selector',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatMenuModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-button-toggle-group
      [value]="preset()"
      (change)="select($event.value)"
      hideSingleSelectionIndicator
      class="presets"
    >
      <mat-button-toggle value="this_month">This month</mat-button-toggle>
      <mat-button-toggle value="last_month">Last month</mat-button-toggle>
      <mat-button-toggle value="last_3_months">Last 3 months</mat-button-toggle>
      <mat-button-toggle value="this_year">This year</mat-button-toggle>
      <mat-button-toggle value="all_time">All time</mat-button-toggle>
    </mat-button-toggle-group>

    <button
      mat-stroked-button
      [matMenuTriggerFor]="customMenu"
      class="custom-btn"
      [class.active]="preset() === 'custom'"
    >
      <span class="material-symbols-outlined">calendar_today</span>
      @if (preset() === 'custom' && activeRangeLabel()) {
        Custom: <span class="active-range">{{ activeRangeLabel() }}</span>
      } @else {
        Custom
      }
    </button>

    <mat-menu #customMenu="matMenu" class="custom-menu">
      <div class="custom-pane" (click)="$event.stopPropagation()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>From</mat-label>
          <input matInput [matDatepicker]="fromPicker" [(ngModel)]="customFrom" />
          <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
          <mat-datepicker #fromPicker />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>To</mat-label>
          <input matInput [matDatepicker]="toPicker" [(ngModel)]="customTo" />
          <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
          <mat-datepicker #toPicker />
        </mat-form-field>
        <button mat-flat-button color="primary" (click)="applyCustom()" [disabled]="!isCustomValid()">
          Apply
        </button>
      </div>
    </mat-menu>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
      }
      .presets ::ng-deep .mat-button-toggle-label-content {
        font-size: 0.82rem;
        padding: 0 14px;
      }
      .custom-btn .material-symbols-outlined {
        font-size: 18px;
        margin-right: 6px;
        vertical-align: middle;
      }
      .custom-btn.active {
        background: color-mix(in srgb, var(--text-primary) 8%, transparent);
        border-color: var(--text-primary);
      }
      .custom-btn .active-range {
        margin-left: 6px;
        font-family: 'JetBrains Mono', monospace;
        font-size: 0.78rem;
        color: var(--text-muted);
      }
      .custom-menu ::ng-deep .mat-mdc-menu-content {
        padding: 16px;
        min-width: 280px;
      }
      .custom-pane {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
    `
  ]
})
export class PeriodSelectorComponent {
  @Input() set initial(value: PeriodPreset) {
    this.preset.set(value);
  }
  /**
   * Slice 14: the signed-in user's accounting boundary day. Drives the
   * built-in "This month" / "Last month" range computation. Defaults to 1
   * (regular calendar month) so the component works for anonymous renders.
   */
  @Input() boundaryDay = 1;
  @Output() rangeChange = new EventEmitter<PeriodChange>();

  @ViewChild(MatMenuTrigger) private customMenuTrigger?: MatMenuTrigger;

  protected readonly preset = signal<PeriodPreset>('this_month');
  protected readonly activeRange = signal<DateRange | null>(null);
  protected customFrom: Date | null = null;
  protected customTo: Date | null = null;

  protected readonly activeRangeLabel = computed(() => {
    const r = this.activeRange();
    if (!r) return null;
    return `${toIso(r.from)} → ${toIso(r.to)}`;
  });

  protected select(preset: PeriodPreset): void {
    this.preset.set(preset);
    const range = rangeForPresetWithBoundary(preset, this.boundaryDay);
    this.activeRange.set(range);
    this.rangeChange.emit({ range, preset });
  }

  protected isCustomValid(): boolean {
    return !!(this.customFrom && this.customTo && this.customFrom <= this.customTo);
  }

  protected applyCustom(): void {
    if (!this.isCustomValid()) return;
    const range: DateRange = { from: this.customFrom as Date, to: this.customTo as Date };
    this.preset.set('custom');
    this.activeRange.set(range);
    this.rangeChange.emit({ range, preset: 'custom' });
    // Close the menu so the user sees the dashboard update behind it. Without
    // this, the menu stays open (the inner stopPropagation prevents the
    // backdrop-click close path) and the apply feels like a no-op.
    this.customMenuTrigger?.closeMenu();
  }
}
