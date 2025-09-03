package com.stepflow.validation;

import com.stepflow.config.FlowConfig;

/**
 * Interface for FlowConfig validators.
 * Provides an extensible pattern for adding new validation rules.
 * Each validator focuses on a specific aspect of workflow validation.
 */
public interface FlowConfigValidator {
    
    /**
     * Validates the given FlowConfig and returns validation results.
     * 
     * @param config the FlowConfig to validate
     * @return validation result containing errors and warnings
     */
    ValidationResult validate(FlowConfig config);
    
    /**
     * @return the name of this validator for logging and debugging
     */
    String getValidatorName();
    
    /**
     * @return the priority of this validator (lower numbers run first)
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * @return true if this validator should stop the validation chain on failure
     */
    default boolean isFailFast() {
        return false;
    }
    
    /**
     * @return description of what this validator checks
     */
    default String getDescription() {
        return "Validates FlowConfig for " + getValidatorName();
    }
}