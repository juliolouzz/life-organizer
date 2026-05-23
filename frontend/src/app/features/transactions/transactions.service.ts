import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse, PageEnvelope } from '../../core/api/api-response';

export type TransactionType = 'INCOME' | 'EXPENSE';

export interface Transaction {
  id: number;
  amount: string;            // BigDecimal arrives as string; format on the way out
  type: TransactionType;
  category: string;
  description: string;
  transactionDate: string;   // ISO yyyy-MM-dd
  createdAt: string;         // ISO instant
  updatedAt: string;
}

export interface CreateTransactionRequest {
  amount: string | number;
  type: TransactionType;
  category: string;
  description: string;
  transactionDate: string;
}

export type UpdateTransactionRequest = CreateTransactionRequest;

export interface ListQuery {
  cursor?: string | null;
  limit?: number;
  from?: string | null;
  to?: string | null;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
  limit: number;
}

@Injectable({ providedIn: 'root' })
export class TransactionsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/transactions`;

  list(query: ListQuery = {}): Observable<Page<Transaction>> {
    let params = new HttpParams();
    if (query.cursor) params = params.set('cursor', query.cursor);
    if (query.limit !== undefined) params = params.set('limit', String(query.limit));
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);

    return this.http
      .get<PageEnvelope<Transaction>>(this.base, { params })
      .pipe(
        map((res) => ({
          items: res.data ?? [],
          nextCursor: typeof res.meta?.nextCursor === 'string' && res.meta.nextCursor !== ''
            ? res.meta.nextCursor
            : null,
          limit: typeof res.meta?.limit === 'number' ? res.meta.limit : 20
        }))
      );
  }

  getById(id: number): Observable<Transaction> {
    return this.http
      .get<ApiResponse<Transaction>>(`${this.base}/${id}`)
      .pipe(map((res) => this.requireData(res)));
  }

  create(req: CreateTransactionRequest): Observable<Transaction> {
    return this.http
      .post<ApiResponse<Transaction>>(this.base, req)
      .pipe(map((res) => this.requireData(res)));
  }

  update(id: number, req: UpdateTransactionRequest): Observable<Transaction> {
    return this.http
      .put<ApiResponse<Transaction>>(`${this.base}/${id}`, req)
      .pipe(map((res) => this.requireData(res)));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) {
      throw new Error(res.message ?? 'API returned no data');
    }
    return res.data;
  }
}
