package com.lms.dto.ai;

import java.util.UUID;

public record DocumentChunk(
    UUID id,
    String sourceId,
    int chunkIndex,
    String content
) {}
