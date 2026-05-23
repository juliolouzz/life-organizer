import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { TransactionType } from '../transactions/transactions.service';

export type Granularity = 'DAY' | 'WEEK' | 'MONTH';

export interface PeriodTotals {
  from: string;
  to: string;
  totalIncome: number | string;
  totalExpense: number | string;
  totalSavings: number | string;
  net: number | string;
}

export interface Summary {
  from: string;
  to: string;
  totalIncome: number | string;
  totalExpense: number | string;
  totalSavings: number | string;
  net: number | string;
  incomeCount: number;
  expenseCount: number;
  savingsCount: number;
  previousPeriod: PeriodTotals;
}

export interface CategoryTotal {
  category: string;
  type: TransactionType;
  total: number | string;
  count: number;
}

export interface BucketTotal {
  bucket: string;
  income: number | string;
  expense: number | string;
  savings: number | string;
  net: number | string;
}

export interface ByPeriodResponse {
  data: BucketTotal[];
  granularity: Granularity;
  from: string;
  to: string;
}

@Injectable({ providedIn: 'root' })
export class InsightsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/insights`;

  summary(from: string, to: string): Observable<Summary> {
    return this.http
      .get<ApiResponse<Summary>>(`${this.base}/summary`, {
        params: new HttpParams().set('from', from).set('to', to)
      })
      .pipe(map((r) => this.requireData(r)));
  }

  byCategory(from: string, to: string): Observable<CategoryTotal[]> {
    return this.http
      .get<ApiResponse<CategoryTotal[]>>(`${this.base}/by-category`, {
        params: new HttpParams().set('from', from).set('to', to)
      })
      .pipe(map((r) => r.data ?? []));
  }

  byPeriod(from: string, to: string, granularity?: Granularity): Observable<ByPeriodResponse> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (granularity) params = params.set('granularity', granularity);
    return this.http.get<ApiResponse<BucketTotal[]>>(`${this.base}/by-period`, { params }).pipe(
      map((res) => ({
        data: res.data ?? [],
        granularity: (res.meta?.['granularity'] as Granularity) ?? 'DAY',
        from: (res.meta?.['from'] as string) ?? from,
        to: (res.meta?.['to'] as string) ?? to
      }))
    );
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) {
      throw new Error(res.message ?? 'API returned no data');
    }
    return res.data;
  }
}
