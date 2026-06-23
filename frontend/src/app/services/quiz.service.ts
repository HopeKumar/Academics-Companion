import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Question, GenerateQuestionRequest, GenerateAdaptiveQuizRequest, GeneratedQuestionWrapper, SubmitAnswerRequest, SubmitAnswerResponse } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class QuizService {
  private baseUrl = 'http://localhost:8080/api/v1/quiz';

  constructor(private http: HttpClient) {}

  generateQuestion(request: GenerateQuestionRequest): Observable<ApiResponse<{ question: Question; explanation: string }>> {
    return this.http.post<ApiResponse<{ question: Question; explanation: string }>>(`${this.baseUrl}/generate-question`, request);
  }

  generateAdaptiveQuiz(request: GenerateAdaptiveQuizRequest): Observable<ApiResponse<GeneratedQuestionWrapper[]>> {
    return this.http.post<ApiResponse<GeneratedQuestionWrapper[]>>(`${this.baseUrl}/generate-adaptive`, request);
  }

  submitAnswer(request: SubmitAnswerRequest): Observable<ApiResponse<SubmitAnswerResponse>> {
    return this.http.post<ApiResponse<SubmitAnswerResponse>>(`${this.baseUrl}/submit`, request);
  }
}
