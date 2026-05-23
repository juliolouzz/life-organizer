import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ImportResult, TransactionsImportService } from './transactions-import.service';

const TEMPLATE_CSV =
  'date,amount,type,category,description\n' +
  '2026-05-01,5000.00,INCOME,Salary,Monthly salary\n' +
  '2026-05-05,1500.00,EXPENSE,Rent,May rent\n' +
  '2026-05-07,42.50,EXPENSE,Groceries,Week 1 shop\n' +
  '2026-05-10,200.00,SAVINGS,Emergency,Monthly transfer\n';

@Component({
  selector: 'app-transactions-import-page',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Import from CSV"
      subtitle="Backfill months or years of transactions in one upload."
    >
      <a mat-stroked-button routerLink="/transactions" data-testid="back-to-transactions">
        <span class="material-symbols-outlined nav-arrow">arrow_back</span>
        Back to transactions
      </a>
    </app-page-header>

    <mat-card appearance="outlined" class="upload-card">
      <h3>1. Choose a CSV file</h3>
      <p class="hint">
        File must have a header row with columns:
        <code>date</code>, <code>amount</code>, <code>type</code>, <code>category</code>,
        and optionally <code>description</code>. Dates may be <code>yyyy-MM-dd</code> or
        <code>dd/MM/yyyy</code>. Type must be <code>INCOME</code>, <code>EXPENSE</code>, or
        <code>SAVINGS</code>. Categories that don't exist yet are created automatically.
      </p>

      <div class="file-row">
        <input
          #fileInput
          type="file"
          accept=".csv,text/csv"
          (change)="onFileSelected($event)"
          style="display: none"
          data-testid="csv-file-input"
        />
        <button mat-stroked-button (click)="fileInput.click()" data-testid="csv-pick">
          <span class="material-symbols-outlined">upload_file</span>
          Choose file
        </button>
        @if (file(); as f) {
          <span class="filename">{{ f.name }} ({{ humanSize(f.size) }})</span>
        } @else {
          <span class="filename muted">No file selected</span>
        }
        <button
          mat-flat-button
          color="primary"
          (click)="upload()"
          [disabled]="!file() || uploading()"
          data-testid="csv-upload"
        >
          @if (uploading()) {
            Uploading...
          } @else {
            Import
          }
        </button>
      </div>

      @if (uploading()) {
        <mat-progress-bar mode="indeterminate" class="progress" />
      }

      <p class="template-line">
        Don't have a CSV?
        <button mat-button color="primary" (click)="downloadTemplate()">
          <span class="material-symbols-outlined">download</span>
          Download a template
        </button>
      </p>
    </mat-card>

    @if (result(); as r) {
      <mat-card appearance="outlined" class="result-card">
        <h3>Import complete</h3>
        <div class="result-counts">
          <div class="count ok">
            <strong>{{ r.inserted }}</strong>
            <small>inserted</small>
          </div>
          <div class="count skip">
            <strong>{{ r.skipped }}</strong>
            <small>skipped</small>
          </div>
          <div class="count err">
            <strong>{{ r.errors.length }}</strong>
            <small>errors</small>
          </div>
        </div>

        @if (r.errors.length > 0) {
          <details class="errors">
            <summary>Show per-row errors</summary>
            <ul>
              @for (e of r.errors; track e.line) {
                <li>
                  <strong>Line {{ e.line }}:</strong> {{ e.message }}
                </li>
              }
            </ul>
          </details>
        }

        <div class="result-actions">
          <a mat-stroked-button routerLink="/transactions" data-testid="result-view-list">
            View transactions
          </a>
          <a mat-stroked-button routerLink="/dashboard" data-testid="result-view-dashboard">
            Back to dashboard
          </a>
        </div>
      </mat-card>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .nav-arrow { font-size: 18px; margin-right: 6px; vertical-align: middle; }
      .upload-card {
        padding: 24px 28px;
        margin-bottom: 20px;
        border-radius: var(--radius-md);
        max-width: 760px;
      }
      h3 { margin: 0 0 8px 0; font-size: 1rem; font-weight: 600; }
      .hint { color: var(--text-muted); font-size: 0.9rem; line-height: 1.5; }
      .hint code {
        background: var(--surface-bg);
        padding: 1px 6px;
        border-radius: 4px;
        font-family: 'JetBrains Mono', monospace;
        font-size: 0.85rem;
      }
      .file-row {
        display: flex;
        align-items: center;
        gap: 14px;
        margin-top: 16px;
        flex-wrap: wrap;
      }
      .filename {
        font-family: 'JetBrains Mono', monospace;
        font-size: 0.88rem;
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .muted { color: var(--text-muted); }
      .progress { margin-top: 16px; }
      .template-line {
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid var(--border-subtle);
        color: var(--text-muted);
        font-size: 0.9rem;
      }
      .template-line .material-symbols-outlined {
        font-size: 18px;
        margin-right: 4px;
        vertical-align: middle;
      }
      .result-card {
        padding: 24px 28px;
        border-radius: var(--radius-md);
        max-width: 760px;
      }
      .result-counts {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 16px;
        margin: 18px 0;
      }
      .count {
        text-align: center;
        padding: 16px;
        border-radius: var(--radius-md);
        border: 1px solid var(--border-subtle);
      }
      .count strong {
        display: block;
        font-size: 2rem;
        font-weight: 700;
        font-family: 'JetBrains Mono', monospace;
        letter-spacing: -0.02em;
      }
      .count small {
        color: var(--text-muted);
        font-size: 0.78rem;
        text-transform: uppercase;
        letter-spacing: 0.06em;
      }
      .count.ok strong { color: var(--money-positive); }
      .count.skip strong { color: #d97706; }
      .count.err strong { color: var(--money-negative); }
      .count.err strong:empty + small,
      .count.err strong:not(:empty)[data-zero='true'] strong { color: var(--text-muted); }
      .errors {
        margin-top: 8px;
        padding: 12px 16px;
        background: var(--surface-bg);
        border-radius: var(--radius-sm);
      }
      .errors summary {
        cursor: pointer;
        font-weight: 500;
        font-size: 0.9rem;
      }
      .errors ul {
        margin: 12px 0 0 0;
        padding-left: 20px;
        font-size: 0.85rem;
        line-height: 1.6;
        max-height: 240px;
        overflow-y: auto;
      }
      .errors li { color: var(--text-primary); }
      .errors li strong { color: var(--money-negative); }
      .result-actions {
        display: flex;
        gap: 12px;
        margin-top: 20px;
        padding-top: 20px;
        border-top: 1px solid var(--border-subtle);
      }
    `
  ]
})
export class TransactionsImportPage {
  private readonly api = inject(TransactionsImportService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly router = inject(Router);

  protected readonly file = signal<File | null>(null);
  protected readonly uploading = signal(false);
  protected readonly result = signal<ImportResult | null>(null);

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    this.file.set(f);
    this.result.set(null);
  }

  protected humanSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  protected upload(): void {
    const f = this.file();
    if (!f || this.uploading()) return;
    this.uploading.set(true);
    this.result.set(null);
    this.api.upload(f).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.result.set(res);
        const totalSeen = res.inserted + res.skipped;
        this.snackBar.open(
          `Imported ${res.inserted} of ${totalSeen} rows`,
          'Dismiss',
          { duration: 4000 }
        );
      },
      error: (err: unknown) => {
        this.uploading.set(false);
        const msg = err instanceof HttpErrorResponse
          ? err.error?.message ?? 'Upload failed'
          : 'Upload failed';
        this.snackBar.open(msg, 'Dismiss', { duration: 5000 });
      }
    });
  }

  protected downloadTemplate(): void {
    const blob = new Blob([TEMPLATE_CSV], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'life-organizer-import-template.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}
