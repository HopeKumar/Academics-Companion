import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponse, LoginRequest, RegisterRequest, AuthResponse } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = 'http://localhost:8080/api/v1/auth';

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest): Observable<ApiResponse<{ message: string; userId: string; username: string }>> {
    return this.http.post<ApiResponse<{ message: string; userId: string; username: string }>>(`${this.baseUrl}/register`, request);
  }

  login(request: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.baseUrl}/login`, request);
  }

  me(): Observable<ApiResponse<{ id: string; username: string; role: string; createdAt: string }>> {
    return this.http.get<ApiResponse<{ id: string; username: string; role: string; createdAt: string }>>(`${this.baseUrl}/me`);
  }

  logout(): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(`${this.baseUrl}/logout`, {});
  }
}
