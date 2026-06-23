package com.lms.service;

import com.lms.dto.DocumentChunkResponse;
import com.lms.dto.DocumentReaderResponse;
import com.lms.exception.ResourceNotFoundException;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DocumentChunkService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentChunkService.class);

    private final SourceRepository sourceRepository;
    private final DocumentChunkRepository chunkRepository;

    @Autowired
    public DocumentChunkService(SourceRepository sourceRepository, DocumentChunkRepository chunkRepository) {
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Validates if a source exists and belongs to the authenticated user.
     * Throws ResourceNotFoundException if source is missing, or SecurityException if unauthorized.
     */
    public Source validateSourceAccess(String sourceId, String userId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source", sourceId));

        if (!source.getUserId().equals(userId)) {
            LOG.error("Access denied: user {} does not own source {}", userId, sourceId);
            throw new SecurityException("Unauthorized to access this source");
        }

        return source;
    }

    /**
     * Retrieves paginated chunks for a source. Supports keyword searching and defaults to sorting by pageNumber then chunkIndex.
     */
    public Page<DocumentChunkResponse> getChunks(String sourceId, String userId, String query, Pageable pageable) {
        validateSourceAccess(sourceId, userId);

        // Ensure sorting by pageNumber then chunkIndex (and id as a fallback)
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("pageNumber"), Sort.Order.asc("chunkIndex"), Sort.Order.asc("id"))
        );

        Page<DocumentChunk> chunkPage;
        if (query != null && !query.trim().isEmpty()) {
            LOG.info("Searching chunks for sourceId={} with query='{}'", sourceId, query);
            chunkPage = chunkRepository.findBySourceIdAndTextContainingIgnoreCase(sourceId, query, sortedPageable);
        } else {
            LOG.info("Fetching chunks for sourceId={}", sourceId);
            chunkPage = chunkRepository.findBySourceId(sourceId, sortedPageable);
        }

        // Fetch dynamic index map for retroactive compatibility (where chunkIndex is absent/zero)
        Map<String, Integer> dynamicIndexMap = calculateDynamicIndices(sourceId);

        List<DocumentChunkResponse> responses = chunkPage.getContent().stream()
                .map(chunk -> {
                    int finalChunkIndex = chunk.getChunkIndex();
                    if (finalChunkIndex == 0) {
                        finalChunkIndex = dynamicIndexMap.getOrDefault(chunk.getId(), 0);
                    }
                    return new DocumentChunkResponse(
                            chunk.getId(),
                            chunk.getSourceId(),
                            finalChunkIndex,
                            chunk.getPageNumber(),
                            chunk.getText(),
                            chunk.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, chunkPage.getTotalElements());
    }

    /**
     * Aggregates chunks page-by-page into a structured response for reading.
     */
    public DocumentReaderResponse getReaderData(String sourceId, String userId) {
        Source source = validateSourceAccess(sourceId, userId);

        List<DocumentChunk> allChunks = new ArrayList<>(chunkRepository.findBySourceId(sourceId));

        // Fetch dynamic index map for retroactive compatibility
        Map<String, Integer> dynamicIndexMap = calculateDynamicIndices(sourceId);

        // Sort all chunks: pageNumber ASC, chunkIndex (or dynamicIndex) ASC, then id ASC
        allChunks.sort((c1, c2) -> {
            if (c1.getPageNumber() != c2.getPageNumber()) {
                return Integer.compare(c1.getPageNumber(), c2.getPageNumber());
            }
            int idx1 = c1.getChunkIndex() == 0 ? dynamicIndexMap.getOrDefault(c1.getId(), 0) : c1.getChunkIndex();
            int idx2 = c2.getChunkIndex() == 0 ? dynamicIndexMap.getOrDefault(c2.getId(), 0) : c2.getChunkIndex();
            if (idx1 != idx2) {
                return Integer.compare(idx1, idx2);
            }
            String id1 = c1.getId() != null ? c1.getId() : "";
            String id2 = c2.getId() != null ? c2.getId() : "";
            return id1.compareTo(id2);
        });

        // Group by page number
        Map<Integer, List<DocumentChunk>> chunksByPage = new TreeMap<>();
        for (DocumentChunk chunk : allChunks) {
            chunksByPage.computeIfAbsent(chunk.getPageNumber(), k -> new ArrayList<>()).add(chunk);
        }

        // Build list of PageContent responses
        List<DocumentReaderResponse.PageContent> pages = new ArrayList<>();
        for (Map.Entry<Integer, List<DocumentChunk>> entry : chunksByPage.entrySet()) {
            int pageNum = entry.getKey();
            List<DocumentChunk> pageChunks = entry.getValue();

            StringBuilder pageContentBuilder = new StringBuilder();
            for (int i = 0; i < pageChunks.size(); i++) {
                if (i > 0) {
                    pageContentBuilder.append("\n\n");
                }
                pageContentBuilder.append(pageChunks.get(i).getText());
            }

            pages.add(new DocumentReaderResponse.PageContent(pageNum, pageContentBuilder.toString()));
        }

        return new DocumentReaderResponse(source.getId(), source.getName(), pages);
    }

    /**
     * Compute a sequential dynamic index based on sorting chunks by pageNumber and then by ID (insertion order).
     * This ensures retroactive compatibility for chunks created prior to introducing `chunkIndex`.
     */
    private Map<String, Integer> calculateDynamicIndices(String sourceId) {
        List<DocumentChunk> allChunks = new ArrayList<>(chunkRepository.findBySourceId(sourceId));
        
        allChunks.sort((c1, c2) -> {
            if (c1.getPageNumber() != c2.getPageNumber()) {
                return Integer.compare(c1.getPageNumber(), c2.getPageNumber());
            }
            String id1 = c1.getId() != null ? c1.getId() : "";
            String id2 = c2.getId() != null ? c2.getId() : "";
            return id1.compareTo(id2);
        });

        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < allChunks.size(); i++) {
            indexMap.put(allChunks.get(i).getId(), i);
        }
        return indexMap;
    }
}
