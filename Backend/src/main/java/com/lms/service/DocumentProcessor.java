package com.lms.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

@Service
public class DocumentProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessor.class);

    private final Tika      tika      = new Tika();
    private final WebClient webClient = WebClient.builder().build();
    private final OCRService ocrService;
    private final ContentSanitizerService sanitizerService;

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentProcessor(OCRService ocrService, ContentSanitizerService sanitizerService) {
        this.ocrService = ocrService;
        this.sanitizerService = sanitizerService;
    }

    public static class Chunk {
        private final String text;
        private final int    pageNumber;

        public Chunk(String text, int pageNumber) {
            this.text       = text;
            this.pageNumber = pageNumber;
        }

        public String getText()       { return text; }
        public int    getPageNumber() { return pageNumber; }
    }

    /**
     * Extracts text page-by-page for PDF, or returns full text as page 1 for other formats.
     */
    @Async
    public CompletableFuture<List<Chunk>> processDocumentAsync(InputStream inputStream, String filename, String contentType) {
        return CompletableFuture.completedFuture(processDocument(inputStream, filename, contentType));
    }

    public List<Chunk> processDocument(InputStream inputStream, String filename, String contentType) {
        List<Chunk> extracted = new ArrayList<>();
        try {
            byte[] bytes = toByteArray(inputStream);

            if (filename.toLowerCase().matches(".*\\.(png|jpe?g)$") || (contentType != null && contentType.startsWith("image/"))) {
                String text = ocrService.extractTextFromImage(new java.io.ByteArrayInputStream(bytes));
                extracted.add(new Chunk(sanitizerService.sanitize(text), 1));
            } else if (filename.toLowerCase().endsWith(".pdf") || "application/pdf".equals(contentType)) {
                extracted.addAll(extractPdfPages(bytes));
            } else if (filename.toLowerCase().endsWith(".txt") || "text/plain".equals(contentType)) {
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                extracted.add(new Chunk(sanitizerService.sanitize(text), 1));
            } else {
                // Use Apache Tika to extract docx, doc, pptx, ppt, etc.
                String text = tika.parseToString(new java.io.ByteArrayInputStream(bytes));
                extracted.add(new Chunk(sanitizerService.sanitize(text), 1));
            }
        } catch (Exception e) {
            LOG.error("Failed to parse document {}: {}", filename, e.getMessage());
            throw new RuntimeException("Document extraction failed: " + e.getMessage(), e);
        }
        return extracted;
    }

    /**
     * Ingests a URL, scrapes its HTML content, strips tag markup, and chunks the resulting text.
     */
    public List<Chunk> processUrl(String urlString) {
        List<Chunk> chunks = new ArrayList<>();
        try {
            LOG.info("Scraping content from URL: {}", urlString);
            String html = webClient.get()
                    .uri(urlString)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String cleanText = sanitizerService.sanitize(html);
            chunks.add(new Chunk(cleanText, 1));
        } catch (Exception e) {
            LOG.error("Failed to process URL {}: {}", urlString, e.getMessage());
            throw new RuntimeException("URL ingestion failed: " + e.getMessage(), e);
        }
        return chunks;
    }

    /**
     * Chunks a list of page extracts into overlapping segments.
     */
    public List<Chunk> createOverlappingChunks(List<Chunk> pageChunks, int chunkSize, int overlap) {
        List<Chunk> overlapping = new ArrayList<>();

        for (Chunk pageChunk : pageChunks) {
            String text = pageChunk.getText();
            int pageNum = pageChunk.getPageNumber();

            if (text == null || text.isBlank()) continue;

            java.text.BreakIterator iterator = java.text.BreakIterator.getSentenceInstance();
            iterator.setText(text);

            int start = iterator.first();
            int end = iterator.next();

            StringBuilder currentChunk = new StringBuilder();

            while (end != java.text.BreakIterator.DONE) {
                String sentence = text.substring(start, end).trim();

                if (!sentence.isEmpty()) {
                    if (sentence.length() > chunkSize) {
                        if (currentChunk.length() > 0) {
                            overlapping.add(new Chunk(currentChunk.toString().trim(), pageNum));
                            currentChunk.setLength(0);
                        }
                        int sStart = 0;
                        while (sStart < sentence.length()) {
                            int sEnd = Math.min(sStart + chunkSize, sentence.length());
                            overlapping.add(new Chunk(sentence.substring(sStart, sEnd).trim(), pageNum));
                            int step = chunkSize - overlap;
                            if (step <= 0) step = chunkSize;
                            if (sEnd == sentence.length()) break;
                            sStart += step;
                        }
                    } else {
                        if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                            overlapping.add(new Chunk(currentChunk.toString().trim(), pageNum));

                            String prev = currentChunk.toString();
                            currentChunk.setLength(0);

                            int overlapStart = Math.max(0, prev.length() - overlap);
                            if (overlapStart > 0) {
                                int spaceIdx = prev.indexOf(' ', overlapStart);
                                if (spaceIdx != -1) {
                                    currentChunk.append(prev.substring(spaceIdx).trim());
                                }
                            }
                        }
                        if (currentChunk.length() > 0) currentChunk.append(" ");
                        currentChunk.append(sentence);
                    }
                }
                start = end;
                end = iterator.next();
            }
            if (currentChunk.length() > 0) {
                overlapping.add(new Chunk(currentChunk.toString().trim(), pageNum));
            }
        }
        return overlapping;
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private List<Chunk> extractPdfPages(byte[] bytes) throws IOException {
        List<Chunk> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int count = document.getNumberOfPages();
            LOG.info("Extracting {} pages from PDF", count);
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            
            // First pass: extract text from all pages to check if it's a scanned PDF
            StringBuilder totalText = new StringBuilder();
            List<String> pageTexts = new java.util.ArrayList<>();
            for (int i = 1; i <= count; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                if (pageText != null) {
                    totalText.append(pageText);
                    pageTexts.add(pageText);
                } else {
                    pageTexts.add("");
                }
            }
            
            if (totalText.toString().trim().length() >= 150) {
                // Not a scanned PDF. Return the extracted text.
                for (int i = 1; i <= count; i++) {
                    pages.add(new Chunk(sanitizerService.sanitize(pageTexts.get(i - 1)), i));
                }
            } else {
                // Scanned PDF. Fall back to OCR rendered at 150 DPI page-by-page.
                LOG.info("Scanned PDF detected (extracted text length {} < 150). Running page-by-page OCR at 150 DPI.", totalText.toString().trim().length());
                for (int i = 1; i <= count; i++) {
                    String pageText = "";
                    if (ocrService.isAvailable()) {
                        try {
                            java.awt.image.BufferedImage bim = renderer.renderImageWithDPI(i - 1, 150);
                            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
                            javax.imageio.ImageIO.write(bim, "png", os);
                            java.io.InputStream is = new java.io.ByteArrayInputStream(os.toByteArray());
                            String ocrText = ocrService.extractTextFromImage(is);
                            if (ocrText != null && !ocrText.isBlank()) {
                                pageText = ocrText;
                            }
                        } catch (Throwable e) {
                            LOG.warn("OCR fallback failed on page {}", i, e);
                        }
                    } else {
                        LOG.warn("Scanned PDF page {} needs OCR but OCR is unavailable.", i);
                    }
                    pages.add(new Chunk(sanitizerService.sanitize(pageText), i));
                }
            }
        }
        return pages;
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

}
