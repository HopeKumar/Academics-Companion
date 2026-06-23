package com.lms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lms.dto.ai.QuizQuestionDTO;
import com.lms.dto.ai.QuizResponse;
import com.lms.model.DocumentChunk;
import com.lms.model.Question;
import com.lms.model.ResponseRecord;
import com.lms.model.Source;
import com.lms.model.StudentLearningProfile;
import com.lms.model.StudentLevel;
import com.lms.model.TopicMastery;
import com.lms.repository.QuestionRepository;
import com.lms.repository.ResponseRecordRepository;
import com.lms.repository.SourceRepository;
import com.lms.repository.StudentLearningProfileRepository;
import com.lms.service.ai.AIOrchestratorService;

@Service
public class QuestionGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionGenerationService.class);

    private final QuestionRepository questionRepository;
    private final HybridRetrievalService retrievalService;
    private final AIOrchestratorService aiOrchestratorService;
    private final StudentLearningProfileRepository profileRepository;
    private final ResponseRecordRepository responseRepository;
    private final TopicMasteryService topicMasteryService;
    private final ContextBuilder contextBuilder;
    private final SourceRepository sourceRepository;

    @Autowired
    public QuestionGenerationService(QuestionRepository questionRepository,
                                     HybridRetrievalService retrievalService,
                                     AIOrchestratorService aiOrchestratorService,
                                     StudentLearningProfileRepository profileRepository,
                                     ResponseRecordRepository responseRepository,
                                     TopicMasteryService topicMasteryService,
                                     ContextBuilder contextBuilder,
                                     SourceRepository sourceRepository) {
        this.questionRepository = questionRepository;
        this.retrievalService   = retrievalService;
        this.aiOrchestratorService = aiOrchestratorService;
        this.profileRepository  = profileRepository;
        this.responseRepository = responseRepository;
        this.topicMasteryService = topicMasteryService;
        this.contextBuilder      = contextBuilder;
        this.sourceRepository    = sourceRepository;
    }

    public GeneratedQuestionWrapper generateQuestion(String userId, String concept, int difficulty, String sourceId, String type) {
        LOG.info("Generating {} question for concept={} difficulty={} sourceId={}", type, concept, difficulty, sourceId);

        String contextText = "";
        try {
            if (sourceId != null && !sourceId.isBlank()) {
                Source source = sourceRepository.findById(sourceId).orElse(null);
                if (source != null && source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
                    String context = source.getExtractedText();
                    if (context.length() > 2500) {
                        context = context.substring(0, 2500);
                    }
                    LOG.info("TRUNCATED_CONTEXT_LENGTH={}", context.length());
                    contextText = context;
                    LOG.info("QUIZ: Using source.getExtractedText() for sourceId: {}", sourceId);
                } else {
                    List<DocumentChunk> chunks = retrievalService.retrieve(userId, concept, List.of(sourceId), 2);
                    if (chunks != null && !chunks.isEmpty()) {
                        ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
                        contextText = ragContext.getContextText();
                    }
                    LOG.info("QUIZ: Using retrieved chunks for sourceId: {}", sourceId);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to retrieve context for quiz question: {}", e.getMessage());
        }

        LOG.info("SOURCE_ID={}", sourceId);
        LOG.info("CONTEXT_LENGTH={}", contextText.length());
        LOG.info("CONTEXT_PREVIEW={}", contextText.substring(0, Math.min(1000, contextText.length())));

        try {
            QuizResponse quizResponse = aiOrchestratorService.generateQuiz(contextText);
            if (quizResponse == null || quizResponse.questions() == null || quizResponse.questions().isEmpty()) {
                throw new com.lms.exception.AIServiceException("AI Quiz generation returned no questions");
            }

            Question targetQuestion = null;
            for (int i = 0; i < quizResponse.questions().size(); i++) {
                QuizQuestionDTO dto = quizResponse.questions().get(i);
                if (dto == null || dto.question() == null || dto.question().isBlank()) {
                    continue;
                }

                String questionText = dto.question().trim();
                List<String> optionsList = dto.options();
                if (optionsList == null) {
                    optionsList = new java.util.ArrayList<>();
                }

                String correctAns = dto.correctAnswer() != null ? dto.correctAnswer().trim() : "A";
                int correctIndex = -1;

                // 1. Try to find the matching option by text
                if (optionsList != null) {
                    for (int oIdx = 0; oIdx < optionsList.size(); oIdx++) {
                        String opt = optionsList.get(oIdx);
                        if (opt != null && opt.trim().equals(correctAns)) {
                            correctIndex = oIdx;
                            break;
                        }
                    }
                }

                // 2. If not found by text, check if correctAns is a letter (A, B, C, D)
                if (correctIndex < 0) {
                    String cleanAns = correctAns.replaceAll("[\\)\\.\\s]", "").toUpperCase();
                    switch (cleanAns) {
                        case "A": correctIndex = 0; break;
                        case "B": correctIndex = 1; break;
                        case "C": correctIndex = 2; break;
                        case "D": correctIndex = 3; break;
                    }
                }

                // 3. Fallback and warning if index is out of bounds or not found
                if (correctIndex < 0 || optionsList == null || correctIndex >= optionsList.size()) {
                    LOG.warn("Correct answer '{}' not found in options: {}", correctAns, optionsList);
                    correctIndex = 0;
                }

                String dbCorrectAnswer = String.valueOf((char) ('A' + correctIndex));

                String id = UUID.randomUUID().toString();
                Question question = new Question(
                        id,
                        questionText,
                        concept,
                        difficulty,
                        dbCorrectAnswer
                );
                question.setTopic(concept);
                question.setType(i == 0 ? type : "MCQ");
                question.setOptions(optionsList);
                question.setCorrectOptionIndex(correctIndex);
                question.setSourceId(sourceId);
                question.setGenerationSource("AI");
                question.setExplanation(dto.explanation());
                questionRepository.save(question);

                if (i == 0) {
                    targetQuestion = question;
                }
            }

            if (targetQuestion == null) {
                throw new com.lms.exception.AIServiceException("No valid questions could be constructed from AI response");
            }

            LOG.info("QUIZ_COMPLETED: sourceId={}", sourceId);
            LOG.info("QUIZ SOURCE = AI");
            return new GeneratedQuestionWrapper(targetQuestion, targetQuestion.getExplanation() != null ? targetQuestion.getExplanation() : "Generated via Mistral Quiz Service.");

        } catch (Exception e) {
            LOG.error("Failed to generate AI quiz question for concept={} difficulty={}, falling back to template-based question. Error: {}", concept, difficulty, e.getMessage());
            try {
                return getFallbackQuestion(concept, difficulty, contextText, sourceId);
            } catch (Exception fallbackEx) {
                LOG.error("Fallback quiz question generation failed", fallbackEx);
                throw new com.lms.exception.AIServiceException("Failed to generate quiz question and fallback failed: " + fallbackEx.getMessage());
            }
        }
    }

    public List<GeneratedQuestionWrapper> generateQuizForStudent(String studentId, String concept, String sourceId, int totalQuestions) {
        StudentLevel level = profileRepository.findByUserId(studentId)
                .map(StudentLearningProfile::getCurrentLevel)
                .orElse(StudentLevel.BEGINNER);
                
        int easyCount = 0;
        int mediumCount = 0;
        int hardCount = 0;
        
        switch (level) {
            case BEGINNER:
                easyCount = (int) (totalQuestions * 0.70);
                mediumCount = (int) (totalQuestions * 0.20);
                hardCount = totalQuestions - easyCount - mediumCount;
                break;
            case INTERMEDIATE:
                easyCount = (int) (totalQuestions * 0.30);
                mediumCount = (int) (totalQuestions * 0.50);
                hardCount = totalQuestions - easyCount - mediumCount;
                break;
            case ADVANCED:
                easyCount = (int) (totalQuestions * 0.10);
                mediumCount = (int) (totalQuestions * 0.30);
                hardCount = totalQuestions - easyCount - mediumCount;
                break;
        }

        List<String> weakTopics = topicMasteryService.getWeakTopics(studentId).stream()
                .map(TopicMastery::getTopic).collect(Collectors.toList());
        List<String> mistakeTopics = responseRepository.findByUserId(studentId).stream()
                .filter(r -> !r.isCorrect())
                .map(ResponseRecord::getConcept).collect(Collectors.toList());

        List<GeneratedQuestionWrapper> quiz = new ArrayList<>();
        
        quiz.addAll(assembleQuestions(studentId, concept, 1, sourceId, easyCount, weakTopics, mistakeTopics));
        quiz.addAll(assembleQuestions(studentId, concept, 2, sourceId, mediumCount, weakTopics, mistakeTopics));
        quiz.addAll(assembleQuestions(studentId, concept, 3, sourceId, hardCount, weakTopics, mistakeTopics));
        
        return quiz;
    }

    private List<GeneratedQuestionWrapper> assembleQuestions(String studentId, String baseConcept, int difficulty, String sourceId, int count, List<String> weakTopics, List<String> mistakeTopics) {
        List<GeneratedQuestionWrapper> results = new ArrayList<>();
        String[] types = {"MCQ", "TRUE_FALSE", "FILL_IN_THE_BLANK", "SCENARIO"};
        
        for (int i = 0; i < count; i++) {
            String targetConcept = baseConcept;
            if (i % 3 == 0 && !weakTopics.isEmpty()) {
                targetConcept = weakTopics.get((int)(Math.random() * weakTopics.size()));
            } else if (i % 3 == 1 && !mistakeTopics.isEmpty()) {
                targetConcept = mistakeTopics.get((int)(Math.random() * mistakeTopics.size()));
            }
            
            List<Question> existing = questionRepository.findByConceptAndDifficultyAndSourceId(targetConcept, difficulty, sourceId);
            if (!existing.isEmpty()) {
                Question q = existing.get((int)(Math.random() * existing.size()));
                results.add(new GeneratedQuestionWrapper(q, "Selected from Question Bank."));
                continue;
            }
            
            String type = types[i % types.length];
            results.add(generateQuestion(studentId, targetConcept, difficulty, sourceId, type));
        }
        return results;
    }

    private GeneratedQuestionWrapper getFallbackQuestion(String concept, int difficulty, String contextText, String sourceId) {
        Question q;
        String explanation;
        String id = UUID.randomUUID().toString();

        if (contextText != null && !contextText.isBlank()) {
            String snippet = contextText.substring(0, Math.min(contextText.length(), 200));
            q = new Question(id,
                    "Based on the text: '" + snippet + "...' What is the primary concept being discussed?",
                    concept, difficulty, "A");
            q.setOptions(List.of(concept, "Something else", "Unknown", "None of the above"));
            q.setCorrectOptionIndex(0);
            explanation = "This is a fallback question generated from the text because AI generation failed.";
        } else {
            q = new Question(id,
                    "What is the core idea of " + concept + "?",
                    concept, difficulty, "A");
            q.setOptions(List.of("The main topic", "Secondary idea", "Unrelated concept", "Nothing"));
            q.setCorrectOptionIndex(0);
            explanation = "This is a generic fallback question because AI generation and context extraction both failed.";
        }

        q.setTopic(concept);
        q.setType("MCQ");
        q.setSourceId(sourceId != null && !sourceId.isBlank() ? sourceId : "fallback-source-id");
        q.setGenerationSource("FALLBACK");
        questionRepository.save(q);
        LOG.info("QUIZ SAVED {}", sourceId);
        LOG.info("QUIZ SOURCE = FALLBACK");
        return new GeneratedQuestionWrapper(q, explanation);
    }

    public static class GeneratedQuestionWrapper {
        private final Question question;
        private final String   explanation;

        public GeneratedQuestionWrapper(Question question, String explanation) {
            this.question    = question;
            this.explanation = explanation;
        }

        public Question getQuestion()    { return question; }
        public String   getExplanation() { return explanation; }
    }
}
