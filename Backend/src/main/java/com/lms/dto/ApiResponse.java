package com.lms.dto;

import org.slf4j.MDC;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private long timestamp;
    private String requestId;
    private String status;
    private String error;
    private java.util.Map<String, String> fieldErrors;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
        this.requestId = MDC.get("requestId");
    }

    public ApiResponse(boolean success, T data, String message) {
        this();
        this.success = success;
        this.data = data;
        this.message = message;
        if (success) {
            this.status = extractStatus(data);
            if (this.status == null) {
                this.status = "COMPLETED";
            }
            if (data instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) data;
                if (map.containsKey("message")) {
                    this.message = String.valueOf(map.get("message"));
                }
            }
        } else {
            this.status = "FAILED";
            this.error = message;
        }
    }

    public ApiResponse(boolean success, T data, String message, String requestId) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.requestId = requestId;
        if (success) {
            this.status = extractStatus(data);
            if (this.status == null) {
                this.status = "COMPLETED";
            }
            if (data instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) data;
                if (map.containsKey("message")) {
                    this.message = String.valueOf(map.get("message"));
                }
            }
        } else {
            this.status = "FAILED";
            this.error = message;
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "Request processed successfully");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setStatus("FAILED");
        response.setError(message);
        response.setMessage(message);
        return response;
    }

    private String extractStatus(T data) {
        if (data == null) {
            return null;
        }
        if (data instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) data;
            if (map.containsKey("status")) {
                return String.valueOf(map.get("status"));
            }
            if (map.containsKey("metadata")) {
                Object metadata = map.get("metadata");
                if (metadata instanceof java.util.Map) {
                    java.util.Map<?, ?> metaMap = (java.util.Map<?, ?>) metadata;
                    if (metaMap.containsKey("status")) {
                        return String.valueOf(metaMap.get("status"));
                    }
                }
            }
        }
        try {
            java.lang.reflect.Method getStatusMethod = data.getClass().getMethod("getStatus");
            Object statusObj = getStatusMethod.invoke(data);
            if (statusObj != null) {
                return String.valueOf(statusObj);
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            java.lang.reflect.Method getMetadataMethod = data.getClass().getMethod("getMetadata");
            Object metadataObj = getMetadataMethod.invoke(data);
            if (metadataObj instanceof java.util.Map) {
                java.util.Map<?, ?> metaMap = (java.util.Map<?, ?>) metadataObj;
                if (metaMap.containsKey("status")) {
                    return String.valueOf(metaMap.get("status"));
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    // Getters & Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
        if (this.success) {
            this.status = extractStatus(data);
            if (this.status == null) {
                this.status = "COMPLETED";
            }
        }
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public java.util.Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(java.util.Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}
