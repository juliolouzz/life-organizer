import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/api/api-response';

export interface RowError {
  line: number;
  message: string;
}

export interface ImportResult {
  inserted: number;
  skipped: number;
  errors: RowError[];
}

@Injectable({ providedIn: 'root' })
export class TransactionsImportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/transactions/import`;

  upload(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http
      .post<ApiResponse<ImportResult>>(this.base, form)
      .pipe(map((r) => this.requireData(r)));
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) throw new Error(res.message ?? 'API returned no data');
    return res.data;
  }
}
