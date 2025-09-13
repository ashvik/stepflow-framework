package com.stepflow.validation;

import com.stepflow.config.FlowConfig;

/**
 * Extensible interface for implementing FlowConfig validation rules.
 * 
 * <p>FlowConfigValidator provides a plugin architecture for workflow configuration validation,
 * allowing the framework to be extended with custom validation logic while maintaining
 * consistent error reporting and priority-based execution.
 * 
 * <h2>Validator Architecture</h2>
 * 
 * <p>The validation system supports:
 * <ul>
 *   <li><strong>Modular Design:</strong> Each validator focuses on specific validation concerns</li>
 *   <li><strong>Priority-Based Execution:</strong> Validators run in priority order (lower first)</li>
 *   <li><strong>Fail-Fast Support:</strong> Critical validators can halt validation on failure</li>
 *   <li><strong>Rich Error Reporting:</strong> Detailed error and warning information</li>
 *   <li><strong>Extensibility:</strong> Custom validators can be easily added</li>
 * </ul>
 * 
 * <h2>Built-in Validators</h2>
 * 
 * <p>The framework includes several built-in validators:
 * <ul>
 *   <li><strong>CycleDetectionValidator:</strong> Detects circular dependencies in workflow graphs</li>
 *   <li><strong>EdgeOrderingValidator:</strong> Ensures unguarded edges come last in edge lists</li>
 * </ul>
 * 
 * <h2>Implementation Examples</h2>
 * 
 * <p><strong>Simple Validation Rule:</strong>
 * <pre>
 * public class StepExistenceValidator implements FlowConfigValidator {
 *     
 *     {@code @Override}
 *     public ValidationResult validate(FlowConfig config) {
 *         ValidationResult.Builder result = ValidationResult.builder();
 *         
 *         // Check all workflow edges reference existing steps
 *         for (Map.Entry&lt;String, WorkflowDef&gt; entry : config.workflows.entrySet()) {
 *             String workflowName = entry.getKey();
 *             WorkflowDef workflow = entry.getValue();
 *             
 *             for (EdgeDef edge : workflow.edges) {
 *                 if (!config.steps.containsKey(edge.from) && 
 *                     !isTerminalState(edge.from)) {
 *                     result.addError(
 *                         ValidationError.builder(ValidationErrorType.MISSING_STEP)
 *                             .workflowName(workflowName)
 *                             .message("Referenced step '" + edge.from + "' does not exist")
 *                             .detail("stepName", edge.from)
 *                             .detail("edgeDefinition", edge.from + " -> " + edge.to)
 *                             .build()
 *                     );
 *                 }
 *             }
 *         }
 *         
 *         return result.build();
 *     }
 *     
 *     {@code @Override}
 *     public String getValidatorName() {
 *         return "StepExistence";
 *     }
 *     
 *     {@code @Override}
 *     public int getPriority() {
 *         return 10;  // Run early - basic structural validation
 *     }
 *     
 *     private boolean isTerminalState(String stepName) {
 *         return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Complex Validator with Warnings:</strong>
 * <pre>
 * public class BusinessRuleValidator implements FlowConfigValidator {
 *     
 *     {@code @Override}
 *     public ValidationResult validate(FlowConfig config) {
 *         ValidationResult.Builder result = ValidationResult.builder();
 *         
 *         // Validate business-specific rules
 *         validatePaymentWorkflows(config, result);
 *         validateSecurityRequirements(config, result);
 *         validatePerformanceGuidelines(config, result);
 *         
 *         return result.build();
 *     }
 *     
 *     private void validatePaymentWorkflows(FlowConfig config, ValidationResult.Builder result) {
 *         for (Map.Entry&lt;String, WorkflowDef&gt; entry : config.workflows.entrySet()) {
 *             String workflowName = entry.getKey();
 *             
 *             if (workflowName.contains("payment") || workflowName.contains("billing")) {
 *                 // Payment workflows must have fraud detection
 *                 boolean hasFraudDetection = entry.getValue().edges.stream()
 *                     .anyMatch(edge -> edge.guard != null && 
 *                               edge.guard.toLowerCase().contains("fraud"));
 *                 
 *                 if (!hasFraudDetection) {
 *                     result.addError(
 *                         ValidationError.builder(ValidationErrorType.BUSINESS_RULE_VIOLATION)
 *                             .workflowName(workflowName)
 *                             .message("Payment workflows must include fraud detection guards")
 *                             .detail("requirement", "FRAUD_DETECTION_MANDATORY")
 *                             .build()
 *                     );
 *                 }
 *             }
 *         }
 *     }
 *     
 *     {@code @Override}
 *     public String getValidatorName() {
 *         return "BusinessRules";
 *     }
 *     
 *     {@code @Override}
 *     public boolean isFailFast() {
 *         return true;  // Business rule violations are critical
 *     }
 *     
 *     {@code @Override}
 *     public String getDescription() {
 *         return "Validates business-specific workflow requirements and compliance rules";
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Performance-Aware Validator:</strong>
 * <pre>
 * public class PerformanceValidator implements FlowConfigValidator {
 *     
 *     private static final int MAX_WORKFLOW_DEPTH = 20;
 *     private static final int MAX_EDGES_FROM_STEP = 10;
 *     
 *     {@code @Override}
 *     public ValidationResult validate(FlowConfig config) {
 *         ValidationResult.Builder result = ValidationResult.builder();
 *         
 *         for (Map.Entry&lt;String, WorkflowDef&gt; entry : config.workflows.entrySet()) {
 *             String workflowName = entry.getKey();
 *             WorkflowDef workflow = entry.getValue();
 *             
 *             // Check workflow depth
 *             int depth = calculateMaxDepth(workflow);
 *             if (depth > MAX_WORKFLOW_DEPTH) {
 *                 result.addWarning(
 *                     ValidationWarning.builder()
 *                         .workflowName(workflowName)
 *                         .message("Workflow depth (" + depth + ") exceeds recommended maximum (" + MAX_WORKFLOW_DEPTH + ")")
 *                         .detail("actualDepth", depth)
 *                         .detail("recommendedMax", MAX_WORKFLOW_DEPTH)
 *                         .build()
 *                 );
 *             }
 *             
 *             // Check branching factor
 *             Map&lt;String, Long&gt; edgeCounts = workflow.edges.stream()
 *                 .collect(Collectors.groupingBy(e -> e.from, Collectors.counting()));
 *             
 *             for (Map.Entry&lt;String, Long&gt; edgeCount : edgeCounts.entrySet()) {
 *                 if (edgeCount.getValue() > MAX_EDGES_FROM_STEP) {
 *                     result.addWarning(
 *                         ValidationWarning.builder()
 *                             .workflowName(workflowName)
 *                             .message("Step '" + edgeCount.getKey() + "' has " + 
 *                                     edgeCount.getValue() + " outgoing edges, consider refactoring")
 *                             .detail("stepName", edgeCount.getKey())
 *                             .detail("edgeCount", edgeCount.getValue())
 *                             .build()
 *                     );
 *                 }
 *             }
 *         }
 *         
 *         return result.build();
 *     }
 *     
 *     {@code @Override}
 *     public String getValidatorName() {
 *         return "Performance";
 *     }
 *     
 *     {@code @Override}
 *     public int getPriority() {
 *         return 200;  // Run after structural validation
 *     }
 * }
 * </pre>
 * 
 * <h2>Validator Registration</h2>
 * 
 * <p><strong>Manual Registration:</strong>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.builder()
 *     .addValidator(new CycleDetectionValidator())
 *     .addValidator(new EdgeOrderingValidator())
 *     .addValidator(new CustomBusinessRuleValidator())
 *     .addValidator(new SecurityValidator())
 *     .build();
 * </pre>
 * 
 * <p><strong>Default Engine with Custom Validators:</strong>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.createDefault();
 * // Default engine already includes built-in validators
 * 
 * // Add custom validators
 * FlowConfigValidationEngine customEngine = FlowConfigValidationEngine.builder()
 *     .addValidators(engine.getValidators())  // Include defaults
 *     .addValidator(new MyCustomValidator())
 *     .build();
 * </pre>
 * 
 * <h2>Error Handling and Reporting</h2>
 * 
 * <p>Validators should use the structured error reporting system:
 * 
 * <pre>
 * // Create detailed error with context
 * ValidationError error = ValidationError.builder(ValidationErrorType.CONFIGURATION_ERROR)
 *     .workflowName(workflowName)
 *     .message("Configuration parameter 'timeout' must be positive")
 *     .location("steps." + stepName + ".config.timeout")
 *     .detail("stepName", stepName)
 *     .detail("actualValue", actualTimeout)
 *     .detail("expectedMin", 1)
 *     .build();
 * 
 * result.addError(error);
 * 
 * // Create warning for best practices
 * ValidationWarning warning = ValidationWarning.builder()
 *     .workflowName(workflowName)
 *     .message("Consider using exponential backoff for retry configuration")
 *     .location("steps." + stepName + ".retry")
 *     .detail("currentBackoff", "FIXED")
 *     .detail("recommendedBackoff", "EXPONENTIAL")
 *     .build();
 * 
 * result.addWarning(warning);
 * </pre>
 * 
 * <h2>Testing Validators</h2>
 * 
 * <p><strong>Unit Testing Pattern:</strong>
 * <pre>
 * {@code @Test}
 * public void testValidConfiguration() {
 *     // Arrange
 *     FlowConfig config = createValidConfig();
 *     MyValidator validator = new MyValidator();
 *     
 *     // Act
 *     ValidationResult result = validator.validate(config);
 *     
 *     // Assert
 *     assertTrue(result.isValid());
 *     assertTrue(result.getErrors().isEmpty());
 *     assertTrue(result.getWarnings().isEmpty());
 * }
 * 
 * {@code @Test}
 * public void testInvalidConfiguration() {
 *     // Arrange
 *     FlowConfig config = createInvalidConfig();
 *     MyValidator validator = new MyValidator();
 *     
 *     // Act
 *     ValidationResult result = validator.validate(config);
 *     
 *     // Assert
 *     assertFalse(result.isValid());
 *     assertEquals(1, result.getErrors().size());
 *     
 *     ValidationError error = result.getErrors().get(0);
 *     assertEquals("Expected error message", error.getMessage());
 *     assertEquals("expectedValue", error.getDetail("actualKey", String.class));
 * }
 * </pre>
 * 
 * @see ValidationResult for result structure
 * @see ValidationError for error reporting
 * @see ValidationWarning for warning reporting
 * @see FlowConfigValidationEngine for validator orchestration
 * 
 * @since 1.0.0
 * @author StepFlow Team
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