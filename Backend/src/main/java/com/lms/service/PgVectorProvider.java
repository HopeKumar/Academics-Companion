package com.lms.service;

import com.lms.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.vectorstore.provider", havingValue = "pgvector")
public class PgVectorProvider implements VectorStore {

    private static final Logger LOG = LoggerFactory.getLogger(PgVectorProvider.class);
    
    // Using required=false because postgres might not be configured if Qdrant is chosen
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
                jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS document_chunks (" +
                    "id uuid PRIMARY KEY, " +
                    "source_id varchar(255), " +
                    "user_id varchar(255), " +
                    "text text, " +
                    "page_number int, " +
                    "mongo_id varchar(255), " +
                    "embedding vector(4096))"
                );
                LOG.info("Initialized pgvector document_chunks table.");
            } catch (Exception e) {
                LOG.warn("Could not initialize pgvector table. Error: {}", e.getMessage());
            }
        }
    }

    @Override
    public void upsertChunks(List<DocumentChunk> chunks) {
        if (jdbcTemplate == null || chunks.isEmpty()) return;

        String sql = "INSERT INTO document_chunks (id, source_id, user_id, text, page_number, mongo_id, embedding) " +
                     "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::vector)";

        for (DocumentChunk chunk : chunks) {
            jdbcTemplate.update(sql,
                chunk.getSourceId(),
                chunk.getUserId(),
                chunk.getText(),
                chunk.getPageNumber(),
                chunk.getId(),
                formatVector(chunk.getEmbedding())
            );
        }
    }

    @Override
    public List<DocumentChunk> search(List<Double> queryVector, List<String> sourceIds, String userId, int topK) {
        if (jdbcTemplate == null) return List.of();

        String vectorStr = formatVector(queryVector);
        
        // Basic implementation for search
        String sql = "SELECT text, source_id, user_id, page_number, mongo_id " +
                     "FROM document_chunks " +
                     "WHERE user_id = ? " +
                     "ORDER BY embedding <=> ?::vector LIMIT ?";

        return jdbcTemplate.query(sql, this::mapRowToChunk, userId, vectorStr, topK);
    }

    @Override
    public void deleteBySourceId(String sourceId) {
        if (jdbcTemplate == null) return;
        jdbcTemplate.update("DELETE FROM document_chunks WHERE source_id = ?", sourceId);
    }

    private String formatVector(List<Double> vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.size(); i++) {
            sb.append(vec.get(i));
            if (i < vec.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private DocumentChunk mapRowToChunk(ResultSet rs, int rowNum) throws SQLException {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setText(rs.getString("text"));
        chunk.setSourceId(rs.getString("source_id"));
        chunk.setUserId(rs.getString("user_id"));
        chunk.setPageNumber(rs.getInt("page_number"));
        chunk.setId(rs.getString("mongo_id"));
        return chunk;
    }
}
