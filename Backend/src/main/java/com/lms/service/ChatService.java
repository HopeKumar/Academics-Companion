package com.lms.service;

import com.lms.model.ChatMessage;
import com.lms.model.ChatSession;
import com.lms.model.Citation;
import com.lms.model.DocumentChunk;
import com.lms.repository.ChatMessageRepository;
import com.lms.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository        sessionRepository;
    private final ChatMessageRepository        messageRepository;
    private final HybridRetrievalService       retrievalService;
    private final ConversationMemoryService    memoryService;
    private final StreamingResponseService     streamingResponseService;
    private final ContextBuilder               contextBuilder;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final AIResponseValidator          aiValidator;

    @Autowired
    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       HybridRetrievalService retrievalService,
                       ConversationMemoryService memoryService,
                       StreamingResponseService streamingResponseService,
                       ContextBuilder contextBuilder,
                       com.lms.service.ai.AIOrchestratorService aiOrchestratorService,
                       AIResponseValidator aiValidator) {
        this.sessionRepository        = sessionRepository;
        this.messageRepository        = messageRepository;
        this.retrievalService         = retrievalService;
        this.memoryService            = memoryService;
        this.streamingResponseService = streamingResponseService;
        this.contextBuilder           = contextBuilder;
        this.aiOrchestratorService    = aiOrchestratorService;
        this.aiValidator              = aiValidator;
    }

    public List<ChatSession> getSessionsForUser(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession(userId, title);
        return sessionRepository.save(session);
    }

    public void deleteSession(String userId, String sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("ChatSession", sessionId));

        sessionRepository.delete(session);
        messageRepository.deleteBySessionId(sessionId);
        LOG.info("Deleted chat session {} and all its messages", sessionId);
    }

    /**
     * Sends a user message, performs RAG context retrieval, queries the AI synchronously, saves history and returns tutor response.
     */
    public ChatMessage sendMessage(String userId, String sessionId, String content, List<String> sourceIds) {
        ChatSession session = resolveSession(userId, sessionId, content);
        String finalSessionId = session.getId();

        if (sourceIds == null) sourceIds = new ArrayList<>();

        // 1. Save user message to database
        ChatMessage userMsg = new ChatMessage(finalSessionId, "USER", content);
        userMsg = messageRepository.save(userMsg);

        // Update session timestamp
        session.setUpdatedAt(Instant.now().toString());
        sessionRepository.save(session);

        // 2. Fetch Hybrid RAG Context
        List<DocumentChunk> chunks = retrievalService.retrieve(userId, content, sourceIds, 4);
        ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);

        // 3. Load active conversation memory (using auto-summarization of older threads)
        ConversationMemoryService.MemoryContext memory = memoryService.loadActiveMemory(finalSessionId, userMsg.getId());

        // 4. Build Prompt
        String prompt = buildPromptWithMemory(content, ragContext.getContextText(), memory);

        // 5. Query LLM synchronously
        long startTime = System.currentTimeMillis();
        String aiResponseText;
        try {
            aiResponseText = aiOrchestratorService.generate(prompt, Map.of());
            long latency = System.currentTimeMillis() - startTime;
            
            aiValidator.logAIRequest(userId, "/chat", "mistral", latency, aiResponseText, true);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("CHAT AI FAILURE: latency={}ms, error={}", latency, e.getMessage(), e);
            aiValidator.logAIRequest(userId, "/chat", "mistral", latency, e.getMessage(), false);
            aiResponseText = "I'm sorry, but an error occurred while connecting to my AI services: " + e.getMessage();
        }

        // 6. Save and return AI message
        ChatMessage aiMsg = new ChatMessage(finalSessionId, "AI", aiResponseText, ragContext.getCitations());
        messageRepository.save(aiMsg);

        return aiMsg;
    }

    /**
     * Sends a user message, performs RAG context retrieval, and returns an SseEmitter streaming token response.
     */
    public SseEmitter sendMessageStream(String userId, String sessionId, String content, List<String> sourceIds) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("ChatSession", sessionId));
        String finalSessionId = session.getId();
        
        LOG.info("STREAM_SESSION_FOUND sessionId={}", finalSessionId);

        if (sourceIds == null) sourceIds = new ArrayList<>();

        // 1. Save user message to database
        ChatMessage userMsg = new ChatMessage(finalSessionId, "USER", content);
        userMsg = messageRepository.save(userMsg);

        // Update session timestamp
        session.setUpdatedAt(Instant.now().toString());
        sessionRepository.save(session);

        // 2. Fetch Hybrid RAG Context
        List<DocumentChunk> chunks = retrievalService.retrieve(userId, content, sourceIds, 4);
        ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
        LOG.info("STREAM_RAG_COMPLETED");

        // 3. Load active conversation memory (with summaries)
        ConversationMemoryService.MemoryContext memory = memoryService.loadActiveMemory(finalSessionId, userMsg.getId());

        // 4. Build Prompt
        String prompt = buildPromptWithMemory(content, ragContext.getContextText(), memory);
        LOG.info("STREAM_PROMPT_CREATED prompt_length={}", prompt.length());

        // 5. Generate Reactive Flux Stream
        LOG.info("STREAM_MISTRAL_REQUEST_STARTED");
        Flux<String> tokenStream = aiOrchestratorService.generateStream(prompt, Map.of());

        // 6. Return Server-Sent Event SseEmitter, saving the finished message on completion
        return streamingResponseService.stream(
                tokenStream,
                finalText -> {
                    // Accumulation callback: Save finalized AI message to database
                    LOG.info("STREAM_DATABASE_SAVE");
                    try {
                        ChatMessage aiMsg = new ChatMessage(finalSessionId, "AI", finalText, ragContext.getCitations());
                        messageRepository.save(aiMsg);
                    } catch (Exception e) {
                        LOG.error("STREAM_FAILED Database save failed: {}", e.getMessage(), e);
                    }
                },
                () -> {
                    LOG.info("STREAM_COMPLETED for session ID: {}", finalSessionId);
                }
        );
    }

    public List<ChatMessage> getHistory(String userId, String sessionId) {
        try {
            sessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("ChatSession", sessionId));
        } catch (IllegalArgumentException e) {
            throw new com.lms.exception.ResourceNotFoundException("ChatSession", sessionId);
        }

        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private ChatSession resolveSession(String userId, String sessionId, String userContent) {
        if (sessionId == null || sessionId.isBlank()) {
            String title = userContent != null && userContent.length() > 30 ? userContent.substring(0, 27) + "..." : userContent;
            return createSession(userId, title != null ? title : "New Chat");
        } else {
            return sessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseGet(() -> {
                        String title = userContent != null && userContent.length() > 30 ? userContent.substring(0, 27) + "..." : userContent;
                        ChatSession session = new ChatSession(userId, title != null ? title : "New Chat");
                        session.setId(sessionId);
                        return sessionRepository.save(session);
                    });
        }
    }

    private String buildPromptWithMemory(String query, String contextText, ConversationMemoryService.MemoryContext memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("System: You are Khanmigo, an elite AI tutor. ground your responses ONLY in the provided study references. ");
        sb.append("If the references do not contain the answer, say clearly that you cannot find the answer in the provided study sources.\n");
        sb.append("ALWAYS cite reference documents in brackets matching the label, e.g. [algorithms_ch1.pdf, Page 42].\n\n");

        sb.append(contextText).append("\n");

        if (memory.getSummaryText() != null && !memory.getSummaryText().isBlank()) {
            sb.append("SUMMARY OF OLD CONVERSATION: ").append(memory.getSummaryText()).append("\n\n");
        }

        if (!memory.getDetailedMessages().isEmpty()) {
            sb.append("RECENT CONVERSATION HISTORY:\n");
            for (ChatMessage msg : memory.getDetailedMessages()) {
                sb.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Student Query: ").append(query).append("\n");
        sb.append("Tutor Response: ");

        return sb.toString();
    }
}
