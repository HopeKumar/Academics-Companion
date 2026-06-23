package com.lms.repository;

import com.lms.model.FlashcardDeck;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Optional;

@Repository
public interface FlashcardDeckRepository extends MongoRepository<FlashcardDeck, String> {
    List<FlashcardDeck> findByUserId(String userId);
    Optional<FlashcardDeck> findByUserIdAndSourceId(String userId, String sourceId);
    List<FlashcardDeck> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);
}
