// State Management
let token = localStorage.getItem('abf_token');
let currentUserId = null;
let currentUsername = null;
let activeTab = 'chatTab';
let currentSessionId = null;
let activeSourceIds = [];
let activeQuestion = null;
let currentChatAbortController = null;

// DOM Elements
const authPanel = document.getElementById('authPanel');
const appPanel = document.getElementById('appPanel');
const authForm = document.getElementById('authForm');
const authTitle = document.getElementById('authTitle');
const authSubtitle = document.getElementById('authSubtitle');
const authSubmitBtn = document.getElementById('authSubmitBtn');
const authSwitchLink = document.getElementById('authSwitchLink');
const authSwitchText = document.getElementById('authSwitchText');
const usernameInput = document.getElementById('usernameInput');
const passwordInput = document.getElementById('passwordInput');
const roleGroup = document.getElementById('roleGroup');
const roleInput = document.getElementById('roleInput');

const userProfileName = document.getElementById('userProfileName');
const userProfileRole = document.getElementById('userProfileRole');
const logoutBtn = document.getElementById('logoutBtn');
const currentTabTitle = document.getElementById('currentTabTitle');

const sidebarSourcesList = document.getElementById('sidebarSourcesList');
const sidebarSessionsList = document.getElementById('sidebarSessionsList');

const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const chatSendBtn = document.getElementById('chatSendBtn');

const dropzone = document.getElementById('dropzone');
const fileInput = document.getElementById('fileInput');
const urlInput = document.getElementById('urlInput');
const urlIngestBtn = document.getElementById('urlIngestBtn');
const sourcesLibraryGrid = document.getElementById('sourcesLibraryGrid');

const quizSetupScreen = document.getElementById('quizSetupScreen');
const quizPlayScreen = document.getElementById('quizPlayScreen');
const quizConcept = document.getElementById('quizConcept');
const quizDifficulty = document.getElementById('quizDifficulty');
const generateQuizBtn = document.getElementById('generateQuizBtn');
const quizBadgeTopic = document.getElementById('quizBadgeTopic');
const quizBadgeDifficulty = document.getElementById('quizBadgeDifficulty');
const quizQuestionText = document.getElementById('quizQuestionText');
const quizOptionsContainer = document.getElementById('quizOptionsContainer');
const quizFeedbackPanel = document.getElementById('quizFeedbackPanel');
const quizFeedbackTitle = document.getElementById('quizFeedbackTitle');
const quizFeedbackExplanation = document.getElementById('quizFeedbackExplanation');
const nextQuizBtn = document.getElementById('nextQuizBtn');

const statsStreakVal = document.getElementById('statsStreakVal');
const statsAccuracyVal = document.getElementById('statsAccuracyVal');
const statsAttemptsVal = document.getElementById('statsAttemptsVal');
const statsMasteryVal = document.getElementById('statsMasteryVal');
const topicMasteryContainer = document.getElementById('topicMasteryContainer');
const aiInsightContainer = document.getElementById('aiInsightContainer');

const citationModal = document.getElementById('citationModal');
const modalTitle = document.getElementById('modalTitle');
const modalPage = document.getElementById('modalPage');
const modalSnippet = document.getElementById('modalSnippet');
const modalCloseBtn = document.getElementById('modalCloseBtn');

const notificationHub = document.getElementById('notificationHub');

// Flag to track form mode: 'LOGIN' or 'REGISTER'
let authMode = 'LOGIN';

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
    bindEvents();
    if (token) {
        verifyTokenAndLoadApp();
    } else {
        showPanel('AUTH');
    }
});

// Navigation & Auth Toggle Panels
function showPanel(panel) {
    if (panel === 'AUTH') {
        authPanel.style.display = 'flex';
        appPanel.style.display = 'none';
    } else {
        authPanel.style.display = 'none';
        appPanel.style.display = 'grid';
        loadDashboardData();
    }
}

// ── Bind Event Listeners ──────────────────────────────────────────────
function bindEvents() {
    // Auth toggles
    authSwitchLink.addEventListener('click', (e) => {
        e.preventDefault();
        toggleAuthMode();
    });

    authForm.addEventListener('submit', (e) => {
        e.preventDefault();
        handleAuthSubmit();
    });

    logoutBtn.addEventListener('click', handleLogout);

    // Nav Switcher
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            const targetTab = item.getAttribute('data-tab');
            switchTab(targetTab);
        });
    });

    // Chat events
    chatSendBtn.addEventListener('click', handleSendChatMessage);
    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendChatMessage();
        }
    });

    // File Ingestion Dropzone
    dropzone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', handleFileSelection);
    
    // Drag-over styling
    dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.style.borderColor = 'var(--accent-violet)';
    });
    dropzone.addEventListener('dragleave', () => {
        dropzone.style.borderColor = 'var(--border-color)';
    });
    dropzone.addEventListener('drop', handleFileDrop);

    // URL Scraper Ingestion
    urlIngestBtn.addEventListener('click', handleUrlScrapeSubmit);

    // Quiz events
    generateQuizBtn.addEventListener('click', handleGenerateQuizQuestion);
    nextQuizBtn.addEventListener('click', () => {
        quizPlayScreen.style.display = 'none';
        quizSetupScreen.style.display = 'block';
    });

    // Modal events
    modalCloseBtn.addEventListener('click', () => {
        citationModal.style.display = 'none';
    });
    window.addEventListener('click', (e) => {
        if (e.target === citationModal) {
            citationModal.style.display = 'none';
        }
    });
}

