import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, DiscussionThread, DiscussionReply } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class DiscussionService {
  private baseUrl = 'http://localhost:8080/api/v1/discussions';

  constructor(private http: HttpClient) {}

  createThread(title: string, content: string, sourceId: string): Observable<ApiResponse<DiscussionThread>> {
    return this.http.post<ApiResponse<DiscussionThread>>(this.baseUrl, { title, content, sourceId });
  }

  getAllThreads(): Observable<ApiResponse<DiscussionThread[]>> {
    return this.http.get<ApiResponse<DiscussionThread[]>>(this.baseUrl);
  }

  createReply(threadId: string, content: string): Observable<ApiResponse<DiscussionReply>> {
    return this.http.post<ApiResponse<DiscussionReply>>(`${this.baseUrl}/reply`, { threadId, content });
  }

  upvoteThread(threadId: string): Observable<ApiResponse<DiscussionThread>> {
    return this.http.post<ApiResponse<DiscussionThread>>(`${this.baseUrl}/upvote`, { threadId });
  }

  upvoteReply(replyId: string): Observable<ApiResponse<DiscussionReply>> {
    return this.http.post<ApiResponse<DiscussionReply>>(`${this.baseUrl}/upvote`, { replyId });
  }

  markAccepted(replyId: string): Observable<ApiResponse<DiscussionReply>> {
    return this.http.post<ApiResponse<DiscussionReply>>(`${this.baseUrl}/accepted`, { replyId });
  }

  generateSummary(threadId: string): Observable<ApiResponse<DiscussionThread>> {
    return this.http.post<ApiResponse<DiscussionThread>>(`${this.baseUrl}/summary`, { threadId });
  }
}
