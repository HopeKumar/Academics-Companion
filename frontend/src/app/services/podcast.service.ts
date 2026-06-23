import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Podcast } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class PodcastService {
  private baseUrl = 'http://localhost:8080/api/v1/podcasts';

  constructor(private http: HttpClient) {}

  generatePodcast(topic: string, sourceId: string): Observable<ApiResponse<{ message: string; id: string }>> {
    return this.http.post<ApiResponse<{ message: string; id: string }>>(`${this.baseUrl}/generate`, { topic, sourceId });
  }

  getPodcastStatus(id: string): Observable<ApiResponse<{ podcastId: string; status: string; audioReady: boolean }>> {
    return this.http.get<ApiResponse<{ podcastId: string; status: string; audioReady: boolean }>>(`${this.baseUrl}/status/${id}`);
  }

  getPodcastResult(id: string): Observable<ApiResponse<string | Record<string, string>>> {
    return this.http.get<ApiResponse<string | Record<string, string>>>(`${this.baseUrl}/result/${id}`);
  }

  getPodcastHistory(): Observable<ApiResponse<Podcast[]>> {
    return this.http.get<ApiResponse<Podcast[]>>(this.baseUrl);
  }

  getPodcastDetails(id: string): Observable<ApiResponse<Podcast>> {
    return this.http.get<ApiResponse<Podcast>>(`${this.baseUrl}/${id}`);
  }

  getPodcastBySourceId(sourceId: string): Observable<ApiResponse<Podcast>> {
    return this.http.get<ApiResponse<Podcast>>(`${this.baseUrl}/source/${sourceId}`);
  }

  getPodcastAudio(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/audio`, { responseType: 'blob' });
  }
}
