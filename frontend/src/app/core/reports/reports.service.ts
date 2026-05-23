import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../api/api-response';

export interface Totals {
  income: number;
  expense: number;
  savings: number;
  net: number;
  transactionCount: number;
}

export interface CategoryAmount {
  name: string;
  type: 'INCOME' | 'EXPENSE' | 'SAVINGS';
  amount: number;
}

export interface DailyBucket {
  date: string;
  income: number;
  expense: number;
  savings: number;
}

export interface SummaryReport {
  year: number;
  month: number;
  totals: Totals;
  topCategories: CategoryAmount[];
  daily: DailyBucket[];
}

export interface Delta {
  absolute: number;
  percent: number | null;
}

export interface YearOverYearReport {
  thisYear: { year: number; month: number; totals: Totals };
  lastYear: { year: number; month: number; totals: Totals };
  deltas: { income: Delta; expense: Delta; savings: Delta; net: Delta };
  topCategoryDeltas: {
    name: string;
    thisYear: number;
    lastYear: number;
    delta: Delta;
  }[];
}

export interface TrendPoint {
  year: number;
  month: number;
  amount: number;
}

export interface CategoryTrendSeries {
  name: string;
  type: 'INCOME' | 'EXPENSE' | 'SAVINGS';
  points: TrendPoint[];
}

export interface CategoryTrendsReport {
  monthsBack: number;
  series: CategoryTrendSeries[];
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  summary(year: number, month: number): Observable<SummaryReport> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http
      .get<ApiResponse<SummaryReport>>(`${this.base}/reports/summary`, { params })
      .pipe(map((r) => this.requireData(r)));
  }

  yoy(year: number, month: number): Observable<YearOverYearReport> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http
      .get<ApiResponse<YearOverYearReport>>(`${this.base}/reports/yoy`, { params })
      .pipe(map((r) => this.requireData(r)));
  }

  trends(months: 6 | 12): Observable<CategoryTrendsReport> {
    const params = new HttpParams().set('months', months);
    return this.http
      .get<ApiResponse<CategoryTrendsReport>>(`${this.base}/reports/trends`, { params })
      .pipe(map((r) => this.requireData(r)));
  }

  downloadSummaryCsv(year: number, month: number): Observable<Blob> {
    return this.downloadBlob(`/reports/summary.csv`, { year, month });
  }

  downloadSummaryPdf(year: number, month: number): Observable<Blob> {
    return this.downloadBlob(`/reports/summary.pdf`, { year, month });
  }

  downloadTransactionsCsv(from: string | null, to: string | null): Observable<Blob> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.downloadBlob(`/reports/transactions.csv`, params);
  }

  private downloadBlob(path: string, params: Record<string, string | number>): Observable<Blob> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([k, v]) => {
      httpParams = httpParams.set(k, v);
    });
    return this.http.get(`${this.base}${path}`, {
      responseType: 'blob',
      params: httpParams
    });
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) {
      throw new Error(res.message ?? 'API returned no data');
    }
    return res.data;
  }
}
