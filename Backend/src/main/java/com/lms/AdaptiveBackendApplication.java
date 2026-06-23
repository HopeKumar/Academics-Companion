package com.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.lms.config.AppProperties;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Entry point for the Adaptive Learning Backend (ABF).
 *
 * Annotations added:
 *   @EnableConfigurationProperties — activates AppProperties (@ConfigurationProperties)
 *   @EnableScheduling              — activates TokenBlacklistService cleanup task
 *                                    and any future @Scheduled methods.
 *   @EnableAsync                   — activates background processing.
 *   @EnableCaching                 — activates caching.
 *
 * DataSource and JPA auto-config are enabled for PostgreSQL.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
@EnableAsync
@EnableCaching
@OpenAPIDefinition(info = @Info(title = "CampusLM API", version = "1.0", description = "Documentation for the Adaptive Learning Backend APIs"))
public class AdaptiveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdaptiveBackendApplication.class, args);
    }
}
