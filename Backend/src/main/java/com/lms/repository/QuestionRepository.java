package com.lms.repository;

import com.lms.model.Question;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for quiz questions.
 */
@Repository
public interface QuestionRepository extends MongoRepository<Question, String> {

    /**
     * Find questions by learning concept.
     */
    List<Question> findByConcept(String concept);

    /**
     * Find questions by difficulty tier.
     */
    List<Question> findByDifficulty(int difficulty);

    /**
     * Find questions by concept and difficulty tier.
     */
    List<Question> findByConceptAndDifficulty(String concept, int difficulty);

    List<Question> findByConceptAndDifficultyAndSourceId(String concept, int difficulty, String sourceId);
}
