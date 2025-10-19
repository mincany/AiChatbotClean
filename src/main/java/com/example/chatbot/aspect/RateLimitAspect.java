package com.example.chatbot.aspect;

import com.example.chatbot.annotation.RateLimit;
import com.example.chatbot.exception.BusinessException;
import com.example.chatbot.service.RateLimitService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimitService rateLimitService;

    @Autowired
    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            logger.warn("Unable to get current request for rate limiting");
            return joinPoint.proceed();
        }

        String key = buildRateLimitKey(request, rateLimit);
        
        if (!rateLimitService.isAllowed(key, rateLimit.limit(), rateLimit.window())) {
            long remainingTokens = rateLimitService.getRemainingTokens(key, rateLimit.limit(), rateLimit.window());
            
            logger.warn("Rate limit exceeded for key: {} (remaining tokens: {})", key, remainingTokens);
            
            throw new BusinessException(
                "Rate limit exceeded. Try again later.",
                "RATE_LIMIT_EXCEEDED",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        long remainingTokens = rateLimitService.getRemainingTokens(key, rateLimit.limit(), rateLimit.window());
        logger.debug("Rate limit check passed for key: {} (remaining tokens: {})", key, remainingTokens);

        return joinPoint.proceed();
    }

    private String buildRateLimitKey(HttpServletRequest request, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // Use custom key if provided
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key());
        } else {
            keyBuilder.append("default");
        }
        
        // Add API key if available and configured
        if (rateLimit.useApiKey()) {
            String apiKey = request.getParameter("api_key");
            if (apiKey != null && !apiKey.isEmpty()) {
                keyBuilder.append(":").append(apiKey);
            } else {
                // Fallback to IP address if no API key
                keyBuilder.append(":").append(getClientIpAddress(request));
            }
        } else {
            // Use IP address
            keyBuilder.append(":").append(getClientIpAddress(request));
        }
        
        return keyBuilder.toString();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
