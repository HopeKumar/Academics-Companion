import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, ResponseRecord } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  constructor(private http: HttpClient) {}

  getAnalytics(): Observable<ApiResponse<Record<string, unknown>>> {
    return this.http.get<ApiResponse<Record<string, unknown>>>('http://localhost:8080/api/v1/analytics');
  }

  getProgress(): Observable<ApiResponse<ResponseRecord[]>> {
    return this.http.get<ApiResponse<ResponseRecord[]>>('http://localhost:8080/api/v1/progress');
  }
}
