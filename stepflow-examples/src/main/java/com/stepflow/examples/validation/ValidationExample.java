package com.stepflow.examples.validation;

import com.stepflow.config.FlowConfig;
import com.stepflow.core.SimpleEngine;
import com.stepflow.validation.*;
import com.stepflow.resource.YamlResourceLoader;

import java.util.Map;

/**
 * Example demonstrating FlowConfig validation before workflow execution.
 * Shows how to catch configuration errors early and provide detailed feedback.
 */
public class ValidationExample {
    
    public static void main(String[] args) {
        System.out.println("=== StepFlow Validation Example ===\n");
        
        // Example 1: Validate a workflow with cycles
        System.out.println("1. Testing workflow with cycle detection:");
        validateWorkflowWithCycle();
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // Example 2: Validate edge ordering issues
        System.out.println("2. Testing workflow with edge ordering issues:");
        validateWorkflowWithEdgeOrderingIssues();
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // Example 3: Validate before execution (recommended pattern)
        System.out.println("3. Validation-first execution pattern:");
        validateBeforeExecution();
    }
    
    /**
     * Demonstrates cycle detection in workflow validation.
     */
    private static void validateWorkflowWithCycle() {
        try {
            // Create validation engine
            FlowConfigValidationEngine validator = FlowConfigValidationEngine.createDefault();
            
            // Load workflow with cycle
            YamlResourceLoader loader = new YamlResourceLoader();
            Map<String, Object> yamlData = Map.of(
                "steps", Map.of(
                    "stepA", Map.of("type", "processStep"),
                    "stepB", Map.of("type", "processStep"),
                    "stepC", Map.of("type", "processStep")
                ),
                "workflows", Map.of(
                    "cyclicWorkflow", Map.of(
                        "root", "stepA",
                        "edges", java.util.List.of(
                            Map.of("from", "stepA", "to", "stepB"),
                            Map.of("from", "stepB", "to", "stepC"),
                            Map.of("from", "stepC", "to", "stepA") // Creates cycle
                        )
                    )
                )
            );
            
            FlowConfig config = convertYamlToFlowConfig(yamlData);
            
            // Validate configuration
            ValidationResult result = validator.validate(config);
            
            if (result.isValid()) {
                System.out.println("✅ Workflow configuration is valid");
            } else {
                System.out.println("❌ Workflow configuration has errors:");
                
                for (ValidationError error : result.getErrors()) {
                    System.out.println("\n  ERROR: " + error.getMessage());
                    System.out.println("  Type: " + error.getType());
                    System.out.println("  Workflow: " + error.getWorkflowName());
                    System.out.println("  Location: " + error.getLocation());
                    
                    // Show cycle-specific details
                    if (error.getType() == ValidationErrorType.CYCLE_DETECTED) {
                        @SuppressWarnings("unchecked")
                        java.util.List<String> cyclePath = error.getDetail("cyclePath", java.util.List.class);
                        @SuppressWarnings("unchecked")
                        java.util.List<String> involvedEdges = error.getDetail("involvedEdges", java.util.List.class);
                        
                        System.out.println("  Cycle Path: " + String.join(" → ", cyclePath));
                        System.out.println("  Involved Edges: " + involvedEdges);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error during validation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrates edge ordering validation.
     */
    private static void validateWorkflowWithEdgeOrderingIssues() {
        try {
            FlowConfigValidationEngine validator = FlowConfigValidationEngine.createDefault();
            
            // Create workflow with edge ordering issues
            Map<String, Object> yamlData = Map.of(
                "steps", Map.of(
                    "validate", Map.of("type", "validateStep"),
                    "process", Map.of("type", "processStep"),
                    "notify", Map.of("type", "notifyStep"),
                    "audit", Map.of("type", "auditStep")
                ),
                "workflows", Map.of(
                    "problematicWorkflow", Map.of(
                        "root", "validate",
                        "edges", java.util.List.of(
                            Map.of("from", "validate", "to", "process"),
                            // Unguarded edge (should be last)
                            Map.of("from", "process", "to", "notify"),
                            // Guarded edge after unguarded (VIOLATION)
                            Map.of("from", "process", "to", "audit", "guard", "auditRequired"),
                            Map.of("from", "notify", "to", "SUCCESS"),
                            Map.of("from", "audit", "to", "SUCCESS")
                        )
                    )
                )
            );
            
            FlowConfig config = convertYamlToFlowConfig(yamlData);
            
            // Validate with detailed error reporting
            ValidationResult result = validator.validate(config);
            
            if (!result.isValid()) {
                System.out.println("❌ Edge ordering violations found:");
                
                for (ValidationError error : result.getErrors()) {
                    System.out.println("\n  ERROR: " + error.getMessage());
                    System.out.println("  Type: " + error.getType());
                    
                    if (error.getType() == ValidationErrorType.UNGUARDED_EDGE_NOT_LAST) {
                        String stepName = error.getDetailAsString("stepName");
                        String unguardedEdge = error.getDetailAsString("unguardedEdge");
                        @SuppressWarnings("unchecked")
                        java.util.List<String> violatingEdges = error.getDetail("violatingEdges", java.util.List.class);
                        
                        System.out.println("  Problem Step: " + stepName);
                        System.out.println("  Unguarded Edge: " + unguardedEdge);
                        System.out.println("  Guarded Edges After: " + violatingEdges);
                        System.out.println("  🔧 Fix: Move unguarded edge to the end of the step's edge list");
                    }
                }
                
                // Show suggested fix
                System.out.println("\n  💡 SUGGESTED FIX:");
                System.out.println("  Reorder edges for step 'process' as follows:");
                System.out.println("    1. process → audit [guard: auditRequired]  # Guarded edges first");
                System.out.println("    2. process → notify                       # Unguarded edge last");
            }
            
        } catch (Exception e) {
            System.err.println("Error during validation: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates the recommended pattern: validate before execution.
     */
    private static void validateBeforeExecution() {
        try {
            // Create validation engine
            FlowConfigValidationEngine validator = FlowConfigValidationEngine.builder()
                .addValidator(new com.stepflow.validation.validators.CycleDetectionValidator())
                .addValidator(new com.stepflow.validation.validators.EdgeOrderingValidator())
                .enableCaching(true)  // Enable caching for repeated validations
                .build();
            
            // Load a valid workflow
            Map<String, Object> yamlData = Map.of(
                "steps", Map.of(
                    "validate", Map.of("type", "validateStep"),
                    "process", Map.of("type", "processStep"),
                    "notify", Map.of("type", "notifyStep")
                ),
                "workflows", Map.of(
                    "orderProcessing", Map.of(
                        "root", "validate",
                        "edges", java.util.List.of(
                            Map.of("from", "validate", "to", "process"),
                            Map.of("from", "process", "to", "notify", "guard", "shouldNotify"),
                            Map.of("from", "process", "to", "SUCCESS"), // Unguarded fallback (last)
                            Map.of("from", "notify", "to", "SUCCESS")
                        )
                    )
                )
            );
            
            FlowConfig config = convertYamlToFlowConfig(yamlData);
            
            // Step 1: Validate configuration
            System.out.println("🔍 Validating workflow configuration...");
            ValidationResult validationResult = validator.validate(config);
            
            if (!validationResult.isValid()) {
                System.out.println("❌ Configuration validation failed!");
                System.out.println(validationResult.getErrorSummary());
                System.out.println("Aborting execution to prevent runtime issues.");
                return;
            }
            
            System.out.println("✅ Configuration validation passed!");
            
            // Show validation metrics
            Map<String, Object> metadata = validationResult.getMetadata();
            System.out.println("  Validation time: " + metadata.get("totalValidationTime") + "ms");
            System.out.println("  Validators run: " + metadata.get("validatorsRun"));
            System.out.println("  Workflows validated: " + metadata.get("workflowsValidated"));
            
            // Step 2: Create engine and execute (safe now)
            System.out.println("\n🚀 Creating engine and executing workflow...");
            SimpleEngine engine = SimpleEngine.create(createTempYamlFile(yamlData), "com.stepflow.examples");
            
            // This would now execute safely since we've validated the configuration
            System.out.println("✅ Engine created successfully - ready for execution!");
            System.out.println("💡 You can now safely call engine.execute() with confidence.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to convert YAML data to FlowConfig.
     */
    private static FlowConfig convertYamlToFlowConfig(Map<String, Object> yamlData) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        return yaml.loadAs(yaml.dump(yamlData), FlowConfig.class);
    }
    
    /**
     * Helper to create a temporary YAML file for SimpleEngine.
     * In real usage, you'd have actual YAML files.
     */
    private static String createTempYamlFile(Map<String, Object> yamlData) {
        // In a real implementation, you'd write to a temp file
        // For this example, we'll just return a placeholder
        return "classpath:examples/simple.yaml";  // Use existing example
    }
}