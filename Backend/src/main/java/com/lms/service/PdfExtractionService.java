package com.lms.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class PdfExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfExtractionService.class);

    public String extractText(InputStream inputStream) {
        try (RandomAccessReadBuffer randomAccessReadBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(randomAccessReadBuffer)) {
             
            if (document.isEncrypted()) {
                LOG.warn("Cannot extract text from encrypted PDF.");
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            LOG.error("Failed to extract text from PDF", e);
            throw new RuntimeException("Failed to extract text from PDF", e);
        }
    }
}
