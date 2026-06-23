package com.lms.repository;

import com.lms.model.Flashcard;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardRepository extends MongoRepository<Flashcard, String> {
    List<Flashcard> findByDeckId(String deckId);
    void deleteByDeckId(String deckId);
}
