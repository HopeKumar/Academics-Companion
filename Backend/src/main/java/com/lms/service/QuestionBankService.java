package com.lms.service;

import com.lms.model.QuestionBank;
import com.lms.repository.QuestionBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionBankService {

    private final QuestionBankRepository repository;

    @Autowired
    public QuestionBankService(QuestionBankRepository repository) {
        this.repository = repository;
    }

    public QuestionBank uploadQuestion(QuestionBank question) {
        return repository.save(question);
    }

    public List<QuestionBank> uploadQuestions(List<QuestionBank> questions) {
        return repository.saveAll(questions);
    }

    public List<QuestionBank> searchQuestions(String subject, String unit, String difficulty, Integer year) {
        List<QuestionBank> all = repository.findAll();
        
        return all.stream()
            .filter(q -> subject == null || subject.equalsIgnoreCase(q.getSubject()))
            .filter(q -> unit == null || unit.equalsIgnoreCase(q.getUnit()))
            .filter(q -> difficulty == null || difficulty.equalsIgnoreCase(q.getDifficulty()))
            .filter(q -> year == null || year.equals(q.getYear()))
            .collect(Collectors.toList());
    }

    public List<QuestionBank> generateQuiz(String subject, String unit, int count) {
        List<QuestionBank> filtered = searchQuestions(subject, unit, null, null);
        java.util.Collections.shuffle(filtered);
        return filtered.stream().limit(count).collect(Collectors.toList());
    }
}
