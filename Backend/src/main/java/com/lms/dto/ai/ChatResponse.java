package com.lms.dto.ai;

public record ChatResponse(
    String reply,
    Double relevanceScore,
    Integer chunkCount,
    String retrievalDiagnostics
) {
    public ChatResponse(String reply) {
        this(reply, 0.0, 0, "No diagnostics available");
    }
}
