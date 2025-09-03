package com.stepflow.validation;

/**
 * Enumeration of validation error types.
 * Each type represents a specific category of validation failure.
 */
public enum ValidationErrorType {
    
    /**
     * Workflow contains circular dependencies between steps.
     * Details include cycle path and involved edges.
     */
    CYCLE_DETECTED("Circular dependency detected in workflow"),
    
    /**
     * Step has multiple edges without guards.
     * Details include step name and problematic unguarded edges.
     */
    MULTIPLE_UNGUARDED_EDGES("Multiple edges without guards from single step"),
    
    /**
     * Unguarded edge is not positioned last among step's outgoing edges.
     * Details include step name, edge order, and expected position.
     */
    UNGUARDED_EDGE_NOT_LAST("Unguarded edge must be positioned last"),
    
    /**
     * Referenced step is not defined in the steps section.
     * Details include step name and referencing locations.
     */
    UNDEFINED_STEP("Referenced step is not defined"),
    
    /**
     * Referenced guard is not defined or cannot be resolved.
     * Details include guard name and referencing edges.
     */
    UNDEFINED_GUARD("Referenced guard is not defined"),
    
    /**
     * Workflow has no root step defined.
     * Details include workflow name.
     */
    MISSING_ROOT("Workflow has no root step defined"),
    
    /**
     * Step has no outgoing edges and is not a terminal step.
     * Details include step name and reachability information.
     */
    DEAD_END("Step has no outgoing edges (dead end)"),
    
    /**
     * Step is defined but never referenced in any workflow.
     * Details include step name and available workflows.
     */
    UNREACHABLE_STEP("Step is defined but unreachable"),
    
    /**
     * Edge configuration is invalid or incomplete.
     * Details include edge information and specific issues.
     */
    INVALID_EDGE_CONFIGURATION("Edge configuration is invalid"),
    
    /**
     * Workflow configuration is malformed or incomplete.
     * Details include workflow name and specific issues.
     */
    MALFORMED_WORKFLOW("Workflow configuration is malformed"),
    
    /**
     * Configuration syntax error or parsing failure.
     * Details include location and parsing error information.
     */
    CONFIGURATION_SYNTAX_ERROR("Configuration syntax error"),
    
    /**
     * Generic validation error that doesn't fit other categories.
     * Details provide specific error information.
     */
    GENERIC("Generic validation error");
    
    private final String description;
    
    ValidationErrorType(String description) {
        this.description = description;
    }
    
    /**
     * @return human-readable description of this error type
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * @return true if this error type is related to workflow structure
     */
    public boolean isStructuralError() {
        return this == CYCLE_DETECTED || 
               this == DEAD_END || 
               this == UNREACHABLE_STEP || 
               this == MISSING_ROOT;
    }
    
    /**
     * @return true if this error type is related to edge configuration
     */
    public boolean isEdgeError() {
        return this == MULTIPLE_UNGUARDED_EDGES ||
               this == UNGUARDED_EDGE_NOT_LAST ||
               this == INVALID_EDGE_CONFIGURATION;
    }
    
    /**
     * @return true if this error type is related to component references
     */
    public boolean isReferenceError() {
        return this == UNDEFINED_STEP ||
               this == UNDEFINED_GUARD;
    }
}