// ── Authentication Pipeline ───────────────────────────────────────────
function toggleAuthMode() {
    if (authMode === 'LOGIN') {
        authMode = 'REGISTER';
        authTitle.innerText = 'Create Account';
        authSubtitle.innerText = 'Register for the adaptive AI tutor';
        authSubmitBtn.innerText = 'Register';
        roleGroup.style.display = 'block';
        authSwitchText.innerText = 'Already have an account?';
        authSwitchLink.innerText = 'Sign In';
    } else {
        authMode = 'LOGIN';
        authTitle.innerText = 'Welcome Back';
        authSubtitle.innerText = 'Sign in to your adaptive learning workspace';
        authSubmitBtn.innerText = 'Sign In';
        roleGroup.style.display = 'none';
        authSwitchText.innerText = "Don't have an account?";
        authSwitchLink.innerText = 'Create one';
    }
}

async function handleAuthSubmit() {
    const username = usernameInput.value.trim();
    const password = passwordInput.value;
    const role = roleInput.value;

    try {
        if (authMode === 'REGISTER') {
            const resp = await fetch('/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, role })
            });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Registration failed');

            showNotification('Registration successful! Logging in...', 'success');
            // Auto login after registration
            authMode = 'LOGIN';
            handleAuthSubmit();
        } else {
            const resp = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Login failed');

            token = data.accessToken;
            localStorage.setItem('abf_token', token);
            currentUserId = data.id;
            currentUsername = data.username;

            userProfileName.innerText = currentUsername;
            userProfileRole.innerText = data.role;

            showNotification('Successfully logged in!', 'success');
            showPanel('APP');
        }
    } catch (e) {
        showNotification(e.message, 'error');
    }
}

async function verifyTokenAndLoadApp() {
    try {
        const resp = await fetch('/auth/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!resp.ok) {
            handleLogout();
            return;
        }
        const data = await resp.json();
        currentUserId = data.id;
        currentUsername = data.username;
        
        userProfileName.innerText = currentUsername;
        userProfileRole.innerText = data.role;

        showPanel('APP');
    } catch (e) {
        handleLogout();
    }
}

function handleLogout() {
    token = null;
    currentUserId = null;
    currentUsername = null;
    localStorage.removeItem('abf_token');
    showPanel('AUTH');
    showNotification('Logged out successfully.', 'success');
}

// ── Tab Management ────────────────────────────────────────────────────
function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-panel').forEach(panel => {
        panel.classList.remove('active');
    });
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-tab') === tabId) {
            item.classList.add('active');
        }
    });

    const activePanel = document.getElementById(tabId);
    activePanel.classList.add('active');

    // Update Header title
    const activeNavItem = document.querySelector(`.nav-item[data-tab="${tabId}"]`);
    currentTabTitle.innerText = activeNavItem ? activeNavItem.innerText.trim() : 'Workspace';

    // Auto-focus chat if active
    if (tabId === 'chatTab') {
        chatInput.focus();
    }
}

// ── Load Dashboard / Sidebar Content ──────────────────────────────────
function loadDashboardData() {
    loadSources();
    loadChatSessions();
    loadAnalytics();
}

