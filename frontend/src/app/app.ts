import { Component, ViewChild, ElementRef, OnDestroy, OnInit, HostListener, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

import { AuthService } from './services/auth.service';
import { DashboardService } from './services/dashboard.service';
import { UploadService } from './services/upload.service';
import { SummaryService } from './services/summary.service';
import { FlashcardService } from './services/flashcard.service';
import { QuizService } from './services/quiz.service';
import { MindMapService } from './services/mindmap.service';
import { PodcastService } from './services/podcast.service';
import { DiscussionService } from './services/discussion.service';
import { AnalyticsService } from './services/analytics.service';
import { NotificationService } from './services/notification.service';
import { RecommendationService } from './services/recommendation.service';
import { StudyPlanService } from './services/study-plan.service';
import { ChatService } from './services/chat.service';
import { OrchestrationService } from './services/orchestration.service';
import { Podcast } from './services/api-models';
import { SelectedDocumentStateService } from './services/selected-document-state.service';

export interface Flashcard {
  topic: string;
  explanation: string;
  id?: string;
  question?: string;
  answer?: string;
  // Client-side only — not persisted to backend
  seen?: boolean;
  notes?: string;
  relatedConcepts?: string[];
  detailText?: string;
  answerText?: string;
}

export interface MindMapNode {
  id: string;
  title?: string;
  label: string;
  description?: string;
  importance?: number;
  keyPoints?: string[];
  related?: string[];
  expanded?: boolean;
  children?: MindMapNode[];
}

export interface QuizQuestion {
  question: string;
  options: string[];
  correct: number;
  explanation?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ThreadMessage {
  authorName: string;
  authorInitials: string;
  authorColor: string;
  timeAgo: string;
  content: string;
}

export interface Discussion {
  id: string | number;
  category: string;
  title: string;
  messages: number;
  active: number;
  lastActivity: string;
  threadMessages?: ThreadMessage[];
}

export interface DocumentContent {
  summaryOverview: string;
  summaryKeyPoints: string[];
  summaryTakeaways: string[];
  transcriptParagraphs: string[];
  audioDuration: number;
  flashcards: Flashcard[];
  quizQuestions: QuizQuestion[];
  discussions: Discussion[];
  quizState: {
    currentQuestionIndex: number;
    selectedAnswer: number | null;
    answerRevealed: boolean;
    quizFinished: boolean;
    quizScore: number;
  };
  flashcardState: {
    currentCardIndex: number;
    cardFlipped: boolean;
  };
  mindMap: MindMapNode;
  chatMessages: ChatMessage[];
}

export interface SourceFile {
  id: string | number;
  name: string;
  selected: boolean;
  status: 'Completed' | 'In progress' | 'Not started';
  progress: number;
  score: number | null;
  content: DocumentContent;
  progressMessage?: string;
  metadata?: any;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
})
export class AppComponent implements OnInit, OnDestroy {
  constructor(
    public authService: AuthService,
    public dashboardService: DashboardService,
    public uploadService: UploadService,
    public summaryService: SummaryService,
    public flashcardService: FlashcardService,
    public quizService: QuizService,
    public mindmapService: MindMapService,
    public podcastService: PodcastService,
    public discussionService: DiscussionService,
    public analyticsService: AnalyticsService,
    public notificationService: NotificationService,
    public recommendationService: RecommendationService,
    public studyPlanService: StudyPlanService,
    public chatService: ChatService,
    public orchestrationService: OrchestrationService,
    public selectedDocumentStateService: SelectedDocumentStateService,
    public cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('accessToken');
    if (token) {
      this.isLoggedIn = true;
      this.email = localStorage.getItem('username') || '';
      this.onLoginSuccess();
    }
    const saved = localStorage.getItem('uploadedSources');
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        this.uploadedSources = parsed.map((s: any) => {
          if (!s.content) {
            s.content = this.generateDefaultContent();
          } else if (!s.content.mindMap) {
            s.content.mindMap = this.generateDefaultContent().mindMap;
          }
          if (!s.content.chatMessages) {
            s.content.chatMessages = [];
          }
          return s;
        });
      } catch (e) {
        console.error('Failed to parse saved state', e);
      }
    }
  }

  saveState() {
    localStorage.setItem('uploadedSources', JSON.stringify(this.uploadedSources));
    this.cdr.detectChanges();
  }

  generateDefaultContent(): DocumentContent {
    return {
      summaryOverview: '',
      summaryKeyPoints: [],
      summaryTakeaways: [],
      transcriptParagraphs: [],
      audioDuration: 0,
      flashcards: [],
      quizQuestions: [],
      discussions: [],
      quizState: {
        currentQuestionIndex: 0,
        selectedAnswer: null,
        answerRevealed: false,
        quizFinished: false,
        quizScore: 0
      },
      flashcardState: {
        currentCardIndex: 0,
        cardFlipped: false
      },
      mindMap: { id: 'root', label: 'Generating Mind Map...', children: [] } as any,
      chatMessages: []
    };
  }

  @ViewChild('scrollContainer') scrollContainer!: ElementRef;
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('threadFileInput') threadFileInput!: ElementRef<HTMLInputElement>;

  @ViewChild('sourceInput')
  sourceInput!: ElementRef<HTMLInputElement>;

  @ViewChild('profileContainer')
  profileContainer!: ElementRef<HTMLDivElement>;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.showProfileMenu && this.profileContainer && !this.profileContainer.nativeElement.contains(event.target as Node)) {
      this.showProfileMenu = false;
    }
  }

  Math = Math;
  isLoggedIn: boolean = false;
  currentView: string = 'home';

  email: string = '';
  password: string = '';
  loginError: string = '';
  showProfileMenu: boolean = false;
  uploadedFileName: string = '';

  // Debug Panel Properties
  debugMode: boolean = true; // Set to true to show Integration Debug Panel
  lastEndpointCalled: string = 'N/A';
  lastRequestPayload: any = null;
  lastResponseStatus: string = 'N/A';
  lastResponseTime: string = 'N/A';
  pollingStatus: string = 'Inactive';
  generationStatus: string = 'N/A';

  isSummaryLoading: boolean = false;
  isFlashcardsLoading: boolean = false;
  activeDeckStats: any = null;
  selectedTopicFilter: string = '';
  isQuizLoading: boolean = false;
  isMindMapLoading: boolean = false;
  isPodcastLoading: boolean = false;

  // Flashcard client-side state (not persisted to backend)
  private flashcardSeenMap: Map<string, boolean> = new Map();
  private flashcardNotesMap: Map<string, string> = new Map();

  toggleCardSeen(event: any): void {
    const card = this.filteredFlashcards[this.currentCardIndex];
    if (!card || !card.id) return;
    const current = this.flashcardSeenMap.get(card.id) || false;
    this.flashcardSeenMap.set(card.id, !current);
  }

  isCardSeen(card: Flashcard): boolean {
    return card && card.id ? (this.flashcardSeenMap.get(card.id) || false) : false;
  }

  get currentCardNotes(): string {
    const card = this.filteredFlashcards[this.currentCardIndex];
    if (!card || !card.id) return '';
    return this.flashcardNotesMap.get(card.id) || '';
  }

  set currentCardNotes(val: string) {
    const card = this.filteredFlashcards[this.currentCardIndex];
    if (!card || !card.id) return;
    this.flashcardNotesMap.set(card.id, val);
  }

  // Mind Map Redesign Properties
  activeSidebarNav: string = 'overview';
  visualNodes: any[] = [];
  visualConnections: any[] = [];
  panX: number = 0;
  panY: number = 0;
  isPanning: boolean = false;
  private dragStartX: number = 0;
  private dragStartY: number = 0;
  isFullscreen: boolean = false;
  selectedMindMapNode: any = null;
  showNodePanel: boolean = false;
  nodeExplanationHtml: string = '';
  nodeExplanationLoading: boolean = false;
  nodeExplanationRawText: string = '';
  private nodeExplanationSubscription: Subscription | null = null;

  conceptSearchQuery: string = '';
  studyModeActive: boolean = false;
  examModeActive: boolean = false;
  hoveredMindMapNode: any = null;
  tooltipX: number = 0;
  tooltipY: number = 0;
  showTooltip: boolean = false;

  showPassword = false;
  showConfirmPassword = false;

  togglePassword() {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPassword() {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  authMode: 'login' | 'register' = 'login'; // View toggle
  showRegistrationSuccess: boolean = false;

  registerForm = new FormGroup({
    fullName: new FormControl('', [Validators.required]),
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', [Validators.required, Validators.minLength(8)]),
    confirmPassword: new FormControl('', [Validators.required]),
    terms: new FormControl(false, [Validators.requiredTrue])
  });

  register(): void {
    if (this.registerForm.valid) {
      const email = this.registerForm.value.email || '';
      const password = this.registerForm.value.password || '';

      this.authService.register({ username: email, password, role: 'STUDENT' }).subscribe({
        next: (res) => {
          this.showRegistrationSuccess = true;
          this.registerForm.reset();
          this.authMode = 'login';
          setTimeout(() => {
            this.showRegistrationSuccess = false;
          }, 3000);
        },
        error: (err) => {
          this.loginError = err.error?.error || 'Registration failed. Please try again.';
        }
      });
    } else {
      this.registerForm.markAllAsTouched();
    }
  }

  get userInitial(): string {
    return this.email ? this.email.charAt(0).toUpperCase() : 'U';
  }

  get userName(): string {
    if (this.email) {
      const namePart = this.email.split('@')[0];
      return namePart.charAt(0).toUpperCase() + namePart.slice(1);
    }
    return 'User';
  }

  toggleProfileMenu(): void {
    this.showProfileMenu = !this.showProfileMenu;
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.performLocalLogout();
      },
      error: () => {
        this.performLocalLogout();
      }
    });
  }

  performLocalLogout(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('username');
    localStorage.removeItem('uploadedSources');
    this.uploadedSources = [];
    this.selectedDocumentStateService.setActiveDocument(null);
    this.isLoggedIn = false;
    this.showProfileMenu = false;
    this.email = '';
    this.password = '';
    this.currentView = 'home';
    this.uploadedFileName = '';
  }

  login(): void {
    this.loginError = '';
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!this.email || !emailRegex.test(this.email)) {
      this.loginError = 'Please enter a valid college or personal email id.';
      return;
    }
    if (!this.password || this.password.length < 6) {
      this.loginError = 'Password must contain minimum of 6 characters.';
      return;
    }

    this.authService.login({ username: this.email, password: this.password }).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          localStorage.setItem('accessToken', res.data?.accessToken);
          localStorage.setItem('refreshToken', res.data?.refreshToken || '');
          localStorage.setItem('username', res.data?.username || this.email);
          this.isLoggedIn = true;
          this.onLoginSuccess();
        } else {
          this.loginError = res.message || 'Login failed.';
        }
      },
      error: (err) => {
        this.loginError = err.error?.error || 'Invalid credentials.';
      }
    });
  }

  showGoogleLoginModal: boolean = false;

  loginWithGoogle(): void {
    this.showGoogleLoginModal = true;
  }

  selectGoogleAccount(mockEmail: string): void {
    const password = 'oauth-placeholder-password';
    this.authService.login({ username: mockEmail, password }).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          localStorage.setItem('accessToken', res.data?.accessToken);
          localStorage.setItem('refreshToken', res.data?.refreshToken || '');
          localStorage.setItem('username', res.data?.username || mockEmail);
          this.email = mockEmail;
          this.isLoggedIn = true;
          this.showGoogleLoginModal = false;
          this.onLoginSuccess();
        }
      },
      error: () => {
        this.authService.register({ username: mockEmail, password, role: 'STUDENT' }).subscribe({
          next: () => {
            this.authService.login({ username: mockEmail, password }).subscribe({
              next: (loginRes) => {
                if (loginRes.success && loginRes.data) {
                  localStorage.setItem('accessToken', loginRes.data?.accessToken);
                  localStorage.setItem('refreshToken', loginRes.data?.refreshToken || '');
                  localStorage.setItem('username', loginRes.data?.username || mockEmail);
                  this.email = mockEmail;
                  this.isLoggedIn = true;
                  this.showGoogleLoginModal = false;
                  this.onLoginSuccess();
                }
              }
            });
          }
        });
      }
    });
  }

  closeGoogleLogin(): void {
    this.showGoogleLoginModal = false;
  }

  onLoginSuccess(): void {
    this.loadSources();
    this.loadNotifications();
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.recommendationService.getRecommendations().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          console.log('Recommendations loaded from backend:', res.data);
        }
      },
      error: (err) => console.error('Failed to load recommendations', err)
    });

    this.studyPlanService.getStudyPlan(this.email).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          console.log('Study Plan loaded from backend:', res.data);
        }
      },
      error: (err) => {
        this.studyPlanService.generateStudyPlan('2 hours daily').subscribe({
          next: (genRes) => {
            if (genRes.success && genRes.data) {
              console.log('Generated new Study Plan:', genRes.data);
            }
          },
          error: (genErr) => console.error('Failed to generate study plan', genErr)
        });
      }
    });
  }

  loadNotifications(): void {
    this.notificationService.getNotifications().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          console.log('Notifications loaded from backend:', res.data);
        }
      },
      error: (err) => console.error('Failed to load notifications', err)
    });
  }

  loadSources(): void {
    this.uploadService.getSources().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const backendSources = res.data;
          this.uploadedSources = backendSources.map((bs) => {
            const existing = this.uploadedSources.find((s) => s.id === bs.id || s.name === bs.name);
            let status: 'Completed' | 'In progress' | 'Not started' = 'Not started';
            let progress = 0;
            const bStatus = (bs.metadata?.['status'] as string)?.toUpperCase();
            
            if (bStatus === 'READY' || bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'SUCCESS') {
              status = 'Completed';
              progress = 100;
            } else if (bStatus === 'PROCESSING' || bStatus === 'CHUNKING' || bStatus === 'EMBEDDING' || bStatus === 'UPLOADING') {
              status = 'In progress';
              progress = 50;
            }
            
            const doc: SourceFile = {
              id: bs.id,
              name: bs.name,
              selected: existing ? existing.selected : false,
              status: status,
              progress: progress,
              score: existing ? existing.score : null,
              content: existing && existing.content ? existing.content : this.generateDefaultContent(),
              progressMessage: this.calculateProgressMessage(bs.metadata, bStatus || ''),
              metadata: bs.metadata
            };

            if (status === 'In progress') {
              this.pollSourceStatus(bs.id);
            } else if (status === 'Completed') {
              this.loadFeatureForActiveDocument(doc, this.currentView);
            }
            
            return doc;
          });
          
          if (this.uploadedFileName) {
            const active = this.uploadedSources.find((s) => s.name === this.uploadedFileName);
            if (active) {
                this.selectedDocumentStateService.setActiveDocument(active);
            } else if (this.uploadedSources.length > 0) {
              this.uploadedFileName = this.uploadedSources[0].name;
              this.selectedDocumentStateService.setActiveDocument(this.uploadedSources[0]);
            }
          } else if (this.uploadedSources.length > 0) {
            this.uploadedFileName = this.uploadedSources[0].name;
            this.selectedDocumentStateService.setActiveDocument(this.uploadedSources[0]);
          }
          
          this.saveState();
        }
      },
      error: (err) => console.error('Failed to load sources', err)
    });
  }

  pollSourceStatus(sourceId: string): void {
    const existing = this.pollingSubscriptions.get(sourceId);
    if (existing) {
      existing.unsubscribe();
      this.pollingSubscriptions.delete(sourceId);
    }
    let isCompleted = false;
    const sub = this.orchestrationService.pollStatus(sourceId, 120000, 3000).subscribe({
      next: (statusData) => {
        const bStatus = (statusData?.status as string)?.toUpperCase();
        const progress = statusData?.progress as number;

        const doc = this.uploadedSources.find((s) => s.id === sourceId);
        if (doc) {
          doc.metadata = statusData?.metadata;
          if (bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'READY' || bStatus === 'SUCCESS') {
            doc.status = 'Completed';
            doc.progress = 100;
            isCompleted = true;
            this.clearPollingSubscription(sourceId);
            this.loadFeatureForActiveDocument(doc, this.currentView);
          } else if (bStatus === 'FAILED' || bStatus === 'FAIL') {
            doc.status = 'Not started';
            doc.progress = 0;
            doc.progressMessage = 'Failed';
            isCompleted = true;
            this.clearPollingSubscription(sourceId);
            alert(`Processing failed for document: ${doc.name}`);
          } else {
            doc.status = 'In progress';
            doc.progress = progress || 50;
            doc.progressMessage = this.calculateProgressMessage(statusData?.metadata, bStatus);
            
            // Asynchronously load completed features while document is still processing
            if (doc.metadata) {
              if (doc.metadata.summaryStatus === 'COMPLETED' && (!doc.content.summaryOverview || doc.content.summaryOverview.trim() === '')) {
                this.loadSummaryForActiveDocument(doc);
              }
              if (doc.metadata.flashcardStatus === 'COMPLETED' && (!doc.content.flashcards || doc.content.flashcards.length === 0)) {
                this.loadFlashcardsForActiveDocument(doc);
              }
              if (doc.metadata.quizStatus === 'COMPLETED' && (!doc.content.quizQuestions || doc.content.quizQuestions.length === 0)) {
                this.loadQuizForActiveDocument(doc);
              }
              if (doc.metadata.mindmapStatus === 'COMPLETED' && (!doc.content.mindMap || doc.content.mindMap.label === 'Generating Mind Map...')) {
                this.loadMindMapForActiveDocument(doc);
              }
              if (doc.metadata.podcastStatus === 'COMPLETED' && (!doc.content.transcriptParagraphs || doc.content.transcriptParagraphs.length === 0 || doc.content.transcriptParagraphs[0] === 'Podcast not generated yet. Generating now...')) {
                this.loadPodcastForActiveDocument(doc);
              }
            }
          }
          this.saveState();
        } else {
          if (bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'READY' || bStatus === 'SUCCESS' || bStatus === 'FAILED' || bStatus === 'FAIL') {
            isCompleted = true;
            this.clearPollingSubscription(sourceId);
          }
        }
      },
      error: (err) => {
        console.error(`Error polling status for source ${sourceId}`, err);
        const doc = this.uploadedSources.find((s) => s.id === sourceId);
        if (doc) {
          doc.status = 'Not started';
          doc.progress = 0;
          this.saveState();
        }
        isCompleted = true;
        this.clearPollingSubscription(sourceId);
      },
      complete: () => {
        this.clearPollingSubscription(sourceId);
      }
    });
    if (!isCompleted) {
      this.pollingSubscriptions.set(sourceId, sub);
    } else {
      sub.unsubscribe();
    }
  }

  calculateProgressMessage(metadata: any, backendStatus: string): string {
    const bStatus = (backendStatus || '').toUpperCase();
    if (bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'READY' || bStatus === 'SUCCESS') {
      return 'Completed';
    }
    if (bStatus === 'FAILED' || bStatus === 'FAIL') {
      return 'Failed';
    }

    const meta = metadata || {};
    if (meta.summaryStatus === 'PROCESSING') {
      return 'Generating summary...';
    } else if (meta.flashcardStatus === 'PROCESSING') {
      return 'Generating flashcards...';
    } else if (meta.quizStatus === 'PROCESSING') {
      return 'Generating quiz...';
    } else if (meta.podcastStatus === 'PROCESSING') {
      return 'Generating podcast...';
    } else if (meta.mindmapStatus === 'PROCESSING') {
      return 'Generating mind map...';
    } else if (meta.summaryStatus === 'COMPLETED' && meta.flashcardStatus === 'COMPLETED' && meta.quizStatus === 'COMPLETED' && meta.podcastStatus === 'COMPLETED') {
      return 'Saving results...';
    } else if (meta.summaryStatus === 'PENDING') {
      return 'Extracting text...';
    }
    return 'Extracting text...';
  }

  loadFeatureForActiveDocument(activeDoc: SourceFile, featureId: string): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    
    switch (featureId) {
      case 'summary':
        this.loadSummaryForActiveDocument(activeDoc);
        break;
      case 'audio':
        this.loadPodcastForActiveDocument(activeDoc);
        break;
      case 'flashcards':
        this.loadFlashcardsForActiveDocument(activeDoc);
        break;
      case 'mindmap':
        this.loadMindMapForActiveDocument(activeDoc);
        break;
      case 'quiz':
        this.loadQuizForActiveDocument(activeDoc);
        break;
      case 'discussion':
        this.loadDiscussionsForActiveDocument(activeDoc);
        break;
      default:
        this.loadSummaryForActiveDocument(activeDoc);
        break;
    }
  }

  loadSummaryForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    this.isSummaryLoading = true;
    this.summaryService.getSummary(activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          activeDoc.content.summaryOverview = res.data?.summary || '';
          activeDoc.content.summaryKeyPoints = res.data?.keyPoints || [];
          activeDoc.content.summaryTakeaways = res.data?.importantTakeaways || [];
          this.saveState();
          this.isSummaryLoading = false;
        } else {
          const summaryStatus = activeDoc.metadata?.summaryStatus;
          if (summaryStatus === 'PROCESSING' || summaryStatus === 'PENDING') {
            this.isSummaryLoading = true;
            return;
          }
          this.generateSummaryForActiveDocument(activeDoc);
        }
      },
      error: () => {
        const summaryStatus = activeDoc.metadata?.summaryStatus;
        if (summaryStatus === 'PROCESSING' || summaryStatus === 'PENDING') {
          this.isSummaryLoading = true;
          return;
        }
        this.generateSummaryForActiveDocument(activeDoc);
      }
    });
  }

  generateSummaryForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    const key = activeDoc.id + '_SUMMARY';
    const existing = this.jobPollingSubscriptions.get(key);
    if (existing) {
      existing.unsubscribe();
      this.jobPollingSubscriptions.delete(key);
    }
    this.isSummaryLoading = true;
    this.summaryService.generateSummary(activeDoc.id as string).subscribe({
      next: (res) => {
        let isJobDone = false;
        const sub = this.orchestrationService.pollJobStatus(activeDoc.id as string, 'SUMMARY').subscribe({
          next: (job) => {
            if (job.status === 'COMPLETED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.summaryService.getSummary(activeDoc.id as string).subscribe({
                next: (summaryRes) => {
                  if (summaryRes.success && summaryRes.data) {
                    activeDoc.content.summaryOverview = summaryRes.data?.summary || '';
                    activeDoc.content.summaryKeyPoints = summaryRes.data?.keyPoints || [];
                    activeDoc.content.summaryTakeaways = summaryRes.data?.importantTakeaways || [];
                    this.saveState();
                  }
                  this.isSummaryLoading = false;
                },
                error: () => {
                  this.isSummaryLoading = false;
                }
              });
            } else if (job.status === 'FAILED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.isSummaryLoading = false;
            }
          },
          error: (err) => {
            console.error('Job polling failed for SUMMARY', err);
            isJobDone = true;
            this.clearJobPollingSubscription(key);
            this.isSummaryLoading = false;
          },
          complete: () => {
            this.clearJobPollingSubscription(key);
          }
        });
        if (!isJobDone) {
          this.jobPollingSubscriptions.set(key, sub);
        } else {
          sub.unsubscribe();
        }
      },
      error: (err) => {
        console.error('Failed to generate summary', err);
        this.isSummaryLoading = false;
      }
    });
  }

  loadActiveDeckStats(sourceId: string): void {
    this.flashcardService.getDecks().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const deck = res.data.find((d: any) => d.sourceId === sourceId);
          if (deck) {
            this.flashcardService.getDeckStats(deck.id).subscribe({
              next: (statsRes) => {
                if (statsRes.success) {
                  this.activeDeckStats = statsRes.data;
                }
              }
            });
          } else {
            this.activeDeckStats = null;
          }
        }
      }
    });
  }

  loadFlashcardsForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    this.isFlashcardsLoading = true;
    this.selectedTopicFilter = '';
    this.loadActiveDeckStats(activeDoc.id as string);
    this.flashcardService.getCardsBySource(activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data && res.data?.length > 0) {
          activeDoc.content.flashcards = res.data?.map((c) => ({
            topic: c.front,
            explanation: c.back,
            id: c.id,
            question: c.front,
            answer: c.back
          }));
          this.saveState();
          this.isFlashcardsLoading = false;
        } else {
          const flashcardStatus = activeDoc.metadata?.flashcardStatus;
          if (flashcardStatus === 'PROCESSING' || flashcardStatus === 'PENDING') {
            this.isFlashcardsLoading = true;
            return;
          }
          this.generateFlashcardsForActiveDocument(activeDoc);
        }
      },
      error: () => {
        const flashcardStatus = activeDoc.metadata?.flashcardStatus;
        if (flashcardStatus === 'PROCESSING' || flashcardStatus === 'PENDING') {
          this.isFlashcardsLoading = true;
          return;
        }
        this.generateFlashcardsForActiveDocument(activeDoc);
      }
    });
  }

  generateFlashcardsForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    const key = activeDoc.id + '_FLASHCARDS';
    const existing = this.jobPollingSubscriptions.get(key);
    if (existing) {
      existing.unsubscribe();
      this.jobPollingSubscriptions.delete(key);
    }
    this.isFlashcardsLoading = true;
    this.flashcardService.generateFlashcards('Core Concepts', activeDoc.id as string).subscribe({
      next: (res) => {
        let isJobDone = false;
        const sub = this.orchestrationService.pollJobStatus(activeDoc.id as string, 'FLASHCARDS').subscribe({
          next: (job) => {
            if (job.status === 'COMPLETED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.flashcardService.getCardsBySource(activeDoc.id as string).subscribe({
                next: (cardRes) => {
                  if (cardRes.success && cardRes.data) {
                    activeDoc.content.flashcards = cardRes.data?.map((c) => ({
                      topic: c.front,
                      explanation: c.back,
                      id: c.id,
                      question: c.front,
                      answer: c.back
                    }));
                    this.saveState();
                    this.loadActiveDeckStats(activeDoc.id as string);
                  }
                  this.isFlashcardsLoading = false;
                },
                error: () => {
                  this.isFlashcardsLoading = false;
                }
              });
            } else if (job.status === 'FAILED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.isFlashcardsLoading = false;
            }
          },
          error: (err) => {
            console.error('Job polling failed for FLASHCARDS', err);
            isJobDone = true;
            this.clearJobPollingSubscription(key);
            this.isFlashcardsLoading = false;
          },
          complete: () => {
            this.clearJobPollingSubscription(key);
          }
        });
        if (!isJobDone) {
          this.jobPollingSubscriptions.set(key, sub);
        } else {
          sub.unsubscribe();
        }
      },
      error: (err) => {
        console.error('Failed to generate flashcards', err);
        this.isFlashcardsLoading = false;
      }
    });
  }

  loadQuizForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    const quizStatus = activeDoc.metadata?.quizStatus;
    if (quizStatus === 'PROCESSING' || quizStatus === 'PENDING') {
      this.isQuizLoading = true;
      return;
    }
    this.isQuizLoading = true;
    this.quizService.generateAdaptiveQuiz({
      concept: 'Core Concepts',
      sourceId: activeDoc.id as string,
      totalQuestions: 10
    }).subscribe({
      next: (res) => {
        if (res.success && res.data && res.data?.length > 0) {
          activeDoc.content.quizQuestions = res.data?.map((wrapper) => {
            const q = wrapper.question;
            return {
              id: q.id,
              question: q.text,
              options: q.options || [],
              correct: q.correctOptionIndex ?? 0,
              explanation: q.explanation || wrapper.explanation || ''
            } as any;
          });
          activeDoc.content.quizState = {
            currentQuestionIndex: 0,
            selectedAnswer: null,
            answerRevealed: false,
            quizFinished: false,
            quizScore: 0
          };
          this.saveState();
        }
        this.isQuizLoading = false;
      },
      error: (err) => {
        console.error('Failed to generate quiz', err);
        this.isQuizLoading = false;
      }
    });
  }

  loadMindMapForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    this.isMindMapLoading = true;
    this.mindmapService.getMindmapBySource(activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const mindMapData = res.data.mindMap;
          if (mindMapData) {
            this.ensureNodeIds(mindMapData);
            activeDoc.content.mindMap = mindMapData;
            if (this.currentView === 'mindmap') {
              setTimeout(() => this.updateLayout(), 50);
            }
          }
          this.saveState();
          this.isMindMapLoading = false;
        } else {
          const mindmapStatus = activeDoc.metadata?.mindmapStatus;
          if (mindmapStatus === 'PROCESSING' || mindmapStatus === 'PENDING') {
            this.isMindMapLoading = true;
            return;
          }
          this.generateMindMapForActiveDocument(activeDoc);
        }
      },
      error: () => {
        const mindmapStatus = activeDoc.metadata?.mindmapStatus;
        if (mindmapStatus === 'PROCESSING' || mindmapStatus === 'PENDING') {
          this.isMindMapLoading = true;
          return;
        }
        this.generateMindMapForActiveDocument(activeDoc);
      }
    });
  }

  generateMindMapForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    const key = activeDoc.id + '_MINDMAP';
    const existing = this.jobPollingSubscriptions.get(key);
    if (existing) {
      existing.unsubscribe();
      this.jobPollingSubscriptions.delete(key);
    }
    this.isMindMapLoading = true;
    this.mindmapService.generateMindmap('Main Concepts', activeDoc.id as string).subscribe({
      next: (res) => {
        let isJobDone = false;
        const sub = this.orchestrationService.pollJobStatus(activeDoc.id as string, 'MINDMAP').subscribe({
          next: (job) => {
            if (job.status === 'COMPLETED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.mindmapService.getMindmapBySource(activeDoc.id as string).subscribe({
                next: (mapRes) => {
                  if (mapRes.success && mapRes.data) {
                    const mindMapData = mapRes.data.mindMap;
                    if (mindMapData) {
                      this.ensureNodeIds(mindMapData);
                      activeDoc.content.mindMap = mindMapData;
                      if (this.currentView === 'mindmap') {
                        setTimeout(() => this.updateLayout(), 50);
                      }
                    }
                    this.saveState();
                  }
                  this.isMindMapLoading = false;
                },
                error: () => {
                  this.isMindMapLoading = false;
                }
              });
            } else if (job.status === 'FAILED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.isMindMapLoading = false;
            }
          },
          error: (err) => {
            console.error('Job polling failed for MINDMAP', err);
            isJobDone = true;
            this.clearJobPollingSubscription(key);
            this.isMindMapLoading = false;
          },
          complete: () => {
            this.clearJobPollingSubscription(key);
          }
        });
        if (!isJobDone) {
          this.jobPollingSubscriptions.set(key, sub);
        } else {
          sub.unsubscribe();
        }
      },
      error: (err) => {
        console.error('Failed to generate mind map', err);
        this.isMindMapLoading = false;
      }
    });
  }

  loadPodcastForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    this.isPodcastLoading = true;
    this.podcastService.getPodcastBySourceId(activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.mapPodcastToActiveDoc(activeDoc, res.data);
          this.isPodcastLoading = false;
        } else {
          const podcastStatus = activeDoc.metadata?.podcastStatus;
          if (podcastStatus === 'PROCESSING' || podcastStatus === 'PENDING') {
            this.isPodcastLoading = true;
            return;
          }
          this.generatePodcastForActiveDocument(activeDoc);
        }
      },
      error: (err) => {
        const podcastStatus = activeDoc.metadata?.podcastStatus;
        if (podcastStatus === 'PROCESSING' || podcastStatus === 'PENDING') {
          this.isPodcastLoading = true;
          return;
        }
        if (err.status === 404) {
          activeDoc.content.transcriptParagraphs = ['Podcast not generated yet. Generating now...'];
        }
        this.generatePodcastForActiveDocument(activeDoc);
      }
    });
  }

  generatePodcastForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    const key = activeDoc.id + '_PODCAST';
    const existing = this.jobPollingSubscriptions.get(key);
    if (existing) {
      existing.unsubscribe();
      this.jobPollingSubscriptions.delete(key);
    }
    this.isPodcastLoading = true;
    this.podcastService.generatePodcast('Audio Overview', activeDoc.id as string).subscribe({
      next: (res) => {
        let isJobDone = false;
        const sub = this.orchestrationService.pollJobStatus(activeDoc.id as string, 'PODCAST').subscribe({
          next: (job) => {
            if (job.status === 'COMPLETED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.podcastService.getPodcastBySourceId(activeDoc.id as string).subscribe({
                next: (podcastRes) => {
                  if (podcastRes.success && podcastRes.data) {
                    this.mapPodcastToActiveDoc(activeDoc, podcastRes.data);
                  }
                  this.isPodcastLoading = false;
                },
                error: () => {
                  this.isPodcastLoading = false;
                }
              });
            } else if (job.status === 'FAILED') {
              isJobDone = true;
              this.clearJobPollingSubscription(key);
              this.isPodcastLoading = false;
            }
          },
          error: (err) => {
            console.error('Job polling failed for PODCAST', err);
            isJobDone = true;
            this.clearJobPollingSubscription(key);
            this.isPodcastLoading = false;
          },
          complete: () => {
            this.clearJobPollingSubscription(key);
          }
        });
        if (!isJobDone) {
          this.jobPollingSubscriptions.set(key, sub);
        } else {
          sub.unsubscribe();
        }
      },
      error: (err) => {
        console.error('Failed to generate podcast', err);
        this.isPodcastLoading = false;
      }
    });
  }

  pollPodcastStatus(activeDoc: SourceFile, podcastId: string): void {
    const existing = this.podcastIntervals.get(podcastId);
    if (existing) {
      clearInterval(existing);
    }
    const intervalId = setInterval(() => {
      this.podcastService.getPodcastDetails(podcastId).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            const podcast = res.data;
            if (podcast.audioStatus === 'COMPLETED' || podcast.audioStatus === 'FAILED' || podcast.status === 'FAILED') {
              clearInterval(intervalId);
              this.podcastIntervals.delete(podcastId);
              this.mapPodcastToActiveDoc(activeDoc, podcast);
              if (podcast.audioStatus === 'FAILED' || podcast.status === 'FAILED') {
                console.error('Podcast audio or script generation failed on server');
              }
            }
          }
        },
        error: (err) => {
          console.error('Error polling podcast status', err);
          clearInterval(intervalId);
          this.podcastIntervals.delete(podcastId);
        }
      });
    }, 4000);
    this.podcastIntervals.set(podcastId, intervalId);
  }

  mapPodcastToActiveDoc(activeDoc: SourceFile, podcast: Podcast): void {
    if (podcast.script) {
      if (typeof podcast.script === 'string') {
        activeDoc.content.transcriptParagraphs = (podcast.script as string).split('\n\n');
        // Build podcastScript for speaker bubble view from plain text
        this.podcastScript = activeDoc.content.transcriptParagraphs.map((line, i) => {
          const isHost1 = i % 2 === 0;
          return { speaker: isHost1 ? 'Host1' : 'Host2', label: isHost1 ? 'MY' : 'AL', line };
        });
      } else if (Array.isArray(podcast.script)) {
        activeDoc.content.transcriptParagraphs = podcast.script.map((s: any) => {
          if (typeof s === 'string') return s;
          const speaker = s.speaker || s.Host || s.host || s.Speaker || '';
          const text = s.text || s.line || s.Text || s.Line || '';
          if (speaker && text) {
            return `${speaker}: ${text}`;
          } else if (text) {
            return text;
          } else if (speaker) {
            return speaker;
          } else {
            return Object.values(s).join(': ');
          }
        });
        // Build structured podcastScript for speaker bubble view
        this.podcastScript = podcast.script.map((s: any) => {
          if (typeof s === 'string') {
            return { speaker: 'Host1', label: 'MY', line: s };
          }
          const rawSpeaker = s.speaker || s.Host || s.host || s.Speaker || 'Host1';
          const isHost1 = rawSpeaker.includes('1') || rawSpeaker.toLowerCase().includes('maya') || rawSpeaker.toLowerCase().includes('host1');
          return {
            speaker: isHost1 ? 'Host1' : 'Host2',
            label: isHost1 ? 'MY' : 'AL',
            line: s.text || s.line || s.Text || s.Line || String(s)
          };
        });
      }
    } else {
      activeDoc.content.transcriptParagraphs = ['Podcast script is empty or still generating.'];
      this.podcastScript = [];
    }
    
    // Fetch actual audio blob and assign URL
    if (podcast.id) {
      if (podcast.audioStatus === 'COMPLETED') {
        this.podcastService.getPodcastAudio(podcast.id).subscribe({
          next: (blob) => {
            if (this.audioUrl) {
              URL.revokeObjectURL(this.audioUrl);
            }
            if (this.audioElement) {
              this.audioElement.pause();
              this.audioElement = null;
            }
            this.audioUrl = URL.createObjectURL(blob);
            this.isPlaying = false;
            this.audioProgress = 0;
            this.audioDuration = 0; // will be updated when loaded
          },
          error: (err) => console.error('Failed to load actual audio blob', err)
        });
      } else if (podcast.audioStatus === 'PROCESSING' || podcast.audioStatus === 'PENDING') {
        this.pollPodcastStatus(activeDoc, podcast.id);
      }
    }

    activeDoc.content.audioDuration = 247;
    this.saveState();
  }

  loadDiscussionsForActiveDocument(activeDoc: SourceFile): void {
    if (!activeDoc || !activeDoc.id || activeDoc.id.toString().startsWith('temp-')) return;
    this.discussionService.getAllThreads().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const threads = res.data?.filter(t => t.sourceId === activeDoc.id) || [];
          activeDoc.content.discussions = threads.map((thread) => {
            const replies: ThreadMessage[] = [];
            if (thread.content) {
              replies.push({
                authorName: thread.authorId === this.email ? 'You' : 'Student',
                authorInitials: (thread.authorId || 'S').charAt(0).toUpperCase(),
                authorColor: thread.authorId === this.email ? '#1a2f6e' : '#1a73e8',
                timeAgo: 'Just now',
                content: thread.content
              });
            }
            if (thread.threadMessages) {
              thread.threadMessages.forEach((reply) => {
                replies.push({
                  authorName: reply.authorId === this.email ? 'You' : 'Student',
                  authorInitials: (reply.authorId || 'S').charAt(0).toUpperCase(),
                  authorColor: reply.authorId === this.email ? '#1a2f6e' : '#8e24aa',
                  timeAgo: 'Just now',
                  content: reply.content
                });
              });
            }
            return {
              id: thread.id,
              category: 'Critical Analysis',
              title: thread.title,
              messages: replies.length,
              active: 1,
              lastActivity: 'just now',
              threadMessages: replies
            };
          });
          this.saveState();
        }
      },
      error: (err) => console.error('Failed to load discussions', err)
    });
  }



  isDragging: boolean = false;

  tabs = [
    { id: 'summary', label: 'Summary' },
    { id: 'audio', label: 'Audio Overview' },
    { id: 'flashcards', label: 'Flashcards' },
    { id: 'mindmap', label: 'Mind Map' },
    { id: 'quiz', label: 'Quiz' },
    { id: 'discussion', label: 'Discussion' },
  ];

  features = [
    { id: 'summary', title: 'Summary', desc: 'AI-generated document summary', icon: '📝' },
    { id: 'audio', title: 'Audio Overview', desc: 'Listen to AI-generated summaries', icon: '🎧' },
    { id: 'flashcards', title: 'Flashcards', desc: 'Interactive learning cards', icon: '🧠' },
    { id: 'mindmap', title: 'Mind Map', desc: 'Visual concept mapping', icon: '🗺️' },
    { id: 'quiz', title: 'Quiz', desc: 'Test your knowledge', icon: '❓' },
    { id: 'discussion', title: 'Discussion', desc: 'Group learning prompts', icon: '💬' },
  ];
  sourcesPanelCollapsed: boolean = false;

  programmes: { category: string, items: string[] }[] = [
    {
      category: 'Undergraduate (UG)',
      items: [
        'BBA', 'BBA (Aviation Management)', 'BBA (Business Analytics)', 'BBA (Fintech & Banking)', 'BBA (International Business)',
        'BBA (Branding & Advertising)', 'BBA (Artificial Intelligence & Data Science)', 'BBA (Enterprise Resource Management)',
        'BBA (Healthcare Management)', 'BCom', 'BCom (Business Analytics)', 'BCom (Business Process Management)',
        'BCom (International Business & Finance)', 'BCom (International Finance & Accounting)', 'BCom (Investment Banking)',
        'BCom (Logistics & Supply Chain Management)', 'BCom (Professional Accounting)', 'BCom (Strategic Finance)',
        'BCom (Blockchain & Fintech)', 'BCom (Enterprise Resource Management)', 'BCom (BFSI)', 'BSc Applied Economics',
        'BCA', 'BCA (Analytics)', 'BCA (Cloud Computing)', 'BCA (Cyber Security)', 'BCA (Internet of Things)',
        'BSc (AI & Machine Learning)', 'BSc (Data Science)', 'BSc (Blockchain Technology)', 'BSc (Quantum Computing)',
        'BSc (Animation & Game Design)', 'BSc (Computer Science & Electronics)', 'BSc (Computer Science & Mathematics)',
        'BSc (Computer Science & Physics)', 'BSc (Computer Science & Statistics)', 'BSc (Physics & Mathematics)',
        'BSc (Forensic Science)', 'BSc (Biotechnology & Genetics)', 'BSc (Biotechnology & Biochemistry)', 'BSc (Biotechnology & Botany)',
        'BSc (Microbiology & Genetics)', 'BSc (Forensic Science & Biotech)', 'BSc (Forensic Science & Biochemistry)',
        'BSc (Forensic Science & Criminology)', 'BSc (Forensic Science & Computer Science)', 'BPA (Physician Associate)',
        'BA (English Literature)', 'BA (Media and Communication)', 'BA (History & Political Science)',
        'BA (Political Science & Sociology)', 'BA (Psychology & English Literature)', 'BSc (Psychology)',
        'BSc (Visual Communication)', 'BA LL.B.', 'BCom LL.B.', 'BBA LL.B.'
      ]
    },
    {
      category: 'Postgraduate (PG)',
      items: [
        'MBA (General)', 'MBA (Fintech)', 'MBA (International Business)', 'Executive MBA',
        'MCom (Financial Analysis)', 'MCom (International Taxation & Applied Finance)', 'MSc Economics', 'MCA',
        'MSc (Data Science)', 'MSc (Cyber Security)', 'MSc (Physics)', 'MSc (Applied Statistics & Data Analytics)',
        'MSc (Biochemistry)', 'MSc (Biotechnology)', 'MSc (Microbiology)', 'MSc (Forensic Science)', 'MSc (Bioinformatics)',
        'MSc (Digital Forensics & Information Security)', 'MA (English Literature)', 'MA (Media and Communication)',
        'MA (Public Policy & International Relations)', 'MSc (Psychology)', 'MSc (Clinical Psychology)',
        'MSc (Counselling Psychology)', 'MSc (Human Resource Development Psychology)', 'MSW',
        'PG Diploma in Business Administration', 'PG Diploma in Banking & Finance', 'PG Diploma in Data Analytics'
      ]
    }
  ];

  courses: { category: string, items: string[] }[] = [
    {
      category: 'Undergraduate (UG)',
      items: [
        'Software Engineering', 'Computational Mathematics', 'Python Programming', 'Full Stack Development',
        'Data Structures and Algorithms', 'Relational Database Management System (RDBMS)', 'Operating System and Linux Administration'
      ]
    },
    {
      category: 'Postgraduate (PG)',
      items: [
        'Cryptography and Cyber Security', 'Cloud Computing Fundamentals', 'Artificial Intelligence Fundamentals'
      ]
    }
  ];

  selectedProgramme: string = '';
  selectedCourse: string = '';
  selectedUnit: string = '';
  openDropdown: 'programmes' | 'courses' | 'units' | null = null;

  units: string[] = [
    'Unit 1: Requirements Engineering',
    'Unit 2: Linear Diophantine Equations',
    'Unit 3: Control Structures and Functions',
    'Unit 4: Symmetric and Asymmetric Cryptography',
    'Unit 5: Memory Management and Virtual Memory'
  ];

  toggleDropdown(type: 'programmes' | 'courses' | 'units'): void {
    if (type === 'courses' && !this.selectedProgramme) {
      alert('Please select a programme first.');
      return;
    }
    if (type === 'units' && !this.selectedCourse) {
      alert('Please select a course first.');
      return;
    }
    if (this.openDropdown === type) {
      this.openDropdown = null;
    } else {
      this.openDropdown = type;
    }
  }

  selectProgramme(prog: string): void {
    this.selectedProgramme = prog;
    this.openDropdown = null;
  }

  courseUrls: Record<string, string> = {
    'Software Engineering': 'https://en.wikipedia.org/wiki/Software_engineering',
    'Computational Mathematics': 'https://en.wikipedia.org/wiki/Computational_mathematics',
    'Python Programming': 'https://en.wikipedia.org/wiki/Python_(programming_language)',
    'Full Stack Development': 'https://en.wikipedia.org/wiki/Web_development',
    'Data Structures and Algorithms': 'https://en.wikipedia.org/wiki/Data_structure',
    'Relational Database Management System (RDBMS)': 'https://en.wikipedia.org/wiki/Relational_database',
    'Operating System and Linux Administration': 'https://en.wikipedia.org/wiki/Operating_system',
    'Cryptography and Cyber Security': 'https://en.wikipedia.org/wiki/Cryptography',
    'Cloud Computing Fundamentals': 'https://en.wikipedia.org/wiki/Cloud_computing',
    'Artificial Intelligence Fundamentals': 'https://en.wikipedia.org/wiki/Artificial_intelligence'
  };

  unitUrls: Record<string, string> = {
    'Unit 1: Requirements Engineering': 'https://en.wikipedia.org/wiki/Requirements_engineering',
    'Unit 2: Linear Diophantine Equations': 'https://en.wikipedia.org/wiki/Diophantine_equation',
    'Unit 3: Control Structures and Functions': 'https://en.wikipedia.org/wiki/Control_flow',
    'Unit 4: Symmetric and Asymmetric Cryptography': 'https://en.wikipedia.org/wiki/Cryptography',
    'Unit 5: Memory Management and Virtual Memory': 'https://en.wikipedia.org/wiki/Memory_management'
  };

  handleUrlUpload(name: string, url: string): void {
    const tempId = 'temp-' + Date.now();
    const newDoc: SourceFile = {
      id: tempId,
      name: name,
      selected: true,
      status: 'In progress',
      progress: 10,
      score: null,
      content: this.generateDefaultContent(),
      progressMessage: 'Extracting text...'
    };
    this.uploadedSources.push(newDoc);
    this.uploadedFileName = name;
    this.saveState();

    this.uploadService.uploadUrl(url).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const backendSource = res.data;
          const docIndex = this.uploadedSources.findIndex(s => s.id === tempId);
          if (docIndex !== -1) {
            this.uploadedSources[docIndex].id = backendSource.id;
            this.uploadedSources[docIndex].status = 'In progress';
            this.uploadedSources[docIndex].progress = 50;
            this.uploadedSources[docIndex].progressMessage = 'Extracting text...';
            this.uploadedSources[docIndex].metadata = backendSource.metadata;
            this.saveState();
            
            this.pollSourceStatus(backendSource.id);
          }
        }
      },
      error: (err) => {
        this.uploadedSources = this.uploadedSources.filter(s => s.id !== tempId);
        if (this.uploadedFileName === name) {
          this.uploadedFileName = '';
          this.currentView = 'home';
        }
        this.saveState();
        alert(err.error?.error || `Failed to scrape url for: ${name}`);
      }
    });
  }

  handleFileUpload(file: File): void {
    const tempId = 'temp-' + Date.now();
    const newDoc: SourceFile = {
      id: tempId,
      name: file.name,
      selected: true,
      status: 'In progress',
      progress: 10,
      score: null,
      content: this.generateDefaultContent(),
      progressMessage: 'Extracting text...'
    };
    this.uploadedSources.push(newDoc);
    this.uploadedFileName = file.name;
    this.saveState();

    this.uploadService.uploadFile(file).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const backendSource = res.data;
          const docIndex = this.uploadedSources.findIndex(s => s.id === tempId);
          if (docIndex !== -1) {
            this.uploadedSources[docIndex].id = backendSource.id;
            this.uploadedSources[docIndex].status = 'In progress';
            this.uploadedSources[docIndex].progress = 50;
            this.uploadedSources[docIndex].progressMessage = 'Extracting text...';
            this.uploadedSources[docIndex].metadata = backendSource.metadata;
            this.saveState();
            
            this.pollSourceStatus(backendSource.id);
          }
        }
      },
      error: (err) => {
        this.uploadedSources = this.uploadedSources.filter(s => s.id !== tempId);
        if (this.uploadedFileName === file.name) {
          this.uploadedFileName = '';
          this.currentView = 'home';
        }
        this.saveState();
        alert(err.error?.error || `Failed to upload file: ${file.name}`);
      }
    });
  }

  selectCourse(course: string): void {
    this.selectedCourse = course;
    this.openDropdown = null;
    const docName = course + ' (Course Material)';
    const exists = this.uploadedSources.some((s) => s.name === docName);
    if (!exists) {
      this.currentView = 'summary';
      const url = this.courseUrls[course] || 'https://en.wikipedia.org/wiki/Software_engineering';
      this.handleUrlUpload(docName, url);
    } else {
      this.uploadedFileName = docName;
      this.currentView = 'summary';
    }
  }

  selectUnit(unit: string): void {
    this.selectedUnit = unit;
    this.openDropdown = null;
    const docName = unit + ' (Material)';
    const exists = this.uploadedSources.some((s) => s.name === docName);
    if (!exists) {
      this.currentView = 'summary';
      const url = this.unitUrls[unit] || 'https://en.wikipedia.org/wiki/Software_engineering';
      this.handleUrlUpload(docName, url);
    } else {
      this.uploadedFileName = docName;
      this.currentView = 'summary';
    }
  }
  private pollingSubscriptions = new Map<string, Subscription>();
  private jobPollingSubscriptions = new Map<string, Subscription>();
  private podcastIntervals = new Map<string, any>();

  clearPollingSubscription(sourceId: string): void {
    const sub = this.pollingSubscriptions.get(sourceId);
    if (sub) {
      sub.unsubscribe();
      this.pollingSubscriptions.delete(sourceId);
    }
  }

  clearJobPollingSubscription(key: string): void {
    const sub = this.jobPollingSubscriptions.get(key);
    if (sub) {
      sub.unsubscribe();
      this.jobPollingSubscriptions.delete(key);
    }
  }

  uploadedSources: SourceFile[] = [];

  sourcesSearchQuery: string = '';

  get activeDocument(): SourceFile | undefined {
    return this.selectedDocumentStateService.getActiveDocument() || undefined;
  }

  // Document Content Getters
  get summaryOverview(): string { return this.activeDocument?.content.summaryOverview || ''; }
  get summaryKeyPoints(): string[] { return this.activeDocument?.content.summaryKeyPoints || []; }
  get summaryTakeaways(): string[] { return this.activeDocument?.content.summaryTakeaways || []; }
  get transcriptParagraphs(): string[] { return this.activeDocument?.content.transcriptParagraphs || []; }

  get flashcards(): Flashcard[] { return this.activeDocument?.content.flashcards || []; }
  get quizQuestions(): QuizQuestion[] { return this.activeDocument?.content.quizQuestions || []; }
  get discussions(): Discussion[] { return this.activeDocument?.content.discussions || []; }
  public get mindMapData(): MindMapNode | null { return this.activeDocument?.content.mindMap || null; }
  get chatMessages(): ChatMessage[] { return this.activeDocument?.content.chatMessages || []; }

  public toggleMindMapNode(node: MindMapNode, event: Event): void {
    event.stopPropagation();
    node.expanded = !node.expanded;
    this.saveState();
  }

  public resetMindMap(node: MindMapNode | undefined, isRoot: boolean = true): void {
    if (!node) return;
    node.expanded = isRoot;
    if (node.children) {
      node.children.forEach(child => this.resetMindMap(child, false));
    }
  }

  get currentCardIndex(): number { return this.activeDocument?.content?.flashcardState.currentCardIndex ?? 0; }
  set currentCardIndex(val: number) { if (this.activeDocument?.content) this.activeDocument.content.flashcardState.currentCardIndex = val; this.saveState(); }

  get cardFlipped(): boolean { return this.activeDocument?.content?.flashcardState.cardFlipped ?? false; }
  set cardFlipped(val: boolean) { if (this.activeDocument?.content) this.activeDocument.content.flashcardState.cardFlipped = val; this.saveState(); }

  get currentQuestionIndex(): number { return this.activeDocument?.content?.quizState.currentQuestionIndex ?? 0; }
  set currentQuestionIndex(val: number) { if (this.activeDocument?.content) this.activeDocument.content.quizState.currentQuestionIndex = val; this.saveState(); }

  get selectedAnswer(): number | null { return this.activeDocument?.content?.quizState.selectedAnswer ?? null; }
  set selectedAnswer(val: number | null) { if (this.activeDocument?.content) this.activeDocument.content.quizState.selectedAnswer = val; this.saveState(); }

  get answerRevealed(): boolean { return this.activeDocument?.content?.quizState.answerRevealed ?? false; }
  set answerRevealed(val: boolean) { if (this.activeDocument?.content) this.activeDocument.content.quizState.answerRevealed = val; this.saveState(); }

  get quizFinished(): boolean { return this.activeDocument?.content?.quizState.quizFinished ?? false; }
  set quizFinished(val: boolean) { if (this.activeDocument?.content) this.activeDocument.content.quizState.quizFinished = val; this.saveState(); }

  get quizScore(): number { return this.activeDocument?.content?.quizState.quizScore ?? 0; }
  set quizScore(val: number) { if (this.activeDocument?.content) this.activeDocument.content.quizState.quizScore = val; this.saveState(); }


  get filteredSources(): SourceFile[] {
    const q = this.sourcesSearchQuery.trim().toLowerCase();
    if (!q) return this.uploadedSources;
    return this.uploadedSources.filter((s) => s.name.toLowerCase().includes(q));
  }

  toggleSourcesPanel(): void {
    this.sourcesPanelCollapsed = !this.sourcesPanelCollapsed;
  }

  triggerSourceInput(): void {
    this.sourceInput?.nativeElement?.click();
  }

  chatInput: string = '';
  isGeneratingResponse: boolean = false;

  sendChatMessage(): void {
    if (!this.chatInput.trim() || !this.activeDocument) return;

    const userMessage = this.chatInput.trim();
    this.chatInput = '';

    this.activeDocument.content.chatMessages.push({ role: 'user', content: userMessage });
    this.saveState();

    const assistantMsgIndex = this.activeDocument.content.chatMessages.length;
    this.activeDocument.content.chatMessages.push({ role: 'assistant', content: '' });
    this.isGeneratingResponse = true;

    const sourceIds = [this.activeDocument.id as string];
    this.chatService.sendMessageStream(userMessage, undefined, sourceIds).subscribe({
      next: (chunk) => {
        if (this.activeDocument && this.activeDocument.content.chatMessages[assistantMsgIndex]) {
          this.activeDocument.content.chatMessages[assistantMsgIndex].content += chunk;
          this.saveState();
        }
      },
      error: (err) => {
        console.error('Streaming chat failed', err);
        if (this.activeDocument && this.activeDocument.content.chatMessages[assistantMsgIndex]) {
          this.activeDocument.content.chatMessages[assistantMsgIndex].content = 'Error generating response. Please try again.';
          this.saveState();
        }
        this.isGeneratingResponse = false;
      },
      complete: () => {
        this.isGeneratingResponse = false;
        this.saveState();
      }
    });
  }

  onSourceAdded(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      Array.from(input.files).forEach((file) => {
        const exists = this.uploadedSources.some((s) => s.name === file.name);
        if (!exists) {
          this.handleFileUpload(file);
        }
      });
      input.value = '';
    }
  }

  openDocumentFeatures(src: SourceFile): void {
    this.uploadedFileName = src.name;
    this.selectedDocumentStateService.setActiveDocument(src);
    this.currentView = 'summary';
    if (this.isPlaying) {
      this.isPlaying = false;
      clearInterval(this.audioInterval);
      this.audioProgress = 0;
    }
    if (src.status === 'Not started') {
      src.status = 'In progress';
      src.progress = 25;
      this.saveState();
    }
    this.loadFeatureForActiveDocument(src, this.currentView);
  }

  isPlaying: boolean = false;
  audioProgress: number = 0;
  audioDuration: number = 0;
  private audioInterval: any = null;
  private audioElement: HTMLAudioElement | null = null;
  private audioUrl: string | null = null;

  togglePlay(): void {
    if (!this.audioElement && this.audioUrl) {
      this.audioElement = new Audio(this.audioUrl);
      this.audioElement.addEventListener('timeupdate', () => {
        this.audioProgress = Math.floor(this.audioElement?.currentTime || 0);
      });
      this.audioElement.addEventListener('loadedmetadata', () => {
        this.audioDuration = Math.floor(this.audioElement?.duration || 0);
      });
      this.audioElement.addEventListener('ended', () => {
        this.isPlaying = false;
        this.audioProgress = 0;
      });
    }

    if (this.audioElement) {
      this.isPlaying = !this.isPlaying;
      if (this.isPlaying) {
        this.audioElement.play().catch(e => {
          console.error("Audio playback failed", e);
          this.isPlaying = false;
        });
      } else {
        this.audioElement.pause();
      }
    } else {
      // Mock fallback if no audio URL yet
      this.isPlaying = !this.isPlaying;
      if (this.isPlaying) {
        this.audioInterval = setInterval(() => {
          if (this.audioProgress < this.audioDuration) {
            this.audioProgress++;
          } else {
            this.isPlaying = false;
            clearInterval(this.audioInterval);
          }
        }, 1000);
      } else {
        clearInterval(this.audioInterval);
      }
    }
  }

  isPodcastMode: boolean = true;
  showAudioWarning: boolean = false;
  playbackSpeed: number = 1.0;
  isMiniPlayerVisible: boolean = false;
  // Structured transcript segments computed from existing podcast.script — no new API
  podcastScript: { speaker: string; label: string; line: string }[] = [];

  toggleAudioMode(): void {
    if (this.isGeneratingAudio) {
      this.showAudioWarning = true;
      setTimeout(() => {
        this.showAudioWarning = false;
      }, 3000);
      return;
    }
    this.isPodcastMode = !this.isPodcastMode;
  }

  setPlaybackSpeed(speed: number): void {
    this.playbackSpeed = speed;
    if (this.audioElement) {
      this.audioElement.playbackRate = speed;
    }
  }

  restoreFullPlayer(): void {
    this.isMiniPlayerVisible = false;
  }

  seekAudio(event: MouseEvent): void {
    const track = event.currentTarget as HTMLElement;
    const rect = track.getBoundingClientRect();
    const percentage = (event.clientX - rect.left) / rect.width;
    this.audioProgress = Math.round(percentage * this.audioDuration);
    if (this.audioElement) {
      this.audioElement.currentTime = this.audioProgress;
    }
  }
  formatTime(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }
  downloadAudio(): void {
    const activeDoc = this.activeDocument;
    if (!activeDoc) return;
    this.podcastService.getPodcastBySourceId(activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const podcastId = res.data?.id;
          this.podcastService.getPodcastAudio(podcastId).subscribe({
            next: (blob) => {
              const url = window.URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `${activeDoc.name}-audio-overview.wav`;
              a.click();
              window.URL.revokeObjectURL(url);
            },
            error: (err) => {
              console.error('Failed to download audio file', err);
              this.fallbackTextDownload(activeDoc);
            }
          });
        } else {
          this.fallbackTextDownload(activeDoc);
        }
      },
      error: () => {
        this.fallbackTextDownload(activeDoc);
      }
    });
  }

  fallbackTextDownload(activeDoc: SourceFile): void {
    const text = this.transcriptParagraphs.join('\n\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${activeDoc.name}-transcript.txt`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  isGeneratingAudio: boolean = false;

  generateAudio(): void {
    const activeDoc = this.activeDocument;
    if (!activeDoc || this.isGeneratingAudio) return;
    this.isGeneratingAudio = true;
    this.podcastService.generatePodcast('Audio Overview', activeDoc.id as string).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.pollPodcastStatus(activeDoc, res.data?.id);
        }
        this.isGeneratingAudio = false;
      },
      error: (err) => {
        console.error('Failed to regenerate podcast', err);
        this.isGeneratingAudio = false;
      }
    });
  }

  get filteredFlashcards(): Flashcard[] {
    const allCards = this.flashcards;
    if (!this.selectedTopicFilter) return allCards;
    return allCards.filter(c => c.topic === this.selectedTopicFilter);
  }

  get topicGroups(): { topic: string, count: number }[] {
    const cards = this.flashcards;
    if (!cards || cards.length === 0) return [];
    const groupsMap = new Map<string, number>();
    cards.forEach(c => {
      const t = c.topic || 'General';
      groupsMap.set(t, (groupsMap.get(t) || 0) + 1);
    });
    return Array.from(groupsMap.entries()).map(([topic, count]) => ({ topic, count }));
  }

  get studiedCardsCount(): number {
    if (!this.activeDeckStats) return 0;
    const total = this.activeDeckStats.totalCards || 0;
    const newCards = this.activeDeckStats.newCards || 0;
    return Math.max(0, total - newCards);
  }

  shuffleCards(): void {
    const activeDoc = this.activeDocument;
    if (!activeDoc || !activeDoc.content.flashcards || activeDoc.content.flashcards.length === 0) return;
    const array = [...activeDoc.content.flashcards];
    for (let i = array.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [array[i], array[j]] = [array[j], array[i]];
    }
    activeDoc.content.flashcards = array;
    this.currentCardIndex = 0;
    this.cardFlipped = false;
    this.saveState();
  }

  selectTopicFilter(topic: string): void {
    this.selectedTopicFilter = topic;
    this.currentCardIndex = 0;
    this.cardFlipped = false;
  }

  flipCard(): void {
    this.cardFlipped = !this.cardFlipped;
  }
  nextCard(): void {
    const activeDoc = this.activeDocument;
    const cards = this.filteredFlashcards;
    if (activeDoc && cards[this.currentCardIndex]) {
      const card = cards[this.currentCardIndex];
      if (card.id) {
        this.flashcardService.reviewCard(card.id, 4).subscribe({
          error: (err) => console.error('Failed to submit card review', err)
        });
      }
    }
    if (this.currentCardIndex < cards.length - 1) {
      this.currentCardIndex++;
      this.cardFlipped = false;
    }
  }
  prevCard(): void {
    const activeDoc = this.activeDocument;
    const cards = this.filteredFlashcards;
    if (activeDoc && cards[this.currentCardIndex]) {
      const card = cards[this.currentCardIndex];
      if (card.id) {
        this.flashcardService.reviewCard(card.id, 4).subscribe({
          error: (err) => console.error('Failed to submit card review', err)
        });
      }
    }
    if (this.currentCardIndex > 0) {
      this.currentCardIndex--;
      this.cardFlipped = false;
    }
  }
  zoomLevel: number = 100;

  zoomIn(): void {
    if (this.zoomLevel < 150) this.zoomLevel += 10;
  }

  zoomOut(): void {
    if (this.zoomLevel > 50) this.zoomLevel -= 10;
  }

  selectAnswer(index: number): void {
    if (this.answerRevealed) return;
    this.selectedAnswer = index;
    this.answerRevealed = true;
    const currentQuestion = this.quizQuestions[this.currentQuestionIndex] as any;
    if (index === currentQuestion.correct) {
      this.quizScore++;
    }

    if (currentQuestion.id) {
      const studentAnswer = String.fromCharCode(65 + index);
      this.quizService.submitAnswer({
        questionId: currentQuestion.id,
        studentAnswer: studentAnswer
      }).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            console.log('Answer submitted to backend:', res.data);
          }
        },
        error: (err) => console.error('Failed to submit answer to backend', err)
      });
    }
  }

  nextQuestion(): void {
    this.selectedAnswer = null;
    this.answerRevealed = false;
    if (this.currentQuestionIndex < this.quizQuestions.length - 1) {
      this.currentQuestionIndex++;
    } else {
      this.quizFinished = true;
      const activeDoc = this.uploadedSources.find(s => s.name === this.uploadedFileName);
      if (activeDoc) {
        const scorePct = Math.round((this.quizScore / this.quizQuestions.length) * 100);
        activeDoc.score = scorePct;
        activeDoc.status = 'Completed';
        activeDoc.progress = 100;
        this.saveState();
      }
    }
  }

  get isQuizSuccessful(): boolean {
    return this.quizScore >= this.quizQuestions.length * 0.7;
  }

  prevQuestion(): void {
    if (this.currentQuestionIndex > 0) {
      this.currentQuestionIndex--;
      this.selectedAnswer = null;
      this.answerRevealed = false;
    }
  }
  retryQuiz(): void {
    this.currentQuestionIndex = 0;
    this.selectedAnswer = null;
    this.answerRevealed = false;
    this.quizFinished = false;
    this.quizScore = 0;
  }

  showNewDiscussion: boolean = false;
  newDiscTitle: string = '';
  newDiscCategory: string = '';

  activeDiscussion: Discussion | null = null;
  newThreadMessage: string = '';
  newDiscMessage: string = '';

  addDiscussion(): void {
    if (this.newDiscTitle.trim() && this.activeDocument) {
      const title = this.newDiscTitle.trim();
      const content = this.newDiscMessage.trim();
      const sourceId = this.activeDocument.id as string;
      
      this.discussionService.createThread(title, content, sourceId).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.loadDiscussionsForActiveDocument(this.activeDocument!);
            this.newDiscTitle = '';
            this.newDiscCategory = 'Critical Analysis';
            this.newDiscMessage = '';
            this.showNewDiscussion = false;
          }
        },
        error: (err) => alert(err.error?.error || 'Failed to create discussion thread')
      });
    }
  }

  openDiscussion(disc: Discussion): void {
    if (!disc.threadMessages) {
      disc.threadMessages = [];
    }
    this.activeDiscussion = disc;
  }

  closeDiscussion(): void {
    this.activeDiscussion = null;
    this.newThreadMessage = '';
  }

  sendThreadMessage(): void {
    if (this.newThreadMessage.trim() && this.activeDiscussion) {
      const threadId = this.activeDiscussion.id as string;
      const content = this.newThreadMessage.trim();
      
      this.discussionService.createReply(threadId, content).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.loadDiscussionsForActiveDocument(this.activeDocument!);
            const updatedDisc = this.activeDocument?.content.discussions.find(d => d.id === threadId);
            if (updatedDisc) {
              this.activeDiscussion = updatedDisc;
            }
            this.newThreadMessage = '';
          }
        },
        error: (err) => alert(err.error?.error || 'Failed to reply to discussion thread')
      });
    }
  }

  triggerFileInput(): void {
    this.fileInput?.nativeElement?.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const exists = this.uploadedSources.some((s) => s.name === file.name);
      if (!exists) {
        this.currentView = 'summary';
        this.handleFileUpload(file);
      } else {
        this.uploadedFileName = file.name;
        this.currentView = 'summary';
        const doc = this.uploadedSources.find((s) => s.name === file.name);
        if (doc && doc.status === 'Completed') {
          this.loadFeatureForActiveDocument(doc, this.currentView);
        }
      }
    }
  }

  triggerThreadFileInput(): void {
    this.threadFileInput?.nativeElement?.click();
  }

  onThreadFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0] && this.activeDiscussion) {
      const file = input.files[0];
      if (!this.activeDiscussion.threadMessages) {
        this.activeDiscussion.threadMessages = [];
      }
      this.activeDiscussion.threadMessages.push({
        authorName: 'You',
        authorInitials: 'Y',
        authorColor: '#1a2f6e',
        timeAgo: 'Just now',
        content: `Shared a file: ${file.name}`
      });
      this.activeDiscussion.messages++;
      this.saveState();
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    const files = event.dataTransfer?.files;
    if (files && files[0]) {
      const file = files[0];
      const exists = this.uploadedSources.some((s) => s.name === file.name);
      if (!exists) {
        this.currentView = 'summary';
        this.handleFileUpload(file);
      } else {
        this.uploadedFileName = file.name;
        this.currentView = 'summary';
        const doc = this.uploadedSources.find((s) => s.name === file.name);
        if (doc && doc.status === 'Completed') {
          this.loadFeatureForActiveDocument(doc, this.currentView);
        }
      }
    }
  }

  navigateToFeature(id: string): void {
    if (!this.uploadedFileName) {
      this.triggerFileInput();
      return;
    }
    this.currentView = id;
    if (id !== 'discussion') {
      this.activeDiscussion = null;
    }
    const activeDoc = this.selectedDocumentStateService.getActiveDocument();
    if (activeDoc) {
      if (activeDoc.status === 'Not started') {
        activeDoc.status = 'In progress';
        activeDoc.progress = 25;
        this.saveState();
      }
      this.loadFeatureForActiveDocument(activeDoc, id);
    }
  }

  get totalDocuments(): number {
    return this.uploadedSources.length;
  }

  get completedDocuments(): number {
    return this.uploadedSources.filter(s => s.status === 'Completed').length;
  }

  get inProgressDocuments(): number {
    return this.uploadedSources.filter(s => s.status === 'In progress').length;
  }

  get notStartedDocuments(): number {
    return this.uploadedSources.filter(s => s.status === 'Not started').length;
  }

  get averageScore(): number {
    const scoredDocs = this.uploadedSources.filter(s => s.score !== null);
    if (scoredDocs.length === 0) return 0;
    const sum = scoredDocs.reduce((acc, s) => acc + (s.score || 0), 0);
    return Math.round(sum / scoredDocs.length);
  }

  sourceToDelete: SourceFile | null = null;

  confirmRemoveSource(src: SourceFile, event: Event): void {
    event.stopPropagation();
    this.sourceToDelete = src;
  }

  cancelDelete(): void {
    this.sourceToDelete = null;
  }

  proceedDelete(): void {
    if (!this.sourceToDelete) return;
    const src = this.sourceToDelete;
    
    if (typeof src.id === 'string' && !src.id.startsWith('temp-')) {
      this.uploadService.deleteSource(src.id).subscribe({
        next: () => {
          this.performLocalDelete(src);
        },
        error: (err) => alert(err.error?.error || `Failed to delete source: ${src.name}`)
      });
    } else {
      this.performLocalDelete(src);
    }
  }

  performLocalDelete(src: SourceFile): void {
    this.uploadedSources = this.uploadedSources.filter((s) => s.id !== src.id);
    if (this.uploadedFileName === src.name) {
      if (this.uploadedSources.length > 0) {
        this.uploadedFileName = this.uploadedSources[0].name;
        this.currentView = 'summary';
      } else {
        this.uploadedFileName = '';
        this.currentView = 'home';
      }
    }
    this.saveState();
    this.sourceToDelete = null;
  }

  navigateToDashboard(): void {
    if (this.currentView === 'mindmap' && this.mindMapData) {
      this.resetMindMap(this.mindMapData);
      this.saveState();
    }
    this.currentView = 'dashboard';
    this.showProfileMenu = false;
  }

  showMyDocumentsInProfile: boolean = false;

  toggleMyDocumentsInProfile(event: Event): void {
    event.stopPropagation();
    this.showMyDocumentsInProfile = !this.showMyDocumentsInProfile;
  }

  selectDocumentFromProfile(doc: SourceFile, event: Event): void {
    event.stopPropagation();
    this.uploadedFileName = doc.name;
    this.selectedDocumentStateService.setActiveDocument(doc);
    this.currentView = 'summary';
    this.showProfileMenu = false;
    this.showMyDocumentsInProfile = false;
    if (this.isPlaying) {
      this.isPlaying = false;
      clearInterval(this.audioInterval);
      this.audioProgress = 0;
    }
    if (doc.status === 'Not started') {
      doc.status = 'In progress';
      doc.progress = 25;
      this.saveState();
    }
    this.loadFeatureForActiveDocument(doc, this.currentView);
  }

  openMyDocuments(): void {
    this.showProfileMenu = false;
    this.sourcesPanelCollapsed = false;
  }

  switchTab(id: string): void {
    if (this.currentView === 'mindmap' && id !== 'mindmap' && this.mindMapData) {
      this.resetMindMap(this.mindMapData);
      this.saveState();
    }
    this.currentView = id;
    if (id !== 'discussion') {
      this.activeDiscussion = null;
    }
    if (id === 'flashcards') {
      this.cardFlipped = false;
    }
    if (id === 'mindmap') {
      setTimeout(() => this.updateLayout(), 50);
    }
    if (this.uploadedFileName && id !== 'dashboard' && id !== 'home') {
      const activeDoc = this.selectedDocumentStateService.getActiveDocument();
      if (activeDoc) {
        if (activeDoc.status === 'Not started') {
          activeDoc.status = 'In progress';
          activeDoc.progress = 25;
          this.saveState();
        }
        this.loadFeatureForActiveDocument(activeDoc, id);
      }
    }
  }

  goBack(): void {
    if (this.currentView === 'mindmap' && this.mindMapData) {
      this.resetMindMap(this.mindMapData);
      this.saveState();
    }
    this.currentView = 'home';
    if (this.isPlaying) {
      this.isPlaying = false;
      clearInterval(this.audioInterval);
      this.audioProgress = 0;
    }
  }

  ngOnDestroy(): void {
    if (this.audioInterval) {
      clearInterval(this.audioInterval);
    }
    if (this.nodeExplanationSubscription) {
      this.nodeExplanationSubscription.unsubscribe();
    }
    this.pollingSubscriptions.forEach(sub => sub.unsubscribe());
    this.pollingSubscriptions.clear();
    this.jobPollingSubscriptions.forEach(sub => sub.unsubscribe());
    this.jobPollingSubscriptions.clear();
    this.podcastIntervals.forEach(intervalId => clearInterval(intervalId));
    this.podcastIntervals.clear();
  }

  ensureNodeIds(node: MindMapNode, prefix: string = 'root'): void {
    if (!node.id) {
      node.id = prefix;
    }
    if (node.expanded === undefined) {
      node.expanded = true;
    }
    if (node.children) {
      node.children.forEach((child, index) => {
        this.ensureNodeIds(child, `${prefix}-${index}`);
      });
    }
  }

  // Track which node IDs existed before the last toggle
  private previousNodeIds = new Set<string>();
  private previousConnIds = new Set<string>();

  updateLayout(newlyExpandedIds?: string[]): void {
    const rawMap = this.mindMapData;
    if (!rawMap || rawMap.label === 'Generating Mind Map...') {
      this.visualNodes = [];
      this.visualConnections = [];
      return;
    }

    this.ensureNodeIds(rawMap);

    // Save old positions for smooth transition animation
    const oldPositions = new Map<string, { x: number, y: number }>();
    if (this.visualNodes) {
      this.visualNodes.forEach(n => oldPositions.set(n.id, { x: n.x, y: n.y }));
    }

    // ── LEFT-TO-RIGHT TREE LAYOUT ──────────────────────────────────────────
    // Root is anchored on the left. All children expand rightward only.
    const ROOT_X = 120;   // Left origin
    const ROOT_Y = 1000;  // Vertical centre of 2000px canvas

    const nodes: any[] = [];
    const connections: any[] = [];

    const rootOld = oldPositions.get(rawMap.id);
    const rootVisual = {
      id: rawMap.id,
      label: rawMap.label,
      level: 0,
      expanded: rawMap.expanded !== false,
      hasChildren: !!(rawMap.children && rawMap.children.length > 0),
      originalNode: rawMap,
      targetX: ROOT_X,
      targetY: ROOT_Y,
      x: rootOld ? rootOld.x : ROOT_X,
      y: rootOld ? rootOld.y : ROOT_Y,
      isNew: !rootOld
    };
    nodes.push(rootVisual);

    if (rootVisual.expanded && rawMap.children && rawMap.children.length > 0) {
      this.layoutRight(rawMap, ROOT_X, ROOT_Y, 1, nodes, connections, oldPositions, newlyExpandedIds);
    }

    this.visualNodes = nodes;
    this.visualConnections = connections;
    this.updateConnections();

    // Animate layout transitions
    this.animateOutward();

    // Update tracked IDs
    this.previousNodeIds = new Set(nodes.map(n => n.id));
    this.previousConnIds = new Set(connections.map(c => `${c.fromId}->${c.toId}`));
  }

  /** Left-to-right tree layout — all children always expand rightward. */
  layoutRight(
    parent: MindMapNode,
    parentX: number,
    parentY: number,
    level: number,
    nodesList: any[],
    connectionsList: any[],
    oldPositions: Map<string, { x: number, y: number }>,
    newlyExpandedIds?: string[]
  ): void {
    if (!parent.children || parent.children.length === 0) return;

    const DX = 400;  // Horizontal gap between levels

    // Recursive helper: total vertical span consumed by a subtree
    const getVerticalSpan = (node: MindMapNode): number => {
      if (!node.expanded || !node.children || node.children.length === 0) {
        return 110;  // Leaf node vertical slot
      }
      return node.children.reduce((acc, child) => acc + getVerticalSpan(child), 0);
    };

    const totalHeight = parent.children.reduce((acc, child) => acc + getVerticalSpan(child), 0);
    let currentY = parentY - totalHeight / 2;

    parent.children.forEach(child => {
      const nodeSpan = getVerticalSpan(child);
      const targetX = parentX + DX;
      const targetY = currentY + nodeSpan / 2;

      const oldPos = oldPositions.get(child.id);
      const isNew = !oldPos;  // Node didn't exist before this layout
      const connKey = `${parent.id}->${child.id}`;
      const isNewConn = !this.previousConnIds.has(connKey);

      const visualChild = {
        id: child.id,
        label: child.label,
        level: level,
        expanded: child.expanded !== false,
        hasChildren: !!(child.children && child.children.length > 0),
        originalNode: child,
        targetX: targetX,
        targetY: targetY,
        x: oldPos ? oldPos.x : parentX,  // Animate from parent position
        y: oldPos ? oldPos.y : parentY,
        isNew: isNew
      };
      nodesList.push(visualChild);

      connectionsList.push({
        fromId: parent.id,
        toId: child.id,
        fromX: parentX,
        fromY: parentY,
        toX: targetX,
        toY: targetY,
        d: '',
        isNew: isNewConn
      });

      if (visualChild.expanded && child.children && child.children.length > 0) {
        this.layoutRight(child, targetX, targetY, level + 1, nodesList, connectionsList, oldPositions, newlyExpandedIds);
      }

      currentY += nodeSpan;
    });
  }

  updateConnections(): void {
    const nodeMap = new Map<string, any>();
    this.visualNodes.forEach(n => nodeMap.set(n.id, n));

    this.visualConnections.forEach(conn => {
      const fromNode = nodeMap.get(conn.fromId);
      const toNode = nodeMap.get(conn.toId);
      if (fromNode && toNode) {
        const x1 = fromNode.x;
        const y1 = fromNode.y;
        const x2 = toNode.x;
        const y2 = toNode.y;

        const dx = x2 - x1;
        const cp1x = x1 + dx * 0.5;
        const cp1y = y1;
        const cp2x = x2 - dx * 0.5;
        const cp2y = y2;

        conn.d = `M ${x1} ${y1} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${x2} ${y2}`;
      }
    });
  }

  animateOutward() {
    let startTime: number | null = null;
    const duration = 300; // Snappy 300ms transition

    const animate = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const elapsed = timestamp - startTime;
      const progress = Math.min(elapsed / duration, 1);

      // Smooth ease out cubic
      const ease = (t: number) => 1 - Math.pow(1 - t, 3);
      const easedProgress = ease(progress);

      this.visualNodes.forEach(node => {
        const startX = node.startX !== undefined ? node.startX : node.x;
        const startY = node.startY !== undefined ? node.startY : node.y;
        
        node.x = startX + (node.targetX - startX) * easedProgress;
        node.y = startY + (node.targetY - startY) * easedProgress;
      });

      this.updateConnections();
      this.cdr.detectChanges();

      if (progress < 1) {
        requestAnimationFrame(animate);
      } else {
        this.visualNodes.forEach(node => {
          node.x = node.targetX;
          node.y = node.targetY;
          node.startX = node.x;
          node.startY = node.y;
        });
        this.updateConnections();
        this.cdr.detectChanges();
      }
    };

    // Store starting positions before animation
    this.visualNodes.forEach(node => {
      node.startX = node.x;
      node.startY = node.y;
    });

    requestAnimationFrame(animate);
  }

  onCanvasMouseDown(event: MouseEvent): void {
    if (event.button !== 0) return;
    const target = event.target as HTMLElement;
    if (
      target.classList.contains('mindmap-canvas-container') ||
      target.classList.contains('dot-grid-bg') ||
      target.tagName.toLowerCase() === 'svg' ||
      target.tagName.toLowerCase() === 'g' ||
      target.tagName.toLowerCase() === 'path'
    ) {
      this.isPanning = true;
      this.dragStartX = event.clientX - this.panX;
      this.dragStartY = event.clientY - this.panY;
      event.preventDefault();
    }
  }

  onCanvasMouseMove(event: MouseEvent): void {
    if (this.isPanning) {
      this.panX = event.clientX - this.dragStartX;
      this.panY = event.clientY - this.dragStartY;
    }
  }

  onCanvasMouseUp(event: MouseEvent): void {
    this.isPanning = false;
  }

  @HostListener('document:mouseup', ['$event'])
  onGlobalMouseUp(event: MouseEvent): void {
    this.isPanning = false;
  }

  onNodeClick(node: any, event: Event): void {
    event.stopPropagation();
    this.selectedMindMapNode = node;
    this.showNodePanel = true;
    
    // Zoom & Focus center on node
    this.focusNode(node);

    const orig = node.originalNode;
    if (orig.description && orig.keyPoints && orig.keyPoints.length > 0) {
      // Load details instantly from local schema
      let html = '';
      html += `<div class="explorer-section">
                 <div class="explorer-score-container">
                   <span class="explorer-score-label">AI Importance Ranking</span>
                   <span class="explorer-score-val">${orig.importance || 8}/10</span>
                 </div>
               </div>`;
               
      html += `<div class="explorer-section">
                 <h4 class="explorer-title">AI Explanation</h4>
                 <p class="explorer-desc">${orig.description}</p>
               </div>`;
               
      if (orig.keyPoints && orig.keyPoints.length > 0) {
        html += `<div class="explorer-section">
                   <h4 class="explorer-title">Key Points</h4>
                   <ul class="explorer-list">`;
        orig.keyPoints.forEach((kp: string) => {
          html += `<li>${kp}</li>`;
        });
        html += `   </ul>
                 </div>`;
      }
      
      if (orig.related && orig.related.length > 0) {
        html += `<div class="explorer-section">
                   <h4 class="explorer-title">Related Concepts</h4>
                   <div class="explorer-related-tags">`;
        orig.related.forEach((rel: string) => {
          html += `<span class="explorer-related-tag" onclick="window.angularComponentReference.searchAndFocus('${rel}')">${rel}</span>`;
        });
        html += `   </div>
                 </div>`;
      }
      this.nodeExplanationHtml = html;
      this.nodeExplanationLoading = false;
      this.cdr.detectChanges();
    } else {
      // Fallback: Stream explanation if missing
      this.nodeExplanationRawText = '';
      this.nodeExplanationHtml = '<div class="explanation-skeleton"><div class="skeleton-line"></div><div class="skeleton-line"></div><div class="skeleton-line"></div></div>';
      this.nodeExplanationLoading = true;

      if (this.nodeExplanationSubscription) {
        this.nodeExplanationSubscription.unsubscribe();
        this.nodeExplanationSubscription = null;
      }

      const activeDoc = this.activeDocument;
      if (!activeDoc) return;

      const prompt = `Based on the uploaded document, provide a short, structured study guide for the topic: "${node.label}".
Format the response using these exact markdown headers:
### AI Explanation
Provide a clear 2-paragraph overview explaining this concept.

### Key Concepts
- Bullet point 1
- Bullet point 2
- Bullet point 3

### Related Topics
- Topic A
- Topic B`;

      const sourceIds = [activeDoc.id as string];
      this.nodeExplanationSubscription = this.chatService.sendMessageStream(prompt, undefined, sourceIds).subscribe({
        next: (chunk) => {
          this.nodeExplanationRawText += chunk;
          this.nodeExplanationHtml = this.parseMarkdown(this.nodeExplanationRawText);
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Failed to stream explanation', err);
          this.nodeExplanationHtml = '<p class="explanation-error">Failed to load AI explanation. Please check your network connection.</p>';
          this.nodeExplanationLoading = false;
          this.cdr.detectChanges();
        },
        complete: () => {
          this.nodeExplanationLoading = false;
          this.cdr.detectChanges();
        }
      });
    }

    // Set globally exposed component reference for inline HTML onclick calls
    (window as any).angularComponentReference = this;
  }

  // Sidebar stats getters
  get totalNodesCount(): number {
    return this.countAllNodes(this.mindMapData);
  }

  get mainTopicsCount(): number {
    return this.countMainTopics(this.mindMapData);
  }

  get subTopicsCount(): number {
    return this.countSubTopics(this.mindMapData);
  }

  private countAllNodes(node: MindMapNode | null): number {
    if (!node) return 0;
    let count = 1;
    if (node.children) {
      node.children.forEach(child => {
        count += this.countAllNodes(child);
      });
    }
    return count;
  }

  private countMainTopics(node: MindMapNode | null): number {
    if (!node || !node.children) return 0;
    return node.children.length;
  }

  private countSubTopics(node: MindMapNode | null): number {
    if (!node) return 0;
    const total = this.countAllNodes(node);
    const main = this.countMainTopics(node);
    return Math.max(0, total - 1 - main);
  }

  selectSidebarNav(navType: string): void {
    this.activeSidebarNav = navType;
    if (navType === 'overview') {
      this.resetMapZoom();
    } else if (navType === 'all') {
      this.expandAll();
    } else if (navType === 'bookmarks') {
      this.collapseAll();
    }
  }

  resetMapZoom(): void {
    this.centerMap();
    if (this.mindMapData) {
      this.resetMindMap(this.mindMapData);
    }
    this.updateLayout();
  }

  focusNode(node: any): void {
    const container = document.querySelector('.mindmap-canvas-container');
    if (!container) return;

    const containerWidth = container.clientWidth || 800;
    const containerHeight = container.clientHeight || 500;

    this.zoomLevel = 110;

    // When side panel is open, shift focus left so the graph remains readable
    // (panel overlays on the right, so we use 38% of width instead of 50%)
    const targetCenterX = this.showNodePanel
      ? containerWidth * 0.32
      : containerWidth * 0.48;
    const targetCenterY = containerHeight * 0.5;

    this.panX = targetCenterX - node.x * (this.zoomLevel / 100);
    this.panY = targetCenterY - node.y * (this.zoomLevel / 100);
    this.saveState();
  }

  searchAndFocus(term: string): void {
    this.conceptSearchQuery = term;
    this.searchConcept();
  }

  searchConcept(): void {
    const query = this.conceptSearchQuery.trim().toLowerCase();
    if (!query) return;

    // Search case insensitive in visual nodes
    const match = this.visualNodes.find(node => 
      node.label.toLowerCase().includes(query) || 
      (node.originalNode.title && node.originalNode.title.toLowerCase().includes(query))
    );

    if (match) {
      this.selectedMindMapNode = match;
      this.showNodePanel = true;
      const dummyEvent = { stopPropagation: () => {} } as Event;
      this.onNodeClick(match, dummyEvent);
    }
  }

  onNodeMouseEnter(node: any, event: MouseEvent): void {
    this.hoveredMindMapNode = node;
    this.showTooltip = true;
    this.updateTooltipPosition(event);
  }

  onNodeMouseMove(event: MouseEvent): void {
    if (this.showTooltip) {
      this.updateTooltipPosition(event);
    }
  }

  onNodeMouseLeave(): void {
    this.showTooltip = false;
    this.hoveredMindMapNode = null;
  }

  private updateTooltipPosition(event: MouseEvent): void {
    const container = document.querySelector('.mindmap-canvas-container');
    if (container) {
      const rect = container.getBoundingClientRect();
      this.tooltipX = event.clientX - rect.left;
      this.tooltipY = event.clientY - rect.top - 55;
    }
  }

  get miniNodes(): any[] {
    return this.visualNodes.map(n => ({
      x: n.x * 0.05,
      y: n.y * 0.05,
      level: n.level,
      importance: n.originalNode.importance || 8
    }));
  }

  get miniViewport(): any {
    const container = document.querySelector('.mindmap-canvas-container');
    if (!container) return { x: 0, y: 0, w: 150, h: 100 };

    const w = container.clientWidth || 800;
    const h = container.clientHeight || 500;
    const z = this.zoomLevel / 100;

    const viewX = -this.panX / z;
    const viewY = -this.panY / z;
    const viewW = w / z;
    const viewH = h / z;

    return {
      x: Math.max(0, Math.min(150, viewX * 0.05)),
      y: Math.max(0, Math.min(100, viewY * 0.05)),
      w: Math.max(10, Math.min(150, viewW * 0.05)),
      h: Math.max(10, Math.min(100, viewH * 0.05))
    };
  }

  onMinimapClick(event: MouseEvent): void {
    const minimapBox = event.currentTarget as HTMLElement;
    const rect = minimapBox.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const clickY = event.clientY - rect.top;

    const canvasX = clickX / 0.05;
    const canvasY = clickY / 0.05;

    const container = document.querySelector('.mindmap-canvas-container');
    if (container) {
      const w = container.clientWidth || 800;
      const h = container.clientHeight || 500;
      const z = this.zoomLevel / 100;
      this.panX = w / 2 - canvasX * z;
      this.panY = h / 2 - canvasY * z;
      this.saveState();
    }
  }

  get keyThemes(): string[] {
    const rawMap = this.mindMapData;
    if (!rawMap || !rawMap.children) return [];
    return rawMap.children.map(child => child.title || child.label);
  }

  toggleNodeExpansion(node: any, event: Event): void {
    event.stopPropagation();
    const expanding = !node.originalNode.expanded;
    node.originalNode.expanded = expanding;

    if (expanding) {
      // Pulse the parent node as tactile feedback
      node.pulse = true;
      setTimeout(() => { node.pulse = false; }, 600);

      // Expand: update layout first, then stagger-animate new nodes
      this.updateLayout([node.id]);
      this.staggerEntranceAnimation();
    } else {
      // Collapse: animate exit, then update layout after delay
      this.animateCollapseExit(node);
    }
    this.saveState();
  }

  /** Stagger fade+slide entrance for any node marked isNew. */
  staggerEntranceAnimation(): void {
    const newNodes = this.visualNodes.filter(n => n.isNew);
    const newConns = this.visualConnections.filter(c => c.isNew);

    // Animate connectors first
    newConns.forEach((conn, i) => {
      setTimeout(() => {
        conn.entering = true;
        this.cdr.detectChanges();
        setTimeout(() => { conn.entering = false; }, 500);
      }, i * 60);
    });

    // Animate nodes with stagger
    newNodes.forEach((node, i) => {
      setTimeout(() => {
        node.entering = true;
        this.cdr.detectChanges();
        setTimeout(() => { node.entering = false; }, 400);
      }, i * 70);
    });
  }

  /** Animate children out before removing them from layout. */
  animateCollapseExit(parentNode: any): void {
    // Mark all currently-visible children of this node as exiting
    const exitingIds = new Set<string>();
    const collectChildren = (nodeId: string) => {
      this.visualNodes.forEach(n => {
        const conn = this.visualConnections.find(c => c.fromId === nodeId && c.toId === n.id);
        if (conn) {
          exitingIds.add(n.id);
          collectChildren(n.id);
        }
      });
    };
    collectChildren(parentNode.id);

    // Apply exiting class
    this.visualNodes.forEach(n => {
      if (exitingIds.has(n.id)) n.exiting = true;
    });
    this.cdr.detectChanges();

    // After animation, do the real layout update
    setTimeout(() => {
      this.visualNodes.forEach(n => { n.exiting = false; });
      this.updateLayout();
    }, 270);
  }

  fitView(): void {
    if (!this.visualNodes || this.visualNodes.length === 0) return;

    let minX = Infinity, maxX = -Infinity;
    let minY = Infinity, maxY = -Infinity;

    this.visualNodes.forEach(node => {
      minX = Math.min(minX, node.targetX);
      maxX = Math.max(maxX, node.targetX);
      minY = Math.min(minY, node.targetY);
      maxY = Math.max(maxY, node.targetY);
    });

    const padding = 150;
    const width = (maxX - minX) + padding * 2;
    const height = (maxY - minY) + padding * 2;

    const container = document.querySelector('.mindmap-canvas-container');
    if (!container) return;

    const containerWidth = container.clientWidth || 800;
    const containerHeight = container.clientHeight || 500;

    const zoomW = containerWidth / width;
    const zoomH = containerHeight / height;
    const idealZoom = Math.min(zoomW, zoomH) * 100;
    this.zoomLevel = Math.max(40, Math.min(idealZoom, 130));

    const centerX = (minX + maxX) / 2;
    const centerY = (minY + maxY) / 2;

    this.panX = containerWidth / 2 - centerX * (this.zoomLevel / 100);
    this.panY = containerHeight / 2 - centerY * (this.zoomLevel / 100);
    this.saveState();
  }

  centerMap(): void {
    // Auto-fit the left-to-right tree into view
    this.fitView();
  }

  toggleFullscreen(): void {
    const container = document.querySelector('.mindmap-canvas-container');
    if (!container) return;
    if (!document.fullscreenElement) {
      container.requestFullscreen().catch(err => {
        console.error('Error enabling fullscreen', err);
      });
      this.isFullscreen = true;
    } else {
      document.exitFullscreen();
      this.isFullscreen = false;
    }
  }

  exportPNG(): void {
    let svgContent = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 3000 2000" width="3000" height="2000" style="background:#F8FAFF;">`;
    
    // Connections
    this.visualConnections.forEach(conn => {
      svgContent += `<path d="${conn.d}" fill="none" stroke="#4C74C0" stroke-width="3.5" opacity="0.75" />`;
    });
    
    // Nodes
    this.visualNodes.forEach(node => {
      const isRoot = node.level === 0;
      const bg = isRoot ? 'url(#rootGrad)' : '#FFFFFF';
      const color = isRoot ? '#FFFFFF' : '#1D2B53';
      const stroke = isRoot ? 'none' : '#3B66B5';
      const radius = '25';
      const width = isRoot ? '240' : '190';
      const height = isRoot ? '60' : '48';
      const offsetX = isRoot ? -120 : -95;
      const offsetY = isRoot ? -30 : -24;
      const label = node.label || 'Concept';

      svgContent += `
        <defs>
          <linearGradient id="rootGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#4C74C0" />
            <stop offset="100%" stop-color="#3B66B5" />
          </linearGradient>
        </defs>
        <g transform="translate(${node.x}, ${node.y})">
          <rect x="${offsetX}" y="${offsetY}" width="${width}" height="${height}" rx="${radius}" fill="${bg}" stroke="${stroke}" stroke-width="2" />
          <text x="0" y="5" font-family="'Outfit', 'Inter', sans-serif" font-size="13px" fill="${color}" text-anchor="middle" font-weight="bold">${label}</text>
        </g>`;
    });
    svgContent += `</svg>`;

    const img = new Image();
    const blob = new Blob([svgContent], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);

    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 3000;
      canvas.height = 2000;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(img, 0, 0);
        canvas.toBlob((pngBlob) => {
          if (pngBlob) {
            const pngUrl = URL.createObjectURL(pngBlob);
            const a = document.createElement('a');
            a.href = pngUrl;
            a.download = `${this.activeDocument?.name || 'mindmap'}.png`;
            a.click();
            URL.revokeObjectURL(pngUrl);
          }
        }, 'image/png');
      }
      URL.revokeObjectURL(url);
    };
    img.src = url;
  }

  exportSVG(): void {
    let svgContent = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 3000 2000" width="3000" height="2000" style="background:#F8FAFF;">`;
    
    // Definitions
    svgContent += `
      <defs>
        <linearGradient id="rootGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#4C74C0" />
          <stop offset="100%" stop-color="#3B66B5" />
        </linearGradient>
      </defs>`;

    // Connections
    this.visualConnections.forEach(conn => {
      svgContent += `<path d="${conn.d}" fill="none" stroke="#4C74C0" stroke-width="3.5" opacity="0.75" />`;
    });

    // Nodes
    this.visualNodes.forEach(node => {
      const isRoot = node.level === 0;
      const bg = isRoot ? 'url(#rootGrad)' : '#FFFFFF';
      const color = isRoot ? '#FFFFFF' : '#1D2B53';
      const stroke = isRoot ? 'none' : '#3B66B5';
      const radius = '25';
      const width = isRoot ? '240' : '190';
      const height = isRoot ? '60' : '48';
      const offsetX = isRoot ? -120 : -95;
      const offsetY = isRoot ? -30 : -24;
      const label = node.label || 'Concept';

      svgContent += `
        <g transform="translate(${node.x}, ${node.y})">
          <rect x="${offsetX}" y="${offsetY}" width="${width}" height="${height}" rx="${radius}" fill="${bg}" stroke="${stroke}" stroke-width="2" />
          <text x="0" y="5" font-family="'Outfit', 'Inter', sans-serif" font-size="13px" fill="${color}" text-anchor="middle" font-weight="bold">${label}</text>
        </g>`;
    });
    svgContent += `</svg>`;

    const blob = new Blob([svgContent], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.activeDocument?.name || 'mindmap'}.svg`;
    a.click();
    URL.revokeObjectURL(url);
  }

  exportPDF(): void {
    window.print();
  }

  parseMarkdown(md: string): string {
    if (!md) return '';
    const lines = md.split('\n');
    let inList = false;
    const resultLines: string[] = [];

    for (let line of lines) {
      let trimmed = line.trim();
      if (trimmed.startsWith('### ')) {
        if (inList) { resultLines.push('</ul>'); inList = false; }
        resultLines.push(`<h4>${trimmed.substring(4)}</h4>`);
      } else if (trimmed.startsWith('## ')) {
        if (inList) { resultLines.push('</ul>'); inList = false; }
        resultLines.push(`<h3>${trimmed.substring(3)}</h3>`);
      } else if (trimmed.startsWith('# ')) {
        if (inList) { resultLines.push('</ul>'); inList = false; }
        resultLines.push(`<h2>${trimmed.substring(2)}</h2>`);
      } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        if (!inList) { resultLines.push('<ul class="explanation-list">'); inList = true; }
        let content = trimmed.substring(2).replace(/\*\*/g, '');
        resultLines.push(`<li>${content}</li>`);
      } else if (trimmed === '') {
        if (inList) { resultLines.push('</ul>'); inList = false; }
      } else {
        if (inList) { resultLines.push('</ul>'); inList = false; }
        let content = line.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        resultLines.push(`<p>${content}</p>`);
      }
    }
    if (inList) { resultLines.push('</ul>'); }
    return resultLines.join('\n');
  }

  collapseAll(): void {
    if (!this.mindMapData) return;
    if (this.mindMapData.children) {
      this.mindMapData.children.forEach(child => {
        this.collapseNodeRecursively(child);
      });
    }
    this.updateLayout();
  }

  expandAll(): void {
    if (!this.mindMapData) return;
    this.expandNodeRecursively(this.mindMapData);
    this.updateLayout();
  }

  private collapseNodeRecursively(node: MindMapNode): void {
    node.expanded = false;
    if (node.children) {
      node.children.forEach(child => this.collapseNodeRecursively(child));
    }
  }

  private expandNodeRecursively(node: MindMapNode): void {
    node.expanded = true;
    if (node.children) {
      node.children.forEach(child => this.expandNodeRecursively(child));
    }
  }
}
