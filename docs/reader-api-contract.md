# Document Reader API Integration Contract

This document outlines the API contract for the Document Reader feature. It defines the endpoint structures, request parameters, response models, and security validation rules necessary for integrating the Angular frontend with the CampusLM backend.

---

## 1. Overview

The Ingestion Pipeline splits uploaded documents into semantic chunks and stores them in MongoDB. The Document Reader endpoints expose these chunks so that the frontend can display them in a structured, page-by-page view or perform keyword search highlighting.

All responses are wrapped in the standard `ApiResponse<T>` envelope:

```typescript
interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
  timestamp: number;
  requestId: string;
  status: string;
  error?: string;
  fieldErrors?: Record<string, string>;
}
```

---

## 2. API Endpoints

### A. Paginated Chunks Endpoint

Retrieves document segments in small slices. Ideal for virtual scrolling, infinite loaders, or search highlighting results.

* **URL**: `/api/v1/sources/{sourceId}/chunks` (also maps to `/sources/{sourceId}/chunks`)
* **Method**: `GET`
* **Authentication**: Required (`Bearer <JWT_TOKEN>`)
* **Request Parameters**:
  * `page` (number, default: `0`): The page index to fetch.
  * `size` (number, default: `50`): The number of chunks to fetch per page.
  * `q` (string, optional): Search keyword query. If provided, filters chunks to only those matching the keyword (case-insensitive).
* **Response Status**: `200 OK`
* **Response Body (`ApiResponse<Page<DocumentChunkResponse>>`)**:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "603d2b2f7b11d82f80c6517a",
        "sourceId": "603d2afc7b11d82f80c65179",
        "chunkIndex": 0,
        "pageNumber": 1,
        "content": "This is the text parsed from the first chunk of page 1.",
        "createdAt": "2026-06-15T18:00:00Z"
      }
    ],
    "pageable": {
      "sort": {
        "sorted": true,
        "unsorted": false,
        "empty": false
      },
      "offset": 0,
      "pageNumber": 0,
      "pageSize": 50,
      "unpaged": false,
      "paged": true
    },
    "totalPages": 1,
    "totalElements": 1,
    "last": true,
    "size": 50,
    "number": 0,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "numberOfElements": 1,
    "first": true,
    "empty": false
  },
  "message": "Request processed successfully",
  "timestamp": 1781530948000,
  "requestId": "req-98213",
  "status": "COMPLETED"
}
```

### B. Consolidated Reader Endpoint

Aggregates all chunks, sorts them, and groups content by page number. Ideal for loading the complete document text into a book-style reader interface.

* **URL**: `/api/v1/sources/{sourceId}/reader` (also maps to `/sources/{sourceId}/reader`)
* **Method**: `GET`
* **Authentication**: Required (`Bearer <JWT_TOKEN>`)
* **Response Status**: `200 OK`
* **Response Body (`ApiResponse<DocumentReaderResponse>`)**:

```json
{
  "success": true,
  "data": {
    "sourceId": "603d2afc7b11d82f80c65179",
    "title": "Machine_Learning_Lecture_Notes.pdf",
    "pages": [
      {
        "page": 1,
        "content": "Full reconstructed page 1 text content by joining all individual page chunks sequentially..."
      },
      {
        "page": 2,
        "content": "Full reconstructed page 2 text content..."
      }
    ]
  },
  "message": "Request processed successfully",
  "timestamp": 1781530949000,
  "requestId": "req-98214",
  "status": "COMPLETED"
}
```

---

## 3. Security & Error Handling

To ensure strict tenant and data security, the backend enforces the following checks:
1. **JWT Validity**: Requests without a valid Bearer token will return `401 Unauthorized`.
2. **Access Control Check**: The backend verifies if the authenticated user ID owns the requested `sourceId`.
   * **Source Not Found**: If the source does not exist, returns `404 Not Found` with message: `"Source not found: <sourceId>"`.
   * **Ownership Denied**: If the source belongs to another user, returns `403 Forbidden` with message: `"Unauthorized to access this source"`.

### Example 403 Error JSON:
```json
{
  "success": false,
  "data": null,
  "message": "Unauthorized to access this source",
  "timestamp": 1781530950000,
  "requestId": "req-98215",
  "status": "FAILED",
  "error": "Unauthorized to access this source"
}
```

---

## 4. Angular Integration Guidelines

### A. TypeScript Interface Declarations

Add these models to your frontend codebase (`src/app/models/document-reader.models.ts`):

```typescript
export interface DocumentChunkResponse {
  id: string;
  sourceId: string;
  chunkIndex: number;
  pageNumber: number;
  content: string;
  createdAt: string;
}

export interface PageContent {
  page: number;
  content: string;
}

export interface DocumentReaderResponse {
  sourceId: string;
  title: string;
  pages: PageContent[];
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}
```

### B. Angular Service Integration

Example service using Angular's `HttpClient` (`src/app/services/document-reader.service.ts`):

```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponse, SpringPage, DocumentChunkResponse, DocumentReaderResponse } from '../models/document-reader.models';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DocumentReaderService {
  private readonly baseUrl = `${environment.apiUrl}/api/v1/sources`;

  constructor(private http: HttpClient) {}

  /**
   * Fetches paginated chunks of a document, optionally matching a search term.
   */
  getDocumentChunks(
    sourceId: string,
    page: number = 0,
    size: number = 50,
    searchQuery?: string
  ): Observable<SpringPage<DocumentChunkResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (searchQuery && searchQuery.trim() !== '') {
      params = params.set('q', searchQuery.trim());
    }

    return this.http.get<ApiResponse<SpringPage<DocumentChunkResponse>>>(`${this.baseUrl}/${sourceId}/chunks`, { params }).pipe(
      map(response => {
        if (response.success) {
          return response.data;
        } else {
          throw new Error(response.message || 'Failed to retrieve chunks');
        }
      })
    );
  }

  /**
   * Fetches the full reader-friendly text of a document grouped page-by-page.
   */
  getDocumentReaderData(sourceId: string): Observable<DocumentReaderResponse> {
    return this.http.get<ApiResponse<DocumentReaderResponse>>(`${this.baseUrl}/${sourceId}/reader`).pipe(
      map(response => {
        if (response.success) {
          return response.data;
        } else {
          throw new Error(response.message || 'Failed to retrieve document reader data');
        }
      })
    );
  }
}
```
