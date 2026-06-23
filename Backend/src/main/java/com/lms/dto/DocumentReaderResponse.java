package com.lms.dto;

import java.util.List;

public record DocumentReaderResponse(
    String sourceId,
    String title,
    List<PageContent> pages
) {
    public record PageContent(
        int page,
        String content
    ) {}
}
