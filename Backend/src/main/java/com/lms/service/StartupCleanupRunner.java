package com.lms.service;

import com.lms.config.FeaturesProperties;
import com.lms.model.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class StartupCleanupRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StartupCleanupRunner.class);

    private final MongoTemplate mongoTemplate;
    private final FeaturesProperties features;

    public StartupCleanupRunner(MongoTemplate mongoTemplate, FeaturesProperties features) {
        this.mongoTemplate = mongoTemplate;
        this.features = features;
    }

    @Override
    public void run(String... args) {
        // Log feature status exactly as requested
        LOG.info("Knowledge Graph: {}", features.isKnowledgeGraphEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Slides: {}", features.isSlidesEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Embeddings: {}", features.isEmbeddingsEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Related Concepts: {}", features.isRelatedConceptsEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("");
        LOG.info("Summary: {}", features.isSummaryEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Flashcards: {}", features.isFlashcardsEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Quiz: {}", features.isQuizEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Mind Map: {}", features.isMindmapEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("Podcast: {}", features.isPodcastEnabled() ? "ENABLED" : "DISABLED");
        LOG.info("");

        try {
            LOG.info("Running application startup cleanup for stuck processing sources...");
            Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);
            
            // Search for sources in PROCESSING or processing status older than 15 minutes
            Query query = new Query(Criteria.where("metadata.status").in("PROCESSING", "processing")
                    .and("createdAt").lt(threshold.toString()));
                    
            Update update = new Update()
                    .set("metadata.status", "FAILED")
                    .set("metadata.error", "Processing timed out (stuck at startup) and was marked failed");
                    
            long modifiedCount = mongoTemplate.updateMulti(query, update, Source.class).getModifiedCount();
            if (modifiedCount > 0) {
                LOG.warn("Startup cleanup marked {} stuck 'PROCESSING' sources as FAILED", modifiedCount);
            } else {
                LOG.info("No stuck processing sources found to clean up.");
            }
        } catch (Exception e) {
            LOG.error("Failed to execute startup processing cleanup runner", e);
        }
    }
}
