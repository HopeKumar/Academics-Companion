package com.lms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Callable;

@Configuration
public class ResilienceConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor();
    }

    public static class RetryExecutor {
        private static final int MAX_RETRIES = 3;
        private static final long INITIAL_BACKOFF_MS = 500;

        public <T> T executeWithRetry(Callable<T> action) throws Exception {
            int attempt = 0;
            long backoff = INITIAL_BACKOFF_MS;

            while (true) {
                try {
                    return action.call();
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= MAX_RETRIES) {
                        LOG.error("Operation failed after {} attempts.", MAX_RETRIES, e);
                        throw e;
                    }
                    LOG.warn("Operation failed, retrying (attempt {}/{}). Error: {}", attempt, MAX_RETRIES, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    backoff *= 2; // Exponential backoff
                }
            }
        }
    }
}
