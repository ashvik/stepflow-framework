package com.stepflow.validation;

import java.util.List;

/**
 * Exception thrown when FlowConfig validation fails.
 * Contains detailed validation results for error analysis and debugging.
 */
public class ValidationException extends RuntimeException {
    
    private final ValidationResult validationResult;
    
    /**
     * Creates a ValidationException with validation results.
     * 
     * @param message the exception message
     * @param validationResult the detailed validation result
     */
    public ValidationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }
    
    /**
     * Creates a ValidationException with validation results and cause.
     * 
     * @param message the exception message
     * @param validationResult the detailed validation result
     * @param cause the underlying cause
     */
    public ValidationException(String message, ValidationResult validationResult, Throwable cause) {
        super(message, cause);
        this.validationResult = validationResult;
    }
    
    /**
     * @return the complete validation result with all errors and warnings
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }
    
    /**
     * @return all validation errors found
     */
    public List<ValidationError> getValidationErrors() {
        return validationResult.getErrors();
    }
    
    /**
     * @return all validation warnings found
     */
    public List<ValidationWarning> getValidationWarnings() {
        return validationResult.getWarnings();
    }
    
    /**
     * @return the number of validation errors
     */
    public int getErrorCount() {
        return validationResult.getErrors().size();
    }
    
    /**
     * @return the number of validation warnings
     */
    public int getWarningCount() {
        return validationResult.getWarnings().size();
    }
    
    /**
     * @return formatted error summary for display
     */
    public String getDetailedMessage() {
        return validationResult.getErrorSummary();
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (validationResult != null) {
            return baseMessage + "\n" + validationResult.getErrorSummary();
        }
        return baseMessage;
    }
    
    @Override
    public String toString() {
        return "ValidationException{" +
               "message='" + super.getMessage() + '\'' +
               ", errorCount=" + getErrorCount() +
               ", warningCount=" + getWarningCount() +
               '}';
    }
}