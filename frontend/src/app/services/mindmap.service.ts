import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, MindMap } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class MindMapService {
  private baseUrl = 'http://localhost:8080/api/v1/mindmaps';

  constructor(private http: HttpClient) {}

  generateMindmap(topic: string, sourceId: string): Observable<ApiResponse<MindMap>> {
    return this.http.post<ApiResponse<MindMap>>(`${this.baseUrl}/generate`, { topic, sourceId });
  }

  getMindmapBySource(sourceId: string): Observable<ApiResponse<MindMap>> {
    return this.http.get<ApiResponse<MindMap>>(`${this.baseUrl}/source/${sourceId}`);
  }

  getAllMindmaps(): Observable<ApiResponse<MindMap[]>> {
    return this.http.get<ApiResponse<MindMap[]>>(this.baseUrl);
  }
}
