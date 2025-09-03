package com.stepflow.core;

import com.stepflow.config.FlowConfig;
import com.stepflow.validation.*;
import com.stepflow.validation.validators.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SimpleEngine's validation capabilities.
 */
public class SimpleEngineValidationIntegrationTest {
    
    @Test
    void testBasicValidation() {
        FlowConfig config = createValidFlowConfig();
        SimpleEngine engine = new SimpleEngine(config);
        
        ValidationResult result = engine.validateConfiguration();
        
        assertTrue(result.isValid(), "Valid configuration should pass validation");
        assertEquals(0, result.getErrors().size(), "Should have no errors");
    }
    
    @Test
    void testValidationWithCycle() {
        FlowConfig config = createFlowConfigWithCycle();
        SimpleEngine engine = new SimpleEngine(config);
        
        ValidationResult result = engine.validateConfiguration();
        
        assertFalse(result.isValid(), "Configuration with cycle should fail validation");
        assertTrue(result.getErrors().size() > 0, "Should have cycle detection errors");
        
        List<ValidationError> cycleErrors = result.getErrorsOfType(ValidationErrorType.CYCLE_DETECTED);
        assertEquals(1, cycleErrors.size(), "Should detect exactly one cycle");
        
        ValidationError cycleError = cycleErrors.get(0);
        assertNotNull(cycleError.getDetail("cyclePath", List.class), "Should have cycle path details");
    }
    
    @Test
    void testValidationOrThrow() {
        FlowConfig validConfig = createValidFlowConfig();
        SimpleEngine validEngine = new SimpleEngine(validConfig);
        
        // Should not throw for valid configuration
        assertDoesNotThrow(() -> validEngine.validateConfigurationOrThrow());
        
        FlowConfig invalidConfig = createFlowConfigWithCycle();
        SimpleEngine invalidEngine = new SimpleEngine(invalidConfig);
        
        // Should throw for invalid configuration
        ValidationException exception = assertThrows(ValidationException.class, 
                () -> invalidEngine.validateConfigurationOrThrow());
        
        assertTrue(exception.getErrorCount() > 0, "Exception should contain validation errors");
        assertNotNull(exception.getValidationResult(), "Exception should contain validation result");
    }
    
    @Test
    void testSingleWorkflowValidation() {
        FlowConfig config = createMultiWorkflowConfig();
        SimpleEngine engine = new SimpleEngine(config);
        
        // Test validating existing workflow
        ValidationResult result = engine.validateWorkflow("validWorkflow");
        assertTrue(result.isValid(), "Valid workflow should pass validation");
        
        // Test validating non-existent workflow
        ValidationResult nonExistentResult = engine.validateWorkflow("nonExistentWorkflow");
        assertFalse(nonExistentResult.isValid(), "Non-existent workflow should fail validation");
    }
    
    @Test
    void testCustomValidationEngine() {
        FlowConfigValidationEngine customValidator = FlowConfigValidationEngine.builder()
            .addValidator(new CycleDetectionValidator())
            .addValidator(new EdgeOrderingValidator())
            .enableCaching(true)
            .build();
        
        FlowConfig config = createValidFlowConfig();
        SimpleEngine engine = new SimpleEngine(config, customValidator);
        
        ValidationResult result = engine.validateConfiguration();
        assertTrue(result.isValid(), "Valid configuration should pass custom validation");
        
        List<FlowConfigValidationEngine.ValidatorInfo> validators = engine.getValidationInfo();
        assertEquals(2, validators.size(), "Should have two custom validators");
    }
    
    @Test
    void testValidationFailFast() {
        FlowConfig config = createFlowConfigWithMultipleIssues();
        SimpleEngine engine = new SimpleEngine(config);
        
        ValidationResult normalResult = engine.validateConfiguration(false);
        ValidationResult failFastResult = engine.validateConfiguration(true);
        
        // Both should detect errors, but fail-fast might have fewer due to early termination
        assertFalse(normalResult.isValid());
        assertFalse(failFastResult.isValid());
        assertTrue(failFastResult.getErrors().size() <= normalResult.getErrors().size());
    }
    
    @Test
    void testValidationMetadata() {
        FlowConfig config = createValidFlowConfig();
        SimpleEngine engine = new SimpleEngine(config);
        
        ValidationResult result = engine.validateConfiguration();
        
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata.get("totalValidationTime"));
        assertNotNull(metadata.get("validatorsRun"));
        assertNotNull(metadata.get("workflowsValidated"));
        
        assertTrue((Long) metadata.get("totalValidationTime") >= 0);
        assertTrue((Integer) metadata.get("validatorsRun") > 0);
    }
    
    // Helper methods to create test configurations
    
    private FlowConfig createValidFlowConfig() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "step1", createStep("step1Impl"),
            "step2", createStep("step2Impl"),
            "step3", createStep("step3Impl")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("step1", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", null), // Unguarded fallback (last)
            createEdge("step2", "SUCCESS", null),
            createEdge("step3", "SUCCESS", null)
        )));
        
        return config;
    }
    
    private FlowConfig createFlowConfigWithCycle() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "stepA", createStep("stepAImpl"),
            "stepB", createStep("stepBImpl"),
            "stepC", createStep("stepCImpl")
        );
        
        config.workflows = Map.of("cyclicWorkflow", createWorkflowDef("stepA", Arrays.asList(
            createEdge("stepA", "stepB", null),
            createEdge("stepB", "stepC", null),
            createEdge("stepC", "stepA", null) // Creates cycle
        )));
        
        return config;
    }
    
    private FlowConfig createFlowConfigWithMultipleIssues() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "stepA", createStep("stepAImpl"),
            "stepB", createStep("stepBImpl")
        );
        
        config.workflows = Map.of("problematicWorkflow", createWorkflowDef("stepA", Arrays.asList(
            // Cycle
            createEdge("stepA", "stepB", null),
            createEdge("stepB", "stepA", null),
            // Multiple unguarded edges from stepA
            createEdge("stepA", "SUCCESS", null)
        )));
        
        return config;
    }
    
    private FlowConfig createMultiWorkflowConfig() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "step1", createStep("step1Impl"),
            "step2", createStep("step2Impl")
        );
        
        config.workflows = Map.of(
            "validWorkflow", createWorkflowDef("step1", Arrays.asList(
                createEdge("step1", "step2", null),
                createEdge("step2", "SUCCESS", null)
            )),
            "anotherWorkflow", createWorkflowDef("step2", Arrays.asList(
                createEdge("step2", "SUCCESS", null)
            ))
        );
        
        return config;
    }
    
    private FlowConfig.StepDef createStep(String type) {
        FlowConfig.StepDef step = new FlowConfig.StepDef();
        step.type = type;
        return step;
    }
    
    private FlowConfig.WorkflowDef createWorkflowDef(String root, List<FlowConfig.EdgeDef> edges) {
        FlowConfig.WorkflowDef workflow = new FlowConfig.WorkflowDef();
        workflow.root = root;
        workflow.edges = edges;
        return workflow;
    }
    
    private FlowConfig.EdgeDef createEdge(String from, String to, String guard) {
        FlowConfig.EdgeDef edge = new FlowConfig.EdgeDef();
        edge.from = from;
        edge.to = to;
        edge.guard = guard;
        return edge;
    }
}