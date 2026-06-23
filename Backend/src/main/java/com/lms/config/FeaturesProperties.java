package com.lms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for feature toggles.
 * Bound from application.properties prefix "features".
 */
@Configuration
@ConfigurationProperties(prefix = "features")
public class FeaturesProperties {

    private boolean summary = true;
    private boolean flashcards = true;
    private boolean quiz = true;
    private boolean mindmap = true;
    private boolean podcast = true;

    private boolean knowledgeGraph = false;
    private boolean slides = false;
    private boolean embeddings = false;
    private boolean relatedConcepts = false;

    // Standard getters and setters for Spring ConfigurationProperties binding
    public boolean isSummary() { return summary; }
    public void setSummary(boolean summary) { this.summary = summary; }

    public boolean isFlashcards() { return flashcards; }
    public void setFlashcards(boolean flashcards) { this.flashcards = flashcards; }

    public boolean isQuiz() { return quiz; }
    public void setQuiz(boolean quiz) { this.quiz = quiz; }

    public boolean isMindmap() { return mindmap; }
    public void setMindmap(boolean mindmap) { this.mindmap = mindmap; }

    public boolean isPodcast() { return podcast; }
    public void setPodcast(boolean podcast) { this.podcast = podcast; }

    public boolean isKnowledgeGraph() { return knowledgeGraph; }
    public void setKnowledgeGraph(boolean knowledgeGraph) { this.knowledgeGraph = knowledgeGraph; }

    public boolean isSlides() { return slides; }
    public void setSlides(boolean slides) { this.slides = slides; }

    public boolean isEmbeddings() { return embeddings; }
    public void setEmbeddings(boolean embeddings) { this.embeddings = embeddings; }

    public boolean isRelatedConcepts() { return relatedConcepts; }
    public void setRelatedConcepts(boolean relatedConcepts) { this.relatedConcepts = relatedConcepts; }

    // Human-readable aliases used in orchestrators and checks
    public boolean isSummaryEnabled() { return isSummary(); }
    public boolean isFlashcardsEnabled() { return isFlashcards(); }
    public boolean isQuizEnabled() { return isQuiz(); }
    public boolean isMindmapEnabled() { return isMindmap(); }
    public boolean isPodcastEnabled() { return isPodcast(); }
    public boolean isKnowledgeGraphEnabled() { return isKnowledgeGraph(); }
    public boolean isSlidesEnabled() { return isSlides(); }
    public boolean isEmbeddingsEnabled() { return isEmbeddings(); }
    public boolean isRelatedConceptsEnabled() { return isRelatedConcepts(); }
}
