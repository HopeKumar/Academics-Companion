import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Summary } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class SummaryService {
  private baseUrl = 'http://localhost:8080/api/v1/summary';

  constructor(private http: HttpClient) {}

  generateSummary(sourceId: string): Observable<ApiResponse<Summary>> {
    return this.http.post<ApiResponse<Summary>>(`${this.baseUrl}/generate`, { sourceId });
  }

  getSummary(sourceId: string): Observable<ApiResponse<Summary>> {
    return this.http.get<ApiResponse<Summary>>(`${this.baseUrl}/source/${sourceId}`);
  }

  downloadSummary(sourceId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${sourceId}/download`, { responseType: 'blob' });
  }
}
