package com.stepflow.validation;

import com.stepflow.config.FlowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for FlowConfigValidationEngine demonstrating validation capabilities.
 */
public class FlowConfigValidationEngineTest {
    
    private FlowConfigValidationEngine validationEngine;
    
    @BeforeEach
    void setUp() {
        validationEngine = FlowConfigValidationEngine.createDefault();
    }
    
    @Test
    void testValidWorkflow() {
        FlowConfig config = createValidWorkflow();
        ValidationResult result = validationEngine.validate(config);
        
        assertTrue(result.isValid(), "Valid workflow should pass validation");
        assertTrue(result.getErrors().isEmpty(), "Valid workflow should have no errors");
    }
    
    @Test
    void testCycleDetection() {
        FlowConfig config = createWorkflowWithCycle();
        ValidationResult result = validationEngine.validate(config);
        
        assertFalse(result.isValid(), "Workflow with cycle should fail validation");
        assertFalse(result.getErrors().isEmpty(), "Should have cycle detection errors");
        
        // Check for cycle detection error
        List<ValidationError> cycleErrors = result.getErrorsOfType(ValidationErrorType.CYCLE_DETECTED);
        assertEquals(1, cycleErrors.size(), "Should detect exactly one cycle");
        
        ValidationError cycleError = cycleErrors.get(0);
        assertEquals("testWorkflow", cycleError.getWorkflowName());
        assertNotNull(cycleError.getDetail("cyclePath", List.class));
        assertNotNull(cycleError.getDetail("involvedEdges", List.class));
        
        System.out.println("Cycle Detection Error:");
        System.out.println("  " + cycleError.getMessage());
        System.out.println("  Cycle Path: " + cycleError.getDetail("cyclePath", List.class));
        System.out.println("  Involved Edges: " + cycleError.getDetail("involvedEdges", List.class));
    }
    
    @Test
    void testMultipleUnguardedEdges() {
        FlowConfig config = createWorkflowWithMultipleUnguardedEdges();
        ValidationResult result = validationEngine.validate(config);
        
        assertFalse(result.isValid(), "Workflow with multiple unguarded edges should fail");
        
        List<ValidationError> edgeErrors = result.getErrorsOfType(ValidationErrorType.MULTIPLE_UNGUARDED_EDGES);
        assertEquals(1, edgeErrors.size(), "Should detect multiple unguarded edges error");
        
        ValidationError edgeError = edgeErrors.get(0);
        assertEquals("testWorkflow", edgeError.getWorkflowName());
        assertEquals("process", edgeError.getDetailAsString("stepName"));
        
        @SuppressWarnings("unchecked")
        List<String> unguardedEdges = (List<String>) edgeError.getDetails().get("unguardedEdges");
        assertTrue(unguardedEdges.size() > 1, "Should identify multiple unguarded edges");
        
        System.out.println("Multiple Unguarded Edges Error:");
        System.out.println("  " + edgeError.getMessage());
        System.out.println("  Unguarded Edges: " + unguardedEdges);
    }
    
    @Test
    void testUnguardedEdgeNotLast() {
        FlowConfig config = createWorkflowWithUnguardedEdgeNotLast();
        ValidationResult result = validationEngine.validate(config);
        
        assertFalse(result.isValid(), "Workflow with unguarded edge not last should fail");
        
        List<ValidationError> orderErrors = result.getErrorsOfType(ValidationErrorType.UNGUARDED_EDGE_NOT_LAST);
        assertEquals(1, orderErrors.size(), "Should detect edge ordering error");
        
        ValidationError orderError = orderErrors.get(0);
        assertEquals("testWorkflow", orderError.getWorkflowName());
        assertEquals("process", orderError.getDetailAsString("stepName"));
        
        System.out.println("Edge Ordering Error:");
        System.out.println("  " + orderError.getMessage());
        System.out.println("  Unguarded Edge: " + orderError.getDetails().get("unguardedEdge"));
        System.out.println("  Violating Edges: " + orderError.getDetails().get("violatingEdges"));
    }
    
    @Test
    void testValidationResultSummary() {
        FlowConfig config = createWorkflowWithMultipleIssues();
        ValidationResult result = validationEngine.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        
        String summary = result.getErrorSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Validation failed"));
        
        System.out.println("Validation Summary:");
        System.out.println(summary);
    }
    
    private FlowConfig createValidWorkflow() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "validate", createStep("validateStep"),
            "process", createStep("processStep"),
            "notify", createStep("notifyStep")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("validate", Arrays.asList(
            createEdge("validate", "process", null),
            createEdge("process", "notify", "validGuard"),
            createEdge("process", "SUCCESS", null), // Fallback unguarded edge last
            createEdge("notify", "SUCCESS", null)
        )));
        
        return config;
    }
    
    private FlowConfig createWorkflowWithCycle() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "stepA", createStep("stepAImpl"),
            "stepB", createStep("stepBImpl"),
            "stepC", createStep("stepCImpl")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("stepA", Arrays.asList(
            createEdge("stepA", "stepB", null),
            createEdge("stepB", "stepC", null),
            createEdge("stepC", "stepA", null) // Creates cycle: A -> B -> C -> A
        )));
        
        return config;
    }
    
    private FlowConfig createWorkflowWithMultipleUnguardedEdges() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "validate", createStep("validateStep"),
            "process", createStep("processStep"),
            "notify", createStep("notifyStep"),
            "audit", createStep("auditStep")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("validate", Arrays.asList(
            createEdge("validate", "process", null),
            createEdge("process", "notify", null),   // Unguarded edge 1
            createEdge("process", "audit", null),    // Unguarded edge 2 - VIOLATION
            createEdge("notify", "SUCCESS", null),
            createEdge("audit", "SUCCESS", null)
        )));
        
        return config;
    }
    
    private FlowConfig createWorkflowWithUnguardedEdgeNotLast() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "validate", createStep("validateStep"),
            "process", createStep("processStep"),
            "notify", createStep("notifyStep"),
            "audit", createStep("auditStep")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("validate", Arrays.asList(
            createEdge("validate", "process", null),
            createEdge("process", "notify", null),      // Unguarded edge (should be last)
            createEdge("process", "audit", "auditGuard"), // Guarded edge after unguarded - VIOLATION
            createEdge("notify", "SUCCESS", null),
            createEdge("audit", "SUCCESS", null)
        )));
        
        return config;
    }
    
    private FlowConfig createWorkflowWithMultipleIssues() {
        FlowConfig config = new FlowConfig();
        config.steps = Map.of(
            "stepA", createStep("stepAImpl"),
            "stepB", createStep("stepBImpl"),
            "stepC", createStep("stepCImpl")
        );
        
        config.workflows = Map.of("testWorkflow", createWorkflowDef("stepA", Arrays.asList(
            // Cycle: A -> B -> A
            createEdge("stepA", "stepB", null),
            createEdge("stepB", "stepA", null),
            // Multiple unguarded edges from stepA
            createEdge("stepA", "stepC", null),
            createEdge("stepC", "SUCCESS", null)
        )));
        
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