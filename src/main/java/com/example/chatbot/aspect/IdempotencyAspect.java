package com.example.chatbot.aspect;

import com.example.chatbot.annotation.Idempotent;
import com.example.chatbot.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public IdempotencyAspect(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            logger.warn("Unable to get current request for idempotency check");
            return joinPoint.proceed();
        }

        String idempotencyKey = request.getParameter(idempotent.keyParam());
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            logger.debug("No idempotency key provided, proceeding with request");
            return joinPoint.proceed();
        }

        String apiKey = request.getParameter("api_key");
        String requestBody = idempotent.includeBody() ? getRequestBodyHash(joinPoint.getArgs()) : null;
        
        String cacheKey = idempotencyService.generateKey(idempotencyKey, apiKey, requestBody);
        
        // Check for cached response
        ResponseEntity<?> cachedResponse = idempotencyService.checkIdempotency(cacheKey);
        if (cachedResponse != null) {
            logger.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return cachedResponse;
        }

        // Execute the method
        Object result = joinPoint.proceed();
        
        // Cache the response if it's a ResponseEntity
        if (result instanceof ResponseEntity) {
            idempotencyService.storeResponse(cacheKey, (ResponseEntity<?>) result, idempotent.ttlSeconds());
            logger.debug("Cached response for idempotency key: {}", idempotencyKey);
        }

        return result;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getRequestBodyHash(Object[] args) {
        try {
            // Find the request body from method arguments
            for (Object arg : args) {
                if (arg != null && !isPrimitiveOrString(arg)) {
                    String json = objectMapper.writeValueAsString(arg);
                    return String.valueOf(json.hashCode());
                }
            }
        } catch (Exception e) {
            logger.warn("Error serializing request body for idempotency check", e);
        }
        return null;
    }

    private boolean isPrimitiveOrString(Object obj) {
        return obj instanceof String || 
               obj instanceof Number || 
               obj instanceof Boolean || 
               obj instanceof Character ||
               obj.getClass().isPrimitive();
    }
}
