package com.stepflow.execution;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents the result of step execution with convenient factory methods.
 */
public class StepResult {
    
    public enum Status {
        SUCCESS, FAILURE, PENDING
    }
    
    public final Status status;
    public final String message;
    public final Map<String, Object> context;
    
    // ======================================================================================
    // CONSTRUCTORS
    // ======================================================================================
    
    public StepResult(Status status) {
        this.status = status;
        this.message = null;
        this.context = new HashMap<>();
    }
    
    public StepResult(Status status, String message) {
        this.status = status;
        this.message = message;
        this.context = new HashMap<>();
    }
    
    public StepResult(Status status, Map<String, Object> context) {
        this.status = status;
        this.message = null;
        this.context = new HashMap<>(context);
    }
    
    public StepResult(Status status, String message, Map<String, Object> context) {
        this.status = status;
        this.message = message;
        this.context = new HashMap<>(context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - SUCCESS
    // ======================================================================================
    
    /**
     * Creates a successful result with no message or context.
     */
    public static StepResult success() {
        return new StepResult(Status.SUCCESS);
    }
    
    /**
     * Creates a successful result with a message.
     */
    public static StepResult success(String message) {
        return new StepResult(Status.SUCCESS, message);
    }
    
    /**
     * Creates a successful result with context data.
     */
    public static StepResult success(Map<String, Object> context) {
        return new StepResult(Status.SUCCESS, context);
    }
    
    /**
     * Creates a successful result with message and context data.
     */
    public static StepResult success(String message, Map<String, Object> context) {
        return new StepResult(Status.SUCCESS, message, context);
    }
    
    /**
     * Creates a successful result with ExecutionContext.
     */
    public static StepResult success(ExecutionContext context) {
        return new StepResult(Status.SUCCESS, context);
    }
    
    /**
     * Creates a successful result with message and ExecutionContext.
     */
    public static StepResult success(String message, ExecutionContext context) {
        return new StepResult(Status.SUCCESS, message, context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - FAILURE
    // ======================================================================================
    
    /**
     * Creates a failed result with no message.
     */
    public static StepResult failure() {
        return new StepResult(Status.FAILURE);
    }
    
    /**
     * Creates a failed result with an error message.
     */
    public static StepResult failure(String message) {
        return new StepResult(Status.FAILURE, message);
    }
    
    /**
     * Creates a failed result with context data.
     */
    public static StepResult failure(Map<String, Object> context) {
        return new StepResult(Status.FAILURE, context);
    }
    
    /**
     * Creates a failed result with message and context data.
     */
    public static StepResult failure(String message, Map<String, Object> context) {
        return new StepResult(Status.FAILURE, message, context);
    }
    
    /**
     * Creates a failed result with ExecutionContext.
     */
    public static StepResult failure(ExecutionContext context) {
        return new StepResult(Status.FAILURE, context);
    }
    
    /**
     * Creates a failed result with message and ExecutionContext.
     */
    public static StepResult failure(String message, ExecutionContext context) {
        return new StepResult(Status.FAILURE, message, context);
    }
    
    /**
     * Creates a failed result from an exception.
     */
    public static StepResult failure(Throwable throwable) {
        return new StepResult(Status.FAILURE, throwable.getMessage());
    }
    
    /**
     * Creates a failed result from an exception with context.
     */
    public static StepResult failure(Throwable throwable, Map<String, Object> context) {
        return new StepResult(Status.FAILURE, throwable.getMessage(), context);
    }
    
    /**
     * Creates a failed result from an exception with ExecutionContext.
     */
    public static StepResult failure(Throwable throwable, ExecutionContext context) {
        return new StepResult(Status.FAILURE, throwable.getMessage(), context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - PENDING
    // ======================================================================================
    
    /**
     * Creates a pending result with no message.
     */
    public static StepResult pending() {
        return new StepResult(Status.PENDING);
    }
    
    /**
     * Creates a pending result with a message.
     */
    public static StepResult pending(String message) {
        return new StepResult(Status.PENDING, message);
    }
    
    /**
     * Creates a pending result with context data.
     */
    public static StepResult pending(Map<String, Object> context) {
        return new StepResult(Status.PENDING, context);
    }
    
    /**
     * Creates a pending result with message and context data.
     */
    public static StepResult pending(String message, Map<String, Object> context) {
        return new StepResult(Status.PENDING, message, context);
    }
    
    /**
     * Creates a pending result with ExecutionContext.
     */
    public static StepResult pending(ExecutionContext context) {
        return new StepResult(Status.PENDING, context);
    }
    
    /**
     * Creates a pending result with message and ExecutionContext.
     */
    public static StepResult pending(String message, ExecutionContext context) {
        return new StepResult(Status.PENDING, message, context);
    }
    
    // ======================================================================================
    // CONVENIENCE METHODS
    // ======================================================================================
    
    /**
     * Checks if this result represents success.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Checks if this result represents failure.
     */
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    /**
     * Checks if this result represents pending state.
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }
    
    /**
     * Gets a value from the context with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = context.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Gets a value from the context with a default value.
     */
    public <T> T getContextValue(String key, Class<T> type, T defaultValue) {
        T value = getContextValue(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Creates a new StepResult with additional context data.
     */
    public StepResult withContext(String key, Object value) {
        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.put(key, value);
        return new StepResult(this.status, this.message, newContext);
    }
    
    /**
     * Creates a new StepResult with additional context data.
     */
    public StepResult withContext(Map<String, Object> additionalContext) {
        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.putAll(additionalContext);
        return new StepResult(this.status, this.message, newContext);
    }
    
    @Override
    public String toString() {
        return "StepResult{status=" + status + ", message='" + message + "', context=" + context + "}";
    }
}