import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Source } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class UploadService {
  private baseUrl = 'http://localhost:8080/api/v1/sources';

  constructor(private http: HttpClient) {}

  getSources(): Observable<ApiResponse<Source[]>> {
    return this.http.get<ApiResponse<Source[]>>(this.baseUrl);
  }

  getSourceStatus(id: string): Observable<ApiResponse<Record<string, unknown>>> {
    return this.http.get<ApiResponse<Record<string, unknown>>>(`${this.baseUrl}/${id}`);
  }

  getJobStatus(id: string, type: string): Observable<any> {
    return this.http.get<any>(`http://localhost:8080/api/v1/jobs/source/${id}/${type}`);
  }

  uploadFile(file: File): Observable<ApiResponse<Source>> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ApiResponse<Source>>(`${this.baseUrl}/upload`, formData);
  }

  uploadUrl(url: string): Observable<ApiResponse<Source>> {
    return this.http.post<ApiResponse<Source>>(`${this.baseUrl}/url`, { url });
  }

  deleteSource(id: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.delete<ApiResponse<{ message: string }>>(`${this.baseUrl}/${id}`);
  }
}
