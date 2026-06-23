package com.lms.health;

import com.lms.service.OCRService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ocr")
public class OcrHealthIndicator implements HealthIndicator {

    private final OCRService ocrService;

    public OcrHealthIndicator(OCRService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public Health health() {
        if (ocrService.isAvailable()) {
            return Health.up().withDetail("message", "Tesseract OCR is available").build();
        } else {
            return Health.down().withDetail("message", "Tesseract OCR is unavailable").build();
        }
    }
}
