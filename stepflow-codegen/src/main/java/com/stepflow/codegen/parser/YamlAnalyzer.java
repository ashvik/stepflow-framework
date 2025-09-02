package com.stepflow.codegen.parser;

import com.stepflow.codegen.model.ComponentMetadata;
import com.stepflow.codegen.model.ComponentMetadata.ComponentType;
import com.stepflow.codegen.model.ComponentMetadata.RetryConfiguration;
import com.stepflow.config.FlowConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes YAML configurations to extract component metadata for code generation
 */
public class YamlAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(YamlAnalyzer.class);
    
    private final Yaml yamlParser = new Yaml();
    
    /**
     * Analyzes a YAML file and extracts component metadata
     */
    public List<ComponentMetadata> analyze(InputStream yamlStream) {
        try {
            Map<String, Object> yamlData = yamlParser.load(yamlStream);
            return analyzeYamlData(yamlData);
        } catch (Exception e) {
            logger.error("Failed to analyze YAML", e);
            throw new RuntimeException("Failed to analyze YAML", e);
        }
    }
    
    /**
     * Analyzes YAML data structure and extracts component metadata
     */
    @SuppressWarnings("unchecked")
    public List<ComponentMetadata> analyzeYamlData(Map<String, Object> yamlData) {
        List<ComponentMetadata> components = new ArrayList<>();
        
        // Extract defaults for configuration inference
        Map<String, Object> defaults = (Map<String, Object>) yamlData.getOrDefault("defaults", Collections.emptyMap());
        Map<String, Object> settings = (Map<String, Object>) yamlData.getOrDefault("settings", Collections.emptyMap());
        
        // Analyze steps
        Map<String, Object> steps = (Map<String, Object>) yamlData.getOrDefault("steps", Collections.emptyMap());
        Map<String, Object> workflows = (Map<String, Object>) yamlData.getOrDefault("workflows", Collections.emptyMap());
        
        // Extract step components
        for (Map.Entry<String, Object> stepEntry : steps.entrySet()) {
            ComponentMetadata stepMetadata = analyzeStep(stepEntry.getKey(), stepEntry.getValue(), defaults, workflows);
            if (stepMetadata != null) {
                components.add(stepMetadata);
            }
        }
        
        // Extract guard components by analyzing workflow edges and step requirements
        Set<String> guardNames = extractGuardNames(steps, workflows);
        for (String guardName : guardNames) {
            ComponentMetadata guardMetadata = analyzeGuard(guardName, defaults, workflows, steps);
            if (guardMetadata != null) {
                components.add(guardMetadata);
            }
        }
        
        logger.info("Extracted {} components ({} steps, {} guards)", 
                   components.size(), 
                   components.stream().filter(c -> c.getType() == ComponentType.STEP).count(),
                   components.stream().filter(c -> c.getType() == ComponentType.GUARD).count());
        
        return components;
    }
    
    @SuppressWarnings("unchecked")
    private ComponentMetadata analyzeStep(String stepName, Object stepData, Map<String, Object> defaults, Map<String, Object> workflows) {
        if (!(stepData instanceof Map)) {
            logger.warn("Invalid step data for {}: {}", stepName, stepData);
            return null;
        }
        
        Map<String, Object> stepMap = (Map<String, Object>) stepData;
        ComponentMetadata metadata = new ComponentMetadata(stepName, ComponentType.STEP);
        
        // Extract type and generate class name
        String type = (String) stepMap.get("type");
        if (type == null) {
            logger.warn("Step {} has no type defined", stepName);
            return null;
        }
        
        metadata.setClassName(generateClassName(stepName, type, ComponentType.STEP));
        metadata.setAnnotationName(generateAnnotationName(stepName));
        metadata.setPackageName("com.stepflow.generated.steps");
        
        // Extract configuration
        Map<String, Object> config = (Map<String, Object>) stepMap.getOrDefault("config", Collections.emptyMap());
        Map<String, Object> mergedConfig = mergeWithDefaults(config, defaults, stepName, "step");
        metadata.setConfig(mergedConfig);
        
        // Extract guards/requirements
        Object guardsObj = stepMap.get("guards");
        if (guardsObj instanceof List) {
            Set<String> guards = new HashSet<>((List<String>) guardsObj);
            metadata.setRequiredGuards(guards);
        }
        
        // Extract retry configuration
        Object retryObj = stepMap.get("retry");
        if (retryObj instanceof Map) {
            Map<String, Object> retryMap = (Map<String, Object>) retryObj;
            RetryConfiguration retry = new RetryConfiguration(
                (Integer) retryMap.getOrDefault("maxAttempts", 3),
                ((Number) retryMap.getOrDefault("delay", 1000L)).longValue(),
                (String) retryMap.get("guard")
            );
            metadata.setRetry(retry);
        }
        
        // Find workflows that use this step
        List<String> usedInWorkflows = findWorkflowsUsingStep(stepName, workflows);
        metadata.setUsedInWorkflows(usedInWorkflows);
        
        // Determine complexity based on configuration and usage
        boolean hasComplexLogic = hasComplexLogic(mergedConfig, usedInWorkflows.size() > 1);
        metadata.setHasComplexLogic(hasComplexLogic);
        
        return metadata;
    }
    
    private ComponentMetadata analyzeGuard(String guardName, Map<String, Object> defaults, 
                                         Map<String, Object> workflows, Map<String, Object> steps) {
        ComponentMetadata metadata = new ComponentMetadata(guardName, ComponentType.GUARD);
        
        metadata.setClassName(generateClassName(guardName, guardName, ComponentType.GUARD));
        metadata.setAnnotationName(generateAnnotationName(guardName));
        metadata.setPackageName("com.stepflow.generated.guards");
        
        // Extract configuration from defaults or infer from usage
        Map<String, Object> config = mergeWithDefaults(Collections.emptyMap(), defaults, guardName, "guard");
        metadata.setConfig(config);
        
        // Find workflows that use this guard
        List<String> usedInWorkflows = findWorkflowsUsingGuard(guardName, workflows);
        metadata.setUsedInWorkflows(usedInWorkflows);
        
        // Determine complexity
        boolean hasComplexLogic = hasComplexLogic(config, usedInWorkflows.size() > 1);
        metadata.setHasComplexLogic(hasComplexLogic);
        
        return metadata;
    }
    
    @SuppressWarnings("unchecked")
    private Set<String> extractGuardNames(Map<String, Object> steps, Map<String, Object> workflows) {
        Set<String> guardNames = new HashSet<>();
        
        // Extract from step guards/requirements
        for (Object stepData : steps.values()) {
            if (stepData instanceof Map) {
                Map<String, Object> stepMap = (Map<String, Object>) stepData;
                Object guardsObj = stepMap.get("guards");
                if (guardsObj instanceof List) {
                    guardNames.addAll((List<String>) guardsObj);
                }
            }
        }
        
        // Extract from workflow edges
        for (Object workflowData : workflows.values()) {
            if (workflowData instanceof Map) {
                Map<String, Object> workflowMap = (Map<String, Object>) workflowData;
                Object edgesObj = workflowMap.get("edges");
                if (edgesObj instanceof List) {
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
                    for (Map<String, Object> edge : edges) {
                        String guard = (String) edge.get("guard");
                        if (guard != null) {
                            guardNames.add(guard);
                        }
                    }
                }
            }
        }
        
        return guardNames;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWithDefaults(Map<String, Object> config, Map<String, Object> defaults, 
                                                 String componentName, String componentType) {
        Map<String, Object> merged = new HashMap<>();
        
        // Add global defaults for component type (e.g., defaults.step.*)
        Map<String, Object> typeDefaults = (Map<String, Object>) defaults.getOrDefault(componentType, Collections.emptyMap());
        merged.putAll(typeDefaults);
        
        // Add component-specific defaults (e.g., defaults.myStepName.*)
        Map<String, Object> componentDefaults = (Map<String, Object>) defaults.getOrDefault(componentName, Collections.emptyMap());
        merged.putAll(componentDefaults);
        
        // Add explicit configuration (highest priority)
        merged.putAll(config);
        
        return merged;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> findWorkflowsUsingStep(String stepName, Map<String, Object> workflows) {
        List<String> usedIn = new ArrayList<>();
        
        for (Map.Entry<String, Object> workflowEntry : workflows.entrySet()) {
            if (workflowEntry.getValue() instanceof Map) {
                Map<String, Object> workflow = (Map<String, Object>) workflowEntry.getValue();
                
                // Check root
                if (stepName.equals(workflow.get("root"))) {
                    usedIn.add(workflowEntry.getKey());
                    continue;
                }
                
                // Check edges
                Object edgesObj = workflow.get("edges");
                if (edgesObj instanceof List) {
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
                    boolean foundInEdges = edges.stream()
                        .anyMatch(edge -> stepName.equals(edge.get("from")) || stepName.equals(edge.get("to")));
                    
                    if (foundInEdges) {
                        usedIn.add(workflowEntry.getKey());
                    }
                }
            }
        }
        
        return usedIn;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> findWorkflowsUsingGuard(String guardName, Map<String, Object> workflows) {
        List<String> usedIn = new ArrayList<>();
        
        for (Map.Entry<String, Object> workflowEntry : workflows.entrySet()) {
            if (workflowEntry.getValue() instanceof Map) {
                Map<String, Object> workflow = (Map<String, Object>) workflowEntry.getValue();
                Object edgesObj = workflow.get("edges");
                
                if (edgesObj instanceof List) {
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
                    boolean foundInEdges = edges.stream()
                        .anyMatch(edge -> guardName.equals(edge.get("guard")));
                    
                    if (foundInEdges) {
                        usedIn.add(workflowEntry.getKey());
                    }
                }
            }
        }
        
        return usedIn;
    }
    
    private String generateClassName(String componentName, String type, ComponentType componentType) {
        // Convert from camelCase or snake_case to PascalCase
        String baseName = componentName;
        if (baseName.contains("_")) {
            baseName = Arrays.stream(baseName.split("_"))
                .map(this::capitalize)
                .collect(Collectors.joining());
        } else {
            baseName = capitalize(baseName);
        }
        
        // Don't add Step/Guard suffixes - keep name as-is
        return baseName;
    }
    
    private String generateAnnotationName(String componentName) {
        // Convert to camelCase starting with lowercase
        String name = componentName;
        if (name.contains("_")) {
            String[] parts = name.split("_");
            StringBuilder result = new StringBuilder(parts[0].toLowerCase());
            for (int i = 1; i < parts.length; i++) {
                result.append(capitalize(parts[i]));
            }
            name = result.toString();
        } else {
            // Convert first character to lowercase
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        }
        return name;
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    private boolean hasComplexLogic(Map<String, Object> config, boolean usedInMultipleWorkflows) {
        // Determine if the component likely has complex logic based on:
        // 1. Number of configuration parameters
        // 2. Types of configuration values
        // 3. Usage across multiple workflows
        
        if (config.size() > 5) return true;
        if (usedInMultipleWorkflows) return true;
        
        // Check for complex configuration types
        return config.values().stream().anyMatch(value -> 
            value instanceof Map || value instanceof List || 
            (value instanceof String && ((String) value).contains("{{"))
        );
    }
}