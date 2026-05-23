import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

export type CategoryKind = 'INCOME' | 'EXPENSE' | 'SAVINGS' | 'BOTH';

export interface Category {
  id: number;
  name: string;
  kind: CategoryKind;
  archived: boolean;
}

export interface CategoryRequest {
  name: string;
  kind: CategoryKind;
}

@Injectable({ providedIn: 'root' })
export class CategoriesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/categories`;

  list(): Observable<Category[]> {
    return this.http.get<ApiResponse<Category[]>>(this.base).pipe(map((r) => r.data ?? []));
  }

  create(req: CategoryRequest): Observable<Category> {
    return this.http
      .post<ApiResponse<Category>>(this.base, req)
      .pipe(map((r) => this.requireData(r)));
  }

  update(id: number, req: CategoryRequest): Observable<Category> {
    return this.http
      .put<ApiResponse<Category>>(`${this.base}/${id}`, req)
      .pipe(map((r) => this.requireData(r)));
  }

  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  private requireData<T>(res: ApiResponse<T>): T {
    if (!res.success || res.data === null) throw new Error(res.message ?? 'API returned no data');
    return res.data;
  }
}
