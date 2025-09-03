/**
 * Comprehensive validation framework for StepFlow workflows.
 * 
 * <p>This package provides an extensible validation system for {@link com.stepflow.config.FlowConfig} instances,
 * enabling early detection of workflow configuration issues before execution.
 * 
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link com.stepflow.validation.FlowConfigValidationEngine} - Main validation coordinator</li>
 *   <li>{@link com.stepflow.validation.FlowConfigValidator} - Interface for creating custom validators</li>
 *   <li>{@link com.stepflow.validation.ValidationResult} - Comprehensive validation results with detailed errors</li>
 *   <li>{@link com.stepflow.validation.ValidationError} - Structured error information with context</li>
 *   <li>{@link com.stepflow.validation.ValidationWarning} - Non-critical issues that may need attention</li>
 * </ul>
 * 
 * <h2>Built-in Validators:</h2>
 * <ul>
 *   <li><strong>Cycle Detection:</strong> Identifies circular dependencies in workflow graphs</li>
 *   <li><strong>Edge Ordering:</strong> Validates guard positioning rules for multi-edge steps</li>
 * </ul>
 * 
 * <h2>Usage Examples:</h2>
 * 
 * <h3>Basic Validation:</h3>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.createDefault();
 * ValidationResult result = engine.validate(flowConfig);
 * 
 * if (!result.isValid()) {
 *     System.err.println("Validation failed:");
 *     for (ValidationError error : result.getErrors()) {
 *         System.err.println("  " + error.getMessage());
 *     }
 * }
 * </pre>
 * 
 * <h3>Custom Validator:</h3>
 * <pre>
 * public class BusinessRuleValidator implements FlowConfigValidator {
 *     {@literal @}Override
 *     public ValidationResult validate(FlowConfig config) {
 *         // Custom validation logic
 *         return ValidationResult.success();
 *     }
 *     
 *     {@literal @}Override
 *     public String getValidatorName() {
 *         return "BusinessRules";
 *     }
 * }
 * </pre>
 * 
 * <h3>Extensible Engine:</h3>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.builder()
 *     .addValidator(new CycleDetectionValidator())
 *     .addValidator(new EdgeOrderingValidator())
 *     .addValidator(new BusinessRuleValidator())
 *     .enableCaching(true)
 *     .build();
 * </pre>
 * 
 * <h2>Error Types:</h2>
 * <p>The validation system provides detailed error categorization through {@link com.stepflow.validation.ValidationErrorType}:
 * <ul>
 *   <li><strong>Structural Errors:</strong> Cycles, dead ends, missing roots</li>
 *   <li><strong>Edge Errors:</strong> Invalid guard ordering, multiple unguarded edges</li>
 *   <li><strong>Reference Errors:</strong> Undefined steps or guards</li>
 *   <li><strong>Configuration Errors:</strong> Malformed YAML, syntax issues</li>
 * </ul>
 * 
 * <h2>Performance Features:</h2>
 * <ul>
 *   <li><strong>Priority-based execution:</strong> Critical validators run first</li>
 *   <li><strong>Fail-fast support:</strong> Stop on first critical error</li>
 *   <li><strong>Result caching:</strong> Cache validation results for repeated checks</li>
 *   <li><strong>Detailed timing:</strong> Performance metrics for optimization</li>
 * </ul>
 * 
 * @since 0.2.0
 */
package com.stepflow.validation;