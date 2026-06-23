package com.lms.model;

import java.time.Instant;

/**
 * A standardized response wrapper for all AI module interactions.
 */
public class AIResult<T> {
    private boolean success;
    private T data;
    private String error;
    private String provider;
    private String model;
    private long latency;
    private String timestamp;

    public AIResult() {
        this.timestamp = Instant.now().toString();
    }

    public static <T> AIResult<T> success(T data, String provider, String model, long latency) {
        AIResult<T> result = new AIResult<>();
        result.setSuccess(true);
        result.setData(data);
        result.setProvider(provider);
        result.setModel(model);
        result.setLatency(latency);
        return result;
    }

    public static <T> AIResult<T> failure(String error, String provider, String model, long latency) {
        AIResult<T> result = new AIResult<>();
        result.setSuccess(false);
        result.setError(error);
        result.setProvider(provider);
        result.setModel(model);
        result.setLatency(latency);
        return result;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getLatency() { return latency; }
    public void setLatency(long latency) { this.latency = latency; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
