import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, StudyPlan } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class StudyPlanService {
  private baseUrl = 'http://localhost:8080/api/v1/study-plan';

  constructor(private http: HttpClient) {}

  generateStudyPlan(availableTime: string): Observable<ApiResponse<StudyPlan>> {
    return this.http.post<ApiResponse<StudyPlan>>(`${this.baseUrl}/generate`, { availableTime });
  }

  getStudyPlan(studentId: string): Observable<ApiResponse<StudyPlan>> {
    return this.http.get<ApiResponse<StudyPlan>>(`${this.baseUrl}/${studentId}`);
  }

  regenerateStudyPlan(availableTime: string): Observable<ApiResponse<StudyPlan>> {
    return this.http.put<ApiResponse<StudyPlan>>(`${this.baseUrl}/regenerate`, { availableTime });
  }
}
