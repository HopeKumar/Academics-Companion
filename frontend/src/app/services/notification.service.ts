import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Notification } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private baseUrl = 'http://localhost:8080/api/v1/notifications';

  constructor(private http: HttpClient) {}

  getNotifications(): Observable<ApiResponse<Notification[]>> {
    return this.http.get<ApiResponse<Notification[]>>(this.baseUrl);
  }

  getUnread(): Observable<ApiResponse<Notification[]>> {
    return this.http.get<ApiResponse<Notification[]>>(`${this.baseUrl}/unread`);
  }

  getUnreadCount(): Observable<ApiResponse<{ unreadCount: number }>> {
    return this.http.get<ApiResponse<{ unreadCount: number }>>(`${this.baseUrl}/unread-count`);
  }

  getSummary(): Observable<ApiResponse<Record<string, unknown>>> {
    return this.http.get<ApiResponse<Record<string, unknown>>>(`${this.baseUrl}/summary`);
  }

  markAsRead(id: string): Observable<ApiResponse<Notification>> {
    return this.http.post<ApiResponse<Notification>>(`${this.baseUrl}/${id}/read`, {});
  }

  markAllRead(): Observable<ApiResponse<{ message: string; updated: number }>> {
    return this.http.post<ApiResponse<{ message: string; updated: number }>>(`${this.baseUrl}/read-all`, {});
  }
}