async function loadSources() {
    try {
        const resp = await fetch('/sources', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await resp.json();
        renderSources(data);
    } catch (e) {
        console.error('Failed to load sources', e);
    }
}

async function loadChatSessions() {
    try {
        const resp = await fetch('/chat/sessions', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await resp.json();
        renderSessions(data);
    } catch (e) {
        console.error('Failed to load sessions', e);
    }
}

async function loadAnalytics() {
    try {
        const resp = await fetch('/analytics', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await resp.json();
        
        // Update stats counters
        statsStreakVal.innerText = `${data.streak} day${data.streak === 1 ? '' : 's'}`;
        statsAccuracyVal.innerText = `${Math.round(data.accuracy * 100)}%`;
        statsAttemptsVal.innerText = `${data.totalAttempts} total responses`;

        // Update mastery level badge based on accuracy/attempts
        let lvl = 1;
        if (data.accuracy > 0.5 && data.totalAttempts > 3) lvl = 2;
        if (data.accuracy > 0.7 && data.totalAttempts > 8) lvl = 3;
        if (data.accuracy > 0.85 && data.totalAttempts > 15) lvl = 4;
        statsMasteryVal.innerText = `Lvl ${lvl}`;

        // Render topic strengths and weaknesses list
        renderMasteryMap(data.strengths, data.weaknesses);

        // Update AI studying guidance insights
        aiInsightContainer.innerText = data.aiInsight;

    } catch (e) {
        console.error('Failed to load analytics', e);
    }
}

// ── Source rendering & ingestion ──────────────────────────────────────
function renderSources(sources) {
    sidebarSourcesList.innerHTML = '';
    sourcesLibraryGrid.innerHTML = '';
    
    // Update Study Tools dropdown
    updateToolsSourceDropdown(sources);

    if (sources.length === 0) {
        sidebarSourcesList.innerHTML = `<div style="padding: 10px; font-size: 0.8rem; color: var(--text-muted); text-align: center;">No sources uploaded</div>`;
        sourcesLibraryGrid.innerHTML = `<div style="grid-column: 1/-1; padding: 40px; text-align: center; color: var(--text-muted); background: var(--bg-card); border-radius: 12px;">No source documents processed yet. Upload notes to begin.</div>`;
        return;
    }

    sources.forEach(src => {
        // Sidebar item
        const sidebarDiv = document.createElement('div');
        sidebarDiv.className = `source-item ${activeSourceIds.includes(src.id) ? 'active' : ''}`;
        sidebarDiv.innerHTML = `
            <span><i class="fa-solid ${getFileIcon(src.type)}"></i> ${abbrev(src.name, 18)}</span>
            <button class="item-delete-btn" onclick="handleDeleteSource(event, '${src.id}')"><i class="fa-solid fa-trash"></i></button>
        `;
        sidebarDiv.addEventListener('click', () => {
            toggleActiveSource(src.id, sidebarDiv);
        });
        sidebarSourcesList.appendChild(sidebarDiv);

        // Grid Library item
        const gridDiv = document.createElement('div');
        gridDiv.className = 'tool-card';
        const status = src.metadata && src.metadata.status ? src.metadata.status : 'complete';
        let statusBadge = `<span style="font-size: 0.75rem; color: var(--accent-emerald);"><i class="fa-solid fa-circle-check"></i> Processed</span>`;
        if (status === 'processing') statusBadge = `<span style="font-size: 0.75rem; color: var(--accent-amber);"><i class="fa-solid fa-circle-notch fa-spin"></i> Processing</span>`;
        if (status === 'failed') statusBadge = `<span style="font-size: 0.75rem; color: var(--accent-rose);"><i class="fa-solid fa-circle-exclamation"></i> Failed</span>`;

        gridDiv.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 12px;">
                <i class="fa-solid ${getFileIcon(src.type)}"></i>
                ${statusBadge}
            </div>
            <h4>${abbrev(src.name, 24)}</h4>
            <p style="font-size: 0.8rem; color: var(--text-muted); margin-top: 6px;">Size: ${formatBytes(src.fileSize)} | Format: ${src.type}</p>
            <button class="btn-primary" style="margin-top: 16px; background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-primary);" onclick="handleDeleteSource(event, '${src.id}')">Delete Source</button>
        `;
        sourcesLibraryGrid.appendChild(gridDiv);
    });
}

function getFileIcon(type) {
    if (type === 'PDF') return 'fa-file-pdf';
    if (type === 'DOCX') return 'fa-file-word';
    if (type === 'URL') return 'fa-file-lines';
    return 'fa-file-lines';
}

function toggleActiveSource(sourceId, element) {
    if (activeSourceIds.includes(sourceId)) {
        activeSourceIds = activeSourceIds.filter(id => id !== sourceId);
        element.classList.remove('active');
    } else {
        activeSourceIds.push(sourceId);
        element.classList.add('active');
    }
}

async function handleDeleteSource(event, sourceId) {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this source document? All associated RAG chunks and contexts will be permanently removed.')) {
        return;
    }

    try {
        const resp = await fetch(`/sources/${sourceId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!resp.ok) throw new Error('Delete failed');
        showNotification('Source document deleted successfully.', 'success');
        activeSourceIds = activeSourceIds.filter(id => id !== sourceId);
        loadDashboardData();
    } catch (e) {
        showNotification(e.message, 'error');
    }
}

// ── Ingestion Handlers ────────────────────────────────────────────────
function handleFileSelection(e) {
    const file = e.target.files[0];
    if (file) ingestFile(file);
}

function handleFileDrop(e) {
    e.preventDefault();
    dropzone.style.borderColor = 'var(--border-color)';
    const file = e.dataTransfer.files[0];
    if (file) ingestFile(file);
}

async function ingestFile(file) {
    const formData = new FormData();
    formData.append('file', file);

    showNotification(`Uploading and parsing ${file.name}...`, 'success');

    try {
        const resp = await fetch('/sources/upload', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Upload failed');

        showNotification('File processing started. View library status in a few seconds.', 'success');
        loadSources();
        // Poll source status reload every 4 seconds up to 3 times
        let counter = 0;
        const interval = setInterval(() => {
            loadSources();
            if (++counter >= 3) clearInterval(interval);
        }, 4000);
    } catch (e) {
        showNotification(e.message, 'error');
    }
}

async function handleUrlScrapeSubmit() {
    const url = urlInput.value.trim();
    if (!url) {
        showNotification('Please enter a valid URL', 'error');
        return;
    }

    showNotification('Sending URL crawl request...', 'success');
    try {
        const resp = await fetch('/sources/url', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ url })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Scrape failed');

        showNotification('Scrape complete! Web contents ingested.', 'success');
        urlInput.value = '';
        loadSources();
    } catch (e) {
        showNotification(e.message, 'error');
    }
}

// ── Sessions & Conversational Chat ────────────────────────────────────
function renderSessions(sessions) {
    sidebarSessionsList.innerHTML = '';
    if (sessions.length === 0) {
        sidebarSessionsList.innerHTML = `<div style="padding: 10px; font-size: 0.8rem; color: var(--text-muted); text-align: center;">No chat threads</div>`;
        return;
    }

    sessions.forEach(sess => {
        const div = document.createElement('div');
        div.className = `session-item ${currentSessionId === sess.id ? 'active' : ''}`;
        div.innerHTML = `
            <span><i class="fa-regular fa-comments"></i> ${abbrev(sess.title, 18)}</span>
            <button class="item-delete-btn" onclick="handleDeleteSession(event, '${sess.id}')"><i class="fa-solid fa-xmark"></i></button>
        `;
        div.addEventListener('click', () => {
            selectChatSession(sess.id);
        });
        sidebarSessionsList.appendChild(div);
    });
}

async function selectChatSession(sessionId) {
    currentSessionId = sessionId;
    // reload sidebar lists to highlight active
    loadChatSessions();

    chatMessages.innerHTML = `<div style="padding: 20px; text-align: center;"><i class="fa-solid fa-circle-notch fa-spin"></i> Loading thread...</div>`;

    try {
        const resp = await fetch(`/chat/history?sessionId=${sessionId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const messages = await resp.json();
        chatMessages.innerHTML = '';
        if (messages.length === 0) {
            chatMessages.innerHTML = `<div class="chat-bubble ai">Thread is active. Send your first message to begin learning.</div>`;
        } else {
            messages.forEach(msg => {
                renderChatMessage(msg);
            });
        }
        scrollChatToBottom();
    } catch (e) {
        showNotification('Failed to load session history', 'error');
    }
}

async function handleDeleteSession(event, sessionId) {
    event.stopPropagation();
    if (!confirm('Delete this conversation history permanently?')) return;

    try {
        const resp = await fetch(`/chat/sessions/${sessionId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!resp.ok) throw new Error('Delete failed');
        showNotification('Conversation deleted.', 'success');
        if (currentSessionId === sessionId) {
            currentSessionId = null;
            chatMessages.innerHTML = `<div class="chat-bubble ai">Start a new session! Ask me any question, or select document resources from your library.</div>`;
        }
        loadChatSessions();
    } catch (e) {
        showNotification(e.message, 'error');
    }
}

function renderChatMessage(msg) {
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble ${msg.sender.toLowerCase()}`;
    
    // Convert newlines to breaks
    let formattedText = msg.content.replace(/\n/g, '<br>');
    bubble.innerHTML = formattedText;

    // Render citations if available
    if (msg.citations && msg.citations.length > 0) {
        const citationsDiv = document.createElement('div');
        citationsDiv.className = 'chat-citations';
        msg.citations.forEach((cit, idx) => {
            const tag = document.createElement('span');
            tag.className = 'citation-tag';
            tag.innerHTML = `<i class="fa-solid fa-quote-left"></i> [${cit.sourceName}, Page ${cit.pageNumber}]`;
            tag.addEventListener('click', () => {
                showCitationModal(cit);
            });
            citationsDiv.appendChild(tag);
        });
        bubble.appendChild(citationsDiv);
    }

    chatMessages.appendChild(bubble);
}

function showCitationModal(cit) {
    modalTitle.innerText = `Citation: ${cit.sourceName}`;
    modalPage.innerText = `Document Resource ID: ${cit.sourceId} | Page: ${cit.pageNumber}`;
    modalSnippet.innerText = cit.snippet;
    citationModal.style.display = 'flex';
}

async function handleSendChatMessage() {
    const text = chatInput.value.trim();
    if (!text) return;

    // Add Optimistic UI bubble for user
    const tempUserMsg = { sender: 'USER', content: text, citations: [] };
    renderChatMessage(tempUserMsg);
    chatInput.value = '';
    scrollChatToBottom();

    // Loading indicator bubble
    const loadingBubble = document.createElement('div');
    loadingBubble.className = 'chat-bubble ai';
    loadingBubble.id = 'chatLoadingIndicator';
    loadingBubble.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center;">
            <span><i class="fa-solid fa-ellipsis fa-fade"></i> Tutor is thinking...</span>
            <button id="cancelStreamBtn" class="item-delete-btn" style="background:var(--bg-panel);"><i class="fa-solid fa-stop"></i></button>
        </div>
    `;
    chatMessages.appendChild(loadingBubble);
    scrollChatToBottom();

    // Set up abort controller
    if (currentChatAbortController) {
        currentChatAbortController.abort();
    }
    currentChatAbortController = new AbortController();

    document.getElementById('cancelStreamBtn').addEventListener('click', () => {
        if (currentChatAbortController) {
            currentChatAbortController.abort();
        }
    });

    try {
        const resp = await fetch('/chat/stream', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                content: text,
                sourceIds: activeSourceIds
            }),
            signal: currentChatAbortController.signal
        });

        if (!resp.ok) {
            const errData = await resp.json();
            throw new Error(errData.error || 'Failed to get AI response');
        }

        // Remove loading indicator
        const loadingEl = document.getElementById('chatLoadingIndicator');
        if (loadingEl) loadingEl.remove();

        // Create empty AI bubble
        const aiBubble = document.createElement('div');
        aiBubble.className = 'chat-bubble ai';
        chatMessages.appendChild(aiBubble);

        const reader = resp.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let accumulatedText = "";

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            const chunk = decoder.decode(value, { stream: true });
            
            const lines = chunk.split('\n');
            for (const line of lines) {
                if (line.startsWith('data:')) {
                    let data = line.replace('data:', '');
                    if (data === '[DONE]') {
                        break;
                    }
                    accumulatedText += data;
                    aiBubble.innerHTML = accumulatedText.replace(/\\n/g, '<br>');
                    scrollChatToBottom();
                } else if (line.startsWith('event: error')) {
                    throw new Error("Stream error");
                }
            }
        }

        // Wait a small bit and then refresh the thread to get citations and proper DB IDs
        setTimeout(() => {
            selectChatSession(currentSessionId);
        }, 500);

    } catch (e) {
        if (e.name === 'AbortError') {
            const loadingEl = document.getElementById('chatLoadingIndicator');
            if (loadingEl) loadingEl.remove();
            const cancelBubble = document.createElement('div');
            cancelBubble.className = 'chat-bubble ai';
            cancelBubble.style.color = 'var(--text-muted)';
            cancelBubble.innerText = 'Generation cancelled by user.';
            chatMessages.appendChild(cancelBubble);
        } else {
            const loadingEl = document.getElementById('chatLoadingIndicator');
            if (loadingEl) loadingEl.remove();

            showNotification(e.message, 'error');
            const errBubble = document.createElement('div');
            errBubble.className = 'chat-bubble ai';
            errBubble.style.color = 'var(--accent-rose)';
            errBubble.innerText = 'Tutoring response timed out or failed. Please check backend log details.';
            chatMessages.appendChild(errBubble);
        }
        scrollChatToBottom();
    } finally {
        currentChatAbortController = null;
    }
}

function scrollChatToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// ── Adaptive MCQ Quizzes ──────────────────────────────────────────────
async function handleGenerateQuizQuestion() {
    const concept = quizConcept.value.trim();
    const difficulty = parseInt(quizDifficulty.value);
    
    if (!concept) {
        showNotification('Please enter a concept topic to quiz on', 'error');
        return;
    }

    // Toggle loader
    generateQuizBtn.innerHTML = `<i class="fa-solid fa-spinner fa-spin"></i> Generating adaptive question...`;
    generateQuizBtn.disabled = true;

    try {
        const sourceId = activeSourceIds.length > 0 ? activeSourceIds[0] : null;

        const resp = await fetch('/quiz/generate-question', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ concept, difficulty, sourceId })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Question generation failed');

        activeQuestion = data.question;
        
        // Setup display badges
        quizBadgeTopic.innerText = activeQuestion.concept.toUpperCase();
        quizBadgeDifficulty.innerText = `Bloom Level ${activeQuestion.difficulty}`;

        // Extract options and clean question text
        // Splitting: "What is polymorphism?\n\nA) Option 1..."
        const parts = activeQuestion.text.split('\n\n');
        quizQuestionText.innerText = parts[0];

        // Options: parse from full choice text lines
        quizOptionsContainer.innerHTML = '';
        const choices = ['A', 'B', 'C', 'D'];
        
        // We know text contains A), B), C), D) choice prefixes
        const optionLines = parts.length > 1 ? parts.slice(1).join('\n').split('\n') : [];
        
        choices.forEach((choice, index) => {
            let label = `Choice ${choice}`;
            // Match corresponding prefix in the generated list
            for (let line of optionLines) {
                if (line.trim().startsWith(`${choice})`)) {
                    label = line.replace(`${choice})`, '').trim();
                    break;
                }
            }

            const btn = document.createElement('button');
            btn.className = 'option-button';
            btn.innerHTML = `
                <div class="option-marker">${choice}</div>
                <span>${label}</span>
            `;
            btn.addEventListener('click', () => {
                selectQuizOption(choice, btn);
            });
            quizOptionsContainer.appendChild(btn);
        });

        // Toggle layout
        quizFeedbackPanel.style.display = 'none';
        quizSetupScreen.style.display = 'none';
        quizPlayScreen.style.display = 'block';

        // Stash generated explanation in memory
        activeQuestion.explanationText = data.explanation;

    } catch (e) {
        showNotification(e.message, 'error');
    } finally {
        generateQuizBtn.innerHTML = `<i class="fa-solid fa-graduation-cap"></i> Generate Quiz MCQ`;
        generateQuizBtn.disabled = false;
    }
}

