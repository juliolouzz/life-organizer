import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface LogoutAllDialogResult {
  confirmed: boolean;
  password: string;
}

@Component({
  selector: 'app-logout-all-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Sign out of all devices?</h2>
    <mat-dialog-content class="content">
      <p>
        Every active session - on every device, including this one - will be signed
        out immediately. You'll need to log in again to come back.
      </p>
      <p>Enter your password to confirm:</p>
      <form [formGroup]="form" class="stack">
        <mat-form-field appearance="outline">
          <mat-label>Password</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-flat-button color="warn" [disabled]="form.invalid" (click)="confirm()">
        Sign out everywhere
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .content { max-width: 460px; }
      .stack { display: flex; flex-direction: column; }
      .stack mat-form-field { width: 100%; }
      p { font-size: 0.92rem; line-height: 1.5; margin: 0 0 12px 0; }
    `
  ]
})
export class LogoutAllDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ref = inject(MatDialogRef<LogoutAllDialogComponent, LogoutAllDialogResult>);

  protected readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required]]
  });

  protected cancel(): void {
    this.ref.close({ confirmed: false, password: '' });
  }

  protected confirm(): void {
    if (this.form.invalid) return;
    this.ref.close({ confirmed: true, password: this.form.controls.password.value });
  }
}
