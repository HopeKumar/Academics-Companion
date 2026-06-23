import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, RecommendationResponse } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private baseUrl = 'http://localhost:8080/api/v1/recommendations';

  constructor(private http: HttpClient) {}

  getRecommendations(): Observable<ApiResponse<RecommendationResponse>> {
    return this.http.get<ApiResponse<RecommendationResponse>>(this.baseUrl);
  }

  getPersonalizedRecommendations(studentId: string): Observable<ApiResponse<RecommendationResponse>> {
    return this.http.get<ApiResponse<RecommendationResponse>>(`${this.baseUrl}/${studentId}`);
  }
}