function selectQuizOption(choice, buttonElement) {
    // Prevent double submits
    if (quizFeedbackPanel.style.display === 'block') return;

    // Highlight selected
    document.querySelectorAll('.option-button').forEach(btn => btn.classList.remove('selected'));
    buttonElement.classList.add('selected');

    // Submit Answer Immediately
    submitQuizAnswer(choice, buttonElement);
}

async function submitQuizAnswer(choice, buttonElement) {
    try {
        const resp = await fetch('/quiz/submit', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                questionId: activeQuestion.id,
                studentAnswer: choice
            })
        });
        const result = await resp.json();
        if (!resp.ok) throw new Error(result.error || 'Submission failed');

        // Render correctness styling
        document.querySelectorAll('.option-button').forEach(btn => {
            const marker = btn.querySelector('.option-marker').innerText.trim();
            if (marker === result.correctAnswer) {
                btn.classList.add('correct');
            } else if (btn.classList.contains('selected')) {
                btn.classList.add('wrong');
            }
        });

        // Trigger dynamic explanation window
        quizFeedbackTitle.innerText = result.correct ? '🎉 Correct Answer!' : '❌ Incorrect Answer';
        quizFeedbackTitle.style.color = result.correct ? 'var(--accent-emerald)' : 'var(--accent-rose)';
        
        // Show AI explanation
        quizFeedbackExplanation.innerText = activeQuestion.explanationText || 'Mastery metrics updated adaptively.';
        quizFeedbackPanel.style.display = 'block';

        // Reload analytics cache silently
        loadAnalytics();

    } catch (e) {
        showNotification(e.message, 'error');
    }
}

