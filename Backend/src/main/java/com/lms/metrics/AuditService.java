package com.lms.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT_LOGGER");
    private final Map<String, Long> userActionCounts = new ConcurrentHashMap<>();

    public void logAction(String userId, String action, String resourceId, Map<String, Object> details) {
        String timestamp = Instant.now().toString();
        
        userActionCounts.merge(userId, 1L, Long::sum);

        AUDIT_LOG.info("AUDIT_EVENT | timestamp={} | userId={} | action={} | resourceId={} | details={}",
                timestamp, userId, action, resourceId, details);
    }

    public void logSecurityEvent(String userId, String eventType, String ipAddress, boolean success) {
        String timestamp = Instant.now().toString();

        AUDIT_LOG.warn("SECURITY_EVENT | timestamp={} | userId={} | type={} | ip={} | success={}",
                timestamp, userId, eventType, ipAddress, success);
    }
}
