package com.lms.service;

import com.lms.model.QuestionBank;
import com.lms.repository.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository repository;

    @InjectMocks
    private QuestionBankService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSearchQuestions() {
        QuestionBank q1 = new QuestionBank();
        q1.setSubject("Math");
        q1.setDifficulty("EASY");
        
        QuestionBank q2 = new QuestionBank();
        q2.setSubject("Science");
        
        when(repository.findAll()).thenReturn(Arrays.asList(q1, q2));
        
        List<QuestionBank> result = service.searchQuestions("Math", null, null, null);
        
        assertEquals(1, result.size());
        assertEquals("Math", result.get(0).getSubject());
    }
}
