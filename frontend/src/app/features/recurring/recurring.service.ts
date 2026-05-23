import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { TransactionType } from '../transactions/transactions.service';

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface Recurring {
  id: number;
  categoryId: number;
  categoryName: string;
  amount: string | number;
  type: TransactionType;
  description: string;
  frequency: Frequency;
  startDate: string;
  endDate: string | null;
  nextDueDate: string;
  paused: boolean;
}

export interface RecurringRequest {
  categoryId: number;
  amount: string | number;
  type: TransactionType;
  description: string;
  frequency: Frequency;
  startDate: string;
  endDate?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RecurringService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/recurring`;

  list(): Observable<Recurring[]> {
    return this.http.get<ApiResponse<Recurring[]>>(this.base).pipe(map((r) => r.data ?? []));
  }

  create(req: RecurringRequest): Observable<Recurring> {
    return this.http
      .post<ApiResponse<Recurring>>(this.base, req)
      .pipe(map((r) => this.requireData(r)));
  }

  update(id: number, req: RecurringRequest): Observable<Recurring> {
    return this.http
      .put<ApiResponse<Recurring>>(`${this.base}/${id}`, req)
      .pipe(map((r) => this.requireData(r)));
  }

  pause(id: number): Observable<Recurring> {
    return this.http
      .post<ApiResponse<Recurring>>(`${this.base}/${id}/pause`, {})
      .pipe(map((r) => this.requireData(r)));
  }

  resume(id: number): Observable<Recurring> {
    return this.http
      .post<ApiResponse<Recurring>>(`${this.base}/${id}/resume`, {})
      .pipe(map((r) => this.requireData(r)));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) throw new Error(res.message ?? 'API returned no data');
    return res.data;
  }
}
