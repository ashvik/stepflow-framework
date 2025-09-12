package com.stepflow.core;

import com.stepflow.engine.Engine;
import com.stepflow.config.FlowConfig;
import com.stepflow.execution.*;
import com.stepflow.resource.YamlResourceLoader;
import com.stepflow.component.DependencyResolver;
import com.stepflow.validation.*;
import com.stepflow.validation.validators.*;

import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified engine providing simplified APIs for StepFlow workflow creation, validation, and execution.
 *
 * <p>SimpleEngine is the single entry point for all StepFlow functionality, combining
 * workflow-based YAML processing, comprehensive validation, external dependency management, 
 * and programmatic construction into one cohesive interface.
 *
 * <p>Key Features:
 * <ul>
 *   <li><strong>Workflow-Based YAML:</strong> Enhanced YAML format supporting complex workflows</li>
 *   <li><strong>Built-in Validation:</strong> Comprehensive FlowConfig validation with detailed error reporting</li>
 *   <li><strong>External Dependencies:</strong> Built-in support for Maven/Gradle dependencies</li>
 *   <li><strong>Automatic Discovery:</strong> Component scanning across local and external JARs</li>
 *   <li><strong>Fluent API:</strong> Programmatic workflow construction</li>
 *   <li><strong>Single Entry Point:</strong> Unified interface for all use cases</li>
 * </ul>
 *
 * <h2>FlowConfig Validation</h2>
 * 
 * <p>SimpleEngine includes integrated validation capabilities that detect configuration issues
 * before workflow execution, enabling fail-fast development and robust production deployments.
 *
 * <h3>Built-in Validators:</h3>
 * <ul>
 *   <li><strong>Cycle Detection:</strong> Identifies circular dependencies in workflow graphs</li>
 *   <li><strong>Edge Ordering:</strong> Validates that unguarded edges are positioned last and unique per step</li>
 * </ul>
 *
 * <h3>Validation Usage Examples:</h3>
 *
 * <p><strong>Basic Validation:</strong>
 * <pre>
 * SimpleEngine engine = SimpleEngine.create("workflow.yaml");
 * ValidationResult result = engine.validateConfiguration();
 * 
 * if (!result.isValid()) {
 *     System.err.println("Configuration issues found:");
 *     System.err.println(result.getErrorSummary());
 *     // Fix issues before execution
 * } else {
 *     // Safe to execute workflows
 *     engine.execute("workflowName", context);
 * }
 * </pre>
 *
 * <p><strong>Validation-First Execution Pattern:</strong>
 * <pre>
 * try {
 *     SimpleEngine engine = SimpleEngine.create("workflow.yaml");
 *     engine.validateConfigurationOrThrow(); // Fail fast if invalid
 *     
 *     // Configuration is valid - safe to execute
 *     StepResult result = engine.execute("workflowName", context);
 * } catch (ValidationException e) {
 *     System.err.println("Validation failed: " + e.getDetailedMessage());
 *     // Handle validation errors
 * }
 * </pre>
 *
 * <p><strong>Single Workflow Validation:</strong>
 * <pre>
 * SimpleEngine engine = SimpleEngine.create("workflow.yaml");
 * ValidationResult result = engine.validateWorkflow("specificWorkflow");
 * 
 * if (result.isValid()) {
 *     // Only this workflow is valid
 * }
 * </pre>
 *
 * <p><strong>Custom Validation Configuration:</strong>
 * <pre>
 * // Using builder pattern
 * SimpleEngine engine = SimpleEngine.builder()
 *     .withExternalYamls("workflow.yaml")
 *     .withPackages("com.example")
 *     .withCustomValidation(validationBuilder -> validationBuilder
 *         .addValidator(new CycleDetectionValidator())
 *         .addValidator(new EdgeOrderingValidator())
 *         .addValidator(new CustomBusinessRuleValidator())
 *         .enableCaching(true))
 *     .build();
 * 
 * // Or using custom validation engine
 * FlowConfigValidationEngine customValidator = FlowConfigValidationEngine.builder()
 *     .addValidator(new CycleDetectionValidator())
 *     .addValidator(new MyBusinessRuleValidator())
 *     .build();
 *     
 * SimpleEngine engine = SimpleEngine.create("workflow.yaml", customValidator, "com.example");
 * </pre>
 *
 * <h3>Validation Error Examples:</h3>
 *
 * <p><strong>Cycle Detection:</strong>
 * <pre>
 * ERROR: Circular dependency detected in workflow 'orderProcessing': validate → process → audit → validate
 * Details:
 *   cyclePath: [validate, process, audit, validate]
 *   involvedEdges: [validate → process, process → audit [guard: auditRequired], audit → validate]
 * </pre>
 *
 * <p><strong>Edge Ordering Violation:</strong>
 * <pre>
 * ERROR: Step 'process' has unguarded edge at position 1, but 2 guarded edge(s) come after it
 * Details:
 *   unguardedEdge: process → notify [no guard]
 *   violatingEdges: [process → audit [guard: auditRequired]]
 *   expectedPosition: last
 * </pre>
 *
 * <h2>Traditional Usage Examples</h2>
 *
 * <p><strong>Local Workflow:</strong>
 * <pre>
 * SimpleEngine engine = SimpleEngine.create("my-workflow.yaml");
 * StepResult result = engine.execute("workflowName", context);
 * </pre>
 *
 * <p><strong>With External Dependencies:</strong>
 * <pre>
 * SimpleEngine engine = SimpleEngine.builder()
 *     .withExternalYamls("my-workflow.yaml")
 *     .withPackages("com.example.steps", "com.security.guards")
 *     .build();
 * </pre>
 *
 * <p><strong>Programmatic Construction:</strong>
 * <pre>
 * SimpleEngine engine = SimpleEngine.workflow("paymentFlow")
 *     .step("validate").using("validatePayment").with("minAmount", 10.0)
 *     .then("process").using("processPayment").with("gateway", "stripe")
 *     .end().build();
 * </pre>
 *
 * <h2>Validation Methods Reference</h2>
 * <ul>
 *   <li><strong>{@link #validateConfiguration()}</strong> - Validates entire FlowConfig</li>
 *   <li><strong>{@link #validateConfiguration(boolean)}</strong> - Validates with fail-fast option</li>
 *   <li><strong>{@link #validateWorkflow(String)}</strong> - Validates single workflow</li>
 *   <li><strong>{@link #validateConfigurationOrThrow()}</strong> - Validates and throws on failure</li>
 *   <li><strong>{@link #getValidationInfo()}</strong> - Gets validator information</li>
 *   <li><strong>{@link #clearValidationCache()}</strong> - Clears validation cache</li>
 * </ul>
 *
 * @author StepFlow Team
 * @version 3.0
 * @since 3.0
 */
public class SimpleEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleEngine.class);

    /** The underlying engine that handles execution */
    private final Engine engine;

    /** Runtime registries for external components */
    private final Map<String, Class<? extends Step>> runtimeStepRegistry = new HashMap<>();
    private final Map<String, Class<? extends Guard>> runtimeGuardRegistry = new HashMap<>();

    /** Dependency resolver for external components */
    private final DependencyResolver dependencyResolver = new DependencyResolver();

    /** Configuration for external resources */
    private final List<String> externalResources = new ArrayList<>();
    private final Set<String> scannedPackages = new HashSet<>();
    
    /** Validation engine for FlowConfig validation */
    private final FlowConfigValidationEngine validationEngine;

    /**
     * Creates a SimpleEngine with the specified FlowConfig and scan packages.
     *
     * @param config the FlowConfig containing workflow definitions
     * @param scanPackages optional package prefixes to scan for components
     */
    private SimpleEngine(FlowConfig config, String... scanPackages) {
        this.engine = new Engine(config, scanPackages);
        this.validationEngine = FlowConfigValidationEngine.createDefault();
    }

    /**
     * Creates a SimpleEngine with built-in external dependency support.
     *
     * @param config the FlowConfig containing workflow definitions
     * @param externalResources list of external resource paths
     * @param scanPackages optional package prefixes to scan for components
     */
    private SimpleEngine(FlowConfig config, List<String> externalResources, String... scanPackages) {
        this.engine = new Engine(config, scanPackages);
        this.validationEngine = FlowConfigValidationEngine.createDefault();
        this.externalResources.addAll(externalResources);
        discoverAndRegisterExternalComponents();
    }
    
    /**
     * Creates a SimpleEngine with custom validation engine.
     *
     * @param config the FlowConfig containing workflow definitions
     * @param validationEngine custom validation engine with specific validators
     * @param scanPackages optional package prefixes to scan for components
     */
    private SimpleEngine(FlowConfig config, FlowConfigValidationEngine validationEngine, String... scanPackages) {
        this.engine = new Engine(config, scanPackages);
        this.validationEngine = validationEngine;
    }
    
    /**
     * Package-private constructor for testing purposes.
     *
     * @param config the FlowConfig containing workflow definitions
     */
    SimpleEngine(FlowConfig config) {
        this.engine = new Engine(config);
        this.validationEngine = FlowConfigValidationEngine.createDefault();
    }
    
    /**
     * Package-private constructor for testing purposes with custom validation.
     *
     * @param config the FlowConfig containing workflow definitions
     * @param validationEngine custom validation engine
     */
    SimpleEngine(FlowConfig config, FlowConfigValidationEngine validationEngine) {
        this.engine = new Engine(config);
        this.validationEngine = validationEngine;
    }

    // ======================================================================================
    // FACTORY METHODS - Single Entry Point for All Use Cases
    // ======================================================================================

    /**
     * Creates a SimpleEngine from a workflow YAML file.
     *
     * <p>This is the primary factory method for creating engines from workflow-based YAML
     * configurations. The YAML file should define complete workflows with steps, guards,
     * and flow definitions.
     *
     * @param resourcePath path to the workflow YAML file (supports classpath:, file:, http: protocols)
     * @return a fully configured SimpleEngine ready for execution
     * @throws RuntimeException if the YAML file cannot be loaded or parsed
     */
    public static SimpleEngine create(String resourcePath) {
        YamlResourceLoader loader = new YamlResourceLoader();
        Map<String, Object> yamlData = loader.loadYaml(resourcePath);
        FlowConfig config = convertYamlToFlowConfig(yamlData);
        LOGGER.info("SimpleEngine created from YAML: {} (workflows={})", resourcePath, config.workflows.keySet());
        return new SimpleEngine(config);
    }

    /**
     * Creates a SimpleEngine from a workflow YAML file with custom package scanning.
     *
     * @param resourcePath path to the workflow YAML file
     * @param scanPackages package prefixes to scan for components
     * @return a fully configured SimpleEngine ready for execution
     */
    public static SimpleEngine create(String resourcePath, String... scanPackages) {
        YamlResourceLoader loader = new YamlResourceLoader();
        Map<String, Object> yamlData = loader.loadYaml(resourcePath);
        FlowConfig config = convertYamlToFlowConfig(yamlData);
        LOGGER.info("SimpleEngine created from YAML: {} with packages {}", resourcePath, Arrays.toString(scanPackages));
        return new SimpleEngine(config, scanPackages);
    }
    
    /**
     * Creates a SimpleEngine from a workflow YAML file with custom validation engine.
     *
     * @param resourcePath path to the workflow YAML file
     * @param validationEngine custom validation engine with specific validators
     * @param scanPackages package prefixes to scan for components
     * @return a fully configured SimpleEngine ready for execution
     */
    public static SimpleEngine create(String resourcePath, FlowConfigValidationEngine validationEngine, String... scanPackages) {
        YamlResourceLoader loader = new YamlResourceLoader();
        Map<String, Object> yamlData = loader.loadYaml(resourcePath);
        FlowConfig config = convertYamlToFlowConfig(yamlData);
        LOGGER.info("SimpleEngine created from YAML: {} with custom validation engine", resourcePath);
        return new SimpleEngine(config, validationEngine, scanPackages);
    }

    /**
     * Creates a builder for configuring SimpleEngine.
     *
     * @return a new EngineBuilder for fluent configuration
     */
    public static EngineBuilder builder() {
        return new EngineBuilder();
    }

    /**
     * Creates a new WorkflowBuilder for programmatic workflow construction.
     *
     * @param workflowName the name of the workflow to create
     * @return a new WorkflowBuilder instance for fluent workflow construction
     */
    public static WorkflowBuilder workflow(String workflowName) {
        return new WorkflowBuilder(workflowName);
    }

    /**
     * Creates a new WorkflowBuilder for programmatic workflow construction with custom package scanning.
     *
     * @param workflowName the name of the workflow to create
     * @param scanPackages the package prefixes to scan for components
     * @return a new WorkflowBuilder instance for fluent workflow construction
     */
    public static WorkflowBuilder workflow(String workflowName, String... scanPackages) {
        return new WorkflowBuilder(workflowName, scanPackages);
    }

    // ======================================================================================
    // EXECUTION METHODS - Delegate to underlying Engine
    // ======================================================================================

    /**
     * Executes a workflow with the given context.
     *
     * @param workflowName the name of the workflow to execute
     * @param context the execution context containing input data
     * @return the step result after workflow execution
     */
    public StepResult execute(String workflowName, ExecutionContext context) {
        LOGGER.debug("Executing workflow '{}' with context keys {}", workflowName, context.keySet());
        return engine.run(workflowName, context);
    }

    /**
     * Executes a workflow with input data as a map.
     *
     * @param workflowName the name of the workflow to execute
     * @param inputData the input data as key-value pairs
     * @return the step result after workflow execution
     */
    public StepResult execute(String workflowName, Map<String, Object> inputData) {
        ExecutionContext context = new ExecutionContext();
        if (inputData != null) {
            inputData.forEach(context::put);
        }
        LOGGER.debug("Executing workflow '{}' with input keys {}", workflowName, inputData != null ? inputData.keySet() : Collections.emptySet());
        return execute(workflowName, context);
    }

    /**
     * Analyzes and prints a comprehensive execution plan for the specified workflow.
     *
     * @param workflowName the name of the workflow to analyze
     */
    public void analyzeWorkflow(String workflowName) {
        LOGGER.info("Analyzing workflow: {}", workflowName);
        engine.analyzeWorkflow(workflowName);
    }

    public FlowConfig getFlowConfig() {
        return engine.getConfig();
    }
    
    // ======================================================================================
    // VALIDATION METHODS - Integrated FlowConfig Validation
    // ======================================================================================
    
    /**
     * Validates the current FlowConfig for structural and configuration issues.
     * 
     * <p>This method runs comprehensive validation checks including:
     * <ul>
     *   <li>Cycle detection in workflow graphs</li>
     *   <li>Edge ordering validation (unguarded edges must be last)</li>
     *   <li>Step and guard reference validation</li>
     *   <li>Workflow structure validation</li>
     * </ul>
     * 
     * <p>Usage example:
     * <pre>
     * SimpleEngine engine = SimpleEngine.create("workflow.yaml");
     * ValidationResult result = engine.validateConfiguration();
     * 
     * if (!result.isValid()) {
     *     System.err.println("Configuration issues found:");
     *     System.err.println(result.getErrorSummary());
     *     // Fix issues before execution
     * } else {
     *     // Safe to execute workflows
     *     engine.execute("workflowName", context);
     * }
     * </pre>
     * 
     * @return comprehensive validation result with errors, warnings, and metadata
     */
    public ValidationResult validateConfiguration() {
        LOGGER.debug("Validating FlowConfig with {} workflow(s)", 
                    getFlowConfig().workflows != null ? getFlowConfig().workflows.size() : 0);
        
        ValidationResult result = validationEngine.validate(getFlowConfig());
        
        if (result.isValid()) {
            LOGGER.info("FlowConfig validation passed successfully");
        } else {
            LOGGER.warn("FlowConfig validation failed with {} error(s), {} warning(s)",
                       result.getErrors().size(), result.getWarnings().size());
        }
        
        return result;
    }
    
    /**
     * Validates the current FlowConfig with fail-fast behavior.
     * 
     * <p>This method stops validation on the first critical error, providing
     * faster feedback for severely broken configurations.
     * 
     * @param failFast if true, stop validation on first critical error
     * @return validation result with errors found up to the first critical failure
     */
    public ValidationResult validateConfiguration(boolean failFast) {
        LOGGER.debug("Validating FlowConfig with fail-fast={}", failFast);
        
        ValidationResult result = validationEngine.validate(getFlowConfig(), failFast);
        
        if (result.isValid()) {
            LOGGER.info("FlowConfig validation passed successfully");
        } else {
            LOGGER.warn("FlowConfig validation failed with {} error(s), {} warning(s){}",
                       result.getErrors().size(), result.getWarnings().size(),
                       failFast ? " (fail-fast mode)" : "");
        }
        
        return result;
    }
    
    /**
     * Validates a specific workflow within the current FlowConfig.
     * 
     * <p>This method creates a temporary FlowConfig containing only the specified
     * workflow and its dependencies, then validates it. Useful for validating
     * individual workflows without checking the entire configuration.
     * 
     * @param workflowName the name of the workflow to validate
     * @return validation result for the specific workflow
     * @throws IllegalArgumentException if the workflow doesn't exist
     */
    public ValidationResult validateWorkflow(String workflowName) {
        FlowConfig fullConfig = getFlowConfig();
        
        if (fullConfig.workflows == null || !fullConfig.workflows.containsKey(workflowName)) {
            return ValidationResult.failure(
                ValidationError.builder(ValidationErrorType.UNDEFINED_STEP)
                    .workflowName(workflowName)
                    .message("Workflow '" + workflowName + "' not found in configuration")
                    .detail("availableWorkflows", fullConfig.workflows != null ? 
                           new ArrayList<>(fullConfig.workflows.keySet()) : Collections.emptyList())
                    .build()
            );
        }
        
        // Create subset config with only the target workflow
        FlowConfig workflowConfig = createWorkflowSubset(fullConfig, workflowName);
        
        LOGGER.debug("Validating workflow '{}' with {} step(s)", 
                    workflowName, workflowConfig.steps != null ? workflowConfig.steps.size() : 0);
        
        ValidationResult result = validationEngine.validate(workflowConfig);
        
        if (result.isValid()) {
            LOGGER.info("Workflow '{}' validation passed successfully", workflowName);
        } else {
            LOGGER.warn("Workflow '{}' validation failed with {} error(s), {} warning(s)",
                       workflowName, result.getErrors().size(), result.getWarnings().size());
        }
        
        return result;
    }
    
    /**
     * Validates the configuration and throws an exception if validation fails.
     * 
     * <p>This is a convenience method for fail-fast scenarios where you want
     * validation errors to prevent engine creation or execution.
     * 
     * <p>Usage example:
     * <pre>
     * try {
     *     SimpleEngine engine = SimpleEngine.create("workflow.yaml");
     *     engine.validateConfigurationOrThrow();
     *     // Configuration is valid, safe to proceed
     *     StepResult result = engine.execute("workflowName", context);
     * } catch (ValidationException e) {
     *     System.err.println("Configuration validation failed: " + e.getMessage());
     *     // Handle validation errors
     * }
     * </pre>
     * 
     * @throws ValidationException if validation fails, containing detailed error information
     */
    public void validateConfigurationOrThrow() {
        ValidationResult result = validateConfiguration();
        
        if (!result.isValid()) {
            throw new ValidationException("FlowConfig validation failed", result);
        }
    }
    
    /**
     * Gets information about the validation engine and registered validators.
     * 
     * @return list of validator information including names, priorities, and descriptions
     */
    public List<FlowConfigValidationEngine.ValidatorInfo> getValidationInfo() {
        return validationEngine.getValidatorInfo();
    }
    
    /**
     * Clears the validation result cache if caching is enabled.
     * 
     * <p>Useful when the FlowConfig has been modified and you want to ensure
     * fresh validation results on the next validation call.
     */
    public void clearValidationCache() {
        validationEngine.clearCache();
        LOGGER.debug("Validation cache cleared");
    }
    
    /**
     * Creates a subset FlowConfig containing only the specified workflow and its dependencies.
     */
    private FlowConfig createWorkflowSubset(FlowConfig fullConfig, String workflowName) {
        FlowConfig subset = new FlowConfig();
        
        // Copy the target workflow
        FlowConfig.WorkflowDef targetWorkflow = fullConfig.workflows.get(workflowName);
        subset.workflows = Map.of(workflowName, targetWorkflow);
        
        // Find and copy all referenced steps
        Set<String> referencedSteps = findReferencedSteps(targetWorkflow);
        subset.steps = new HashMap<>();
        
        if (fullConfig.steps != null) {
            for (String stepName : referencedSteps) {
                if (fullConfig.steps.containsKey(stepName)) {
                    subset.steps.put(stepName, fullConfig.steps.get(stepName));
                }
            }
        }
        
        // Copy global settings and defaults
        subset.settings = fullConfig.settings;
        subset.defaults = fullConfig.defaults;
        
        return subset;
    }
    
    /**
     * Finds all steps referenced by a workflow (in edges and root).
     */
    private Set<String> findReferencedSteps(FlowConfig.WorkflowDef workflow) {
        Set<String> referencedSteps = new HashSet<>();
        
        if (workflow.root != null) {
            referencedSteps.add(workflow.root);
        }
        
        if (workflow.edges != null) {
            for (FlowConfig.EdgeDef edge : workflow.edges) {
                if (edge.from != null && !isTerminal(edge.from)) {
                    referencedSteps.add(edge.from);
                }
                if (edge.to != null && !isTerminal(edge.to)) {
                    referencedSteps.add(edge.to);
                }
            }
        }
        
        return referencedSteps;
    }
    
    private boolean isTerminal(String stepName) {
        return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
    }

    // ======================================================================================
    // EXTERNAL COMPONENT DISCOVERY - Built-in Support
    // ======================================================================================

    /**
     * Discovers and registers external components from configured packages.
     * This method scans external JARs for Step and Guard implementations.
     */
    private void discoverAndRegisterExternalComponents() {
        Set<String> packageNames = extractPackageNamesFromResources();

        for (String packageName : packageNames) {
            scannedPackages.add(packageName);

            DependencyResolver.ComponentResolution resolution =
                dependencyResolver.resolveAllComponents(packageName);

            // Register discovered Steps
            for (String stepClassName : resolution.getStepClasses()) {
                registerRuntimeStepClass(stepClassName);
            }

            // Register discovered Guards
            for (String guardClassName : resolution.getGuardClasses()) {
                registerRuntimeGuardClass(guardClassName);
            }
        }
    }

    /**
     * Extracts package names from external resource paths for component scanning.
     */
    private Set<String> extractPackageNamesFromResources() {
        Set<String> packages = new HashSet<>();
        for (String resourcePath : externalResources) {
            // Extract package name from resource path (simplified logic)
            if (resourcePath.contains("/")) {
                String[] parts = resourcePath.split("/");
                if (parts.length > 0) {
                    packages.add(parts[0]); // Use first part as package prefix
                }
            }
        }
        return packages;
    }

    /**
     * Registers a Step class in the runtime registry.
     */
    private void registerRuntimeStepClass(String stepClassName) {
        Class<?> stepClass = dependencyResolver.resolveStepClass(stepClassName);
        if (stepClass != null && Step.class.isAssignableFrom(stepClass)) {
            @SuppressWarnings("unchecked")
            Class<? extends Step> typedStepClass = (Class<? extends Step>) stepClass;

            String simpleName = convertToCamelCase(stepClass.getSimpleName());
            runtimeStepRegistry.put(simpleName, typedStepClass);
            runtimeStepRegistry.put(stepClassName, typedStepClass);
        }
    }

    /**
     * Registers a Guard class in the runtime registry.
     */
    private void registerRuntimeGuardClass(String guardClassName) {
        Class<?> guardClass = dependencyResolver.resolveGuardClass(guardClassName);
        if (guardClass != null && Guard.class.isAssignableFrom(guardClass)) {
            @SuppressWarnings("unchecked")
            Class<? extends Guard> typedGuardClass = (Class<? extends Guard>) guardClass;

            String simpleName = convertToCamelCase(guardClass.getSimpleName());
            runtimeGuardRegistry.put(simpleName, typedGuardClass);
            runtimeGuardRegistry.put(guardClassName, typedGuardClass);
        }
    }

    /**
     * Converts class names to camelCase for registration.
     */
    private String convertToCamelCase(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Gets information about discovered components.
     */
    public ComponentDiscoveryInfo getDiscoveryInfo() {
        return new ComponentDiscoveryInfo(
            runtimeStepRegistry.size(),
            runtimeGuardRegistry.size(),
            new ArrayList<>(scannedPackages),
            new ArrayList<>(externalResources)
        );
    }

    /**
     * Converts YAML data to FlowConfig.
     */
    private static FlowConfig convertYamlToFlowConfig(Map<String, Object> yamlData) {
        Yaml yaml = new Yaml();
        return yaml.loadAs(yaml.dump(yamlData), FlowConfig.class);
    }

    // ======================================================================================
    // BUILDER CLASSES - Fluent Configuration API
    // ======================================================================================

    /**
     * Simplified builder for configuring SimpleEngine.
     */
    public static class EngineBuilder {
        private final List<String> yamlPaths = new ArrayList<>();
        private String[] scanPackages;
        private FlowConfigValidationEngine customValidationEngine;

        /**
         * Adds YAML files containing workflows and step definitions.
         */
        public EngineBuilder withExternalYamls(String... yamls) {
            this.yamlPaths.addAll(Arrays.asList(yamls));
            return this;
        }

        /**
         * Sets all packages to scan for Step and Guard implementations.
         * This includes both local and external JAR packages.
         */
        public EngineBuilder withPackages(String... allRequiredPackages) {
            this.scanPackages = allRequiredPackages;
            return this;
        }
        
        /**
         * Sets a custom validation engine with specific validators.
         */
        public EngineBuilder withValidationEngine(FlowConfigValidationEngine validationEngine) {
            this.customValidationEngine = validationEngine;
            return this;
        }
        
        /**
         * Configures validation with custom validators.
         * 
         * <p>Usage example:
         * <pre>
         * EngineBuilder builder = SimpleEngine.builder()
         *     .withCustomValidation(validationBuilder -> validationBuilder
         *         .addValidator(new CycleDetectionValidator())
         *         .addValidator(new EdgeOrderingValidator()) 
         *         .addValidator(new BusinessRuleValidator())
         *         .enableCaching(true));
         * </pre>
         */
        public EngineBuilder withCustomValidation(java.util.function.Function<FlowConfigValidationEngine.Builder, FlowConfigValidationEngine.Builder> configurer) {
            FlowConfigValidationEngine.Builder validationBuilder = FlowConfigValidationEngine.builder();
            this.customValidationEngine = configurer.apply(validationBuilder).build();
            return this;
        }

        /**
         * Builds the configured SimpleEngine.
         */
        public SimpleEngine build() {
            if (yamlPaths.isEmpty()) {
                throw new IllegalStateException("At least one YAML file must be specified");
            }

            // Load and merge all YAML files
            YamlResourceLoader loader = new YamlResourceLoader();
            FlowConfig mergedConfig = new FlowConfig();
            mergedConfig.steps = new HashMap<>();
            mergedConfig.workflows = new HashMap<>();

            for (String yamlPath : yamlPaths) {
                Map<String, Object> yamlData = loader.loadYaml(yamlPath);
                FlowConfig config = convertYamlToFlowConfig(yamlData);
                mergeConfigs(mergedConfig, config);
                LOGGER.debug("Merged YAML: {} (workflows={})", yamlPath, config.workflows.keySet());
            }

            LOGGER.info("SimpleEngine built with {} YAML(s); packages={}", yamlPaths.size(), Arrays.toString(scanPackages));
            
            if (customValidationEngine != null) {
                return new SimpleEngine(mergedConfig, customValidationEngine, scanPackages);
            } else {
                return new SimpleEngine(mergedConfig, scanPackages);
            }
        }

        /**
         * Merges configurations from multiple YAML files.
         */
        private void mergeConfigs(FlowConfig main, FlowConfig external) {
            if (external.steps != null) {
                main.steps.putAll(external.steps);
            }
            if (external.workflows != null) {
                main.workflows.putAll(external.workflows);
            }
            if (external.settings != null) {
                if (main.settings == null) main.settings = new HashMap<>();
                main.settings.putAll(external.settings);
            }
            if (external.defaults != null) {
                if (main.defaults == null) main.defaults = new HashMap<>();
                main.defaults.putAll(external.defaults);
            }
            LOGGER.debug("Merged config: steps={}, workflows={}, settingsKeys={}, defaultsKeys={}",
                    main.steps.size(), main.workflows.size(),
                    main.settings != null ? main.settings.keySet() : Collections.emptySet(),
                    main.defaults != null ? main.defaults.keySet() : Collections.emptySet());
        }
    }

    /**
     * Fluent builder for programmatic workflow construction.
     */
    public static class WorkflowBuilder {
        private final String workflowName;
        private final FlowConfig config = new FlowConfig();
        private final String[] scanPackages;

        public WorkflowBuilder(String workflowName) {
            this.workflowName = workflowName;
            this.scanPackages = null;
            this.config.steps = new HashMap<>();
            this.config.workflows = new HashMap<>();
            this.config.settings = new HashMap<>();
            this.config.defaults = new HashMap<>();
        }

        public WorkflowBuilder(String workflowName, String... scanPackages) {
            this.workflowName = workflowName;
            this.scanPackages = scanPackages;
            this.config.steps = new HashMap<>();
            this.config.workflows = new HashMap<>();
            this.config.settings = new HashMap<>();
            this.config.defaults = new HashMap<>();
        }

        public StepBuilder step(String stepName) {
            return new StepBuilder(this, stepName);
        }

        /**
         * Explicitly sets the workflow root step.
         */
        public WorkflowBuilder root(String stepName) {
            FlowConfig.WorkflowDef workflowDefinition = config.workflows.computeIfAbsent(workflowName, k -> {
                FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
                wf.edges = new ArrayList<>();
                return wf;
            });
            workflowDefinition.root = stepName;
            return this;
        }

        /** Adds or updates a top-level setting. */
        public WorkflowBuilder setting(String key, Object value) {
            if (config.settings == null) config.settings = new HashMap<>();
            config.settings.put(key, value);
            return this;
        }

        /** Adds defaults for a category (e.g., "step" or "guard"). */
        public WorkflowBuilder defaults(String category, Map<String, Object> values) {
            if (config.defaults == null) config.defaults = new HashMap<>();
            Map<String, Object> target = config.defaults.computeIfAbsent(category, k -> new HashMap<>());
            if (values != null) target.putAll(values);
            return this;
        }

        /** Adds defaults for a specific step or guard by name. */
        public WorkflowBuilder defaultsFor(String name, Map<String, Object> values) {
            if (config.defaults == null) config.defaults = new HashMap<>();
            Map<String, Object> target = config.defaults.computeIfAbsent(name, k -> new HashMap<>());
            if (values != null) target.putAll(values);
            return this;
        }

        public SimpleEngine build() {
            if (scanPackages != null) {
                return new SimpleEngine(config, scanPackages);
            } else {
                return new SimpleEngine(config);
            }
        }

        /** Builds the engine and validates configuration immediately, throwing on failure. */
        public SimpleEngine buildValidated() {
            SimpleEngine eng = build();
            eng.validateConfigurationOrThrow();
            return eng;
        }

        FlowConfig getConfig() {
            return config;
        }

        String getWorkflowName() {
            return workflowName;
        }
    }

    /**
     * Fluent builder for configuring individual workflow steps.
     */
    public static class StepBuilder {
        private final WorkflowBuilder workflowBuilder;
        private final String stepName;

        public StepBuilder(WorkflowBuilder workflowBuilder, String stepName) {
            this.workflowBuilder = workflowBuilder;
            this.stepName = stepName;
        }

        public StepBuilder using(String componentType) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            stepDefinition.type = componentType;
            return this;
        }

        public StepBuilder with(String configurationKey, Object configurationValue) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            if (stepDefinition.config == null) {
                stepDefinition.config = new HashMap<>();
            }
            stepDefinition.config.put(configurationKey, configurationValue);
            return this;
        }

        /**
         * Adds a single step-level guard by name.
         */
        public StepBuilder guard(String guardName) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            if (stepDefinition.guards == null) {
                stepDefinition.guards = new ArrayList<>();
            }
            stepDefinition.guards.add(guardName);
            return this;
        }

        /**
         * Adds multiple step-level guards by name.
         */
        public StepBuilder guards(String... guardNames) {
            if (guardNames == null || guardNames.length == 0) return this;
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            if (stepDefinition.guards == null) {
                stepDefinition.guards = new ArrayList<>();
            }
            stepDefinition.guards.addAll(Arrays.asList(guardNames));
            return this;
        }

        /**
         * Configures engine-driven retry with fixed delay. If retryGuardName is provided, the engine will retry
         * failed step executions up to maxAttempts with delayMs between attempts when the guard passes.
         * If retryGuardName is null, the engine will retry unconditionally up to maxAttempts with fixed delay.
         */
        public StepBuilder retry(int maxAttempts, long delayMs, String retryGuardName) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
            retry.maxAttempts = maxAttempts;
            retry.delay = delayMs;
            retry.guard = retryGuardName;
            stepDefinition.retry = retry;
            return this;
        }

        /**
         * Configures engine-driven retry without a guard (unconditional), using fixed delay.
         */
        public StepBuilder retry(int maxAttempts, long delayMs) {
            return retry(maxAttempts, delayMs, null);
        }

        /**
         * Configures engine-driven retry with exponential backoff.
         * If retryGuardName is provided, guard controls whether to continue retrying between attempts;
         * if null, retries are unconditional.
         */
        public StepBuilder retryExponential(int maxAttempts, long baseDelayMs, Double multiplier, Long maxDelayMs, String retryGuardName) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
            retry.maxAttempts = maxAttempts;
            retry.delay = baseDelayMs;
            retry.guard = retryGuardName;
            retry.backoff = "EXPONENTIAL";
            retry.multiplier = (multiplier != null ? multiplier : 2.0);
            retry.maxDelay = maxDelayMs;
            stepDefinition.retry = retry;
            return this;
        }

        /**
         * Alternative API that takes number of retries (excluding the initial attempt).
         * For example, retries=2 means up to 3 total attempts.
         */
        public StepBuilder retries(int retries, long delayMs) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
            retry.retries = Math.max(0, retries);
            retry.delay = delayMs;
            retry.guard = null; // unconditional by default
            stepDefinition.retry = retry;
            return this;
        }

        public StepBuilder retries(int retries, long delayMs, String retryGuardName) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
            retry.retries = Math.max(0, retries);
            retry.delay = delayMs;
            retry.guard = retryGuardName;
            stepDefinition.retry = retry;
            return this;
        }

        public StepBuilder retriesExponential(int retries, long baseDelayMs) {
            return retriesExponential(retries, baseDelayMs, 2.0, null, null);
        }

        public StepBuilder retriesExponential(int retries, long baseDelayMs, double multiplier) {
            return retriesExponential(retries, baseDelayMs, multiplier, null, null);
        }

        public StepBuilder retriesExponential(int retries, long baseDelayMs, double multiplier, Long maxDelayMs) {
            return retriesExponential(retries, baseDelayMs, multiplier, maxDelayMs, null);
        }

        public StepBuilder retriesExponential(int retries, long baseDelayMs, Double multiplier, Long maxDelayMs, String retryGuardName) {
            FlowConfig.StepDef stepDefinition = getOrCreateStepDef();
            FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
            retry.retries = Math.max(0, retries);
            retry.delay = baseDelayMs;
            retry.guard = retryGuardName;
            retry.backoff = "EXPONENTIAL";
            retry.multiplier = (multiplier != null ? multiplier : 2.0);
            retry.maxDelay = maxDelayMs;
            stepDefinition.retry = retry;
            return this;
        }

        /** Convenience overloads for exponential backoff without a guard. */
        public StepBuilder retryExponential(int maxAttempts, long baseDelayMs) {
            return retryExponential(maxAttempts, baseDelayMs, 2.0, null, null);
        }

        public StepBuilder retryExponential(int maxAttempts, long baseDelayMs, double multiplier) {
            return retryExponential(maxAttempts, baseDelayMs, multiplier, null, null);
        }

        public StepBuilder retryExponential(int maxAttempts, long baseDelayMs, double multiplier, Long maxDelayMs) {
            return retryExponential(maxAttempts, baseDelayMs, multiplier, maxDelayMs, null);
        }

        /** Adds an unguarded edge to the next step and returns the workflow builder. */
        public WorkflowBuilder then(String nextStepName) {
            return to(nextStepName).endEdge();
        }

        /** Adds an unguarded edge to the next step and returns the workflow builder. */
        public WorkflowBuilder thenSuccess() {
            return to("SUCCESS").endEdge();
        }

        /** Adds an unguarded edge to the next step and returns the workflow builder. */
        public WorkflowBuilder thenFailure() {
            return to("FAILURE").endEdge();
        }

        /** Begins a guarded edge to the next step and returns the edge builder for further configuration. */
        @Deprecated
        public EdgeBuilder toIf(String guardName, String nextStepName) {
            return to(nextStepName).guard(guardName);
        }

        // ------------------------------------------------------------
        // Concise helpers to avoid EdgeBuilder for common cases
        // These make toIf largely unnecessary for day-to-day usage.
        // ------------------------------------------------------------

        /** Adds a guarded edge with onFailure=SKIP (continue evaluating next edges), then returns WorkflowBuilder. */
        public WorkflowBuilder when(String guardName, String to) {
            return route(r -> r.when(guardName, to));
        }

        /** Adds a guarded edge with onFailure=STOP (terminate workflow), then returns WorkflowBuilder. */
        public WorkflowBuilder whenStop(String guardName, String to) {
            return route(r -> r.whenStop(guardName, to));
        }

        /** Adds a guarded edge with onFailure=CONTINUE (proceed to target), then returns WorkflowBuilder. */
        public WorkflowBuilder whenContinue(String guardName, String to) {
            return route(r -> r.whenContinue(guardName, to));
        }

        /** Adds a guarded edge with onFailure=ALTERNATIVE (redirect to alternative target), then returns WorkflowBuilder. */
        public WorkflowBuilder whenAlternative(String guardName, String to, String alternativeTarget) {
            return route(r -> r.whenAlternative(guardName, to, alternativeTarget));
        }

        /** Adds a guarded edge with onFailure=RETRY (re-evaluate guard), then returns WorkflowBuilder. */
        public WorkflowBuilder whenRetry(String guardName, String to, int attempts, long delayMs) {
            return route(r -> r.whenRetry(guardName, to, attempts, delayMs));
        }

        /** Adds an unguarded default edge and returns WorkflowBuilder. */
        public WorkflowBuilder otherwise(String to) {
            return route(r -> r.otherwise(to));
        }

        /** Begins configuration of an edge from this step to the given target. */
        public EdgeBuilder to(String nextStepName) {
            FlowConfig workflowConfig = workflowBuilder.getConfig();
            String workflowName = workflowBuilder.getWorkflowName();

            FlowConfig.WorkflowDef workflowDefinition = workflowConfig.workflows.computeIfAbsent(workflowName,
                workflowKey -> {
                    FlowConfig.WorkflowDef newWorkflow = new FlowConfig.WorkflowDef();
                    newWorkflow.edges = new ArrayList<>();
                    return newWorkflow;
                });

            if (workflowDefinition.root == null) {
                workflowDefinition.root = stepName;
            }

            FlowConfig.EdgeDef executionEdge = new FlowConfig.EdgeDef();
            executionEdge.from = stepName;
            executionEdge.to = nextStepName;
            workflowDefinition.edges.add(executionEdge);
            return new EdgeBuilder(workflowBuilder, executionEdge);
        }

        public WorkflowBuilder end() {
            FlowConfig workflowConfig = workflowBuilder.getConfig();
            String workflowName = workflowBuilder.getWorkflowName();
            FlowConfig.WorkflowDef workflowDefinition = workflowConfig.workflows.get(workflowName);

            if (workflowDefinition != null) {
                FlowConfig.EdgeDef terminalEdge = new FlowConfig.EdgeDef();
                terminalEdge.from = stepName;
                terminalEdge.to = "SUCCESS";
                terminalEdge.kind = "terminal";
                workflowDefinition.edges.add(terminalEdge);
            }

            return workflowBuilder;
        }

        /**
         * Returns to the parent workflow builder without adding any terminal edge.
         * Useful after configuration blocks like route(...).
         */
        public WorkflowBuilder endStep() {
            return workflowBuilder;
        }

        /**
         * Compact DSL for adding multiple outgoing edges from this step.
         * Returns the parent WorkflowBuilder for concise chaining (like then).
         * Example:
         *   .route(r -> r
         *       .when("VipCustomer", "premium")       // default onFailure=SKIP
         *       .whenContinue("HasCoupon", "discount")
         *       .otherwise("standard")                 // unguarded fallback (must be last)
         *   )
         */
        public WorkflowBuilder route(java.util.function.Consumer<RouteBuilder> configurer) {
            RouteBuilder rb = new RouteBuilder(this);
            configurer.accept(rb);
            return workflowBuilder;
        }

        /** Marks this step as the workflow root explicitly. */
        public StepBuilder root() {
            FlowConfig workflowConfig = workflowBuilder.getConfig();
            String workflowName = workflowBuilder.getWorkflowName();
            FlowConfig.WorkflowDef workflowDefinition = workflowConfig.workflows.computeIfAbsent(workflowName,
                workflowKey -> {
                    FlowConfig.WorkflowDef newWorkflow = new FlowConfig.WorkflowDef();
                    newWorkflow.edges = new ArrayList<>();
                    return newWorkflow;
                });
            workflowDefinition.root = stepName;
            return this;
        }

        /** Convenience: add unguarded terminal transition to SUCCESS. */
        public WorkflowBuilder toSuccess() { return to("SUCCESS").endEdge(); }

        /** Convenience: add unguarded terminal transition to FAILURE. */
        public WorkflowBuilder toFailure() { return to("FAILURE").endEdge(); }

        /** Convenience: add guarded terminal transition to SUCCESS. */
        public WorkflowBuilder toSuccessIf(String guardName) { return to("SUCCESS").guard(guardName).endEdge(); }

        /** Convenience: add guarded terminal transition to FAILURE. */
        public WorkflowBuilder toFailureIf(String guardName) { return to("FAILURE").guard(guardName).endEdge(); }

        /** Adds an unguarded edge and returns a StepBuilder for the target step to continue chaining. */
        public StepBuilder thenStep(String nextStepName) {
            then(nextStepName);
            return new StepBuilder(workflowBuilder, nextStepName);
        }

        private FlowConfig.StepDef getOrCreateStepDef() {
            FlowConfig workflowConfig = workflowBuilder.getConfig();
            FlowConfig.StepDef def = workflowConfig.steps.get(stepName);
            if (def == null) {
                def = new FlowConfig.StepDef();
                def.config = new HashMap<>();
                def.guards = new ArrayList<>();
                workflowConfig.steps.put(stepName, def);
            }
            return def;
        }
    }

    /**
     * Compact builder to add multiple edges from a single step with minimal verbosity.
     */
    public static class RouteBuilder {
        private final StepBuilder stepBuilder;
        private final WorkflowBuilder workflowBuilder;
        private final String stepName;
        private final FlowConfig.WorkflowDef workflowDefinition;
        private boolean closed;

        RouteBuilder(StepBuilder stepBuilder) {
            this.stepBuilder = stepBuilder;
            this.workflowBuilder = stepBuilder.workflowBuilder;
            this.stepName = stepBuilder.stepName;
            FlowConfig wfConfig = workflowBuilder.getConfig();
            String wfName = workflowBuilder.getWorkflowName();
            this.workflowDefinition = wfConfig.workflows.computeIfAbsent(wfName, k -> {
                FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
                wf.edges = new ArrayList<>();
                return wf;
            });
            if (this.workflowDefinition.root == null) {
                this.workflowDefinition.root = this.stepName;
            }
            this.closed = false;
        }

        private void assertOpen() {
            if (closed) {
                throw new IllegalStateException("RouteBuilder is closed after otherwise(); no further edges can be added.");
            }
        }

        private FlowConfig.EdgeDef newEdge(String to) {
            FlowConfig.EdgeDef e = new FlowConfig.EdgeDef();
            e.from = stepName;
            e.to = to;
            this.workflowDefinition.edges.add(e);
            return e;
        }

        private void setFailure(FlowConfig.EdgeDef e, String strategy, Integer attempts, Long delay, String alt) {
            if (e.onFailure == null) e.onFailure = new FlowConfig.OnFailure();
            e.onFailure.strategy = strategy;
            e.onFailure.attempts = attempts;
            e.onFailure.delay = delay;
            e.onFailure.alternativeTarget = alt;
        }

        /** Adds a guarded edge; default onFailure=SKIP to continue evaluating next edges. */
        public RouteBuilder when(String guardName, String to) {
            assertOpen();
            FlowConfig.EdgeDef e = newEdge(to);
            e.guard = guardName;
            setFailure(e, "SKIP", null, null, null);
            return this;
        }

        /** Guarded edge with STOP onFailure (terminate workflow). */
        public RouteBuilder whenStop(String guardName, String to) {
            assertOpen();
            FlowConfig.EdgeDef e = newEdge(to);
            e.guard = guardName;
            setFailure(e, "STOP", null, null, null);
            return this;
        }

        /** Guarded edge with CONTINUE onFailure (proceed to target despite failure). */
        public RouteBuilder whenContinue(String guardName, String to) {
            assertOpen();
            FlowConfig.EdgeDef e = newEdge(to);
            e.guard = guardName;
            setFailure(e, "CONTINUE", null, null, null);
            return this;
        }

        /** Guarded edge with ALTERNATIVE onFailure (redirect to alternativeTarget). */
        public RouteBuilder whenAlternative(String guardName, String to, String alternativeTarget) {
            assertOpen();
            FlowConfig.EdgeDef e = newEdge(to);
            e.guard = guardName;
            setFailure(e, "ALTERNATIVE", null, null, alternativeTarget);
            return this;
        }

        /** Guarded edge with RETRY onFailure (re-evaluate guard). */
        public RouteBuilder whenRetry(String guardName, String to, int attempts, long delayMs) {
            assertOpen();
            FlowConfig.EdgeDef e = newEdge(to);
            e.guard = guardName;
            setFailure(e, "RETRY", attempts, delayMs, null);
            return this;
        }

        /** Unguarded default edge; must be the final edge in this route. */
        public StepBuilder otherwise(String to) {
            assertOpen();
            newEdge(to); // no guard
            closed = true;
            return stepBuilder;
        }

        /** Unguarded default edge; must be the final edge in this route. */
        public StepBuilder otherwiseSuccess() {
            assertOpen();
            newEdge("SUCCESS"); // no guard
            closed = true;
            return stepBuilder;
        }

        /** Unguarded default edge; must be the final edge in this route. */
        public StepBuilder otherwiseFailure() {
            assertOpen();
            newEdge("FAILURE"); // no guard
            closed = true;
            return stepBuilder;
        }
    }
    /**
     * Fluent builder for configuring an individual workflow edge.
     */
    public static class EdgeBuilder {
        private final WorkflowBuilder workflowBuilder;
        private final FlowConfig.EdgeDef edge;

        EdgeBuilder(WorkflowBuilder workflowBuilder, FlowConfig.EdgeDef edge) {
            this.workflowBuilder = workflowBuilder;
            this.edge = edge;
        }

        /** Sets an edge-level guard. */
        public EdgeBuilder guard(String guardName) {
            this.edge.guard = guardName;
            return this;
        }

        /** onFailure: STOP (default). */
        public EdgeBuilder onFailureStop() {
            ensureOnFailure();
            this.edge.onFailure.strategy = "STOP";
            this.edge.onFailure.alternativeTarget = null;
            this.edge.onFailure.attempts = null;
            this.edge.onFailure.delay = null;
            return this;
        }

        /** onFailure: SKIP. */
        public EdgeBuilder onFailureSkip() {
            ensureOnFailure();
            this.edge.onFailure.strategy = "SKIP";
            this.edge.onFailure.alternativeTarget = null;
            this.edge.onFailure.attempts = null;
            this.edge.onFailure.delay = null;
            return this;
        }

        /** onFailure: CONTINUE. */
        public EdgeBuilder onFailureContinue() {
            ensureOnFailure();
            this.edge.onFailure.strategy = "CONTINUE";
            this.edge.onFailure.alternativeTarget = null;
            this.edge.onFailure.attempts = null;
            this.edge.onFailure.delay = null;
            return this;
        }

        /** onFailure: ALTERNATIVE -> target. */
        public EdgeBuilder onFailureAlternative(String alternativeTarget) {
            ensureOnFailure();
            this.edge.onFailure.strategy = "ALTERNATIVE";
            this.edge.onFailure.alternativeTarget = alternativeTarget;
            this.edge.onFailure.attempts = null;
            this.edge.onFailure.delay = null;
            return this;
        }

        /** onFailure: RETRY attempts/delay. */
        public EdgeBuilder onFailureRetry(int attempts, long delayMs) {
            ensureOnFailure();
            this.edge.onFailure.strategy = "RETRY";
            this.edge.onFailure.attempts = attempts;
            this.edge.onFailure.delay = delayMs;
            this.edge.onFailure.alternativeTarget = null;
            return this;
        }

        /** Finalizes the edge configuration and returns the parent workflow builder. */
        public WorkflowBuilder endEdge() {
            return workflowBuilder;
        }

        /** Finalizes the edge and returns a StepBuilder for the same source step to continue chaining. */
        public StepBuilder endEdgeToStep() {
            return new StepBuilder(workflowBuilder, edge.from);
        }

        private void ensureOnFailure() {
            if (this.edge.onFailure == null) this.edge.onFailure = new FlowConfig.OnFailure();
        }
    }

    /**
     * Information about discovered components.
     */
    public static class ComponentDiscoveryInfo {
        public final int runtimeStepCount;
        public final int runtimeGuardCount;
        public final List<String> scannedPackages;
        public final List<String> externalResources;

        public ComponentDiscoveryInfo(int runtimeStepCount, int runtimeGuardCount,
                                    List<String> scannedPackages, List<String> externalResources) {
            this.runtimeStepCount = runtimeStepCount;
            this.runtimeGuardCount = runtimeGuardCount;
            this.scannedPackages = new ArrayList<>(scannedPackages);
            this.externalResources = new ArrayList<>(externalResources);
        }

        public int getTotalRuntimeComponents() {
            return runtimeStepCount + runtimeGuardCount;
        }

        @Override
        public String toString() {
            return String.format("ComponentDiscoveryInfo{steps=%d, guards=%d, packages=%s, resources=%s}",
                    runtimeStepCount, runtimeGuardCount, scannedPackages, externalResources);
        }
    }
}
