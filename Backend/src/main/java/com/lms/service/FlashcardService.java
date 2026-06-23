package com.lms.service;

import com.lms.dto.ai.FlashcardDTO;
import com.lms.dto.ai.FlashcardsResponse;
import com.lms.model.AIResult;
import com.lms.model.DocumentChunk;
import com.lms.model.Flashcard;
import com.lms.model.FlashcardDeck;
import com.lms.repository.FlashcardDeckRepository;
import com.lms.repository.FlashcardRepository;
import com.lms.service.ai.AIOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import com.lms.model.JobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.resilience4j.retry.annotation.Retry;
import com.lms.repository.SourceRepository;
import com.lms.service.ai.provider.PromptManager;
import com.lms.model.Source;

@Service
public class FlashcardService {

    private static final Logger LOG = LoggerFactory.getLogger(FlashcardService.class);

    private final FlashcardRepository     flashcardRepository;
    private final FlashcardDeckRepository deckRepository;
    private final HybridRetrievalService  retrievalService;
    private final ContextBuilder          contextBuilder;
    private final AIOrchestratorService   aiOrchestratorService;
    private final SpacedRepetitionScheduler sm2;
    private final JobStatusService jobStatusService;
    private final SourceRepository        sourceRepository;
    private final PromptManager           promptManager;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            FlashcardDeckRepository deckRepository,
                            HybridRetrievalService retrievalService,
                            ContextBuilder contextBuilder,
                            AIOrchestratorService aiOrchestratorService,
                            SpacedRepetitionScheduler sm2,
                            JobStatusService jobStatusService,
                            SourceRepository sourceRepository,
                            PromptManager promptManager) {
        this.flashcardRepository = flashcardRepository;
        this.deckRepository      = deckRepository;
        this.retrievalService    = retrievalService;
        this.contextBuilder      = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.sm2                 = sm2;
        this.jobStatusService    = jobStatusService;
        this.sourceRepository    = sourceRepository;
        this.promptManager       = promptManager;
    }

    @Async
    public void generateDeckAsync(String userId, String topic, String sourceId) {
        jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "PROCESSING", "Generating flashcards...");
        try {
            AIResult<FlashcardDeck> result = generateDeck(userId, topic, sourceId);
            if (result.isSuccess()) {
                jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "COMPLETED", "Flashcards generated successfully");
            } else {
                jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "FAILED", result.getError());
            }
        } catch (Exception e) {
            LOG.error("Async flashcard generation failed for sourceId {}: {}", sourceId, e.getMessage());
            jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "FAILED", e.getMessage());
        }
    }

    @Retry(name = "aiService", fallbackMethod = "generateDeckFallback")
    public AIResult<FlashcardDeck> generateDeck(String userId, String topic, String sourceId) {
        LOG.info("[FLASHCARD_START] REQUEST received for userId={} sourceId={}", userId, sourceId);
        // Return cached deck if one already exists for this source and has cards
        if (sourceId != null) {
            java.util.Optional<FlashcardDeck> existing = deckRepository.findByUserIdAndSourceId(userId, sourceId);
            if (existing.isPresent()) {
                List<Flashcard> cards = flashcardRepository.findByDeckId(existing.get().getId());
                if (cards != null && !cards.isEmpty()) {
                    LOG.info("Returning cached flashcard deck with {} cards for userId={} sourceId={}", cards.size(), userId, sourceId);
                    return AIResult.success(existing.get(), "Mistral", "mistral", 0);
                } else {
                    LOG.warn("Cached deck present but has 0 cards. Deleting it to trigger fresh generation.");
                    deckRepository.delete(existing.get());
                }
            }
        }

        long startTime = System.currentTimeMillis();
        String documentName = "Unknown Source";
        Source source = null;
        if (sourceId != null) {
            source = sourceRepository.findById(sourceId).orElse(null);
            if (source != null) {
                documentName = source.getName();
            }
        }

        String contextText = "";
        if (source != null && source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
            String context = source.getExtractedText();
            if (context.length() > 1500) {
                context = context.substring(0, 1500);
            }
            LOG.info("TRUNCATED_CONTEXT_LENGTH={}", context.length());
            contextText = context;
            LOG.info("FLASHCARD: Using source.getExtractedText() for sourceId: {}", sourceId);
        } else {
            List<DocumentChunk> chunks = retrievalService.retrieve(userId, topic,
                    sourceId != null ? List.of(sourceId) : List.of(), 5);
            ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
            contextText = ragContext.getContextText();
            LOG.info("FLASHCARD: Using retrieved chunks for sourceId: {}", sourceId);
        }

        int combinedTextLength = contextText != null ? contextText.length() : 0;

        LOG.info("Before calling AI - sourceId: {}, document title: {}, combined text length: {}",
                sourceId, documentName, combinedTextLength);

        LOG.info("[FLASHCARD_SOURCE_ID] sourceId: {}", sourceId);
        LOG.info("[FLASHCARD_DOCUMENT_NAME] name: {}", documentName);
        LOG.info("[FLASHCARD_CHARS_SENT_TO_AI] chars: {}", combinedTextLength);

        LOG.info("SOURCE_ID={}", sourceId);
        LOG.info("CONTEXT_LENGTH={}", combinedTextLength);
        LOG.info("CONTEXT_PREVIEW={}", contextText.substring(0, Math.min(1000, combinedTextLength)));

        String fullPrompt = promptManager.getFlashcardPrompt() + "\n\nText:\n" + contextText;
        String preview = fullPrompt.length() > 500 ? fullPrompt.substring(0, 500) + "..." : fullPrompt;
        LOG.info("[FLASHCARD_PROMPT_PREVIEW] promptPreview: {}", preview);

        try {
            FlashcardsResponse response = aiOrchestratorService.generateFlashcards(contextText);
            long latency = System.currentTimeMillis() - startTime;

            if (response == null || response.flashcards() == null || response.flashcards().isEmpty()) {
                LOG.error("[FLASHCARD_ERROR] AI returned an empty or null flashcard list. Topic: {}, UserId: {}, SourceId: {}", topic, userId, sourceId);
                throw new com.lms.exception.AIServiceException("Flashcard generation failed: AI returned 0 flashcards.");
            }

            FlashcardDeck deck = new FlashcardDeck(userId, sourceId, topic, topic + " Flashcards");
            deck.setGenerationSource("AI");
            deck = deckRepository.save(deck);

            List<Flashcard> flashcards = new ArrayList<>();
            for (FlashcardDTO cardDto : response.flashcards()) {
                String front = cardDto.topic() != null ? cardDto.topic() : "Unknown topic";
                String back  = cardDto.explanation() != null ? cardDto.explanation() : "Unknown explanation";
                flashcards.add(new Flashcard(deck.getId(), front, back, topic));
            }
            flashcardRepository.saveAll(flashcards);
            LOG.info("FLASHCARDS_COMPLETED: sourceId={}", sourceId);
            LOG.info("FLASHCARD SOURCE = AI");
            LOG.info("[FLASHCARD_COMPLETE] Generated deck in {}ms: deckId={} userId={} topic={} cards={}", latency, deck.getId(), userId, topic, flashcards.size());
            
            return AIResult.success(deck, "Mistral", "mistral", latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("Failed to generate flashcards: {}", e.getMessage(), e);
            throw new com.lms.exception.AIServiceException("Flashcard generation failed: " + e.getMessage());
        }
    }

    public AIResult<FlashcardDeck> generateDeckFallback(String userId, String topic, String sourceId, Throwable t) {
        LOG.error("FLASHCARD FALLBACK TRIGGERED. Root cause class={}, message={}", 
                t.getClass().getSimpleName(), t.getMessage(), t);
        throw new com.lms.exception.AIServiceException("Flashcard generation failed: " + t.getMessage(), t);
    }

    public List<FlashcardDeck> getDecksForUser(String userId) {
        return deckRepository.findByUserId(userId);
    }

    public List<Flashcard> getCardsForDeck(String deckId) {
        return flashcardRepository.findByDeckId(deckId);
    }

    public List<Flashcard> getDueCards(String userId) {
        List<FlashcardDeck> decks = deckRepository.findByUserId(userId);
        List<Flashcard> allDue = new ArrayList<>();

        for (FlashcardDeck deck : decks) {
            List<Flashcard> cards = flashcardRepository.findByDeckId(deck.getId());
            cards.stream()
                    .filter(sm2::isDue)
                    .forEach(allDue::add);
        }

        LOG.info("getDueCards: userId={} dueCards={}", userId, allDue.size());
        return allDue;
    }

    public Map<String, Object> getDeckStats(String deckId) {
        List<Flashcard> cards = flashcardRepository.findByDeckId(deckId);
        if (cards.isEmpty()) {
            return Map.of("deckId", deckId, "totalCards", 0, "dueCards", 0,
                    "averageEaseFactor", 0.0, "message", "Deck is empty");
        }

        long dueCount    = cards.stream().filter(sm2::isDue).count();
        double avgEF     = cards.stream().mapToDouble(Flashcard::getEaseFactor).average().orElse(2.5);
        long   mature    = cards.stream().filter(c -> c.getInterval() >= 21).count();
        long   young     = cards.stream().filter(c -> c.getInterval() > 0 && c.getInterval() < 21).count();
        long   newCards  = cards.stream().filter(c -> c.getRepetitions() == 0).count();

        return Map.of(
                "deckId",             deckId,
                "totalCards",         cards.size(),
                "dueCards",           dueCount,
                "averageEaseFactor",  Math.round(avgEF * 100.0) / 100.0,
                "matureCards",        mature,
                "youngCards",         young,
                "newCards",           newCards
        );
    }

    public Flashcard reviewFlashcard(String flashcardId, int quality) {
        if (flashcardId == null) {
            throw new com.lms.exception.ResourceNotFoundException("Flashcard", "null");
        }
        
        Flashcard card = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("Flashcard", flashcardId));

        if (card.getDeckId() == null) {
            throw new com.lms.exception.ResourceNotFoundException("Deck", "null");
        }
        
        deckRepository.findById(card.getDeckId())
                .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("Deck", card.getDeckId()));

        Flashcard updated = sm2.scheduleNext(card, quality);
        flashcardRepository.save(updated);
        LOG.debug("Flashcard reviewed: id={} quality={} nextReview={}", flashcardId, quality, updated.getNextReviewDate());
        return updated;
    }
}
