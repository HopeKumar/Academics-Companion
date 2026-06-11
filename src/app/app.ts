import { Component, ViewChild, ElementRef, OnDestroy, OnInit, HostListener } from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';

interface Flashcard {
  question: string;
  answer: string;
}

export interface MindMapNode {
  id: string;
  label: string;
  expanded?: boolean;
  children?: MindMapNode[];
}

interface QuizQuestion {
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

interface Discussion {
  id: number;
  category: string;
  title: string;
  messages: number;
  active: number;
  lastActivity: string;
  threadMessages?: ThreadMessage[];
}

interface DocumentContent {
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

interface SourceFile {
  id: number;
  name: string;
  selected: boolean;
  status: 'Completed' | 'In progress' | 'Not started';
  progress: number;
  score: number | null;
  content: DocumentContent;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
})
export class AppComponent implements OnInit, OnDestroy {
  ngOnInit() {
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
          // Force update summary overview for the demo
          s.content.summaryOverview = this.generateDefaultContent().summaryOverview;
          // Force update flashcards to use latest statements
          s.content.flashcards = this.generateDefaultContent().flashcards;
          return s;
        });
      } catch (e) {
        // ignore
      }
    }
  }

  saveState() {
    localStorage.setItem('uploadedSources', JSON.stringify(this.uploadedSources));
  }

  generateDefaultContent(): DocumentContent {
    return {
      summaryOverview: 'A recent breakthrough in astronomy, made possible by observations from the James Webb Space Telescope, has provided new evidence that some supermassive black holes may have formed before the galaxies that now host them. Traditionally, scientists believed that galaxies formed first, accumulating stars and gas over time, while the black holes at their centers gradually grew by consuming surrounding matter. However, researchers studying extremely distant galaxies from the early universe discovered a surprisingly massive black hole residing within a relatively small and young galaxy. Because the light from this system has traveled for more than 13 billion years to reach Earth, astronomers are effectively looking back in time to a period shortly after the Big Bang. The observations suggest that the black hole may have reached tens of millions of times the mass of the Sun before the galaxy itself had fully developed. This finding challenges existing theories of galaxy evolution and raises important questions about how the earliest structures in the universe were formed. Scientists now believe that some black holes may have originated from unusually large “seed” black holes, allowing them to grow rapidly and influence the formation of their surrounding galaxies. The discovery offers valuable insight into one of the greatest mysteries in modern astrophysics: how supermassive black holes appeared so quickly in the young universe. Future observations and studies will help determine whether this phenomenon was common or rare, potentially leading to a major revision of current models of cosmic evolution.',
      summaryKeyPoints: [
        'Important concepts are summarized clearly',
        'Key definitions are highlighted',
        'Complex topics are simplified',
        'Useful for quick revision before exams',
      ],
      summaryTakeaways: [
        'Understand the core ideas first',
        'Revise important topics regularly',
        'Focus on highlighted key points',
        'Practice using flashcards and quizzes',
      ],
      transcriptParagraphs: [
        "Welcome to this comprehensive overview of the document you've uploaded.",
        "In this audio summary, we'll explore the key themes, important concepts, and critical insights from your material.",
        'The document begins by establishing the foundational concepts.',
        'The relationship between theory and practice is examined in detail.',
        'The material explores the historical context that shaped these developments.',
        'The document transitions to a detailed analysis of methodology.',
        'The conclusion emphasizes the importance of integrating qualitative and quantitative perspectives.',
      ],
      audioDuration: 247,
      flashcards: [
        { question: 'The primary goal of the methodology is to provide a systematic approach for analyzing complex data patterns.', answer: 'To provide a systematic approach for analyzing complex data patterns.' },
        { question: 'The foundational concepts established first are core theoretical frameworks and definitions.', answer: 'Core theoretical frameworks and definitions.' },
        { question: 'The document connects theory to practice by translating abstract concepts into real-world applications.', answer: 'By translating abstract concepts into real-world applications.' },
      ],
      quizQuestions: [
        {
          question: 'What is the primary goal of the methodology?',
          options: ['Reduce processing time', 'Analyze complex data patterns', 'Replace systems', 'Simplify collection'],
          correct: 1,
          explanation: 'The methodology is designed to analyze complex data patterns and derive meaningful insights from the data.'
        },
        {
          question: 'How are theory and practice connected?',
          options: ['Completely separate', 'Theory only', 'Real-world application', 'No connection'],
          correct: 2,
          explanation: 'Theory provides the concepts and principles, while practice applies them in real-world situations. Therefore theory and practice are connected through real-world application.'
        },
        {
          question: 'Which of the following is considered a foundational framework?',
          options: ['Linear progression model', 'Systematic integration', 'Abstract theory representation', 'Empirical synthesis'],
          correct: 0,
          explanation: 'The linear progression model is highlighted as the primary foundational framework in the initial chapters.'
        },
        {
          question: 'What does the document state about continuous learning?',
          options: ['It is optional', 'It is a core requirement', 'It only applies to beginners', 'It is not mentioned'],
          correct: 1,
          explanation: 'Continuous learning is emphasized as a core requirement for adapting to evolving methodologies.'
        },
        {
          question: 'When should the analytical model be applied?',
          options: ['Before data collection', 'During the final review', 'Throughout the entire process', 'Only when errors occur'],
          correct: 2,
          explanation: 'The analytical model is designed to be a continuous evaluation tool applied throughout the entire process.'
        },
        {
          question: 'How does the approach handle unexpected variables?',
          options: ['By ignoring them', 'By categorizing them as outliers', 'By adapting the framework dynamically', 'By restarting the process'],
          correct: 2,
          explanation: 'Dynamic adaptation of the framework is the recommended method for handling unexpected variables without losing progress.'
        },
        {
          question: 'What is the main advantage of the proposed model over traditional ones?',
          options: ['Lower cost', 'Higher flexibility and accuracy', 'Simpler terminology', 'Faster implementation time'],
          correct: 1,
          explanation: 'The proposed model offers significantly higher flexibility and accuracy, which are its main advantages.'
        },
        {
          question: 'According to the summary, what is the first step in problem-solving?',
          options: ['Defining the scope', 'Gathering resources', 'Testing hypotheses', 'Publishing results'],
          correct: 0,
          explanation: 'Defining the scope is always the first step to ensure focused and effective problem-solving.'
        },
        {
          question: 'What role does qualitative data play?',
          options: ['It is secondary to quantitative', 'It provides context and depth', 'It is purely illustrative', 'It is generally excluded'],
          correct: 1,
          explanation: 'Qualitative data is essential because it provides the necessary context and depth to the quantitative findings.'
        },
        {
          question: 'What is the recommended method for validating results?',
          options: ['Peer review and cross-examination', 'Self-assessment', 'Automated algorithms only', 'Historical comparison'],
          correct: 0,
          explanation: 'Peer review and cross-examination are recommended to ensure robust and unbiased validation of the results.'
        }
      ],
      discussions: [
        { id: 1, category: 'Critical Analysis', title: 'Critical Analysis of Document Limitations', messages: 8, active: 5, lastActivity: '15m ago' },
        { id: 2, category: 'Comparative Discussion', title: 'Comparing Traditional vs Adaptive Methods', messages: 12, active: 3, lastActivity: '30m ago' },
        { id: 3, category: 'Real-World Application', title: 'Real-World Industry Applications', messages: 6, active: 2, lastActivity: '1h ago' },
        { id: 4, category: 'Ethical Considerations', title: 'Ethical Implications at Scale', messages: 15, active: 7, lastActivity: '2h ago' },
        { id: 5, category: 'Future Perspectives', title: 'Future Enhancements and Evolving Tech', messages: 9, active: 4, lastActivity: '10m ago' },
        { id: 6, category: 'Design Patterns', title: 'Understanding Scalable Design Architectures', messages: 21, active: 9, lastActivity: '5m ago' }
      ],
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
      mindMap: {
        id: 'root',
        label: 'Introduction to Software Engineering and Models',
        expanded: true,
        children: [
          {
            id: 'c1', label: 'Software Engineering Overview', expanded: false,
            children: [{ id: 'c1-1', label: 'Definition', expanded: false }, { id: 'c1-2', label: 'History', expanded: false }]
          },
          {
            id: 'c2', label: 'Software Myths', expanded: false,
            children: [{ id: 'c2-1', label: 'Management Myths', expanded: false }, { id: 'c2-2', label: 'Customer Myths', expanded: false }, { id: 'c2-3', label: 'Practitioner Myths', expanded: false }]
          },
          {
            id: 'c3', label: 'Engineering Ethics', expanded: false,
            children: []
          },
          {
            id: 'c4', label: 'Software Process Models', expanded: false,
            children: [{ id: 'c4-1', label: 'Waterfall', expanded: false }, { id: 'c4-2', label: 'Agile', expanded: false }]
          },
          {
            id: 'c5', label: 'Software Development Life Cycle (SDLC)', expanded: false,
            children: [{ id: 'c5-1', label: 'Requirements', expanded: false }, { id: 'c5-2', label: 'Design', expanded: false }]
          }
        ]
      },
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
      const email = this.registerForm.value.email;
      const password = this.registerForm.value.password;

      const users = JSON.parse(localStorage.getItem('registeredUsers') || '[]');
      const existingUser = users.find((u: any) => u.email === email);
      if (existingUser) {
        existingUser.password = password; // update if exists
      } else {
        users.push({ email, password });
      }
      localStorage.setItem('registeredUsers', JSON.stringify(users));

      this.showRegistrationSuccess = true;
      this.registerForm.reset();
      this.authMode = 'login';

      setTimeout(() => {
        this.showRegistrationSuccess = false;
      }, 3000);
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
    this.isLoggedIn = false;
    this.showProfileMenu = false;
    this.email = '';
    this.password = '';
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

    const users = JSON.parse(localStorage.getItem('registeredUsers') || '[]');
    const user = users.find((u: any) => u.email === this.email);

    if (!user) {
      this.loginError = 'Account not found. Please register.';
      return;
    }

    if (user.password !== this.password) {
      this.loginError = 'Invalid credentials.';
      return;
    }

    this.isLoggedIn = true;
  }

  showGoogleLoginModal: boolean = false;

  loginWithGoogle(): void {
    this.showGoogleLoginModal = true;
  }

  selectGoogleAccount(mockEmail: string): void {
    this.email = mockEmail;
    this.isLoggedIn = true;
    this.showGoogleLoginModal = false;
  }

  closeGoogleLogin(): void {
    this.showGoogleLoginModal = false;
  }

  uploadedFileName: string = '';

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

  selectCourse(course: string): void {
    this.selectedCourse = course;
    this.openDropdown = null;

    let courseDoc = this.uploadedSources.find(s => s.name === course + ' (Course Material)');
    if (!courseDoc) {
      courseDoc = {
        id: Date.now() + Math.random(),
        name: course + ' (Course Material)',
        selected: true,
        status: 'In progress',
        progress: 25,
        score: null,
        content: this.generateDefaultContent()
      };
      this.uploadedSources.push(courseDoc);
      this.saveState();
    }

    this.uploadedFileName = courseDoc.name;
    this.currentView = 'summary';
  }

  selectUnit(unit: string): void {
    this.selectedUnit = unit;
    this.openDropdown = null;

    let unitDoc = this.uploadedSources.find(s => s.name === unit + ' (Material)');
    if (!unitDoc) {
      unitDoc = {
        id: Date.now() + Math.random(),
        name: unit + ' (Material)',
        selected: true,
        status: 'In progress',
        progress: 25,
        score: null,
        content: this.generateDefaultContent()
      };
      this.uploadedSources.push(unitDoc);
      this.saveState();
    }

    this.uploadedFileName = unitDoc.name;
    this.currentView = 'summary';
  }

  uploadedSources: SourceFile[] = [];

  sourcesSearchQuery: string = '';

  get activeDocument(): SourceFile | undefined {
    return this.uploadedSources.find(s => s.name === this.uploadedFileName);
  }

  // Document Content Getters
  get summaryOverview(): string { return this.activeDocument?.content.summaryOverview || ''; }
  get summaryKeyPoints(): string[] { return this.activeDocument?.content.summaryKeyPoints || []; }
  get summaryTakeaways(): string[] { return this.activeDocument?.content.summaryTakeaways || []; }
  get transcriptParagraphs(): string[] { return this.activeDocument?.content.transcriptParagraphs || []; }
  get audioDuration(): number { return this.activeDocument?.content.audioDuration || 0; }
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

    // Add user message
    this.activeDocument.content.chatMessages.push({ role: 'user', content: userMessage });
    this.saveState();

    // Scroll to bottom simulation could go here

    // Simulate AI response
    this.isGeneratingResponse = true;
    setTimeout(() => {
      this.isGeneratingResponse = false;
      if (this.activeDocument) {
        this.activeDocument.content.chatMessages.push({
          role: 'assistant',
          content: `Here is a customized summary response based on your request: "${userMessage}".\n\n- The content has been adjusted accordingly.\n- This simulates an intelligent AI assistant.\n- Please let me know if you need any other modifications to the summary.`
        });
        this.saveState();
      }
    }, 1500);
  }

  onSourceAdded(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      Array.from(input.files).forEach((file) => {
        const exists = this.uploadedSources.some((s) => s.name === file.name);
        if (!exists) {
          this.uploadedSources.push({
            id: Date.now() + Math.random(),
            name: file.name,
            selected: true,
            status: 'Not started',
            progress: 0,
            score: null,
            content: this.generateDefaultContent()
          });
          this.saveState();
        }
      });
      input.value = '';
    }
  }

  openDocumentFeatures(src: SourceFile): void {
    this.uploadedFileName = src.name;
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
  }

  isPlaying: boolean = false;
  audioProgress: number = 0;
  private audioInterval: any = null;

  togglePlay(): void {
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

  isPodcastMode: boolean = true;
  showAudioWarning: boolean = false;

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

  seekAudio(event: MouseEvent): void {
    const track = event.currentTarget as HTMLElement;
    const rect = track.getBoundingClientRect();
    const percentage = (event.clientX - rect.left) / rect.width;
    this.audioProgress = Math.round(percentage * this.audioDuration);
  }
  formatTime(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }
  downloadAudio(): void {
    const text = this.transcriptParagraphs.join('\n\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'audio-summary.txt';
    a.click();
    window.URL.revokeObjectURL(url);
  }

  isGeneratingAudio: boolean = false;

  generateAudio(): void {
    if (this.isGeneratingAudio) return;
    this.isGeneratingAudio = true;
    setTimeout(() => {
      this.isGeneratingAudio = false;
    }, 2000);
  }

  flipCard(): void {
    this.cardFlipped = !this.cardFlipped;
  }
  nextCard(): void {
    if (this.currentCardIndex < this.flashcards.length - 1) {
      this.currentCardIndex++;
      this.cardFlipped = false;
    }
  }
  prevCard(): void {
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
    const currentQuestion = this.quizQuestions[this.currentQuestionIndex];
    if (index === currentQuestion.correct) {
      this.quizScore++;
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
      const newThreadMessages: ThreadMessage[] = [];

      if (this.newDiscMessage.trim()) {
        newThreadMessages.push({
          authorName: 'You',
          authorInitials: 'Y',
          authorColor: '#1a2f6e',
          timeAgo: 'Just now',
          content: this.newDiscMessage.trim()
        });
      }

      this.activeDocument.content.discussions.unshift({
        id: Date.now(),
        category: this.newDiscCategory || 'Critical Analysis',
        title: this.newDiscTitle,
        messages: newThreadMessages.length,
        active: 1,
        lastActivity: 'just now',
        threadMessages: newThreadMessages
      });
      this.newDiscTitle = '';
      this.newDiscCategory = 'Critical Analysis';
      this.newDiscMessage = '';
      this.showNewDiscussion = false;
      this.saveState();
    }
  }

  openDiscussion(disc: Discussion): void {
    if (!disc.threadMessages) {
      disc.threadMessages = [
        { authorName: 'Sarah Johnson', authorInitials: 'S', authorColor: '#8e24aa', timeAgo: '2 hours ago', content: 'I think the resource constraints mentioned in section 2 really limit the applicability of this method in real-world scenarios. Has anyone else noticed this?' },
        { authorName: 'Michael Chen', authorInitials: 'M', authorColor: '#1a73e8', timeAgo: '1 hour ago', content: 'Good point Sarah! However, if you look at the appendix, they suggest a workaround using cloud infrastructure which could potentially mitigate those constraints.' },
        { authorName: 'Emily Rodriguez', authorInitials: 'E', authorColor: '#e53935', timeAgo: '15 mins ago', content: 'I agree with both of you. While the initial constraints are tough, the cloud workaround seems viable for mid-to-large scale implementations.' }
      ];
    }
    this.activeDiscussion = disc;
  }

  closeDiscussion(): void {
    this.activeDiscussion = null;
    this.newThreadMessage = '';
  }

  sendThreadMessage(): void {
    if (this.newThreadMessage.trim() && this.activeDiscussion) {
      if (!this.activeDiscussion.threadMessages) {
        this.activeDiscussion.threadMessages = [];
      }
      this.activeDiscussion.threadMessages.push({
        authorName: 'You',
        authorInitials: 'Y',
        authorColor: '#1a2f6e',
        timeAgo: 'Just now',
        content: this.newThreadMessage.trim()
      });
      this.activeDiscussion.messages++;
      this.newThreadMessage = '';
      this.saveState();
    }
  }

  triggerFileInput(): void {
    this.fileInput?.nativeElement?.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      this.uploadedFileName = file.name;
      this.currentView = 'summary';

      const exists = this.uploadedSources.some((s) => s.name === file.name);
      if (!exists) {
        this.uploadedSources.push({ id: Date.now(), name: file.name, selected: true, status: 'Not started', progress: 0, score: null, content: this.generateDefaultContent() });
      }
      this.saveState();
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
      this.uploadedFileName = file.name;
      this.currentView = 'summary';

      const exists = this.uploadedSources.some((s) => s.name === file.name);
      if (!exists) {
        this.uploadedSources.push({ id: Date.now(), name: file.name, selected: true, status: 'Not started', progress: 0, score: null, content: this.generateDefaultContent() });
      }
      this.saveState();
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
    if (this.uploadedFileName && id !== 'dashboard' && id !== 'home') {
      const activeDoc = this.uploadedSources.find(s => s.name === this.uploadedFileName);
      if (activeDoc && activeDoc.status === 'Not started') {
        activeDoc.status = 'In progress';
        activeDoc.progress = 25;
        this.saveState();
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
  }
}