// ── Mastery & Analytics rendering ─────────────────────────────────────
function renderMasteryMap(strengths, weaknesses) {
    topicMasteryContainer.innerHTML = '';
    const allMasteries = [...strengths, ...weaknesses];

    if (allMasteries.length === 0) {
        topicMasteryContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px;">No concept mastery calculated yet. Take quizzes to see insights.</div>`;
        return;
    }

    // Sort confidence descending
    allMasteries.sort((a, b) => b.confidence - a.confidence);

    allMasteries.forEach(m => {
        const row = document.createElement('div');
        row.style.border = '1px solid var(--border-color)';
        row.style.borderRadius = '8px';
        row.style.padding = '14px 20px';
        row.style.background = 'rgba(255,255,255,0.01)';
        row.style.display = 'flex';
        row.style.align-items = 'center';
        row.style.justify-content = 'space-between';

        const confPct = Math.round(m.confidence * 100);
        let trendIcon = `<i class="fa-solid fa-arrow-right" style="color: var(--text-muted);" title="Stable"></i>`;
        if (m.trend === 'improving') trendIcon = `<i class="fa-solid fa-circle-chevron-up" style="color: var(--accent-emerald);" title="Improving"></i>`;
        if (m.trend === 'declining') trendIcon = `<i class="fa-solid fa-circle-chevron-down" style="color: var(--accent-rose);" title="Declining"></i>`;

        row.innerHTML = `
            <div style="display: flex; align-items: center; gap: 14px;">
                ${trendIcon}
                <div>
                    <h4 style="font-size: 0.95rem;">${m.topic.toUpperCase()}</h4>
                    <p style="font-size: 0.8rem; color: var(--text-muted); margin-top: 2px;">Attempts: ${m.totalAttempts} | Tier: ${m.tier}</p>
                </div>
            </div>
            <div style="text-align: right;">
                <span style="font-size: 1.15rem; font-weight: 600; font-family: var(--font-heading); color: ${m.confidence >= 0.7 ? 'var(--accent-emerald)' : 'var(--accent-amber)'}">${confPct}%</span>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 2px;">Mastery Confidence</p>
            </div>
        `;
        topicMasteryContainer.appendChild(row);
    });
}

