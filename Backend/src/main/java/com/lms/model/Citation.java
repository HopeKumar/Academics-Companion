package com.lms.model;

/**
 * Represents a reference to a source document chunk used in generating an AI response.
 */
public class Citation {

    private String sourceId;
    private String sourceName;
    private String snippet;
    private int    pageNumber;

    // ── Constructors ──────────────────────────────────────────────────────

    public Citation() {}

    public Citation(String sourceId, String sourceName, String snippet, int pageNumber) {
        this.sourceId   = sourceId;
        this.sourceName = sourceName;
        this.snippet    = snippet;
        this.pageNumber = pageNumber;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getSourceId()                  { return sourceId; }
    public void   setSourceId(String sourceId)   { this.sourceId = sourceId; }

    public String getSourceName()                    { return sourceName; }
    public void   setSourceName(String sourceName)   { this.sourceName = sourceName; }

    public String getSnippet()                { return snippet; }
    public void   setSnippet(String snippet)  { this.snippet = snippet; }

    public int  getPageNumber()              { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
}
