package com.stepflow.validation;

import java.util.*;

/**
 * Represents a validation error with detailed context information.
 * Provides structured error details for debugging and error reporting.
 */
public class ValidationError {
    
    private final ValidationErrorType type;
    private final String workflowName;
    private final String message;
    private final Map<String, Object> details;
    private final String location;
    private final Throwable cause;
    
    private ValidationError(Builder builder) {
        this.type = builder.type;
        this.workflowName = builder.workflowName;
        this.message = builder.message;
        this.details = Collections.unmodifiableMap(new HashMap<>(builder.details));
        this.location = builder.location;
        this.cause = builder.cause;
    }
    
    public ValidationErrorType getType() {
        return type;
    }
    
    public String getWorkflowName() {
        return workflowName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public String getLocation() {
        return location;
    }
    
    public Throwable getCause() {
        return cause;
    }
    
    /**
     * Gets a specific detail value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, Class<T> type) {
        Object value = details.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Gets a detail value as string.
     */
    public String getDetailAsString(String key) {
        Object value = details.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationError{type=").append(type)
          .append(", workflow='").append(workflowName)
          .append("', message='").append(message).append("'");
        
        if (location != null) {
            sb.append(", location='").append(location).append("'");
        }
        
        if (!details.isEmpty()) {
            sb.append(", details=").append(details);
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Creates a new builder for constructing validation errors.
     */
    public static Builder builder(ValidationErrorType type) {
        return new Builder(type);
    }
    
    /**
     * Builder for constructing ValidationError instances.
     */
    public static class Builder {
        private final ValidationErrorType type;
        private String workflowName;
        private String message;
        private final Map<String, Object> details = new HashMap<>();
        private String location;
        private Throwable cause;
        
        public Builder(ValidationErrorType type) {
            this.type = type;
        }
        
        public Builder workflowName(String workflowName) {
            this.workflowName = workflowName;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder location(String location) {
            this.location = location;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }
        
        // Convenience methods for common details
        
        public Builder cycleDetails(List<String> cyclePath, List<String> involvedEdges) {
            detail("cyclePath", cyclePath);
            detail("involvedEdges", involvedEdges);
            detail("cycleLength", cyclePath.size());
            return this;
        }
        
        public Builder edgeOrderingDetails(String stepName, List<String> guardedEdges, 
                                          List<String> unguardedEdges, int firstUnguardedIndex) {
            detail("stepName", stepName);
            detail("guardedEdges", guardedEdges);
            detail("unguardedEdges", unguardedEdges);
            detail("firstUnguardedIndex", firstUnguardedIndex);
            detail("totalEdges", guardedEdges.size() + unguardedEdges.size());
            return this;
        }
        
        public Builder multipleUnguardedEdgesDetails(String stepName, List<String> unguardedEdges, 
                                                    List<Integer> unguardedIndices) {
            detail("stepName", stepName);
            detail("unguardedEdges", unguardedEdges);
            detail("unguardedIndices", unguardedIndices);
            detail("unguardedCount", unguardedEdges.size());
            return this;
        }
        
        public ValidationError build() {
            if (message == null) {
                message = "Validation error of type " + type;
            }
            return new ValidationError(this);
        }
    }
}