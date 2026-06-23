package com.lms.service;

import com.lms.model.Citation;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.repository.SourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContextBuilder {

    private final SourceRepository sourceRepository;

    @Autowired
    public ContextBuilder(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public static class RagContext {
        private final String         contextText;
        private final List<Citation> citations;

        public RagContext(String contextText, List<Citation> citations) {
            this.contextText = contextText;
            this.citations   = citations;
        }

        public String         getContextText() { return contextText; }
        public List<Citation> getCitations()   { return citations; }
    }

    /**
     * Builds standard reference prompts and maps source IDs to actual filenames.
     */
    public RagContext buildContext(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new RagContext("No reference context available.", new ArrayList<>());
        }

        StringBuilder sb = new StringBuilder();

        List<Citation> citations = new ArrayList<>();
        Map<String, String> sourceNamesCache = new HashMap<>();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String sourceId = chunk.getSourceId();

            String sourceName = sourceNamesCache.computeIfAbsent(sourceId, id -> {
                Source doc = sourceRepository.findById(id).orElse(null);
                return doc != null ? doc.getName() : "Unknown Source";
            });

            int marker = i + 1;
            sb.append(String.format("Reference [%d]: Source \"%s\", Page %d\n",
                    marker, sourceName, chunk.getPageNumber()));
            sb.append("Content:\n");
            sb.append(chunk.getText().trim()).append("\n");
            sb.append("-----------------------\n");

            // Build structural Citation
            citations.add(new Citation(sourceId, sourceName, chunk.getText(), chunk.getPageNumber()));
        }

        return new RagContext(sb.toString(), citations);
    }
}
