import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, StudentDashboardResponse } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private baseUrl = 'http://localhost:8080/api/v1/dashboard';

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<ApiResponse<Record<string, unknown>>> {
    return this.http.get<ApiResponse<Record<string, unknown>>>(this.baseUrl);
  }

  getStudentDashboard(studentId: string): Observable<ApiResponse<StudentDashboardResponse>> {
    return this.http.get<ApiResponse<StudentDashboardResponse>>(`${this.baseUrl}/student/${studentId}`);
  }
}
