package com.stepflow.validation;

import java.util.*;

/**
 * Comprehensive validation result containing all validation errors and warnings.
 * Provides detailed information about validation failures for debugging and error reporting.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;
    private final Map<String, Object> metadata;
    
    private ValidationResult(Builder builder) {
        this.valid = builder.errors.isEmpty();
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }
    
    /**
     * @return true if validation passed (no errors), false otherwise
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * @return true if validation failed (has errors), false otherwise
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * @return true if validation has warnings, false otherwise
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * @return immutable list of validation errors
     */
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    /**
     * @return immutable list of validation warnings
     */
    public List<ValidationWarning> getWarnings() {
        return warnings;
    }
    
    /**
     * @return validation metadata (timing, stats, etc.)
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Gets errors of a specific type.
     * 
     * @param errorType the error type to filter by
     * @return list of errors matching the type
     */
    public List<ValidationError> getErrorsOfType(ValidationErrorType errorType) {
        return errors.stream()
                .filter(error -> error.getType() == errorType)
                .toList();
    }
    
    /**
     * Gets errors for a specific workflow.
     * 
     * @param workflowName the workflow name to filter by
     * @return list of errors for the workflow
     */
    public List<ValidationError> getErrorsForWorkflow(String workflowName) {
        return errors.stream()
                .filter(error -> workflowName.equals(error.getWorkflowName()))
                .toList();
    }
    
    /**
     * @return formatted error summary for logging/display
     */
    public String getErrorSummary() {
        if (valid) {
            return "Validation passed successfully";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Validation failed with ").append(errors.size()).append(" error(s)");
        if (hasWarnings()) {
            sb.append(" and ").append(warnings.size()).append(" warning(s)");
        }
        sb.append(":\n");
        
        for (ValidationError error : errors) {
            sb.append("  ERROR: ").append(error.getMessage()).append("\n");
            if (error.getDetails() != null && !error.getDetails().isEmpty()) {
                for (Map.Entry<String, Object> detail : error.getDetails().entrySet()) {
                    sb.append("    ").append(detail.getKey()).append(": ").append(detail.getValue()).append("\n");
                }
            }
        }
        
        for (ValidationWarning warning : warnings) {
            sb.append("  WARN: ").append(warning.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ValidationResult{valid=%s, errors=%d, warnings=%d}", 
                valid, errors.size(), warnings.size());
    }
    
    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new Builder().build();
    }
    
    /**
     * Creates a failed validation result with a single error.
     */
    public static ValidationResult failure(ValidationError error) {
        return new Builder().addError(error).build();
    }
    
    /**
     * Creates a new builder for constructing validation results.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for constructing ValidationResult instances.
     */
    public static class Builder {
        private final List<ValidationError> errors = new ArrayList<>();
        private final List<ValidationWarning> warnings = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        
        public Builder addError(ValidationError error) {
            this.errors.add(error);
            return this;
        }
        
        public Builder addErrors(Collection<ValidationError> errors) {
            this.errors.addAll(errors);
            return this;
        }
        
        public Builder addWarning(ValidationWarning warning) {
            this.warnings.add(warning);
            return this;
        }
        
        public Builder addWarnings(Collection<ValidationWarning> warnings) {
            this.warnings.addAll(warnings);
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder addMetadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }
        
        /**
         * Merges another validation result into this builder.
         */
        public Builder merge(ValidationResult other) {
            this.errors.addAll(other.getErrors());
            this.warnings.addAll(other.getWarnings());
            this.metadata.putAll(other.getMetadata());
            return this;
        }
        
        public ValidationResult build() {
            return new ValidationResult(this);
        }
    }
}