export interface RegisterRequest {
  username: string;
  password?: string; // Optional if using OAuth
  role: 'STUDENT' | 'TEACHER' | 'ADMIN';
}

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  username?: string;
  role?: string;
  id?: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
  timestamp: number;
  requestId: string;
}

export interface Source {
  id: string;
  userId: string;
  name: string;
  type: string;
  url: string;
  fileSize: number;
  createdAt: string;
  metadata?: Record<string, string | number | boolean | null>;
}

export interface DocumentArtifact {
  id: string;
  documentId: string;
  type: 'SUMMARY' | 'FLASHCARDS' | 'QUIZ' | 'MIND_MAP' | 'EMBEDDINGS';
  storageKey: string;
  status: string;
  createdAt: string;
}

export interface Document {
  id: string;
  ownerId: string;
  originalFilename: string;
  fileHashSha256: string;
  sizeBytes: number;
  mimeType: string;
  status: 'UPLOADING' | 'UPLOADED' | 'CHUNKING' | 'EMBEDDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED';
  summaryStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  quizStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  flashcardStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  mindmapStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  discussionStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  embeddingStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  podcastStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
  artifacts: DocumentArtifact[];
}

export interface Summary {
  id: string;
  sourceId: string;
  summary: string;
  keyPoints: string[];
  importantTakeaways: string[];
  definitions: string[];
  nextSteps: string[];
  generatedAt: string;
}

export interface Flashcard {
  id: string;
  deckId: string;
  front: string;
  back: string;
  topic: string;
  interval: number;
  easeFactor: number;
  repetitions: number;
  nextReviewDate: string;
}

export interface FlashcardDeck {
  id: string;
  userId: string;
  sourceId: string;
  topic: string;
  title: string;
  createdAt: string;
}

export interface AIResult<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface Question {
  id: string;
  text: string;
  concept: string;
  difficulty: number;
  correctAnswer: string;
  topic: string;
  type: string;
  options?: string[];
  correctOptionIndex?: number;
  explanation?: string;
}

export interface GeneratedQuestionWrapper {
  question: Question;
  explanation: string;
}

export interface SubmitAnswerRequest {
  questionId: string;
  studentAnswer: string;
}

export interface SubmitAnswerResponse {
  correct: boolean;
  correctAnswer: string;
  concept: string;
  confidenceScore: number;
  accuracy: number;
  streak: number;
}

export interface GenerateQuestionRequest {
  concept: string;
  difficulty: number;
  sourceId: string;
}

export interface GenerateAdaptiveQuizRequest {
  concept: string;
  sourceId: string;
  totalQuestions: number;
}

export interface MindMapNode {
  id: string;
  label: string;
  title?: string;
  description?: string;
  importance?: number;
  keyPoints?: string[];
  related?: string[];
  expanded?: boolean;
  children?: MindMapNode[];
}

export interface MindMap {
  id: string;
  userId: string;
  sourceId: string;
  mindMap?: MindMapNode;
  generatedAt: string;
}

export interface Podcast {
  id: string;
  userId: string;
  sourceId: string;
  topic: string;
  script: any;
  audioFile?: string;
  audioUrl?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  audioStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
}

export interface DiscussionReply {
  id: string;
  threadId: string;
  content: string;
  authorId: string;
  upvotes: number;
  acceptedAnswer: boolean;
  createdAt: string;
}

export interface DiscussionThread {
  id: string;
  title: string;
  content: string;
  sourceId: string;
  authorId: string;
  aiSummary?: string;
  upvotes: number;
  createdAt: string;
  updatedAt: string;
  threadMessages: DiscussionReply[];
}

export interface ChatRequest {
  sessionId?: string;
  content: string;
  sourceIds?: string[];
}

export interface ChatMessage {
  id?: string;
  sessionId?: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp?: string;
}

export interface ChatSession {
  id: string;
  userId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Notification {
  id: string;
  userId: string;
  type: 'UPLOAD_COMPLETE' | 'UPLOAD_FAILED' | 'STUDY_REMINDER' | 'MILESTONE' | 'AI_ALERT' | 'RECOMMENDATION';
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  createdAtMs: number;
  resourceId?: string;
  resourceType?: string;
}

export interface StudyPlan {
  id: string;
  userId: string;
  generatedAt: string;
  availableTime: string;
  dailyPlan?: Record<string, string | number | boolean | null | undefined>[];
  weeklyPlan?: Record<string, string | number | boolean | null | undefined>[];
}

export interface RecommendationResponse {
  recommendedFlashcards: string[];
  recommendedQuizzes: string[];
  recommendedSummaries: string[];
  recommendedMindMaps: string[];
  recommendedStudySessions: string[];
  weakTopics: string[];
  systemInsight?: Record<string, string | number | boolean | null | undefined>;
}

export interface StudentDashboardResponse {
  currentLevel: string;
  masteryScore: number;
  averageScore: number;
  weakTopics: string[];
  strongTopics: string[];
  recentProgress: string;
  studyStreak: number;
}

export interface ResponseRecord {
  id: string;
  userId: string;
  questionId: string;
  concept: string;
  difficulty: number;
  correct: boolean;
  studentAnswer: string;
  timestamp: number;
  longTimestamp?: number;
}
