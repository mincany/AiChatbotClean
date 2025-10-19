package com.example.chatbot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * Rate limit key - defaults to method name
     */
    String key() default "";
    
    /**
     * Number of requests allowed per time window
     */
    int limit() default 100;
    
    /**
     * Time window in seconds
     */
    int window() default 60;
    
    /**
     * Whether to use API key for rate limiting
     */
    boolean useApiKey() default true;
}
