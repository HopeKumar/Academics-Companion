package com.lms.controller;

import com.lms.model.AIResult;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.SlideService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.lms.config.FeaturesProperties;
import com.lms.model.SlideDeckResponse;

@RestController
@RequestMapping({"/slides", "/api/v1/slides"})
public class SlideController {

    private final SlideService slideService;
    private final AuthService authService;
    private final FeaturesProperties features;

    public SlideController(SlideService slideService, AuthService authService, FeaturesProperties features) {
        this.slideService = slideService;
        this.authService = authService;
        this.features = features;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateSlides(@RequestBody Map<String, String> payload) {
        if (!features.isSlidesEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String topic = payload.get("topic");
        String sourceId = payload.get("sourceId");

        try {
            AIResult<List<SlideDeckResponse>> slidesResult = slideService.generateSlides(user.getId(), topic, sourceId);
            return ResponseEntity.ok(slidesResult);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
