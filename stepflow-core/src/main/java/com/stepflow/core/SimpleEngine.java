package com.stepflow.core;

import com.stepflow.engine.Engine;
import com.stepflow.config.FlowConfig;
import com.stepflow.execution.*;
import com.stepflow.resource.YamlResourceLoader;
import com.stepflow.component.DependencyResolver;

import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified engine providing simplified APIs for StepFlow workflow creation and execution.
 *
 * <p>SimpleEngine is the single entry point for all StepFlow functionality, combining
 * workflow-based YAML processing, external dependency management, and programmatic construction
 * into one cohesive interface.
 *
 * <p>Key Features:
 * <ul>
 *   <li><strong>Workflow-Based YAML:</strong> Enhanced YAML format supporting complex workflows</li>
 *   <li><strong>External Dependencies:</strong> Built-in support for Maven/Gradle dependencies</li>
 *   <li><strong>Automatic Discovery:</strong> Component scanning across local and external JARs</li>
 *   <li><strong>Fluent API:</strong> Programmatic workflow construction</li>
 *   <li><strong>Single Entry Point:</strong> Unified interface for all use cases</li>
 * </ul>
 *
 * <p>Usage Examples:
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
 *     .withWorkflow("my-workflow.yaml")
 *     .withExternalSteps("com.example.steps", "order-steps.yaml")
 *     .withExternalGuards("com.security.guards", "auth-guards.yaml")
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

    /**
     * Creates a SimpleEngine with the specified FlowConfig and scan packages.
     *
     * @param config the FlowConfig containing workflow definitions
     * @param scanPackages optional package prefixes to scan for components
     */
    private SimpleEngine(FlowConfig config, String... scanPackages) {
        this.engine = new Engine(config, scanPackages);
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
        this.externalResources.addAll(externalResources);
        discoverAndRegisterExternalComponents();
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
            return new SimpleEngine(mergedConfig, scanPackages);
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
        }

        public WorkflowBuilder(String workflowName, String... scanPackages) {
            this.workflowName = workflowName;
            this.scanPackages = scanPackages;
            this.config.steps = new HashMap<>();
            this.config.workflows = new HashMap<>();
        }

        public StepBuilder step(String stepName) {
            return new StepBuilder(this, stepName);
        }

        public SimpleEngine build() {
            if (scanPackages != null) {
                return new SimpleEngine(config, scanPackages);
            } else {
                return new SimpleEngine(config);
            }
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
         * Configures engine-driven retry. If retryGuardName is provided, the engine will retry
         * failed step executions up to maxAttempts with delayMs between attempts when the guard passes.
         * If retryGuardName is null, the engine executes the step once; the step may implement its own retry.
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
         * Configures retry parameters for steps that handle retry internally (no engine guard).
         * The engine will still execute the step once; the step is expected to use these values.
         */
        public StepBuilder retry(int maxAttempts, long delayMs) {
            return retry(maxAttempts, delayMs, null);
        }

        public WorkflowBuilder then(String nextStepName) {
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

            return workflowBuilder;
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
