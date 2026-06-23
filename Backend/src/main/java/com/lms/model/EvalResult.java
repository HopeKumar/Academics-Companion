package com.lms.model;

/**
 * Result of an AI-powered answer evaluation.
 * Returned by AIService to its callers.
 */
public class EvalResult {

    private final boolean correct;
    private final String  explanation;
    private final boolean cached;

    public EvalResult(boolean correct, String explanation, boolean cached) {
        this.correct     = correct;
        this.explanation = explanation;
        this.cached      = cached;
    }

    public boolean isCorrect()      { return correct; }
    public String  getExplanation() { return explanation; }
    public boolean isCached()       { return cached; }
}
