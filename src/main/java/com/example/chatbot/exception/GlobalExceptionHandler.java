package com.example.chatbot.exception;

import com.example.chatbot.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle custom business exceptions
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ApiResponse.ErrorDetails errorDetails = new ApiResponse.ErrorDetails(
            "VALIDATION_ERROR", 
            "Validation failed",
            errors.toString()
        );
        
        ApiResponse<Object> response = ApiResponse.error(errorDetails);
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle constraint violations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        String errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        ApiResponse<Object> response = ApiResponse.error("Validation failed: " + errors, "CONSTRAINT_VIOLATION");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        ApiResponse<Object> response = ApiResponse.error(message, "MISSING_PARAMETER");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle malformed JSON requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        ApiResponse<Object> response = ApiResponse.error("Malformed JSON request", "MALFORMED_JSON");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle file upload size exceeded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        ApiResponse<Object> response = ApiResponse.error("File size exceeds maximum allowed limit", "FILE_SIZE_EXCEEDED");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Handle method not allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        String message = String.format("Method '%s' is not supported for this endpoint", ex.getMethod());
        ApiResponse<Object> response = ApiResponse.error(message, "METHOD_NOT_ALLOWED");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handle endpoint not found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFound(NoHandlerFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        String message = String.format("Endpoint '%s %s' not found", ex.getHttpMethod(), ex.getRequestURL());
        ApiResponse<Object> response = ApiResponse.error(message, "ENDPOINT_NOT_FOUND");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex, WebRequest request) {
        String requestId = generateRequestId();
        logError(ex, requestId, request);
        
        ApiResponse<Object> response = ApiResponse.error("An internal server error occurred", "INTERNAL_SERVER_ERROR");
        response.setRequestId(requestId);
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Generate unique request ID for tracing
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Log error with context information
     */
    private void logError(Exception ex, String requestId, WebRequest request) {
        HttpServletRequest httpRequest = null;
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            httpRequest = ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        
        if (httpRequest != null) {
            logger.error("Error [RequestId: {}] [Method: {}] [URI: {}] [RemoteAddr: {}]: {}", 
                requestId, 
                httpRequest.getMethod(), 
                httpRequest.getRequestURI(), 
                httpRequest.getRemoteAddr(), 
                ex.getMessage(), 
                ex);
        } else {
            logger.error("Error [RequestId: {}]: {}", requestId, ex.getMessage(), ex);
        }
    }
}
