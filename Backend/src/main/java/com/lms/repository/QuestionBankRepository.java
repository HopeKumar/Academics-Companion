package com.lms.repository;

import com.lms.model.QuestionBank;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QuestionBankRepository extends MongoRepository<QuestionBank, String> {
    List<QuestionBank> findBySubject(String subject);
    List<QuestionBank> findBySubjectAndUnit(String subject, String unit);
    List<QuestionBank> findBySubjectAndDifficulty(String subject, String difficulty);
    List<QuestionBank> findByYear(int year);
}
