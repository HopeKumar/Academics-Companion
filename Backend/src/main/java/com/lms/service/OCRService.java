package com.lms.service;

import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

@Service
public class OCRService {

    private static final Logger LOG = LoggerFactory.getLogger(OCRService.class);
    private final Tesseract tesseract;
    private boolean isAvailable = false;

    public OCRService() {
        this.tesseract = new Tesseract();
        try {
            String datapath = System.getenv("TESSDATA_PREFIX") != null 
                    ? System.getenv("TESSDATA_PREFIX") 
                    : "/usr/share/tessdata";
            tesseract.setDatapath(datapath);
            tesseract.setLanguage("eng");
            
            // Try to perform a dummy OCR to verify native library and tessdata are loaded
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
            tesseract.doOCR(img);
            isAvailable = true;
            LOG.info("Tesseract OCR successfully initialized and available.");
        } catch (Throwable t) {
            LOG.warn("Tesseract OCR is not available (native libraries or tessdata missing). OCR fallbacks will be skipped. Error: {}", t.getMessage());
            isAvailable = false;
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public String extractTextFromImage(InputStream imageStream) {
        if (!isAvailable) {
            LOG.warn("OCR requested but Tesseract is not available. Skipping OCR.");
            return "";
        }
        try {
            LOG.info("OCR_STARTED");
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IllegalArgumentException("Could not read image stream");
            }
            String result = tesseract.doOCR(image);
            LOG.info("OCR_COMPLETED");
            return result;
        } catch (Throwable e) {
            LOG.error("OCR extraction failed", e);
            throw new RuntimeException("OCR processing failed: " + e.getMessage(), e);
        }
    }
}
