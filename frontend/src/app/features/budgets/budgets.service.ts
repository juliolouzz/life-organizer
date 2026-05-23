import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

export interface Budget {
  id: number;
  categoryId: number;
  categoryName: string;
  amount: string | number;
  month: number;
  year: number;
}

export interface BudgetStatusItem {
  budgetId: number;
  categoryId: number;
  categoryName: string;
  budgeted: string | number;
  spent: string | number;
  remaining: string | number;
  percent: number;
}

export interface BudgetRequest {
  categoryId: number;
  amount: string | number;
  month: number;
  year: number;
}

@Injectable({ providedIn: 'root' })
export class BudgetsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/budgets`;

  list(year: number, month: number): Observable<Budget[]> {
    const params = new HttpParams().set('year', String(year)).set('month', String(month));
    return this.http
      .get<ApiResponse<Budget[]>>(this.base, { params })
      .pipe(map((r) => r.data ?? []));
  }

  status(year: number, month: number): Observable<BudgetStatusItem[]> {
    const params = new HttpParams().set('year', String(year)).set('month', String(month));
    return this.http
      .get<ApiResponse<BudgetStatusItem[]>>(`${this.base}/status`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  create(req: BudgetRequest): Observable<Budget> {
    return this.http
      .post<ApiResponse<Budget>>(this.base, req)
      .pipe(map((r) => this.requireData(r)));
  }

  update(id: number, req: BudgetRequest): Observable<Budget> {
    return this.http
      .put<ApiResponse<Budget>>(`${this.base}/${id}`, req)
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