// ── Notification Alerts Helper ────────────────────────────────────────
function showNotification(message, type = 'success') {
    const div = document.createElement('div');
    div.className = `notification ${type}`;
    
    let icon = 'fa-circle-check';
    if (type === 'error') icon = 'fa-circle-xmark';
    
    div.innerHTML = `
        <i class="fa-solid ${icon}"></i>
        <span>${message}</span>
    `;

    notificationHub.appendChild(div);

    // Auto-remove notification after 4 seconds
    setTimeout(() => {
        div.style.opacity = '0';
        div.style.transition = '0.5s ease-out';
        setTimeout(() => div.remove(), 500);
    }, 4000);
}

// Expose notification helper globally for mock tool triggers
window.showNotification = showNotification;

// ── String and bytes helpers ──────────────────────────────────────────
function abbrev(str, len) {
    return str.length > len ? str.substring(0, len - 3) + '...' : str;
}

function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

// ── Generative Study Tools ────────────────────────────────────────────

// Elements
const toolsSourceSelect = document.getElementById('toolsSourceSelect');
const generateSummaryBtn = document.getElementById('generateSummaryBtn');
const generateFlashcardsBtn = document.getElementById('generateFlashcardsBtn');
const generateMindmapBtn = document.getElementById('generateMindmapBtn');
const generateAudioBtn = document.getElementById('generateAudioBtn');

// Update dropdown when sources change
function updateToolsSourceDropdown(sources) {
    const currentVal = toolsSourceSelect.value;
    toolsSourceSelect.innerHTML = '<option value="">Select a source document...</option>';
    
    if (sources && sources.length > 0) {
        sources.forEach(src => {
            const opt = document.createElement('option');
            opt.value = src.id;
            opt.innerText = abbrev(src.name, 40);
            toolsSourceSelect.appendChild(opt);
        });
        
        // Restore selection if still exists
        if (currentVal && sources.find(s => s.id === currentVal)) {
            toolsSourceSelect.value = currentVal;
        } else if (activeSourceIds.length > 0 && sources.find(s => s.id === activeSourceIds[0])) {
            toolsSourceSelect.value = activeSourceIds[0];
        } else if (sources.length > 0) {
            toolsSourceSelect.value = sources[0].id;
        }
    }
}

