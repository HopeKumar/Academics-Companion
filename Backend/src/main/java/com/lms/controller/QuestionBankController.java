package com.lms.controller;

import com.lms.model.QuestionBank;
import com.lms.service.QuestionBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/question-bank", "/api/v1/question-bank"})
public class QuestionBankController {

    private final QuestionBankService service;

    @Autowired
    public QuestionBankController(QuestionBankService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<List<QuestionBank>> uploadQuestions(@RequestBody List<QuestionBank> questions) {
        return ResponseEntity.ok(service.uploadQuestions(questions));
    }

    @GetMapping("/search")
    public ResponseEntity<List<QuestionBank>> searchQuestions(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(service.searchQuestions(subject, unit, difficulty, year));
    }

    @GetMapping("/generate")
    public ResponseEntity<List<QuestionBank>> generateQuiz(
            @RequestParam String subject,
            @RequestParam(required = false) String unit,
            @RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(service.generateQuiz(subject, unit, count));
    }
}
