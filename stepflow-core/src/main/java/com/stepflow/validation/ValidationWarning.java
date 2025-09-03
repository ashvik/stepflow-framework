package com.stepflow.validation;

import java.util.*;

/**
 * Represents a validation warning that doesn't prevent execution but may indicate issues.
 */
public class ValidationWarning {
    
    private final String workflowName;
    private final String message;
    private final Map<String, Object> details;
    private final String location;
    
    private ValidationWarning(Builder builder) {
        this.workflowName = builder.workflowName;
        this.message = builder.message;
        this.details = Collections.unmodifiableMap(new HashMap<>(builder.details));
        this.location = builder.location;
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
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationWarning{workflow='").append(workflowName)
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
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String workflowName;
        private String message;
        private final Map<String, Object> details = new HashMap<>();
        private String location;
        
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
        
        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }
        
        public ValidationWarning build() {
            return new ValidationWarning(this);
        }
    }
}