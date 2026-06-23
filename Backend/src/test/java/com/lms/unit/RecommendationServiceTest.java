package com.lms.unit;

import com.lms.TestFixtures;
import com.lms.model.TopicMastery;
import com.lms.repository.TopicMasteryRepository;
import com.lms.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationService — risk profiling, action builder,
 * system insight generation. All repository calls are mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService — Risk Profiling & Recommendations")
class RecommendationServiceTest {

    @Mock private TopicMasteryRepository masteryRepo;
    @Mock private com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(masteryRepo, aiOrchestratorService, objectMapper);
    }

    @Test
    @DisplayName("No mastery data → default 'start first quiz' action returned")
    void noDataReturnsDefault() {
        when(masteryRepo.findByStudentId("user-1")).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.recommend("user-1");

        assertThat(result).containsKey("weakTopics");
        assertThat(result).containsKey("recommendedActions");
        assertThat(result).containsKey("systemInsight");

        @SuppressWarnings("unchecked")
        List<Object> weakTopics = (List<Object>) result.get("weakTopics");
        assertThat(weakTopics).isEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("recommendedActions");
        assertThat(actions).isNotEmpty();
    }

    @Test
    @DisplayName("Weak topic (confidence < 0.65) appears in weakTopics list")
    void weakTopicIsIdentified() {
        TopicMastery weak = TestFixtures.weakTopic("user-1", "calculus");
        when(masteryRepo.findByStudentId("user-1")).thenReturn(List.of(weak));

        Map<String, Object> result = service.recommend("user-1");

        @SuppressWarnings("unchecked")
        List<String> weakTopics = (List<String>) result.get("weakTopics");
        assertThat(weakTopics).contains("calculus");
    }

    @Test
    @DisplayName("Mastered topic (confidence > 0.80) does NOT appear in weakTopics")
    void masteredTopicNotInWeakList() {
        TopicMastery mastered = TestFixtures.masteredTopic("user-1", "algebra");
        when(masteryRepo.findByStudentId("user-1")).thenReturn(List.of(mastered));

        Map<String, Object> result = service.recommend("user-1");

        @SuppressWarnings("unchecked")
        List<String> weakTopics = (List<String>) result.get("weakTopics");
        assertThat(weakTopics).doesNotContain("algebra");
    }

    @Test
    @DisplayName("All mastered → system insight mentions all topics mastered")
    void allMasteredInsight() {
        TopicMastery m1 = TestFixtures.masteredTopic("user-1", "algebra");
        TopicMastery m2 = TestFixtures.masteredTopic("user-1", "calculus");
        when(masteryRepo.findByStudentId("user-1")).thenReturn(List.of(m1, m2));

        Map<String, Object> result = service.recommend("user-1");
        String insight = (String) result.get("systemInsight");

        assertThat(insight).containsIgnoringCase("master");
    }

    @Test
    @DisplayName("Mastery snapshot contains all topics with risk and confidence")
    void masterySnapshotIsComplete() {
        TopicMastery m = TestFixtures.weakTopic("user-1", "physics");
        when(masteryRepo.findByStudentId("user-1")).thenReturn(List.of(m));

        Map<String, Object> result = service.recommend("user-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshot = (List<Map<String, Object>>) result.get("masterySnapshot");
        assertThat(snapshot).isNotEmpty();
        Map<String, Object> entry = snapshot.get(0);
        assertThat(entry).containsKeys("topic", "accuracy", "confidence", "trend", "tier", "risk", "riskReason");
    }

    @Test
    @DisplayName("Recommended actions have required fields: topic, action, priority")
    void recommendedActionsHaveRequiredFields() {
        TopicMastery weak = TestFixtures.weakTopic("user-1", "chemistry");
        when(masteryRepo.findByStudentId("user-1")).thenReturn(List.of(weak));

        Map<String, Object> result = service.recommend("user-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("recommendedActions");
        for (Map<String, Object> action : actions) {
            assertThat(action).containsKeys("topic", "action", "priority");
        }
    }

    @Test
    @DisplayName("Maximum 3 weak topics returned even when more exist")
    void maxThreeWeakTopics() {
        List<TopicMastery> many = List.of(
                TestFixtures.weakTopic("user-1", "t1"),
                TestFixtures.weakTopic("user-1", "t2"),
                TestFixtures.weakTopic("user-1", "t3"),
                TestFixtures.weakTopic("user-1", "t4"),
                TestFixtures.weakTopic("user-1", "t5")
        );
        when(masteryRepo.findByStudentId("user-1")).thenReturn(many);

        Map<String, Object> result = service.recommend("user-1");

        @SuppressWarnings("unchecked")
        List<String> weakTopics = (List<String>) result.get("weakTopics");
        assertThat(weakTopics).hasSizeLessThanOrEqualTo(3);
    }
}
