package com.lms.dto;

public record DocumentChunkResponse(
    String id,
    String sourceId,
    int chunkIndex,
    int pageNumber,
    String content,
    String createdAt
) {}
