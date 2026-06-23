package com.lms.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.springframework.web.multipart.MultipartException;

import java.util.HashMap;
import java.util.Map;
import com.lms.dto.ApiResponse;
import com.lms.exception.ResourceNotFoundException;
import com.lms.exception.DuplicateDocumentException;
import com.lms.exception.StorageException;
import com.lms.exception.AIServiceException;
import com.lms.exception.SlideGenerationException;
import com.lms.exception.ApiException;

/**
 * Global exception handler.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Typed exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        LOG.warn("ResourceNotFound: {} id={}", ex.getResourceType(), ex.getResourceId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateDocument(
            DuplicateDocumentException ex, WebRequest request) {
        LOG.warn("DuplicateDocumentException: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Object>> handleStorageException(
            StorageException ex, WebRequest request) {
        LOG.error("StorageException: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleAiService(
            AIServiceException ex, WebRequest request) {
        LOG.warn("AIServiceException: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(SlideGenerationException.class)
    public ResponseEntity<ApiResponse<Object>> handleSlideGeneration(
            SlideGenerationException ex, WebRequest request) {
        LOG.warn("SlideGenerationException: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(
            ApiException ex, WebRequest request) {
        LOG.warn("ApiException: {}", ex.getMessage());
        return problem(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(
            MissingServletRequestParameterException ex, WebRequest request) {
        LOG.warn("Missing parameter: {}", ex.getParameterName());
        return problem(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(
            MissingServletRequestPartException ex, WebRequest request) {
        LOG.warn("Missing part: {}", ex.getRequestPartName());
        return problem(HttpStatus.BAD_REQUEST, "Missing required file/part: " + ex.getRequestPartName());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipartException(
            MultipartException ex, WebRequest request) {
        LOG.warn("MultipartException: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Current request is not a multipart request or file is missing.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        LOG.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoSuchElement(
            java.util.NoSuchElementException ex, WebRequest request) {
        LOG.warn("NoSuchElementException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage() != null ? ex.getMessage() : "Resource not found"));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Object>> handleNullPointer(
            NullPointerException ex, WebRequest request) {
        LOG.error("NullPointerException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error("Missing required data or null reference encountered"));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, WebRequest request) {
        LOG.warn("AccessDenied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurity(
            SecurityException ex, WebRequest request) {
        LOG.warn("SecurityException: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── Validation ────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName     = ((FieldError) error).getField();
            String errorMessage  = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ApiResponse<Object> response = ApiResponse.error("One or more fields failed validation.");
        response.setFieldErrors(fieldErrors);
        LOG.error("Returning Validation Error Response. status: 400, errors: {}", fieldErrors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(
            org.springframework.security.core.AuthenticationException ex, WebRequest request) {
        LOG.warn("AuthenticationException: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Invalid or missing authentication credentials.");
    }

    @ExceptionHandler(com.mongodb.MongoException.class)
    public ResponseEntity<ApiResponse<Object>> handleMongoException(
            com.mongodb.MongoException ex, WebRequest request) {
        LOG.error("MongoException: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred. Please try again later.");
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccessException(
            org.springframework.dao.DataAccessException ex, WebRequest request) {
        LOG.error("DataAccessException: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred. Please try again later.");
    }

    // ── Catch-all ─────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAll(Exception ex, WebRequest request) {
        LOG.error("Unhandled exception at {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Object>> problem(
            HttpStatus status, String detail) {
        ApiResponse<Object> response = ApiResponse.error(detail);
        LOG.error("Returning Error Response. status: {}, detail: {}", status.value(), detail);
        return new ResponseEntity<>(response, status);
    }
}
