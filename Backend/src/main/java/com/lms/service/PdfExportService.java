package com.lms.service;

import com.lms.model.Flashcard;
import com.lms.model.FlashcardDeck;
import com.lms.model.Summary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class PdfExportService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfExportService.class);
    private final MinioStorageService minioStorageService;

    public PdfExportService(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    public String exportSummary(Summary summary) throws IOException {
        String filename = "summary_" + summary.getId() + ".pdf";
        String key = "exports/" + filename;
        Path tempFile = Files.createTempFile("summary-", ".pdf");

        try {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    PDType1Font fontBold16 = new PDType1Font(FontName.HELVETICA_BOLD);
                    PDType1Font fontBold14 = new PDType1Font(FontName.HELVETICA_BOLD);
                    PDType1Font fontReg12 = new PDType1Font(FontName.HELVETICA);

                    contentStream.beginText();
                    contentStream.setLeading(18.0f);
                    contentStream.newLineAtOffset(50, 700);

                    contentStream.setFont(fontBold16, 16);
                    contentStream.showText("Summary Document");
                    contentStream.newLine();
                    contentStream.newLine();

                    String text = summary.getSummary() != null ? summary.getSummary() : "No summary available";
                    drawWrappedText(contentStream, text, fontReg12, 12, 500);
                    
                    contentStream.newLine();

                    contentStream.setFont(fontBold14, 14);
                    contentStream.showText("Key Points:");
                    contentStream.newLine();
                    
                    if (summary.getKeyPoints() != null) {
                        for (String kp : summary.getKeyPoints()) {
                            drawWrappedText(contentStream, "- " + kp, fontReg12, 12, 500);
                        }
                    }

                    contentStream.endText();
                }
                document.save(tempFile.toFile());
            }

            minioStorageService.uploadLocalFile(key, tempFile, "application/pdf");
            LOG.info("Successfully exported summary to MinIO key: {}", key);
            return key;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public String exportFlashcards(FlashcardDeck deck, List<Flashcard> flashcards) throws IOException {
        String filename = "flashcards_" + deck.getId() + ".pdf";
        String key = "exports/" + filename;
        Path tempFile = Files.createTempFile("flashcards-", ".pdf");

        try {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    PDType1Font fontBold16 = new PDType1Font(FontName.HELVETICA_BOLD);
                    PDType1Font fontBold12 = new PDType1Font(FontName.HELVETICA_BOLD);
                    PDType1Font fontReg12 = new PDType1Font(FontName.HELVETICA);

                    contentStream.beginText();
                    contentStream.setLeading(18.0f);
                    contentStream.newLineAtOffset(50, 700);

                    String title = "Flashcard Deck: " + (deck.getTitle() != null ? deck.getTitle() : deck.getTopic());
                    contentStream.setFont(fontBold16, 16);
                    contentStream.showText(sanitizeForPdf(title));
                    contentStream.newLine();
                    contentStream.newLine();

                    if (flashcards != null) {
                        int linesCount = 0;
                        for (Flashcard f : flashcards) {
                            if (linesCount > 20) {
                                // Basic pagination check to avoid going out of bounds
                                contentStream.setFont(fontReg12, 12);
                                contentStream.showText("... (more cards omitted for brevity)");
                                break;
                            }
                            String front = f.getFront() != null ? f.getFront() : "";
                            drawWrappedText(contentStream, "Q: " + front, fontBold12, 12, 500);
                            linesCount += Math.max(1, (front.length() / 70));
                            
                            String back = f.getBack() != null ? f.getBack() : "";
                            drawWrappedText(contentStream, "A: " + back, fontReg12, 12, 500);
                            linesCount += Math.max(1, (back.length() / 70));
                            
                            contentStream.setFont(fontReg12, 12);
                            String diffText = "Difficulty: " + f.getEaseFactor() + " | Review: " + f.getNextReviewDate();
                            contentStream.showText(sanitizeForPdf(diffText));
                            contentStream.newLine();
                            contentStream.newLine();
                            linesCount += 2;
                        }
                    }

                    contentStream.endText();
                }
                document.save(tempFile.toFile());
            }

            minioStorageService.uploadLocalFile(key, tempFile, "application/pdf");
            LOG.info("Successfully exported flashcards to MinIO key: {}", key);
            return key;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String sanitizeForPdf(String text) {
        if (text == null) {
            return "";
        }
        text = text.replace("“", "\"")
                   .replace("”", "\"")
                   .replace("‘", "'")
                   .replace("’", "'")
                   .replace("—", "-")
                   .replace("–", "-")
                   .replace("\u00a0", " ")
                   .replace("\u2022", "-");
        
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private void drawWrappedText(PDPageContentStream contentStream, String text, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float maxWidth) throws IOException {
        contentStream.setFont(font, fontSize);
        String sanitized = sanitizeForPdf(text);
        String[] lines = sanitized.split("\\r?\\n", -1);
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                contentStream.newLine();
                continue;
            }
            String[] words = line.split(" ");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                float width = (font.getStringWidth(testLine) / 1000f) * fontSize;
                if (width > maxWidth) {
                    if (currentLine.length() > 0) {
                        contentStream.showText(currentLine.toString());
                        contentStream.newLine();
                        currentLine = new StringBuilder(word);
                    } else {
                        contentStream.showText(word);
                        contentStream.newLine();
                    }
                } else {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
            }
            if (currentLine.length() > 0) {
                contentStream.showText(currentLine.toString());
                contentStream.newLine();
            }
        }
    }
}
