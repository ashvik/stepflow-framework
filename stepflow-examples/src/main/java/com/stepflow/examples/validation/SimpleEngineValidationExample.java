package com.stepflow.examples.validation;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;
import com.stepflow.validation.*;
import com.stepflow.validation.validators.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive example demonstrating SimpleEngine's integrated validation capabilities.
 * Shows different ways to use validation before workflow execution.
 */
public class SimpleEngineValidationExample {
    
    public static void main(String[] args) {
        System.out.println("=== SimpleEngine Integrated Validation Example ===\n");
        
        // Example 1: Basic validation using SimpleEngine
        System.out.println("1. Basic validation with SimpleEngine:");
        basicValidationExample();
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Example 2: Custom validation configuration
        System.out.println("2. Custom validation configuration:");
        customValidationExample();
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Example 3: Validation-first execution pattern
        System.out.println("3. Validation-first execution pattern:");
        validationFirstExecutionExample();
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Example 4: Single workflow validation
        System.out.println("4. Single workflow validation:");
        singleWorkflowValidationExample();
    }
    
    /**
     * Demonstrates basic validation using SimpleEngine's built-in validation.
     */
    private static void basicValidationExample() {
        try {
            // Create SimpleEngine (automatically includes validation)
            SimpleEngine engine = SimpleEngine.create("classpath:examples/complex.yaml", "com.stepflow.examples");
            
            System.out.println("🔍 Running basic configuration validation...");
            
            // Validate configuration
            ValidationResult result = engine.validateConfiguration();
            
            if (result.isValid()) {
                System.out.println("✅ Configuration is valid!");
                System.out.println("  Validation time: " + result.getMetadata().get("totalValidationTime") + "ms");
                System.out.println("  Validators run: " + result.getMetadata().get("validatorsRun"));
            } else {
                System.out.println("❌ Configuration has issues:");
                for (ValidationError error : result.getErrors()) {
                    System.out.println("  • " + error.getMessage());
                    System.out.println("    Type: " + error.getType());
                    System.out.println("    Location: " + error.getLocation());
                }
            }
            
            // Show registered validators
            System.out.println("\n📋 Registered validators:");
            for (FlowConfigValidationEngine.ValidatorInfo info : engine.getValidationInfo()) {
                System.out.println("  • " + info.name + " (priority: " + info.priority + 
                                 ", fail-fast: " + info.failFast + ")");
                System.out.println("    " + info.description);
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates custom validation configuration.
     */
    private static void customValidationExample() {
        try {
            // Create custom validation engine with additional validators
            FlowConfigValidationEngine customValidator = FlowConfigValidationEngine.builder()
                .addValidator(new CycleDetectionValidator())
                .addValidator(new EdgeOrderingValidator())
                .enableCaching(true)
                .build();
            
            // Create SimpleEngine with custom validation
            SimpleEngine engine = SimpleEngine.builder()
                .withExternalYamls("classpath:examples/complex.yaml")
                .withPackages("com.stepflow.examples")
                .withValidationEngine(customValidator)
                .build();
            
            System.out.println("🛠️ Using custom validation engine...");
            
            // Run validation with fail-fast
            ValidationResult result = engine.validateConfiguration(true);
            
            if (result.isValid()) {
                System.out.println("✅ Custom validation passed!");
            } else {
                System.out.println("❌ Custom validation found issues:");
                System.out.println(result.getErrorSummary());
            }
            
        } catch (Exception e) {
            System.err.println("Custom validation error: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates the validation-first execution pattern.
     */
    private static void validationFirstExecutionExample() {
        try {
            // Create engine
            SimpleEngine engine = SimpleEngine.create("classpath:examples/complex.yaml", "com.stepflow.examples");
            
            System.out.println("🚦 Validation-first execution pattern...");
            
            // Step 1: Validate configuration before any execution
            try {
                engine.validateConfigurationOrThrow();
                System.out.println("✅ Configuration validation passed - safe to execute!");
            } catch (ValidationException e) {
                System.err.println("❌ Configuration validation failed!");
                System.err.println("Error count: " + e.getErrorCount());
                System.err.println("Warning count: " + e.getWarningCount());
                System.err.println("\nDetailed errors:");
                for (ValidationError error : e.getValidationErrors()) {
                    System.err.println("  • " + error.getMessage());
                }
                return; // Don't proceed with execution
            }
            
            // Step 2: Execute workflows (safe now)
            System.out.println("\n🚀 Executing workflow...");
            ExecutionContext context = new ExecutionContext();
            context.put("amount", 150.0); // Above threshold for complex workflow
            
            StepResult result = engine.execute("complexFlow", context);
            
            if (result.isSuccess()) {
                System.out.println("✅ Workflow executed successfully!");
                System.out.println("Result context keys: " + result.context.keySet());
            } else {
                System.out.println("❌ Workflow execution failed: " + result.message);
            }
            
        } catch (Exception e) {
            System.err.println("Execution error: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates single workflow validation.
     */
    private static void singleWorkflowValidationExample() {
        try {
            SimpleEngine engine = SimpleEngine.create("classpath:examples/complex.yaml", "com.stepflow.examples");
            
            System.out.println("🎯 Validating specific workflow...");
            
            // Validate only the 'complexFlow' workflow
            ValidationResult result = engine.validateWorkflow("complexFlow");
            
            if (result.isValid()) {
                System.out.println("✅ Workflow 'complexFlow' is valid!");
                
                // Show validation metadata
                Map<String, Object> metadata = result.getMetadata();
                System.out.println("  Validation time: " + metadata.get("totalValidationTime") + "ms");
                System.out.println("  Workflows validated: " + metadata.get("workflowsValidated"));
                
            } else {
                System.out.println("❌ Workflow 'complexFlow' has issues:");
                for (ValidationError error : result.getErrorsForWorkflow("complexFlow")) {
                    System.out.println("  • " + error.getMessage());
                    System.out.println("    Details: " + error.getDetails());
                }
            }
            
            // Try validating non-existent workflow
            System.out.println("\n🔍 Testing validation of non-existent workflow...");
            ValidationResult nonExistentResult = engine.validateWorkflow("nonExistentWorkflow");
            
            if (!nonExistentResult.isValid()) {
                System.out.println("✅ Correctly detected missing workflow:");
                System.out.println("  " + nonExistentResult.getErrors().get(0).getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Single workflow validation error: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates validation with custom business rules.
     */
    @SuppressWarnings("unused")
    private static void customBusinessRuleValidationExample() {
        try {
            // Create custom validation engine with business rules
            FlowConfigValidationEngine customValidator = FlowConfigValidationEngine.builder()
                .addValidator(new CycleDetectionValidator())
                .addValidator(new EdgeOrderingValidator())
                // Could add custom business rule validator here
                .build();
            
            // Use fluent validation configuration
            SimpleEngine engine = SimpleEngine.builder()
                .withExternalYamls("classpath:examples/complex.yaml")
                .withPackages("com.stepflow.examples")
                .withCustomValidation(validationBuilder -> validationBuilder
                    .addValidator(new CycleDetectionValidator())
                    .addValidator(new EdgeOrderingValidator())
                    .enableCaching(true))
                .build();
            
            System.out.println("🧪 Custom business rule validation...");
            
            ValidationResult result = engine.validateConfiguration();
            
            if (result.isValid()) {
                System.out.println("✅ All business rules passed!");
            } else {
                System.out.println("❌ Business rule violations found:");
                System.out.println(result.getErrorSummary());
            }
            
        } catch (Exception e) {
            System.err.println("Custom business rule validation error: " + e.getMessage());
        }
    }
}