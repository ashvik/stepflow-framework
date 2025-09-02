package com.stepflow.engine;

import com.stepflow.config.FlowConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Tests for edge validation logic in Engine constructor.
 * Verifies that invalid edge configurations are properly detected and reported.
 */
public class EdgeValidationTest {

    private FlowConfig config;

    @BeforeEach
    void setUp() {
        config = new FlowConfig();
        config.steps = new HashMap<>();
        config.workflows = new HashMap<>();
    }

    @Test
    void testValidSingleEdgeConfiguration() {
        // Single edge per step - should not trigger validation
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", null)
        ));

        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testValidMultipleGuardedEdgesConfiguration() {
        // Multiple edges, all with guards - should be valid
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", "guard2"),
            createEdge("step1", "step4", "guard3")
        ));

        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testValidDefaultEdgeLastConfiguration() {
        // Multiple edges with default edge last - should be valid
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", "guard2"),
            createEdge("step1", "step4", null) // Default edge last
        ));

        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testInvalidMultipleDefaultEdgesConfiguration() {
        // Multiple default edges from same step - should fail
        // Put both default edges at the end to trigger "multiple default edges" error
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", null),  // Default edge 1 - last position
            createEdge("step1", "step4", null)   // Default edge 2 - INVALID (would be unreachable anyway)
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        String message = exception.getMessage();
        // The validation will catch "unreachable edges" first since the first default edge makes subsequent ones unreachable
        assertTrue(message.contains("unreachable edges after default edge"));
        assertTrue(message.contains("testWorkflow"));
        assertTrue(message.contains("step1"));
    }

    @Test 
    void testInvalidMultipleDefaultEdgesAtEndConfiguration() {
        // Test specifically for multiple default edges - this should trigger the multiple defaults check
        // We need to construct the workflow edges carefully to reach the multiple defaults validation
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", null),  // First default edge  
            createEdge("step1", "step3", null)   // Second default edge - should trigger multiple defaults error
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        String message = exception.getMessage();
        // This should trigger either multiple defaults or unreachable edges - both are valid failures
        assertTrue(message.contains("multiple edges without guards") || 
                  message.contains("unreachable edges after default edge"));
        assertTrue(message.contains("testWorkflow"));
        assertTrue(message.contains("step1"));
    }

    @Test
    void testInvalidDefaultEdgeNotLastConfiguration() {
        // Default edge not last - should fail
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", null),    // Default edge - INVALID position
            createEdge("step1", "step4", "guard2") // Unreachable
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        assertTrue(exception.getMessage().contains("unreachable edges after default edge"));
        assertTrue(exception.getMessage().contains("testWorkflow"));
        assertTrue(exception.getMessage().contains("step1"));
        assertTrue(exception.getMessage().contains("index 1"));
        assertTrue(exception.getMessage().contains("step4"));
    }

    @Test
    void testInvalidDefaultEdgeInMiddleConfiguration() {
        // Default edge in middle with multiple unreachable edges
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", null),     // Default edge - INVALID position
            createEdge("step1", "step4", "guard2"), // Unreachable 1
            createEdge("step1", "step5", "guard3")  // Unreachable 2
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        assertTrue(exception.getMessage().contains("unreachable edges after default edge"));
        assertTrue(exception.getMessage().contains("[step4, step5]"));
    }

    @Test
    void testValidMultipleStepsWithProperEdgeConfiguration() {
        // Multiple steps, each with proper edge configuration
        setupWorkflow("testWorkflow", Arrays.asList(
            // Step1: guarded edges with default last
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", "guard2"),
            createEdge("step1", "step4", null),
            // Step2: single edge
            createEdge("step2", "SUCCESS", null),
            // Step3: all guarded edges
            createEdge("step3", "step5", "guard3"),
            createEdge("step3", "step6", "guard4")
        ));

        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testInvalidMixedStepsConfiguration() {
        // One valid step, one invalid step
        setupWorkflow("testWorkflow", Arrays.asList(
            // Step1: valid configuration
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", null),
            // Step2: invalid configuration (default not last)
            createEdge("step2", "step4", null),     // Default edge - INVALID position
            createEdge("step2", "step5", "guard2")  // Unreachable
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        assertTrue(exception.getMessage().contains("step2"));
        assertTrue(exception.getMessage().contains("unreachable"));
    }

    @Test
    void testEmptyGuardTreatedAsDefault() {
        // Empty string guard should be treated as default edge
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", ""),       // Empty guard = default
            createEdge("step1", "step4", "guard2")  // Unreachable
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        assertTrue(exception.getMessage().contains("unreachable edges"));
    }

    @Test
    void testWhitespaceOnlyGuardTreatedAsDefault() {
        // Whitespace-only guard should be treated as default edge
        setupWorkflow("testWorkflow", Arrays.asList(
            createEdge("step1", "step2", "guard1"),
            createEdge("step1", "step3", "   "),    // Whitespace guard = default
            createEdge("step1", "step4", "guard2")  // Unreachable
        ));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Engine(config)
        );

        assertTrue(exception.getMessage().contains("unreachable edges"));
    }

    @Test
    void testNoWorkflowsConfiguration() {
        // No workflows defined - should not fail
        config.workflows = null;
        assertDoesNotThrow(() -> new Engine(config));

        config.workflows = new HashMap<>();
        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testEmptyEdgesConfiguration() {
        // Workflow with no edges - should not fail
        FlowConfig.WorkflowDef workflow = new FlowConfig.WorkflowDef();
        workflow.edges = new ArrayList<>();
        config.workflows.put("testWorkflow", workflow);

        assertDoesNotThrow(() -> new Engine(config));
    }

    @Test
    void testNullEdgesConfiguration() {
        // Workflow with null edges - should not fail
        FlowConfig.WorkflowDef workflow = new FlowConfig.WorkflowDef();
        workflow.edges = null;
        config.workflows.put("testWorkflow", workflow);

        assertDoesNotThrow(() -> new Engine(config));
    }

    // Helper methods

    private void setupWorkflow(String workflowName, List<FlowConfig.EdgeDef> edges) {
        FlowConfig.WorkflowDef workflow = new FlowConfig.WorkflowDef();
        workflow.root = "step1";
        workflow.edges = edges;
        config.workflows.put(workflowName, workflow);

        // Add step definitions for all referenced steps
        Set<String> stepNames = new HashSet<>();
        for (FlowConfig.EdgeDef edge : edges) {
            if (edge.from != null && !isTerminal(edge.from)) {
                stepNames.add(edge.from);
            }
            if (edge.to != null && !isTerminal(edge.to)) {
                stepNames.add(edge.to);
            }
        }

        for (String stepName : stepNames) {
            FlowConfig.StepDef stepDef = new FlowConfig.StepDef();
            stepDef.type = "testStep";
            config.steps.put(stepName, stepDef);
        }
    }

    private FlowConfig.EdgeDef createEdge(String from, String to, String guard) {
        FlowConfig.EdgeDef edge = new FlowConfig.EdgeDef();
        edge.from = from;
        edge.to = to;
        edge.guard = guard;
        return edge;
    }

    private boolean isTerminal(String stepName) {
        return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
    }
}