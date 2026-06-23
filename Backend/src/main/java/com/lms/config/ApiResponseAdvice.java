package com.lms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.dto.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Autowired
    public ApiResponseAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (org.springframework.http.converter.ResourceHttpMessageConverter.class.isAssignableFrom(converterType)
            || org.springframework.http.converter.ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        Class<?> parameterType = returnType.getParameterType();
        if (ApiResponse.class.isAssignableFrom(parameterType)) {
            return false;
        }
        if (org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class.isAssignableFrom(parameterType)
                || org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(parameterType)
                || org.springframework.core.io.Resource.class.isAssignableFrom(parameterType)
                || byte[].class.isAssignableFrom(parameterType)
                || parameterType.getName().equals("void")) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        
        String path = request.getURI().getPath();
        if (path == null || path.contains("/error") || path.contains("/actuator") || path.contains("/swagger") || path.contains("/v3/api-docs")) {
            return body;
        }

        if (body instanceof ApiResponse) {
            return body;
        }

        boolean isError = false;
        String errorMessage = "Request failed";
        Map<String, String> fieldErrors = null;

        if (response instanceof org.springframework.http.server.ServletServerHttpResponse) {
            int status = ((org.springframework.http.server.ServletServerHttpResponse) response).getServletResponse().getStatus();
            if (status >= 400) {
                isError = true;
            }
        }

        if (body instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
            if (map.containsKey("success") && Boolean.FALSE.equals(map.get("success"))) {
                isError = true;
                if (map.containsKey("message")) {
                    errorMessage = String.valueOf(map.get("message"));
                } else if (map.containsKey("error")) {
                    errorMessage = String.valueOf(map.get("error"));
                }
                if (map.containsKey("fieldErrors")) {
                    try {
                        fieldErrors = (Map<String, String>) map.get("fieldErrors");
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        if (isError) {
            if (body instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
                if (map.containsKey("message")) {
                    errorMessage = String.valueOf(map.get("message"));
                } else if (map.containsKey("error")) {
                    errorMessage = String.valueOf(map.get("error"));
                }
            } else if (body instanceof String) {
                errorMessage = (String) body;
            }
            ApiResponse<Object> errResponse = ApiResponse.error(errorMessage);
            if (fieldErrors != null) {
                errResponse.setFieldErrors(fieldErrors);
            }
            if (body instanceof String || returnType.getParameterType().equals(String.class)) {
                try {
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return objectMapper.writeValueAsString(errResponse);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize ApiResponse to JSON string", e);
                }
            }
            return errResponse;
        }

        ApiResponse<Object> apiResponse = ApiResponse.success(body);

        if (body instanceof String || returnType.getParameterType().equals(String.class)) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(apiResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize ApiResponse to JSON string", e);
            }
        }

        return apiResponse;
    }
}
