package com.lms.unit;

import com.lms.TestFixtures;
import com.lms.model.ResponseRecord;
import com.lms.model.TopicMastery;
import com.lms.repository.ResponseRecordRepository;
import com.lms.repository.TopicMasteryRepository;
import com.lms.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import org.bson.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsService — streak calculation, aggregation logic.
 * Uses Mockito to avoid MongoDB and Ollama dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService — Aggregation & Streak Calculation")
class AnalyticsServiceTest {

    @Mock private ResponseRecordRepository responseRepo;
    @Mock private TopicMasteryRepository   masteryRepo;
    @Mock private com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    @Mock private MongoTemplate            mongoTemplate;
    @Mock private com.lms.repository.StudentLearningProfileRepository profileRepo;
    @Mock private com.lms.repository.UserRepository userRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(responseRepo, masteryRepo, aiOrchestratorService, mongoTemplate, profileRepo, userRepository);
    }

    // ── Streak calculation ────────────────────────────────────────────────

    @Test
    @DisplayName("Empty history → streak is 0")
    void emptyHistoryStreakIsZero() {
        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("responses"), eq(Document.class)))
                .thenReturn(mockResults);

        int streak = service.calculateDailyStreak("user-1");
        assertThat(streak).isEqualTo(0);
    }

    @Test
    @DisplayName("Activity only today → streak is 1")
    void activityTodayStreakIsOne() {
        String todayStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.now());
        Document doc = new Document("_id", todayStr);

        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(doc));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("responses"), eq(Document.class)))
                .thenReturn(mockResults);

        int streak = service.calculateDailyStreak("user-1");
        assertThat(streak).isEqualTo(1);
    }

    // ── Analytics aggregation ─────────────────────────────────────────────

    @Test
    @DisplayName("getAnalytics returns required keys")
    void analyticsHasRequiredKeys() {
        when(responseRepo.countByUserId("user-1")).thenReturn(1L);
        when(responseRepo.countByUserIdAndCorrectTrue("user-1")).thenReturn(1L);
        when(masteryRepo.findByStudentId("user-1")).thenReturn(Collections.emptyList());

        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("responses"), eq(Document.class)))
                .thenReturn(mockResults);

        Map<String, Object> result = service.getAnalytics("user-1");

        assertThat(result).containsKeys(
                "totalAttempts", "correctAttempts", "accuracy", "streak",
                "strengths", "weaknesses", "aiInsight"
        );
    }

    @Test
    @DisplayName("Accuracy is 1.0 when all answers correct")
    void allCorrectAccuracyIsOne() {
        when(responseRepo.countByUserId("user-1")).thenReturn(2L);
        when(responseRepo.countByUserIdAndCorrectTrue("user-1")).thenReturn(2L);
        when(masteryRepo.findByStudentId("user-1")).thenReturn(Collections.emptyList());

        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("responses"), eq(Document.class)))
                .thenReturn(mockResults);

        Map<String, Object> result = service.getAnalytics("user-1");

        assertThat((Double) result.get("accuracy")).isEqualTo(1.0);
        assertThat((Long) result.get("correctAttempts")).isEqualTo(2L);
    }

}
