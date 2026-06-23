package com.lms.service;

import com.lms.model.StudentLearningProfile;
import com.lms.model.StudentLevel;
import com.lms.repository.StudentLearningProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdaptiveLearningServiceTest {

    @Mock
    private StudentLearningProfileRepository repository;

    @InjectMocks
    private AdaptiveLearningService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetProfile_NewUser() {
        when(repository.findByUserId("user1")).thenReturn(Optional.empty());
        when(repository.save(any(StudentLearningProfile.class))).thenAnswer(i -> i.getArgument(0));

        StudentLearningProfile profile = service.getProfile("user1");
        
        assertEquals("user1", profile.getUserId());
        assertEquals(StudentLevel.BEGINNER, profile.getCurrentLevel());
    }

    @Test
    void testTrackQuizPerformance_LevelUp() {
        StudentLearningProfile profile = new StudentLearningProfile();
        profile.setUserId("user1");
        profile.setCurrentLevel(StudentLevel.BEGINNER);
        
        when(repository.findByUserId("user1")).thenReturn(Optional.of(profile));
        when(repository.save(any(StudentLearningProfile.class))).thenAnswer(i -> i.getArgument(0));

        StudentLearningProfile updated = service.trackQuizPerformance("user1", 100.0);
        
        assertEquals(StudentLevel.INTERMEDIATE, updated.getCurrentLevel());
    }
}
