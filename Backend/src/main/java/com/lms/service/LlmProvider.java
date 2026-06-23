package com.lms.service;

import reactor.core.publisher.Flux;
import java.util.Map;

/**
 * Interface defining contract for LLM text generators, supporting streaming.
 */
public interface LlmProvider {

    /**
     * Generate text synchronously based on a prompt.
     */
    String generate(String prompt, Map<String, Object> options);

    /**
     * Generate a reactive token stream for Server-Sent Events.
     */
    Flux<String> generateStream(String prompt, Map<String, Object> options);
}