// Bind Tool Events
document.addEventListener('DOMContentLoaded', () => {
    if(generateSummaryBtn) generateSummaryBtn.addEventListener('click', handleGenerateSummary);
    if(generateFlashcardsBtn) generateFlashcardsBtn.addEventListener('click', handleGenerateFlashcards);
    if(generateMindmapBtn) generateMindmapBtn.addEventListener('click', handleGenerateMindmap);
    if(generateAudioBtn) generateAudioBtn.addEventListener('click', handleGenerateAudio);
    
    // Flashcard UI
    const fcPrevBtn = document.getElementById('fcPrevBtn');
    const fcNextBtn = document.getElementById('fcNextBtn');
    const activeFlashcard = document.getElementById('activeFlashcard');
    
    if (activeFlashcard) {
        activeFlashcard.addEventListener('click', () => {
            activeFlashcard.classList.toggle('is-flipped');
        });
    }
    
    if (fcPrevBtn) {
        fcPrevBtn.addEventListener('click', () => {
            if (currentFlashcardIndex > 0) {
                currentFlashcardIndex--;
                renderCurrentFlashcard();
            }
        });
    }
    
    if (fcNextBtn) {
        fcNextBtn.addEventListener('click', () => {
            if (currentFlashcards && currentFlashcardIndex < currentFlashcards.length - 1) {
                currentFlashcardIndex++;
                renderCurrentFlashcard();
            }
        });
    }
    
    // Audio Player UI
    const audioPlayBtn = document.getElementById('audioPlayBtn');
    if (audioPlayBtn) {
        audioPlayBtn.addEventListener('click', toggleAudioPlayer);
    }
});

// UI Helper: toggle visibility
function setToolState(toolName, state) {
    const empty = document.getElementById(`${toolName}Empty`);
    const placeholder = document.getElementById(`${toolName}Placeholder`);
    const content = document.getElementById(`${toolName}Content`);
    
    if(empty) empty.style.display = 'none';
    if(placeholder) placeholder.style.display = 'none';
    if(content) content.style.display = 'none';
    
    const target = document.getElementById(`${toolName}${state}`);
    if(target) target.style.display = state === 'Content' ? 'block' : 'flex';
}

// ── Summary Logic
async function handleGenerateSummary() {
    const sourceId = toolsSourceSelect.value;
    if (!sourceId) {
        showNotification('Please select a source document first', 'error');
        return;
    }
    
    setToolState('summary', 'Placeholder');
    generateSummaryBtn.disabled = true;
    
    try {
        const resp = await fetch('/summary/generate', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sourceId })
        });
        
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Summary generation failed');
        
        renderSummaryContent(data);
        setToolState('summary', 'Content');
        showNotification('Summary generated successfully', 'success');
    } catch (e) {
        showNotification(e.message, 'error');
        setToolState('summary', 'Empty');
    } finally {
        generateSummaryBtn.disabled = false;
    }
}

function renderSummaryContent(data) {
    const container = document.getElementById('summaryContent');
    let html = `<div class="summary-content">`;
    
    if (data.summary) {
        html += `<div class="summary-text">${data.summary.replace(/\\n/g, '<br>')}</div>`;
    }
    
    html += `<div class="summary-lists">`;
    if (data.keyPoints && data.keyPoints.length > 0) {
        html += `<div class="summary-list"><h4><i class="fa-solid fa-key"></i> Key Points</h4><ul>`;
        data.keyPoints.forEach(pt => html += `<li>${pt}</li>`);
        html += `</ul></div>`;
    }
    
    if (data.importantTakeaways && data.importantTakeaways.length > 0) {
        html += `<div class="summary-list"><h4><i class="fa-solid fa-lightbulb"></i> Important Takeaways</h4><ul>`;
        data.importantTakeaways.forEach(pt => html += `<li>${pt}</li>`);
        html += `</ul></div>`;
    }
    html += `</div>`;
    
    if (data.definitions && data.definitions.length > 0) {
        html += `<div class="summary-definitions"><h4><i class="fa-solid fa-book"></i> Definitions</h4><div class="def-grid">`;
        data.definitions.forEach(def => {
            if (def.includes(':')) {
                const parts = def.split(':');
                html += `<div class="def-item"><span class="def-term">${parts[0].trim()}</span>${parts.slice(1).join(':').trim()}</div>`;
            } else {
                html += `<div class="def-item">${def}</div>`;
            }
        });
        html += `</div></div>`;
    }
    
    html += `</div>`;
    container.innerHTML = html;
}

// ── Flashcards Logic
let currentFlashcards = null;
let currentFlashcardIndex = 0;

