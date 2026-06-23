package com.lms;

import com.lms.service.LlmProvider;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Deterministic fake LLM provider for unit tests.
 *
 * Returns a fixed, predictable response instead of hitting Ollama.
 * Configure the response to return via setNextResponse() before each test.
 */
public class MockLlmProvider implements LlmProvider {

    private String nextResponse = "CORRECT\nThe student provided the correct answer.";
    private String nextStreamToken = "stream-token";

    public void setNextResponse(String response) {
        this.nextResponse = response;
    }

    public void setNextStreamToken(String token) {
        this.nextStreamToken = token;
    }

    @Override
    public String generate(String prompt, Map<String, Object> options) {
        return nextResponse;
    }

    @Override
    public Flux<String> generateStream(String prompt, Map<String, Object> options) {
        return Flux.just(nextStreamToken, " ", "complete");
    }
}
