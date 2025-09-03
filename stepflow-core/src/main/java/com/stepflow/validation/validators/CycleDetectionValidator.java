package com.stepflow.validation.validators;

import com.stepflow.config.FlowConfig;
import com.stepflow.validation.*;

import java.util.*;

/**
 * Validator that detects cycles in workflow graphs.
 * Uses depth-first search with cycle detection to identify circular dependencies.
 * Provides detailed information about detected cycles including the cycle path and involved edges.
 */
public class CycleDetectionValidator implements FlowConfigValidator {
    
    @Override
    public String getValidatorName() {
        return "CycleDetection";
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority - structural integrity is critical
    }
    
    @Override
    public boolean isFailFast() {
        return true; // Cycles prevent execution, so fail fast
    }
    
    @Override
    public String getDescription() {
        return "Detects circular dependencies in workflow graphs";
    }
    
    @Override
    public ValidationResult validate(FlowConfig config) {
        ValidationResult.Builder resultBuilder = ValidationResult.builder();
        
        if (config.workflows == null || config.workflows.isEmpty()) {
            return resultBuilder.build();
        }
        
        long startTime = System.currentTimeMillis();
        int totalWorkflows = config.workflows.size();
        int cyclesDetected = 0;
        
        for (Map.Entry<String, FlowConfig.WorkflowDef> workflowEntry : config.workflows.entrySet()) {
            String workflowName = workflowEntry.getKey();
            FlowConfig.WorkflowDef workflow = workflowEntry.getValue();
            
            List<CycleInfo> cycles = detectCycles(workflowName, workflow);
            
            for (CycleInfo cycle : cycles) {
                cyclesDetected++;
                
                ValidationError error = ValidationError.builder(ValidationErrorType.CYCLE_DETECTED)
                    .workflowName(workflowName)
                    .message(String.format("Circular dependency detected in workflow '%s': %s", 
                            workflowName, formatCyclePath(cycle.cyclePath)))
                    .location(String.format("workflow '%s', cycle starting at '%s'", 
                            workflowName, cycle.cyclePath.get(0)))
                    .cycleDetails(cycle.cyclePath, cycle.involvedEdges)
                    .detail("cycleStartStep", cycle.cyclePath.get(0))
                    .detail("cycleEndStep", cycle.cyclePath.get(cycle.cyclePath.size() - 1))
                    .build();
                
                resultBuilder.addError(error);
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        return resultBuilder
            .addMetadata("validationTime", endTime - startTime)
            .addMetadata("workflowsValidated", totalWorkflows)
            .addMetadata("cyclesDetected", cyclesDetected)
            .build();
    }
    
    /**
     * Detects all cycles in a workflow using depth-first search.
     */
    private List<CycleInfo> detectCycles(String workflowName, FlowConfig.WorkflowDef workflow) {
        List<CycleInfo> cycles = new ArrayList<>();
        
        if (workflow.edges == null || workflow.edges.isEmpty()) {
            return cycles;
        }
        
        // Build adjacency list representation
        Map<String, List<EdgeInfo>> graph = buildGraph(workflow);
        
        // Track visited states for DFS
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        
        // Start DFS from all unvisited nodes to catch disconnected cycles
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                List<String> cyclePath = new ArrayList<>();
                if (dfsDetectCycle(node, graph, visited, recursionStack, parent, cyclePath)) {
                    // Extract the actual cycle from the path
                    CycleInfo cycle = extractCycle(cyclePath, graph);
                    if (cycle != null) {
                        cycles.add(cycle);
                    }
                }
            }
        }
        
        return cycles;
    }
    
    /**
     * Builds graph representation from workflow edges.
     */
    private Map<String, List<EdgeInfo>> buildGraph(FlowConfig.WorkflowDef workflow) {
        Map<String, List<EdgeInfo>> graph = new HashMap<>();
        
        for (FlowConfig.EdgeDef edge : workflow.edges) {
            if (edge.from != null && edge.to != null && 
                !isTerminal(edge.from) && !isTerminal(edge.to)) {
                
                graph.computeIfAbsent(edge.from, k -> new ArrayList<>())
                     .add(new EdgeInfo(edge.from, edge.to, edge.guard));
            }
        }
        
        return graph;
    }
    
    /**
     * Performs DFS to detect cycles.
     */
    private boolean dfsDetectCycle(String node, 
                                  Map<String, List<EdgeInfo>> graph,
                                  Set<String> visited, 
                                  Set<String> recursionStack,
                                  Map<String, String> parent,
                                  List<String> currentPath) {
        
        visited.add(node);
        recursionStack.add(node);
        currentPath.add(node);
        
        List<EdgeInfo> neighbors = graph.getOrDefault(node, Collections.emptyList());
        
        for (EdgeInfo edge : neighbors) {
            String neighbor = edge.to;
            
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                if (dfsDetectCycle(neighbor, graph, visited, recursionStack, parent, currentPath)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                // Found a back edge - cycle detected
                currentPath.add(neighbor); // Complete the cycle
                return true;
            }
        }
        
        recursionStack.remove(node);
        currentPath.remove(currentPath.size() - 1);
        return false;
    }
    
    /**
     * Extracts cycle information from the detected cycle path.
     */
    private CycleInfo extractCycle(List<String> path, Map<String, List<EdgeInfo>> graph) {
        if (path.size() < 2) {
            return null;
        }
        
        // Find where the cycle actually starts
        String cycleStart = path.get(path.size() - 1); // The repeated node
        int startIndex = path.indexOf(cycleStart);
        
        if (startIndex == -1 || startIndex == path.size() - 1) {
            return null;
        }
        
        // Extract the cycle path (from first occurrence to the repeated node)
        List<String> cyclePath = new ArrayList<>(path.subList(startIndex, path.size()));
        
        // Find involved edges
        List<String> involvedEdges = new ArrayList<>();
        for (int i = 0; i < cyclePath.size() - 1; i++) {
            String from = cyclePath.get(i);
            String to = cyclePath.get(i + 1);
            
            List<EdgeInfo> edges = graph.getOrDefault(from, Collections.emptyList());
            for (EdgeInfo edge : edges) {
                if (edge.to.equals(to)) {
                    String edgeDesc = formatEdge(edge);
                    involvedEdges.add(edgeDesc);
                    break;
                }
            }
        }
        
        return new CycleInfo(cyclePath, involvedEdges);
    }
    
    private boolean isTerminal(String stepName) {
        return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
    }
    
    private String formatCyclePath(List<String> cyclePath) {
        return String.join(" → ", cyclePath);
    }
    
    private String formatEdge(EdgeInfo edge) {
        if (edge.guard != null && !edge.guard.trim().isEmpty()) {
            return String.format("%s → %s [guard: %s]", edge.from, edge.to, edge.guard);
        } else {
            return String.format("%s → %s", edge.from, edge.to);
        }
    }
    
    /**
     * Holds information about a detected cycle.
     */
    private static class CycleInfo {
        final List<String> cyclePath;
        final List<String> involvedEdges;
        
        CycleInfo(List<String> cyclePath, List<String> involvedEdges) {
            this.cyclePath = new ArrayList<>(cyclePath);
            this.involvedEdges = new ArrayList<>(involvedEdges);
        }
    }
    
    /**
     * Holds information about an edge in the graph.
     */
    private static class EdgeInfo {
        final String from;
        final String to;
        final String guard;
        
        EdgeInfo(String from, String to, String guard) {
            this.from = from;
            this.to = to;
            this.guard = guard;
        }
    }
}