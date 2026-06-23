import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Flashcard, FlashcardDeck, AIResult } from './api-models';

@Injectable({
  providedIn: 'root'
})
export class FlashcardService {
  private baseUrl = 'http://localhost:8080/api/v1/flashcards';

  constructor(private http: HttpClient) {}

  generateFlashcards(topic: string, sourceId: string): Observable<ApiResponse<AIResult<FlashcardDeck>>> {
    return this.http.post<ApiResponse<AIResult<FlashcardDeck>>>(`${this.baseUrl}/generate`, { topic, sourceId });
  }

  getCardsBySource(sourceId: string): Observable<ApiResponse<Flashcard[]>> {
    return this.http.get<ApiResponse<Flashcard[]>>(`${this.baseUrl}/source/${sourceId}`);
  }

  getDueCards(): Observable<ApiResponse<Flashcard[]>> {
    return this.http.get<ApiResponse<Flashcard[]>>(`${this.baseUrl}/due`);
  }

  getDecks(): Observable<ApiResponse<FlashcardDeck[]>> {
    return this.http.get<ApiResponse<FlashcardDeck[]>>(`${this.baseUrl}/decks`);
  }

  getCards(deckId: string): Observable<ApiResponse<Flashcard[]>> {
    return this.http.get<ApiResponse<Flashcard[]>>(`${this.baseUrl}/decks/${deckId}`);
  }

  getDeckStats(deckId: string): Observable<ApiResponse<Record<string, unknown>>> {
    return this.http.get<ApiResponse<Record<string, unknown>>>(`${this.baseUrl}/decks/${deckId}/stats`);
  }

  reviewCard(cardId: string, quality: number): Observable<ApiResponse<Flashcard>> {
    return this.http.post<ApiResponse<Flashcard>>(`${this.baseUrl}/review`, { cardId, quality });
  }

  downloadDeck(deckId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/decks/${deckId}/download`, { responseType: 'blob' });
  }
}
