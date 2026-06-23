import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, ChatSession, ChatMessage } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private baseUrl = 'http://localhost:8080/api/v1/chat';

  constructor(private http: HttpClient) {}

  getSessions(): Observable<ApiResponse<ChatSession[]>> {
    return this.http.get<ApiResponse<ChatSession[]>>(`${this.baseUrl}/sessions`);
  }

  sendMessage(content: string, sessionId?: string, sourceIds?: string[]): Observable<ApiResponse<ChatMessage>> {
    return this.http.post<ApiResponse<ChatMessage>>(this.baseUrl, { sessionId, content, sourceIds });
  }

  getHistory(sessionId: string): Observable<ApiResponse<ChatMessage[]>> {
    return this.http.get<ApiResponse<ChatMessage[]>>(`${this.baseUrl}/history?sessionId=${sessionId}`);
  }

  deleteSession(sessionId: string): Observable<ApiResponse<{ success: boolean; message: string }>> {
    return this.http.delete<ApiResponse<{ success: boolean; message: string }>>(`${this.baseUrl}/sessions/${sessionId}`);
  }

  sendMessageStream(content: string, sessionId?: string, sourceIds?: string[]): Observable<string> {
    return new Observable<string>(observer => {
      const token = localStorage.getItem('accessToken');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json'
      };
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      fetch(`${this.baseUrl}/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ sessionId, content, sourceIds })
      })
      .then(response => {
        if (!response.ok) {
          throw new Error(`Chat stream HTTP error! Status: ${response.status}`);
        }
        const reader = response.body?.getReader();
        const decoder = new TextDecoder();
        if (!reader) {
          observer.error('No reader available on response body');
          return;
        }

        const push = () => {
          reader.read().then(({ done, value }) => {
            if (done) {
              observer.complete();
              return;
            }
            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');
            for (const line of lines) {
              if (line.startsWith('data:')) {
                const data = line.slice(5).trim();
                observer.next(data);
              }
            }
            push();
          }).catch(err => {
            observer.error(err);
          });
        };
        push();
      })
      .catch(err => {
        observer.error(err);
      });
    });
  }
}
