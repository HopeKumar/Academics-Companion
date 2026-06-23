# API Endpoint Map

## Features

### Upload
- **Frontend Component**: `AppComponent`
- **Service**: `UploadService`
- **API Endpoint**: `POST /api/v1/sources/upload`
- **Request DTO**: `FormData (file)`
- **Response DTO**: `ApiResponse<Source>`
- **Status**: WORKING

### Summary
- **Frontend Component**: `AppComponent`
- **Service**: `SummaryService`
- **API Endpoint**: `POST /api/v1/summary/generate`
- **Request DTO**: `{ sourceId: string }`
- **Response DTO**: `ApiResponse<Summary>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### Flashcards
- **Frontend Component**: `AppComponent`
- **Service**: `FlashcardService`
- **API Endpoint**: `POST /api/v1/flashcards/generate`
- **Request DTO**: `{ topic: string, sourceId: string }`
- **Response DTO**: `ApiResponse<FlashcardGenerationResponse>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### Quiz
- **Frontend Component**: `AppComponent`
- **Service**: `QuizService`
- **API Endpoint**: `POST /api/v1/quiz/generate-adaptive`
- **Request DTO**: `GenerateAdaptiveQuizRequest`
- **Response DTO**: `ApiResponse<GeneratedQuestionWrapper[]>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### MindMap
- **Frontend Component**: `AppComponent`
- **Service**: `MindMapService`
- **API Endpoint**: `POST /api/v1/mindmaps/generate`
- **Request DTO**: `{ topic: string, sourceId: string }`
- **Response DTO**: `ApiResponse<MindMap>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### Discussion
- **Frontend Component**: `AppComponent`
- **Service**: `DiscussionService`
- **API Endpoint**: `POST /api/v1/discussions`
- **Request DTO**: `{ title: string, content: string, sourceId: string }`
- **Response DTO**: `ApiResponse<DiscussionThread>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### Podcast
- **Frontend Component**: `AppComponent`
- **Service**: `PodcastService`
- **API Endpoint**: `POST /api/v1/podcasts/generate`
- **Request DTO**: `{ topic: string, sourceId: string }`
- **Response DTO**: `ApiResponse<{ message: string, id: string }>`
- **Status**: BROKEN / NOT CALLED (Will be fixed in Orchestration)

### Study Plan
- **Frontend Component**: `AppComponent`
- **Service**: `StudyPlanService`
- **API Endpoint**: `POST /api/v1/study-plan/generate`
- **Request DTO**: `{ availableTime: string }`
- **Response DTO**: `ApiResponse<StudyPlan>`
- **Status**: WORKING

### Dashboard
- **Frontend Component**: `AppComponent`
- **Service**: `DashboardService`
- **API Endpoint**: `GET /api/v1/dashboard/student/{studentId}`
- **Request DTO**: `N/A`
- **Response DTO**: `ApiResponse<StudentDashboardResponse>`
- **Status**: WORKING

### Analytics
- **Frontend Component**: `AppComponent`
- **Service**: `AnalyticsService`
- **API Endpoint**: `GET /api/v1/analytics`
- **Request DTO**: `N/A`
- **Response DTO**: `ApiResponse<Record<string, unknown>>`
- **Status**: WORKING

### Auth (Login/Register)
- **Frontend Component**: `AppComponent`
- **Service**: `AuthService`
- **API Endpoint**: `POST /api/v1/auth/login` and `POST /api/v1/auth/register`
- **Request DTO**: `LoginRequest` and `RegisterRequest`
- **Response DTO**: `ApiResponse<AuthResponse>`
- **Status**: WORKING