async function handleGenerateFlashcards() {
    const sourceId = toolsSourceSelect.value;
    if (!sourceId) {
        showNotification('Please select a source document first', 'error');
        return;
    }
    
    setToolState('flashcards', 'Placeholder');
    generateFlashcardsBtn.disabled = true;
    
    try {
        const resp = await fetch('/flashcards/generate', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sourceId })
        });
        
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Flashcard generation failed');
        
        // Fetch the generated cards
        const cardsResp = await fetch(`/flashcards/deck/${data.id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const cardsData = await cardsResp.json();
        
        if (cardsData && cardsData.length > 0) {
            currentFlashcards = cardsData;
            currentFlashcardIndex = 0;
            renderCurrentFlashcard();
            setToolState('flashcards', 'Content');
            showNotification('Flashcards generated successfully', 'success');
        } else {
            throw new Error('No flashcards returned');
        }
    } catch (e) {
        showNotification(e.message, 'error');
        setToolState('flashcards', 'Empty');
    } finally {
        generateFlashcardsBtn.disabled = false;
    }
}

function renderCurrentFlashcard() {
    if (!currentFlashcards || currentFlashcards.length === 0) return;
    
    const card = currentFlashcards[currentFlashcardIndex];
    const frontText = document.getElementById('fcFrontText');
    const backText = document.getElementById('fcBackText');
    const progressText = document.getElementById('fcProgress');
    const fcPrevBtn = document.getElementById('fcPrevBtn');
    const fcNextBtn = document.getElementById('fcNextBtn');
    const activeFlashcard = document.getElementById('activeFlashcard');
    
    // Ensure card is showing front before changing content
    activeFlashcard.classList.remove('is-flipped');
    
    // Update content after flip animation finishes (if it was flipped)
    setTimeout(() => {
        frontText.innerText = card.front;
        backText.innerText = card.back;
        progressText.innerText = `${currentFlashcardIndex + 1} / ${currentFlashcards.length}`;
        
        fcPrevBtn.disabled = currentFlashcardIndex === 0;
        fcNextBtn.disabled = currentFlashcardIndex === currentFlashcards.length - 1;
    }, 150);
}

// ── Mind Map Logic
async function handleGenerateMindmap() {
    const sourceId = toolsSourceSelect.value;
    if (!sourceId) {
        showNotification('Please select a source document first', 'error');
        return;
    }
    
    setToolState('mindmap', 'Placeholder');
    generateMindmapBtn.disabled = true;
    
    try {
        const resp = await fetch('/mindmaps/generate', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sourceId })
        });
        
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Mind map generation failed');
        
        renderMindmapSvg(data);
        setToolState('mindmap', 'Content');
        showNotification('Mind map generated successfully', 'success');
    } catch (e) {
        showNotification(e.message, 'error');
        setToolState('mindmap', 'Empty');
    } finally {
        generateMindmapBtn.disabled = false;
    }
}

function renderMindmapSvg(data) {
    const svg = document.getElementById('mindmapSvg');
    svg.innerHTML = '';
    
    if (!data.data || !data.data.root) {
        svg.innerHTML = '<text x="400" y="200" fill="white" text-anchor="middle">Failed to load mind map data</text>';
        return;
    }
    
    const rootLabel = data.data.root;
    const children = data.data.children || [];
    
    // Simple radial layout
    const centerX = 400;
    const centerY = 200;
    const radius = 150;
    
    let html = '';
    
    // Draw links first so they are behind nodes
    children.forEach((child, i) => {
        const angle = (i / children.length) * 2 * Math.PI;
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);
        
        html += `<line x1="${centerX}" y1="${centerY}" x2="${x}" y2="${y}" class="mindmap-link" />`;
    });
    
    // Draw root node
    html += `
        <g class="mindmap-node">
            <rect x="${centerX - 80}" y="${centerY - 25}" width="160" height="50" style="stroke: var(--accent-violet);"></rect>
            <text x="${centerX}" y="${centerY}">${abbrev(rootLabel, 20)}</text>
        </g>
    `;
    
    // Draw child nodes
    children.forEach((child, i) => {
        const angle = (i / children.length) * 2 * Math.PI;
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);
        const label = child.label || child.name || 'Unnamed Concept';
        
        html += `
            <g class="mindmap-node">
                <rect x="${x - 70}" y="${y - 20}" width="140" height="40" style="stroke: var(--accent-emerald);"></rect>
                <text x="${x}" y="${y}">${abbrev(label, 18)}</text>
            </g>
        `;
    });
    
    svg.innerHTML = html;
}

// ── Audio Overview Logic
let audioIsPlaying = false;
let currentUtterance = null;

async function handleGenerateAudio() {
    const sourceId = toolsSourceSelect.value;
    if (!sourceId) {
        showNotification('Please select a source document first', 'error');
        return;
    }
    
    setToolState('audio', 'Placeholder');
    generateAudioBtn.disabled = true;
    
    // Stop any existing playback
    if (window.speechSynthesis) window.speechSynthesis.cancel();
    audioIsPlaying = false;
    updateAudioUI();
    
    try {
        const resp = await fetch('/podcasts/generate', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ sourceId })
        });
        
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Audio generation failed');
        
        document.getElementById('audioScriptBox').innerText = data.script || 'No script generated.';
        setToolState('audio', 'Content');
        showNotification('Audio script generated. Ready to play.', 'success');
    } catch (e) {
        showNotification(e.message, 'error');
        setToolState('audio', 'Empty');
    } finally {
        generateAudioBtn.disabled = false;
    }
}

function toggleAudioPlayer() {
    if (!window.speechSynthesis) {
        showNotification('Text-to-speech not supported in this browser.', 'error');
        return;
    }
    
    if (audioIsPlaying) {
        window.speechSynthesis.pause();
        audioIsPlaying = false;
    } else {
        if (window.speechSynthesis.paused) {
            window.speechSynthesis.resume();
        } else {
            const script = document.getElementById('audioScriptBox').innerText;
            currentUtterance = new SpeechSynthesisUtterance(script);
            currentUtterance.rate = 1.0;
            currentUtterance.pitch = 1.0;
            currentUtterance.onend = () => {
                audioIsPlaying = false;
                updateAudioUI();
            };
            window.speechSynthesis.speak(currentUtterance);
        }
        audioIsPlaying = true;
    }
    updateAudioUI();
}

function updateAudioUI() {
    const btn = document.getElementById('audioPlayBtn');
    const icon = document.getElementById('audioPlayingIcon');
    
    if (btn) {
        btn.innerHTML = audioIsPlaying ? '<i class="fa-solid fa-pause"></i>' : '<i class="fa-solid fa-play"></i>';
    }
    
    if (icon) {
        icon.style.animation = audioIsPlaying ? 'pulse 2s infinite' : 'none';
        icon.style.opacity = audioIsPlaying ? '1' : '0.5';
    }
}
