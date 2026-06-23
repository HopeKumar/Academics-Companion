package com.lms.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SummaryResponse(
    String summary,
    List<String> keyPoints,
    List<String> importantTakeaways,
    List<DefinitionEntry> definitions,
    List<String> nextSteps
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefinitionEntry(String term, String definition) {}
}
