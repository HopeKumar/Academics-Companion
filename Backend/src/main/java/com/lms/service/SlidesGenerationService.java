package com.lms.service;

import com.lms.model.SlideExport;
import com.lms.model.Summary;
import com.lms.model.Flashcard;
import com.lms.model.FlashcardDeck;
import com.lms.repository.SlideExportRepository;
import com.lms.repository.SummaryRepository;
import com.lms.repository.FlashcardDeckRepository;
import com.lms.repository.FlashcardRepository;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.ObjectProvider;

@Service
public class SlidesGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(SlidesGenerationService.class);

    private final SlideExportRepository slideExportRepository;
    private final SummaryRepository summaryRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;
    private final MinioStorageService minioStorageService;
    private final ObjectProvider<SlidesGenerationService> selfProvider;

    public SlidesGenerationService(SlideExportRepository slideExportRepository,
                                   SummaryRepository summaryRepository,
                                   FlashcardDeckRepository deckRepository,
                                   FlashcardRepository flashcardRepository,
                                   MinioStorageService minioStorageService,
                                   ObjectProvider<SlidesGenerationService> selfProvider) {
        this.slideExportRepository = slideExportRepository;
        this.summaryRepository = summaryRepository;
        this.deckRepository = deckRepository;
        this.flashcardRepository = flashcardRepository;
        this.minioStorageService = minioStorageService;
        this.selfProvider = selfProvider;
    }

    public String submitSlideGeneration(String userId, String sourceId) {
        SlideExport export = slideExportRepository.findByUserIdAndSourceId(userId, sourceId).orElse(new SlideExport(userId, sourceId));
        export.setStatus("PROCESSING");
        export = slideExportRepository.save(export);

        selfProvider.getIfAvailable().generateExportAsync(export.getId(), userId, sourceId);
        return export.getId();
    }

    @Async
    public CompletableFuture<SlideExport> generateExportAsync(String exportId, String userId, String sourceId) {
        SlideExport export = slideExportRepository.findById(exportId).orElse(null);
        if (export == null) return CompletableFuture.completedFuture(null);

        Path tempPptFile = null;
        Path tempPdfFile = null;
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            
            // Fetch Data
            Summary summary = summaryRepository.findBySourceId(sourceId).orElse(null);
            FlashcardDeck deck = deckRepository.findByUserIdAndSourceId(userId, sourceId).orElse(null);
            List<Flashcard> flashcards = deck != null ? flashcardRepository.findByDeckId(deck.getId()) : null;

            // 1. Title Slide
            XSLFSlide titleSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE));
            titleSlide.getPlaceholder(0).setText("Generated Presentation");
            titleSlide.getPlaceholder(1).setText("Source ID: " + sourceId);

            // 2. Overview Slide
            XSLFSlide overviewSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            overviewSlide.getPlaceholder(0).setText("Overview");
            XSLFTextShape overviewBody = overviewSlide.getPlaceholder(1);
            overviewBody.clearText();
            if (summary != null && summary.getSummary() != null) {
                overviewBody.addNewTextParagraph().addNewTextRun().setText(
                        summary.getSummary().length() > 300 ? summary.getSummary().substring(0, 300) + "..." : summary.getSummary()
                );
            } else {
                overviewBody.addNewTextParagraph().addNewTextRun().setText("No summary available.");
            }

            // 3. Key Concepts / Bullet Points
            XSLFSlide conceptsSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            conceptsSlide.getPlaceholder(0).setText("Key Concepts");
            XSLFTextShape conceptsBody = conceptsSlide.getPlaceholder(1);
            conceptsBody.clearText();
            if (summary != null && summary.getKeyPoints() != null && !summary.getKeyPoints().isEmpty()) {
                for (String kp : summary.getKeyPoints()) {
                    conceptsBody.addNewTextParagraph().addNewTextRun().setText(kp);
                }
            } else {
                conceptsBody.addNewTextParagraph().addNewTextRun().setText("No key concepts extracted.");
            }

            // 4. Definitions Slide
            XSLFSlide definitionsSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            definitionsSlide.getPlaceholder(0).setText("Definitions");
            XSLFTextShape defBody = definitionsSlide.getPlaceholder(1);
            defBody.clearText();
            if (flashcards != null && !flashcards.isEmpty()) {
                int count = 0;
                for (Flashcard f : flashcards) {
                    if (count++ >= 4) break; // limit to 4 per slide
                    defBody.addNewTextParagraph().addNewTextRun().setText("Q: " + f.getFront() + " | A: " + f.getBack());
                }
            } else {
                defBody.addNewTextParagraph().addNewTextRun().setText("No flashcards found.");
            }

            // 5. Applications Slide
            XSLFSlide applicationsSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            applicationsSlide.getPlaceholder(0).setText("Applications");
            XSLFTextShape appsBody = applicationsSlide.getPlaceholder(1);
            appsBody.clearText();
            if (summary != null && summary.getNextSteps() != null && !summary.getNextSteps().isEmpty()) {
                for (String app : summary.getNextSteps()) {
                    appsBody.addNewTextParagraph().addNewTextRun().setText(app);
                }
            } else {
                appsBody.addNewTextParagraph().addNewTextRun().setText("No applications/next steps available.");
            }

            // 6. Revision Notes Slide
            XSLFSlide revisionSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            revisionSlide.getPlaceholder(0).setText("Revision Notes");
            XSLFTextShape revisionBody = revisionSlide.getPlaceholder(1);
            revisionBody.clearText();
            if (summary != null && summary.getImportantTakeaways() != null && !summary.getImportantTakeaways().isEmpty()) {
                for (String takeaway : summary.getImportantTakeaways()) {
                    revisionBody.addNewTextParagraph().addNewTextRun().setText(takeaway);
                }
            } else {
                revisionBody.addNewTextParagraph().addNewTextRun().setText("No important takeaways available.");
            }

            // 7. Quiz Highlights Slide
            XSLFSlide quizSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            quizSlide.getPlaceholder(0).setText("Quiz Highlights");
            XSLFTextShape quizBody = quizSlide.getPlaceholder(1);
            quizBody.clearText();
            quizBody.addNewTextParagraph().addNewTextRun().setText("Review flashcards for important test topics.");

            // 8. Conclusion Slide
            XSLFSlide conclusionSlide = ppt.createSlide(ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT));
            conclusionSlide.getPlaceholder(0).setText("Conclusion");
            XSLFTextShape conclusionBody = conclusionSlide.getPlaceholder(1);
            conclusionBody.clearText();
            conclusionBody.addNewTextParagraph().addNewTextRun().setText("End of presentation. Review summary and flashcards for mastery.");

            // Save File PPTX using temp file
            String fileName = sourceId + ".pptx";
            tempPptFile = Files.createTempFile("slides-", ".pptx");
            try (OutputStream out = Files.newOutputStream(tempPptFile)) {
                ppt.write(out);
            }

            String pptKey = "slides/" + fileName;
            minioStorageService.uploadLocalFile(pptKey, tempPptFile, "application/vnd.openxmlformats-officedocument.presentationml.presentation");

            export.setPptFile(pptKey);
            export.setDownloadUrl("/api/slides/" + sourceId + "/download?format=pptx");
            
            // --- Generate PDF ---
            try (PDDocument pdfDoc = new PDDocument()) {
                PDPage pdfPage = new PDPage();
                pdfDoc.addPage(pdfPage);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(pdfDoc, pdfPage)) {
                    PDType1Font fontBold18 = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                    PDType1Font fontReg12 = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                    contentStream.beginText();
                    contentStream.setLeading(18.0f);
                    contentStream.newLineAtOffset(50, 700);

                    contentStream.setFont(fontBold18, 18);
                    contentStream.showText(sanitizeForPdf("Generated Presentation - " + sourceId));
                    contentStream.newLine();
                    contentStream.newLine();

                    if (summary != null && summary.getSummary() != null) {
                        drawWrappedText(contentStream, summary.getSummary(), fontReg12, 12, 500);
                    } else {
                        contentStream.setFont(fontReg12, 12);
                        contentStream.showText("No summary available.");
                        contentStream.newLine();
                    }
                    contentStream.endText();
                }
                
                String pdfFileName = sourceId + ".pdf";
                tempPdfFile = Files.createTempFile("slides-", ".pdf");
                pdfDoc.save(tempPdfFile.toFile());

                String pdfKey = "slides/" + pdfFileName;
                minioStorageService.uploadLocalFile(pdfKey, tempPdfFile, "application/pdf");
                
                export.setPdfFile(pdfKey);
                export.setPdfDownloadUrl("/api/slides/" + sourceId + "/download?format=pdf");
            } catch (Exception pdfEx) {
                LOG.error("Failed to generate PDF for sourceId {}", sourceId, pdfEx);
            }
            // ---------------------

            export.setStatus("COMPLETED");
            slideExportRepository.save(export);
            
            LOG.info("Successfully generated PPTX/PDF for sourceId: {}", sourceId);
            return CompletableFuture.completedFuture(export);

        } catch (Exception e) {
            LOG.error("Failed to generate PPTX for sourceId {}", sourceId, e);
            export.setStatus("FAILED");
            slideExportRepository.save(export);
            return CompletableFuture.completedFuture(export);
        } finally {
            if (tempPptFile != null) {
                try {
                    Files.deleteIfExists(tempPptFile);
                } catch (Exception ex) {
                    LOG.warn("Failed to delete temp PPTX: {}", tempPptFile, ex);
                }
            }
            if (tempPdfFile != null) {
                try {
                    Files.deleteIfExists(tempPdfFile);
                } catch (Exception ex) {
                    LOG.warn("Failed to delete temp PDF: {}", tempPdfFile, ex);
                }
            }
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
