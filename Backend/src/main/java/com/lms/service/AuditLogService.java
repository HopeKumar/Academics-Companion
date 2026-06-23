package com.lms.service;

import com.lms.model.AuditLog;
import com.lms.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a security or lifecycle audit event.
     *
     * @param userId     The user identifier (ID or username) associated with the action.
     * @param action     The action type (e.g. "USER_LOGIN", "UPLOAD_DOCUMENT").
     * @param resourceId The ID of the affected resource (optional).
     * @param details    Additional human-readable or structured details (optional).
     * @param ipAddress  The remote client's IP address (optional).
     */
    public void log(String userId, String action, String resourceId, String details, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog(userId, action, resourceId, details, ipAddress);
            auditLogRepository.save(auditLog);
            LOG.info("[AUDIT] Action: {}, User: {}, Resource: {}, IP: {}, Details: {}", 
                    action, userId, resourceId, ipAddress, details);
        } catch (Exception e) {
            LOG.error("Failed to write audit log to database: action={}, user={}", action, userId, e);
        }
    }
}
