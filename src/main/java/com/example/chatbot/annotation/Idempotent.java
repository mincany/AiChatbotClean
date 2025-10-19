package com.example.chatbot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    
    /**
     * Idempotency key parameter name in request
     */
    String keyParam() default "idempotency_key";
    
    /**
     * Time to live for idempotency cache in seconds
     */
    long ttlSeconds() default 3600; // 1 hour
    
    /**
     * Whether to include request body in idempotency check
     */
    boolean includeBody() default true;
}
