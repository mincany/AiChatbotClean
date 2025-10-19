package com.example.chatbot.service;

import com.example.chatbot.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Content Guardrails Service for detecting toxic content and confidential data
 * 
 * Provides policy enforcement and content filtering capabilities:
 * - Toxic language detection
 * - Confidential data pattern matching (SSN, credit cards, etc.)
 * - Custom policy rules
 * - Comprehensive logging for compliance
 */
@Service
public class ContentGuardrailsService {

    private static final Logger logger = LoggerFactory.getLogger(ContentGuardrailsService.class);

    // Toxic words and phrases (expandable list)
    private static final Set<String> TOXIC_WORDS = Set.of(
            // Profanity
            "fuck", "shit", "damn", "bitch", "asshole", "bastard",
            // Hate speech indicators
            "hate", "kill", "murder", "terrorist", "nazi", "fascist",
            // Discriminatory terms
            "retard", "faggot", "nigger", "chink", "spic",
            // Threats
            "bomb", "explosion", "attack", "violence", "harm"
    );

    // Confidential data patterns
    private static final Map<String, Pattern> CONFIDENTIAL_PATTERNS = Map.of(
            "SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{9}\\b"),
            "CREDIT_CARD", Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"),
            "EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            "PHONE", Pattern.compile("\\b\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b"),
            "IP_ADDRESS", Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "API_KEY", Pattern.compile("\\b[A-Za-z0-9]{32,}\\b"),
            "PASSWORD", Pattern.compile("(?i)password[\\s:=]+[\\w!@#$%^&*()]+")
    );

    // Business-specific confidential patterns (customize as needed)
    private static final Map<String, Pattern> BUSINESS_PATTERNS = Map.of(
            "EMPLOYEE_ID", Pattern.compile("\\bEMP\\d{6}\\b"),
            "CUSTOMER_ID", Pattern.compile("\\bCUST\\d{8}\\b"),
            "INTERNAL_CODE", Pattern.compile("\\b[A-Z]{3}-\\d{4}-[A-Z]{2}\\b")
    );

    /**
     * Validate content against all guardrail policies
     * 
     * @param content The content to validate
     * @param contentType Type of content (query, response, context)
     * @param userId User ID for logging
     * @return ValidationResult with details
     */
    public ValidationResult validateContent(String content, ContentType contentType, String userId) {
        logger.debug("🛡️ Validating {} content for user: {} (length: {})", 
                contentType, userId, content.length());

        List<PolicyViolation> violations = new ArrayList<>();
        
        // Check for toxic content
        List<PolicyViolation> toxicViolations = detectToxicContent(content);
        violations.addAll(toxicViolations);
        
        // Check for confidential data
        List<PolicyViolation> confidentialViolations = detectConfidentialData(content);
        violations.addAll(confidentialViolations);
        
        // Check business-specific patterns
        List<PolicyViolation> businessViolations = detectBusinessConfidentialData(content);
        violations.addAll(businessViolations);

        boolean isValid = violations.isEmpty();
        
        // Log validation results
        logValidationResult(content, contentType, userId, violations, isValid);
        
        return new ValidationResult(isValid, violations, getSanitizedContent(content, violations));
    }

    /**
     * Quick validation that throws exception on violation
     */
    public void enforcePolicy(String content, ContentType contentType, String userId) {
        ValidationResult result = validateContent(content, contentType, userId);
        
        if (!result.isValid()) {
            String violationSummary = result.getViolations().stream()
                    .map(v -> v.getType() + ":" + v.getPattern())
                    .collect(Collectors.joining(", "));
            
            logger.warn("🚨 Policy violation detected - User: {}, Type: {}, Violations: {}", 
                    userId, contentType, violationSummary);
            
            throw new BusinessException(
                "Content policy violation detected: " + violationSummary,
                "CONTENT_POLICY_VIOLATION",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Detect toxic language and inappropriate content
     */
    private List<PolicyViolation> detectToxicContent(String content) {
        List<PolicyViolation> violations = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        for (String toxicWord : TOXIC_WORDS) {
            if (lowerContent.contains(toxicWord)) {
                violations.add(new PolicyViolation(
                    ViolationType.TOXIC_CONTENT,
                    toxicWord,
                    "Toxic language detected",
                    PolicySeverity.HIGH
                ));
            }
        }
        
        // Check for patterns that might indicate toxic intent
        if (lowerContent.matches(".*\\b(kill|murder|harm)\\s+(you|yourself|me|us)\\b.*")) {
            violations.add(new PolicyViolation(
                ViolationType.THREAT,
                "threat_pattern",
                "Threatening language detected",
                PolicySeverity.CRITICAL
            ));
        }
        
        return violations;
    }

    /**
     * Detect confidential data patterns
     */
    private List<PolicyViolation> detectConfidentialData(String content) {
        List<PolicyViolation> violations = new ArrayList<>();
        
        for (Map.Entry<String, Pattern> entry : CONFIDENTIAL_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(content).find()) {
                violations.add(new PolicyViolation(
                    ViolationType.CONFIDENTIAL_DATA,
                    entry.getKey(),
                    "Confidential data pattern detected: " + entry.getKey(),
                    PolicySeverity.HIGH
                ));
            }
        }
        
        return violations;
    }

    /**
     * Detect business-specific confidential patterns
     */
    private List<PolicyViolation> detectBusinessConfidentialData(String content) {
        List<PolicyViolation> violations = new ArrayList<>();
        
        for (Map.Entry<String, Pattern> entry : BUSINESS_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(content).find()) {
                violations.add(new PolicyViolation(
                    ViolationType.BUSINESS_CONFIDENTIAL,
                    entry.getKey(),
                    "Business confidential pattern detected: " + entry.getKey(),
                    PolicySeverity.MEDIUM
                ));
            }
        }
        
        return violations;
    }

    /**
     * Create sanitized version of content with violations masked
     */
    private String getSanitizedContent(String content, List<PolicyViolation> violations) {
        String sanitized = content;
        
        for (PolicyViolation violation : violations) {
            if (violation.getType() == ViolationType.CONFIDENTIAL_DATA || 
                violation.getType() == ViolationType.BUSINESS_CONFIDENTIAL) {
                
                Pattern pattern = CONFIDENTIAL_PATTERNS.get(violation.getPattern());
                if (pattern == null) {
                    pattern = BUSINESS_PATTERNS.get(violation.getPattern());
                }
                
                if (pattern != null) {
                    sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
                }
            }
        }
        
        return sanitized;
    }

    /**
     * Log validation results for compliance and monitoring
     */
    private void logValidationResult(String content, ContentType contentType, String userId, 
                                   List<PolicyViolation> violations, boolean isValid) {
        
        // Create structured log entry for CloudWatch
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("userId", userId);
        logEntry.put("contentType", contentType.toString());
        logEntry.put("contentLength", content.length());
        logEntry.put("isValid", isValid);
        logEntry.put("violationCount", violations.size());
        
        if (!violations.isEmpty()) {
            logEntry.put("violations", violations.stream()
                    .map(v -> Map.of(
                        "type", v.getType().toString(),
                        "pattern", v.getPattern(),
                        "severity", v.getSeverity().toString()
                    ))
                    .collect(Collectors.toList()));
        }
        
        // Log with appropriate level based on violations
        if (violations.isEmpty()) {
            logger.debug("✅ Content validation passed: {}", logEntry);
        } else {
            boolean hasCritical = violations.stream()
                    .anyMatch(v -> v.getSeverity() == PolicySeverity.CRITICAL);
            
            if (hasCritical) {
                logger.error("🚨 CRITICAL content violation: {}", logEntry);
            } else {
                logger.warn("⚠️ Content policy violation: {}", logEntry);
            }
        }
        
        // Additional structured logging for CloudWatch metrics
        logger.info("GUARDRAILS_METRIC userId={} contentType={} valid={} violations={} severity={}", 
                userId, contentType, isValid, violations.size(),
                violations.stream()
                        .map(v -> v.getSeverity().toString())
                        .collect(Collectors.joining(",")));
    }

    // Enums and data classes
    
    public enum ContentType {
        USER_QUERY,
        AI_RESPONSE,
        CONTEXT_CHUNK,
        KNOWLEDGE_UPLOAD
    }

    public enum ViolationType {
        TOXIC_CONTENT,
        THREAT,
        CONFIDENTIAL_DATA,
        BUSINESS_CONFIDENTIAL
    }

    public enum PolicySeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<PolicyViolation> violations;
        private final String sanitizedContent;

        public ValidationResult(boolean valid, List<PolicyViolation> violations, String sanitizedContent) {
            this.valid = valid;
            this.violations = violations;
            this.sanitizedContent = sanitizedContent;
        }

        public boolean isValid() { return valid; }
        public List<PolicyViolation> getViolations() { return violations; }
        public String getSanitizedContent() { return sanitizedContent; }
    }

    public static class PolicyViolation {
        private final ViolationType type;
        private final String pattern;
        private final String description;
        private final PolicySeverity severity;

        public PolicyViolation(ViolationType type, String pattern, String description, PolicySeverity severity) {
            this.type = type;
            this.pattern = pattern;
            this.description = description;
            this.severity = severity;
        }

        public ViolationType getType() { return type; }
        public String getPattern() { return pattern; }
        public String getDescription() { return description; }
        public PolicySeverity getSeverity() { return severity; }
    }
}
