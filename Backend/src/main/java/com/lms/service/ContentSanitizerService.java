package com.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ContentSanitizerService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentSanitizerService.class);

    // Patterns for HTML artifacts that survive Jsoup text extraction
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern CSS_STYLE_BLOCK = Pattern.compile("(?si)<style[^>]*>.*?</style>");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?si)<script[^>]*>.*?</script>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern APPLE_CONVERTED = Pattern.compile("(?i)Apple-converted-space");
    private static final Pattern CSS_FONT_REFS = Pattern.compile("(?i)(Helvetica|Arial|Times New Roman|font-family|font-size|line-height|margin-\\w+|padding-\\w+|text-align|color:\\s*#[0-9a-f]+)[;:]?");
    private static final Pattern CSS_UNITS = Pattern.compile("\\d+(\\.\\d+)?\\s*(px|pt|em|rem|%)");
    private static final Pattern HTML_ENTITIES = Pattern.compile("&(nbsp|lt|gt|amp|quot|#\\d+|#x[0-9a-fA-F]+);");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    // Match BLUE SECTION, RED SECTION etc. HTML comment leftovers
    private static final Pattern SECTION_COMMENTS = Pattern.compile("(?i)-{1,3}\\s*(BLUE|RED|GREEN|YELLOW|WHITE|BLACK|GREY|GRAY)\\s*SECTION\\s*-{0,3}");

    /**
     * Sanitizes raw text or HTML, extracting pure text and stripping ALL tags,
     * CSS fragments, HTML comments, Apple-converted-space, font references,
     * and any other formatting artifacts that pollute vector embeddings.
     */
    public String sanitize(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }

        String clean = rawContent;

        try {
            // Phase 1: Remove script/style/comment blocks BEFORE Jsoup parsing
            clean = SCRIPT_BLOCK.matcher(clean).replaceAll(" ");
            clean = CSS_STYLE_BLOCK.matcher(clean).replaceAll(" ");
            clean = HTML_COMMENT.matcher(clean).replaceAll(" ");

            // Phase 2: Use Jsoup to extract text from remaining HTML
            clean = Jsoup.parse(clean).text();

            // Phase 3: Remove residual artifacts that Jsoup's text() leaves behind
            clean = APPLE_CONVERTED.matcher(clean).replaceAll(" ");
            clean = SECTION_COMMENTS.matcher(clean).replaceAll(" ");
            clean = CSS_FONT_REFS.matcher(clean).replaceAll(" ");
            clean = CSS_UNITS.matcher(clean).replaceAll(" ");
            clean = ANY_TAG.matcher(clean).replaceAll(" ");
            clean = HTML_ENTITIES.matcher(clean).replaceAll(" ");

            // Phase 4: Normalize whitespace
            clean = MULTI_SPACE.matcher(clean).replaceAll(" ");

            return clean.trim();
        } catch (Exception e) {
            LOG.warn("Jsoup parsing failed, falling back to aggressive regex cleanup: {}", e.getMessage());
            // Nuclear fallback: strip everything that looks like markup
            clean = SCRIPT_BLOCK.matcher(rawContent).replaceAll(" ");
            clean = CSS_STYLE_BLOCK.matcher(clean).replaceAll(" ");
            clean = HTML_COMMENT.matcher(clean).replaceAll(" ");
            clean = ANY_TAG.matcher(clean).replaceAll(" ");
            clean = APPLE_CONVERTED.matcher(clean).replaceAll(" ");
            clean = SECTION_COMMENTS.matcher(clean).replaceAll(" ");
            clean = CSS_FONT_REFS.matcher(clean).replaceAll(" ");
            clean = HTML_ENTITIES.matcher(clean).replaceAll(" ");
            clean = MULTI_SPACE.matcher(clean).replaceAll(" ");
            return clean.trim();
        }
    }
}
