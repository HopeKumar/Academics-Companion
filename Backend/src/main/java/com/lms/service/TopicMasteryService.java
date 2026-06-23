package com.lms.service;

import com.lms.model.TopicMastery;
import com.lms.repository.TopicMasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Per-student topic mastery service.
 *
 * Fixed:
 *   - Broken SLF4J format strings "{:.2f}" replaced with "{}".
 *     SLF4J uses {} placeholders — it does not support printf-style formatting.
 *     String.format() used instead for the formatted values.
 *   - All update logic (withCorrectAnswer / withWrongAnswer) PRESERVED EXACTLY
 */
@Service
public class TopicMasteryService {

    private static final Logger LOG = LoggerFactory.getLogger(TopicMasteryService.class);

    private final TopicMasteryRepository masteryRepo;

    @Autowired
    public TopicMasteryService(TopicMasteryRepository masteryRepo) {
        this.masteryRepo = masteryRepo;
    }

    // ── Record correct answer ─────────────────────────────────────────────

    public TopicMastery recordCorrect(String studentId, String topic) {
        TopicMastery updated;
        try {
            TopicMastery current = findOrCreate(studentId, topic);
            updated = current.withCorrectAnswer();
            masteryRepo.save(updated);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            LOG.warn("Duplicate key collision for studentId={} topic={} in recordCorrect. Retrying.", studentId, topic);
            TopicMastery current = findOrCreate(studentId, topic);
            updated = current.withCorrectAnswer();
            masteryRepo.save(updated);
        }
        LOG.debug("MASTERY UPDATE correct student={} topic={} confidence={} accuracy={}",
                studentId, topic,
                String.format("%.2f", updated.getConfidenceScore()),
                String.format("%.2f", updated.getAccuracy()));
        return updated;
    }

    // ── Record wrong answer ───────────────────────────────────────────────

    public TopicMastery recordWrong(String studentId, String topic) {
        TopicMastery updated;
        try {
            TopicMastery current = findOrCreate(studentId, topic);
            updated = current.withWrongAnswer();
            masteryRepo.save(updated);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            LOG.warn("Duplicate key collision for studentId={} topic={} in recordWrong. Retrying.", studentId, topic);
            TopicMastery current = findOrCreate(studentId, topic);
            updated = current.withWrongAnswer();
            masteryRepo.save(updated);
        }
        LOG.debug("MASTERY UPDATE wrong student={} topic={} confidence={} accuracy={}",
                studentId, topic,
                String.format("%.2f", updated.getConfidenceScore()),
                String.format("%.2f", updated.getAccuracy()));
        return updated;
    }

    // ── Get current mastery ───────────────────────────────────────────────

    public TopicMastery getMastery(String studentId, String topic) {
        return findOrCreate(studentId, topic);
    }

    // ── Get all mastery for student ───────────────────────────────────────

    public List<TopicMastery> getAllMastery(String studentId) {
        return masteryRepo.findByStudentId(studentId);
    }

    // ── Get all mastery by subject ────────────────────────────────────────

    public List<TopicMastery> getMasteryBySubject(String subject) {
        return masteryRepo.findBySubject(subject);
    }

    // ── Get weak topics ───────────────────────────────────────────────────

    public List<TopicMastery> getWeakTopics(String studentId) {
        return masteryRepo.findByStudentIdAndMasteryScoreLessThanEqual(studentId, 40.0);
    }

    // ── Get strong topics ─────────────────────────────────────────────────

    public List<TopicMastery> getStrongTopics(String studentId) {
        return masteryRepo.findByStudentIdAndMasteryScoreGreaterThanEqual(studentId, 71.0);
    }

    // ── findOrCreate pattern (PRESERVED from original) ───────────────────

    private TopicMastery findOrCreate(String studentId, String topic) {
        return masteryRepo.findByStudentIdAndTopic(studentId, topic)
                .orElse(TopicMastery.create(studentId, "Unknown", topic));
    }
}
