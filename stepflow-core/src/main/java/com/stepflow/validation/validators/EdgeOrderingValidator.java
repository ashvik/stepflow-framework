package com.stepflow.validation.validators;

import com.stepflow.config.FlowConfig;
import com.stepflow.validation.*;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Validator that checks edge ordering rules for multi-edge steps.
 * Enforces the following rules:
 * 1. Each step can have at most one edge without a guard (unguarded edge)
 * 2. Unguarded edges must be positioned last among all outgoing edges from a step
 * 
 * These rules ensure predictable workflow routing where guarded edges are evaluated first,
 * and the unguarded edge serves as a fallback that's only reached if all guards fail.
 */
public class EdgeOrderingValidator implements FlowConfigValidator {
    
    @Override
    public String getValidatorName() {
        return "EdgeOrdering";
    }
    
    @Override
    public int getPriority() {
        return 20; // High priority - routing logic is critical
    }
    
    @Override
    public boolean isFailFast() {
        return true; // Invalid edge ordering prevents correct execution
    }
    
    @Override
    public String getDescription() {
        return "Validates edge ordering rules - unguarded edges must be last and unique per step";
    }
    
    @Override
    public ValidationResult validate(FlowConfig config) {
        ValidationResult.Builder resultBuilder = ValidationResult.builder();
        
        if (config.workflows == null || config.workflows.isEmpty()) {
            return resultBuilder.build();
        }
        
        long startTime = System.currentTimeMillis();
        int totalWorkflows = config.workflows.size();
        int stepsValidated = 0;
        int violationsFound = 0;
        
        for (Map.Entry<String, FlowConfig.WorkflowDef> workflowEntry : config.workflows.entrySet()) {
            String workflowName = workflowEntry.getKey();
            FlowConfig.WorkflowDef workflow = workflowEntry.getValue();
            
            EdgeOrderingResult validationResult = validateWorkflowEdgeOrdering(workflowName, workflow);
            
            stepsValidated += validationResult.stepsValidated;
            violationsFound += validationResult.violations.size();
            
            for (EdgeOrderingViolation violation : validationResult.violations) {
                ValidationError error = createValidationError(workflowName, violation);
                resultBuilder.addError(error);
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        return resultBuilder
            .addMetadata("validationTime", endTime - startTime)
            .addMetadata("workflowsValidated", totalWorkflows)
            .addMetadata("stepsValidated", stepsValidated)
            .addMetadata("violationsFound", violationsFound)
            .build();
    }
    
    /**
     * Validates edge ordering for a single workflow.
     */
    private EdgeOrderingResult validateWorkflowEdgeOrdering(String workflowName, FlowConfig.WorkflowDef workflow) {
        List<EdgeOrderingViolation> violations = new ArrayList<>();
        
        if (workflow.edges == null || workflow.edges.isEmpty()) {
            return new EdgeOrderingResult(0, violations);
        }
        
        // Group edges by their source step
        Map<String, List<IndexedEdge>> edgesByStep = groupEdgesByStep(workflow.edges);
        
        // Validate each step's outgoing edges
        for (Map.Entry<String, List<IndexedEdge>> entry : edgesByStep.entrySet()) {
            String stepName = entry.getKey();
            List<IndexedEdge> stepEdges = entry.getValue();
            
            // Skip validation for single edges and terminal steps
            if (stepEdges.size() <= 1 || isTerminal(stepName)) {
                continue;
            }
            
            EdgeOrderingViolation violation = validateStepEdgeOrdering(stepName, stepEdges);
            if (violation != null) {
                violations.add(violation);
            }
        }
        
        return new EdgeOrderingResult(edgesByStep.size(), violations);
    }
    
    /**
     * Groups workflow edges by their source step with original indices preserved.
     */
    private Map<String, List<IndexedEdge>> groupEdgesByStep(List<FlowConfig.EdgeDef> edges) {
        Map<String, List<IndexedEdge>> edgesByStep = new LinkedHashMap<>();
        
        for (int i = 0; i < edges.size(); i++) {
            FlowConfig.EdgeDef edge = edges.get(i);
            if (edge.from != null) {
                edgesByStep.computeIfAbsent(edge.from, k -> new ArrayList<>())
                          .add(new IndexedEdge(edge, i));
            }
        }
        
        return edgesByStep;
    }
    
    /**
     * Validates edge ordering rules for a single step.
     */
    private EdgeOrderingViolation validateStepEdgeOrdering(String stepName, List<IndexedEdge> stepEdges) {
        List<IndexedEdge> guardedEdges = new ArrayList<>();
        List<IndexedEdge> unguardedEdges = new ArrayList<>();
        
        // Categorize edges based on guard presence
        for (IndexedEdge indexedEdge : stepEdges) {
            if (isGuarded(indexedEdge.edge)) {
                guardedEdges.add(indexedEdge);
            } else {
                unguardedEdges.add(indexedEdge);
            }
        }
        
        // Check for multiple unguarded edges
        if (unguardedEdges.size() > 1) {
            return createMultipleUnguardedEdgesViolation(stepName, unguardedEdges);
        }
        
        // Check unguarded edge positioning (if present)
        if (unguardedEdges.size() == 1) {
            IndexedEdge unguardedEdge = unguardedEdges.get(0);
            
            // Find if any guarded edges come after the unguarded edge
            List<IndexedEdge> edgesAfterUnguarded = stepEdges.stream()
                .filter(edge -> edge.originalIndex > unguardedEdge.originalIndex)
                .filter(edge -> isGuarded(edge.edge))
                .toList();
            
            if (!edgesAfterUnguarded.isEmpty()) {
                return createUnguardedEdgeNotLastViolation(stepName, unguardedEdge, 
                                                         guardedEdges, edgesAfterUnguarded);
            }
        }
        
        return null; // No violations found
    }
    
    /**
     * Creates violation for multiple unguarded edges from same step.
     */
    private EdgeOrderingViolation createMultipleUnguardedEdgesViolation(String stepName, 
                                                                       List<IndexedEdge> unguardedEdges) {
        List<String> edgeDescriptions = unguardedEdges.stream()
            .map(indexed -> formatEdge(indexed.edge))
            .toList();
        
        List<Integer> indices = unguardedEdges.stream()
            .map(indexed -> indexed.originalIndex)
            .toList();
        
        return new EdgeOrderingViolation(
            EdgeOrderingViolationType.MULTIPLE_UNGUARDED_EDGES,
            stepName,
            String.format("Step '%s' has %d unguarded edges, but only one is allowed", 
                         stepName, unguardedEdges.size()),
            Map.of(
                "unguardedEdges", edgeDescriptions,
                "unguardedIndices", indices,
                "unguardedCount", unguardedEdges.size()
            )
        );
    }
    
    /**
     * Creates violation for unguarded edge not being positioned last.
     */
    private EdgeOrderingViolation createUnguardedEdgeNotLastViolation(String stepName,
                                                                     IndexedEdge unguardedEdge,
                                                                     List<IndexedEdge> allGuardedEdges,
                                                                     List<IndexedEdge> edgesAfterUnguarded) {
        List<String> guardedEdgeDescriptions = allGuardedEdges.stream()
            .map(indexed -> formatEdge(indexed.edge))
            .toList();
        
        List<String> violatingEdgeDescriptions = edgesAfterUnguarded.stream()
            .map(indexed -> formatEdge(indexed.edge))
            .toList();
        
        return new EdgeOrderingViolation(
            EdgeOrderingViolationType.UNGUARDED_EDGE_NOT_LAST,
            stepName,
            String.format("Step '%s' has unguarded edge at position %d, but %d guarded edge(s) come after it", 
                         stepName, unguardedEdge.originalIndex, edgesAfterUnguarded.size()),
            Map.of(
                "unguardedEdge", formatEdge(unguardedEdge.edge),
                "unguardedIndex", unguardedEdge.originalIndex,
                "guardedEdges", guardedEdgeDescriptions,
                "violatingEdges", violatingEdgeDescriptions,
                "expectedPosition", "last"
            )
        );
    }
    
    /**
     * Creates ValidationError from EdgeOrderingViolation.
     */
    private ValidationError createValidationError(String workflowName, EdgeOrderingViolation violation) {
        ValidationErrorType errorType = violation.type == EdgeOrderingViolationType.MULTIPLE_UNGUARDED_EDGES
            ? ValidationErrorType.MULTIPLE_UNGUARDED_EDGES
            : ValidationErrorType.UNGUARDED_EDGE_NOT_LAST;
        
        ValidationError.Builder errorBuilder = ValidationError.builder(errorType)
            .workflowName(workflowName)
            .message(violation.message)
            .location(String.format("workflow '%s', step '%s'", workflowName, violation.stepName))
            .details(violation.details);
        
        // Add type-specific details
        if (violation.type == EdgeOrderingViolationType.MULTIPLE_UNGUARDED_EDGES) {
            @SuppressWarnings("unchecked")
            List<String> unguardedEdges = (List<String>) violation.details.get("unguardedEdges");
            @SuppressWarnings("unchecked")
            List<Integer> unguardedIndices = (List<Integer>) violation.details.get("unguardedIndices");
            
            errorBuilder.multipleUnguardedEdgesDetails(violation.stepName, unguardedEdges, unguardedIndices);
        } else {
            @SuppressWarnings("unchecked")
            List<String> guardedEdges = (List<String>) violation.details.get("guardedEdges");
            List<String> unguardedEdges = List.of((String) violation.details.get("unguardedEdge"));
            Integer firstUnguardedIndex = (Integer) violation.details.get("unguardedIndex");
            
            errorBuilder.edgeOrderingDetails(violation.stepName, guardedEdges, unguardedEdges, firstUnguardedIndex);
        }
        
        return errorBuilder.build();
    }
    
    private boolean isTerminal(String stepName) {
        return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
    }
    
    private boolean isGuarded(FlowConfig.EdgeDef edge) {
        return edge.guard != null && !edge.guard.trim().isEmpty();
    }
    
    private String formatEdge(FlowConfig.EdgeDef edge) {
        if (isGuarded(edge)) {
            return String.format("%s → %s [guard: %s]", edge.from, edge.to, edge.guard);
        } else {
            return String.format("%s → %s [no guard]", edge.from, edge.to);
        }
    }
    
    /**
     * Holds an edge with its original index in the workflow definition.
     */
    private static class IndexedEdge {
        final FlowConfig.EdgeDef edge;
        final int originalIndex;
        
        IndexedEdge(FlowConfig.EdgeDef edge, int originalIndex) {
            this.edge = edge;
            this.originalIndex = originalIndex;
        }
    }
    
    /**
     * Result of edge ordering validation for a workflow.
     */
    private static class EdgeOrderingResult {
        final int stepsValidated;
        final List<EdgeOrderingViolation> violations;
        
        EdgeOrderingResult(int stepsValidated, List<EdgeOrderingViolation> violations) {
            this.stepsValidated = stepsValidated;
            this.violations = violations;
        }
    }
    
    /**
     * Represents an edge ordering violation.
     */
    private static class EdgeOrderingViolation {
        final EdgeOrderingViolationType type;
        final String stepName;
        final String message;
        final Map<String, Object> details;
        
        EdgeOrderingViolation(EdgeOrderingViolationType type, String stepName, 
                             String message, Map<String, Object> details) {
            this.type = type;
            this.stepName = stepName;
            this.message = message;
            this.details = details;
        }
    }
    
    /**
     * Types of edge ordering violations.
     */
    private enum EdgeOrderingViolationType {
        MULTIPLE_UNGUARDED_EDGES,
        UNGUARDED_EDGE_NOT_LAST
    }
}