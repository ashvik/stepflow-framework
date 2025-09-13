package com.stepflow.config;

import java.util.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Central configuration container for StepFlow workflow definitions and settings.
 * 
 * <p>FlowConfig serves as the primary configuration model for the StepFlow framework,
 * containing all the necessary definitions to execute workflows including step configurations,
 * workflow routing definitions, global settings, and default values. This class supports
 * both YAML-driven configuration and programmatic construction.
 * 
 * <h2>Configuration Structure</h2>
 * 
 * <p>A FlowConfig contains four main sections:
 * <ul>
 *   <li><strong>Steps:</strong> Step type mappings and configurations</li>
 *   <li><strong>Workflows:</strong> Workflow routing and edge definitions</li>
 *   <li><strong>Settings:</strong> Global configuration values</li>
 *   <li><strong>Defaults:</strong> Default values organized by category or component name</li>
 * </ul>
 * 
 * <h2>YAML Configuration Format</h2>
 * 
 * <p><strong>Complete Configuration Example:</strong>
 * <pre>
 * # Global settings accessible to all components
 * settings:
 *   database:
 *     url: "jdbc:postgresql://localhost/mydb"
 *     timeout: 30000
 *     pool_size: 10
 *   external_services:
 *     payment_gateway:
 *       url: "https://api.stripe.com"
 *       timeout: 15000
 *       api_version: "2020-08-27"
 *   notifications:
 *     email:
 *       template: "order-confirmation"
 *       retry_count: 3
 * 
 * # Default values by category
 * defaults:
 *   step:               # Applied to all steps
 *     timeout: 5000
 *     retry_attempts: 1
 *   guard:              # Applied to all guards
 *     cache_duration: 300
 *   validateStep:       # Applied to specific step type
 *     strict_mode: true
 * 
 * # Step definitions
 * steps:
 *   validate:
 *     type: "validateStep"           # Maps to component name
 *     guards: ["BusinessHoursGuard"]  # Step-level guards
 *     config:
 *       min_amount: 10.0
 *       max_amount: 10000.0
 *       currency: "USD"
 *     retry:
 *       maxAttempts: 3
 *       delay: 1000
 *       guard: "ShouldRetryGuard"
 *   
 *   processPayment:
 *     type: "paymentProcessingStep"
 *     config:
 *       gateway: "stripe"
 *       timeout: 30000
 *     retry:
 *       maxAttempts: 5
 *       delay: 2000
 *       backoff: "EXPONENTIAL"
 *       multiplier: 2.0
 *       maxDelay: 30000
 *   
 *   notify:
 *     type: "notificationStep"
 *     guards: ["CustomerOptedInGuard"]
 * 
 * # Workflow definitions with routing
 * workflows:
 *   orderProcessing:
 *     root: validate
 *     edges:
 *       - from: validate
 *         to: processPayment
 *         guard: "OrderValidGuard"
 *       - from: validate
 *         to: manualReview
 *         guard: "RequiresReviewGuard"
 *         onFailure: 
 *           strategy: "SKIP"
 *       - from: validate
 *         to: rejected            # Default path (no guard)
 *       
 *       - from: processPayment
 *         to: notify
 *         guard: "PaymentSuccessGuard"
 *       - from: processPayment  
 *         to: refund
 *         condition: "payment_failed"
 *         onFailure:
 *           strategy: "RETRY"
 *           attempts: 3
 *           delay: 5000
 *       
 *       - from: notify
 *         to: SUCCESS
 * </pre>
 * 
 * <h2>Programmatic Configuration</h2>
 * 
 * <p><strong>Building Configuration Programmatically:</strong>
 * <pre>
 * FlowConfig config = new FlowConfig();
 * 
 * // Configure global settings
 * config.settings.put("database.timeout", 30000);
 * config.settings.put("api.base_url", "https://api.example.com");
 * 
 * // Set defaults
 * Map&lt;String, Object&gt; stepDefaults = new HashMap&lt;&gt;();
 * stepDefaults.put("timeout", 5000);
 * stepDefaults.put("retry_attempts", 1);
 * config.defaults.put("step", stepDefaults);
 * 
 * // Define steps
 * FlowConfig.StepDef validateStep = new FlowConfig.StepDef();
 * validateStep.type = "validateStep";
 * validateStep.config.put("min_amount", 10.0);
 * validateStep.guards.add("BusinessHoursGuard");
 * config.steps.put("validate", validateStep);
 * 
 * FlowConfig.StepDef processStep = new FlowConfig.StepDef();
 * processStep.type = "paymentProcessingStep";
 * processStep.config.put("gateway", "stripe");
 * 
 * // Configure retry policy
 * FlowConfig.RetryConfig retry = new FlowConfig.RetryConfig();
 * retry.maxAttempts = 5;
 * retry.delay = 1000L;
 * retry.backoff = "EXPONENTIAL";
 * retry.multiplier = 2.0;
 * processStep.retry = retry;
 * config.steps.put("processPayment", processStep);
 * 
 * // Define workflow
 * FlowConfig.WorkflowDef workflow = new FlowConfig.WorkflowDef();
 * workflow.root = "validate";
 * 
 * FlowConfig.EdgeDef edge1 = new FlowConfig.EdgeDef();
 * edge1.from = "validate";
 * edge1.to = "processPayment";
 * edge1.guard = "OrderValidGuard";
 * workflow.edges.add(edge1);
 * 
 * FlowConfig.EdgeDef edge2 = new FlowConfig.EdgeDef();
 * edge2.from = "processPayment";
 * edge2.to = "SUCCESS";
 * workflow.edges.add(edge2);
 * 
 * config.workflows.put("orderProcessing", workflow);
 * </pre>
 * 
 * <h2>Configuration Sections</h2>
 * 
 * <p><strong>Settings Section:</strong>
 * Global configuration accessible via {@code @ConfigValue} with {@code globalPath}:
 * <pre>
 * settings:
 *   api:
 *     timeout: 30000
 *     retry_count: 3
 *   features:
 *     cache_enabled: true
 *     debug_mode: false
 * 
 * // Access in step implementation:
 * {@code @ConfigValue(value = "timeout", globalPath = "api.timeout")}
 * private Integer apiTimeout;  // Gets 30000
 * </pre>
 * 
 * <p><strong>Defaults Section:</strong>
 * Hierarchical default values with inheritance:
 * <pre>
 * defaults:
 *   step:                    # Applied to all steps
 *     timeout: 5000
 *   guard:                   # Applied to all guards  
 *     cache_ttl: 300
 *   validateStep:            # Applied to specific step type
 *     strict_mode: true
 *   OrderValidationGuard:    # Applied to specific guard
 *     min_score: 80
 * </pre>
 * 
 * <h2>Step Configuration</h2>
 * 
 * <p>Each step definition contains:
 * <ul>
 *   <li><strong>type:</strong> Component name or class reference</li>
 *   <li><strong>config:</strong> Step-specific configuration parameters</li>
 *   <li><strong>guards:</strong> List of step-level guards (all must pass)</li>
 *   <li><strong>retry:</strong> Retry policy for step execution</li>
 * </ul>
 * 
 * <p><strong>Retry Configuration:</strong>
 * <pre>
 * steps:
 *   unstableService:
 *     type: "externalApiStep"
 *     retry:
 *       maxAttempts: 5        # Maximum retry attempts
 *       delay: 1000          # Base delay in milliseconds
 *       guard: "ShouldRetryGuard"  # Optional retry condition
 *       backoff: "EXPONENTIAL"     # Backoff strategy
 *       multiplier: 2.0           # Exponential multiplier
 *       maxDelay: 30000          # Maximum delay cap
 * </pre>
 * 
 * <h2>Workflow Configuration</h2>
 * 
 * <p>Workflow definitions specify execution flow:
 * <ul>
 *   <li><strong>root:</strong> Starting step name</li>
 *   <li><strong>edges:</strong> Transitions between steps with conditions</li>
 * </ul>
 * 
 * <p><strong>Edge Configuration:</strong>
 * <pre>
 * workflows:
 *   complex_flow:
 *     root: "start"
 *     edges:
 *       - from: "start"
 *         to: "premium_path"
 *         guard: "VIPCustomerGuard"     # Edge-level guard
 *         onFailure:                    # Guard failure handling
 *           strategy: "SKIP"           # Try next edge
 *       
 *       - from: "start"
 *         to: "standard_path"
 *         # No guard - fallback path
 *       
 *       - from: "premium_path"
 *         to: "SUCCESS"
 *         condition: "processing_complete"  # Alternative to guard
 *         kind: "conditional"               # Edge type
 * </pre>
 * 
 * <h2>Guard Failure Strategies</h2>
 * 
 * <p>Edge guards support sophisticated failure handling:
 * <ul>
 *   <li><strong>STOP:</strong> Terminate workflow immediately</li>
 *   <li><strong>SKIP:</strong> Try next edge from same source step</li>
 *   <li><strong>ALTERNATIVE:</strong> Redirect to different target step</li>
 *   <li><strong>RETRY:</strong> Re-evaluate guard with delays</li>
 *   <li><strong>CONTINUE:</strong> Proceed despite guard failure</li>
 * </ul>
 * 
 * <p><strong>Failure Strategy Examples:</strong>
 * <pre>
 * edges:
 *   - from: "validate"
 *     to: "process"
 *     guard: "DataAvailableGuard"
 *     onFailure:
 *       strategy: "RETRY"
 *       attempts: 5
 *       delay: 2000
 *   
 *   - from: "process"
 *     to: "premium_flow"
 *     guard: "VIPCustomerGuard"
 *     onFailure:
 *       strategy: "ALTERNATIVE"
 *       alternativeTarget: "standard_flow"
 * </pre>
 * 
 * <h2>Configuration Validation</h2>
 * 
 * <p>FlowConfig supports comprehensive validation through {@link com.stepflow.validation.FlowConfigValidationEngine}:
 * <ul>
 *   <li>Cycle detection in workflow graphs</li>
 *   <li>Edge ordering validation (unguarded edges must be last)</li>
 *   <li>Step and guard component resolution</li>
 *   <li>Configuration completeness checks</li>
 * </ul>
 * 
 * <h2>YAML Export</h2>
 * 
 * <p>FlowConfig can be serialized back to YAML format:
 * <pre>
 * FlowConfig config = loadConfiguration();
 * String yamlOutput = config.toFormattedYaml();
 * 
 * // Results in properly formatted, deterministic YAML output
 * // with consistent ordering and indentation
 * </pre>
 * 
 * <h2>Configuration Merging</h2>
 * 
 * <p>Multiple FlowConfig instances can be merged for modular configuration:
 * <pre>
 * FlowConfig baseConfig = loadFromYaml("base-config.yaml");
 * FlowConfig envConfig = loadFromYaml("production-config.yaml");
 * 
 * // Environment config overrides base config
 * FlowConfig merged = mergeConfigurations(baseConfig, envConfig);
 * </pre>
 * 
 * <h2>Template Support</h2>
 * 
 * <p>Configuration values support template variables:
 * <pre>
 * settings:
 *   environment: "production"
 *   database:
 *     url: "jdbc:postgresql://{{environment}}-db.example.com/mydb"
 * 
 * steps:
 *   apiCall:
 *     config:
 *       endpoint: "https://{{environment}}.api.example.com/v1"
 * </pre>
 * 
 * @see com.stepflow.core.SimpleEngine for configuration usage
 * @see com.stepflow.validation.FlowConfigValidationEngine for validation
 * @see StepDef for step configuration details
 * @see WorkflowDef for workflow routing details
 * @see RetryConfig for retry policy configuration
 * @see EdgeDef for edge routing configuration
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public class FlowConfig {
    
    public Map<String, StepDef> steps = new HashMap<>();
    public Map<String, WorkflowDef> workflows = new HashMap<>();
    public Map<String, Object> settings = new HashMap<>();
    public Map<String, Map<String, Object>> defaults = new HashMap<>();
    
    /**
     * Step definition containing type mapping, configuration, guards, and retry policy.
     * 
     * <p>StepDef defines how a step should be instantiated and configured within a workflow.
     * It maps logical step names to concrete implementations and provides configuration
     * data for dependency injection.
     * 
     * <p><strong>YAML Structure:</strong>
     * <pre>
     * steps:
     *   stepName:
     *     type: "componentName"           # Required: component to instantiate
     *     guards: ["Guard1", "Guard2"]     # Optional: step-level guards
     *     config:                         # Optional: step-specific config
     *       key1: value1
     *       key2: value2
     *     retry:                          # Optional: retry policy
     *       maxAttempts: 3
     *       delay: 1000
     * </pre>
     * 
     * <p><strong>Example Configurations:</strong>
     * <pre>
     * // Simple step without guards or retry
     * validateOrder:
     *   type: "orderValidationStep"
     *   config:
     *     min_amount: 10.0
     *     currency: "USD"
     * 
     * // Step with guards and retry
     * processPayment:
     *   type: "paymentProcessingStep"
     *   guards: ["BusinessHoursGuard", "VIPCustomerGuard"]
     *   config:
     *     gateway: "stripe"
     *     timeout: 30000
     *   retry:
     *     maxAttempts: 5
     *     delay: 2000
     *     backoff: "EXPONENTIAL"
     *     guard: "ShouldRetryGuard"
     * </pre>
     * 
     * @see RetryConfig for retry policy details
     */
    public static class StepDef {
        /** 
         * Component name or class reference for step instantiation.
         * Must match a registered component via {@code @StepComponent} annotation.
         */
        public String type;
        
        /** 
         * Step-specific configuration map.
         * Values are injected into step fields via {@code @ConfigValue} annotations.
         */
        public Map<String, Object> config = new HashMap<>();
        
        /** 
         * List of step-level guard names.
         * ALL guards must return true for the step to execute.
         * If any guard fails, the step is skipped.
         */
        public List<String> guards = new ArrayList<>();
        
        /** 
         * Optional retry configuration for step execution failures.
         * Controls automatic retry behavior when step returns failure status.
         */
        public RetryConfig retry;
    }
    
    /**
     * Workflow definition specifying execution flow through a directed graph of steps.
     * 
     * <p>WorkflowDef defines the routing logic for workflow execution, specifying
     * the starting point and the transitions between steps based on guards and conditions.
     * 
     * <p><strong>YAML Structure:</strong>
     * <pre>
     * workflows:
     *   workflowName:
     *     root: "startStepName"     # Required: first step to execute
     *     edges:                    # Required: transition definitions
     *       - from: "step1"
     *         to: "step2"
     *         guard: "guardName"     # Optional: transition condition
     *       - from: "step2"
     *         to: "SUCCESS"          # Terminal state
     * </pre>
     * 
     * <p><strong>Example Workflow:</strong>
     * <pre>
     * orderProcessing:
     *   root: "validate"
     *   edges:
     *     # Multiple conditional paths from validate
     *     - from: "validate"
     *       to: "premiumProcessing"
     *       guard: "VIPCustomerGuard"
     *       onFailure: { strategy: "SKIP" }
     *     
     *     - from: "validate"
     *       to: "standardProcessing"
     *       # No guard - fallback path
     *     
     *     # Converge to notification
     *     - from: "premiumProcessing"
     *       to: "notify"
     *     - from: "standardProcessing"
     *       to: "notify"
     *     
     *     # Final transition
     *     - from: "notify"
     *       to: "SUCCESS"
     * </pre>
     * 
     * @see EdgeDef for transition details
     */
    public static class WorkflowDef {
        /** 
         * Name of the first step to execute when workflow starts.
         * Must reference a step defined in the steps section.
         */
        public String root;
        
        /** 
         * List of directed edges defining transitions between steps.
         * Edges are evaluated in declaration order for multi-path scenarios.
         */
        public List<EdgeDef> edges = new ArrayList<>();
    }
    
    /**
     * Edge definition specifying a transition between two steps with optional conditions.
     * 
     * <p>EdgeDef represents a directed transition in the workflow graph, potentially
     * guarded by conditions. Edges support sophisticated failure handling strategies
     * and can represent different types of transitions.
     * 
     * <p><strong>Basic Edge Types:</strong>
     * <ul>
     *   <li><strong>Unconditional:</strong> Always taken if reached</li>
     *   <li><strong>Guarded:</strong> Taken only if guard evaluates to true</li>
     *   <li><strong>Conditional:</strong> Taken based on context condition</li>
     *   <li><strong>Fallback:</strong> Default path when other edges fail</li>
     * </ul>
     * 
     * <p><strong>YAML Examples:</strong>
     * <pre>
     * edges:
     *   # Simple unconditional edge
     *   - from: "step1"
     *     to: "step2"
     *   
     *   # Guarded edge with failure handling
     *   - from: "validate"
     *     to: "process"
     *     guard: "DataValidGuard"
     *     onFailure:
     *       strategy: "SKIP"        # Try next edge if guard fails
     *   
     *   # Alternative routing with retry
     *   - from: "process"
     *     to: "finalize"
     *     guard: "ProcessSuccessGuard"
     *     onFailure:
     *       strategy: "RETRY"
     *       attempts: 3
     *       delay: 5000
     *   
     *   # Conditional edge
     *   - from: "process"
     *     to: "rollback"
     *     condition: "payment_failed"
     *     kind: "error_handling"
     *   
     *   # Fallback edge (no guard)
     *   - from: "validate"
     *     to: "defaultProcessing"
     * </pre>
     * 
     * @see OnFailure for guard failure handling strategies
     */
    public static class EdgeDef {
        /** 
         * Source step name for this transition.
         * Must reference an existing step or be a terminal state.
         */
        public String from;
        
        /** 
         * Target step name for this transition.
         * Can be a step name, "SUCCESS", or "FAILURE" for terminal states.
         */
        public String to;
        
        /** 
         * Optional guard name that must evaluate to true for transition.
         * Guards are evaluated in edge declaration order.
         */
        public String guard;
        
        /** 
         * Alternative to guard - a simple string condition.
         * Used for basic conditional routing without guard components.
         */
        public String condition;
        
        /** 
         * Edge type classification for documentation and tooling.
         * Common values: "normal", "error_handling", "retry", "fallback".
         */
        public String kind = "normal";
        
        /** 
         * Strategy for handling guard evaluation failures.
         * Defines what happens when the guard returns false.
         */
        public OnFailure onFailure;
    }
    
    /**
     * Retry configuration for automatic step execution retry on failures.
     * 
     * <p>RetryConfig defines the policy for automatically retrying failed step executions.
     * It supports various backoff strategies, conditional retry logic, and attempt limiting.
     * 
     * <p><strong>Retry Strategies:</strong>
     * <ul>
     *   <li><strong>Fixed Delay:</strong> Constant delay between attempts</li>
     *   <li><strong>Exponential Backoff:</strong> Increasing delays with multiplier</li>
     *   <li><strong>Conditional:</strong> Retry only when guard condition passes</li>
     * </ul>
     * 
     * <p><strong>Configuration Examples:</strong>
     * <pre>
     * # Simple fixed delay retry
     * retry:
     *   maxAttempts: 3
     *   delay: 1000
     * 
     * # Exponential backoff with limits
     * retry:
     *   maxAttempts: 5
     *   delay: 1000          # Initial delay
     *   backoff: "EXPONENTIAL"
     *   multiplier: 2.0      # Each delay = previous * 2.0
     *   maxDelay: 30000      # Cap at 30 seconds
     * 
     * # Conditional retry based on error type
     * retry:
     *   maxAttempts: 3
     *   delay: 2000
     *   guard: "ShouldRetryGuard"  # Only retry if guard passes
     * 
     * # Alternative retry count specification
     * retry:
     *   retries: 2           # Same as maxAttempts: 3
     *   delay: 1500
     * </pre>
     * 
     * <p><strong>Backoff Calculation:</strong>
     * For exponential backoff, delay is calculated as:
     * {@code effectiveDelay = min(delay * (multiplier ^ attemptNumber), maxDelay)}
     * 
     * <p><strong>Guard Integration:</strong>
     * When a retry guard is specified, the guard is evaluated after each failure
     * to determine if retry should proceed. This enables intelligent retry logic
     * based on error type, system state, or other conditions.
     */
    public static class RetryConfig {
        /** 
         * Maximum number of execution attempts (including initial attempt).
         * Default: 3 attempts (initial + 2 retries).
         */
        public int maxAttempts = 3;
        
        /** 
         * Base delay in milliseconds between retry attempts.
         * For exponential backoff, this is the initial delay.
         * Default: 1000ms (1 second).
         */
        public long delay = 1000;
        
        /** 
         * Optional guard name for conditional retry logic.
         * If specified, guard must return true for retry to proceed.
         */
        public String guard;
        
        /** 
         * Backoff strategy name. Currently supported: "EXPONENTIAL".
         * If null or empty, uses fixed delay strategy.
         */
        public String backoff;
        
        /** 
         * Multiplier for exponential backoff strategy.
         * Each subsequent delay = previous delay * multiplier.
         * Only used when backoff="EXPONENTIAL".
         */
        public Double multiplier;
        
        /** 
         * Maximum delay cap in milliseconds for exponential backoff.
         * Prevents delays from growing beyond reasonable limits.
         * Only used when backoff="EXPONENTIAL".
         */
        public Long maxDelay;
        
        /** 
         * Alternative way to specify retry count.
         * If provided, maxAttempts is calculated as retries + 1.
         * Useful for configurations where "retry count" is more intuitive than "total attempts".
         */
        public Integer retries;
    }

    /**
     * Configuration for handling edge guard evaluation failures.
     * 
     * <p>OnFailure defines what happens when an edge guard returns false,
     * enabling sophisticated workflow routing and error recovery strategies.
     * 
     * <p><strong>Available Strategies:</strong>
     * <ul>
     *   <li><strong>STOP:</strong> Terminate workflow execution immediately (default)</li>
     *   <li><strong>SKIP:</strong> Skip this edge and try the next edge from same step</li>
     *   <li><strong>ALTERNATIVE:</strong> Redirect to a different target step</li>
     *   <li><strong>RETRY:</strong> Re-evaluate the guard after a delay</li>
     *   <li><strong>CONTINUE:</strong> Proceed with the transition despite guard failure</li>
     * </ul>
     * 
     * <p><strong>Strategy Examples:</strong>
     * <pre>
     * # Stop workflow on guard failure (default)
     * - from: "validate"
     *   to: "process"
     *   guard: "CriticalDataGuard"
     *   onFailure:
     *     strategy: "STOP"           # Workflow terminates
     * 
     * # Skip to next edge on failure
     * - from: "process"
     *   to: "premiumPath"
     *   guard: "VIPCustomerGuard"
     *   onFailure:
     *     strategy: "SKIP"           # Try next edge from "process"
     * 
     * # Redirect to alternative step
     * - from: "validate"
     *   to: "autoProcess"
     *   guard: "AutoProcessEligible"
     *   onFailure:
     *     strategy: "ALTERNATIVE"
     *     alternativeTarget: "manualReview"
     * 
     * # Retry guard evaluation
     * - from: "check"
     *   to: "proceed"
     *   guard: "ResourceAvailableGuard"
     *   onFailure:
     *     strategy: "RETRY"
     *     attempts: 5
     *     delay: 2000              # Wait 2s between retries
     * 
     * # Continue despite guard failure (rare)
     * - from: "optional"
     *   to: "cleanup"
     *   guard: "OptionalCleanupGuard"
     *   onFailure:
     *     strategy: "CONTINUE"       # Proceed to cleanup anyway
     * </pre>
     * 
     * <p><strong>Use Case Guidelines:</strong>
     * <ul>
     *   <li><strong>STOP:</strong> Critical validations that must pass</li>
     *   <li><strong>SKIP:</strong> Optional paths with fallbacks</li>
     *   <li><strong>ALTERNATIVE:</strong> Binary routing decisions</li>
     *   <li><strong>RETRY:</strong> Transient conditions (resource availability)</li>
     *   <li><strong>CONTINUE:</strong> Non-critical optional operations</li>
     * </ul>
     */
    public static class OnFailure {
        /** 
         * Strategy name for handling guard failure.
         * Must be one of: STOP, SKIP, ALTERNATIVE, RETRY, CONTINUE.
         * Default: "STOP".
         */
        public String strategy = "STOP";
        
        /** 
         * Target step name when strategy=ALTERNATIVE.
         * Workflow will transition to this step instead of the original target.
         */
        public String alternativeTarget;
        /** Used when strategy=RETRY: number of retry attempts */
        public Integer attempts;
        /** Used when strategy=RETRY: delay in milliseconds between attempts */
        public Long delay;
    }
    
    public String toFormattedYaml() {
        // Build a YAML-friendly tree using linked maps/lists to preserve order
        Map<String, Object> root = new LinkedHashMap<>();

        if (settings != null && !settings.isEmpty()) {
            root.put("settings", new LinkedHashMap<>(settings));
        }

        if (defaults != null && !defaults.isEmpty()) {
            // Ensure nested maps are linked for stable output
            Map<String, Object> defs = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : defaults.entrySet()) {
                defs.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            root.put("defaults", defs);
        }

        if (steps != null && !steps.isEmpty()) {
            Map<String, Object> stepsNode = new LinkedHashMap<>();
            // Use natural ordering for determinism in output
            List<String> names = new ArrayList<>(steps.keySet());
            Collections.sort(names);
            for (String name : names) {
                StepDef sd = steps.get(name);
                Map<String, Object> stepMap = new LinkedHashMap<>();
                if (sd.type != null && !sd.type.isEmpty()) {
                    stepMap.put("type", sd.type);
                }
                if (sd.config != null && !sd.config.isEmpty()) {
                    stepMap.put("config", new LinkedHashMap<>(sd.config));
                }
                if (sd.guards != null && !sd.guards.isEmpty()) {
                    stepMap.put("guards", new ArrayList<>(sd.guards));
                }
                if (sd.retry != null) {
                    Map<String, Object> retry = new LinkedHashMap<>();
                    retry.put("maxAttempts", sd.retry.maxAttempts);
                    retry.put("delay", sd.retry.delay);
                    if (sd.retry.guard != null && !sd.retry.guard.isEmpty()) {
                        retry.put("guard", sd.retry.guard);
                    }
                    if (sd.retry.backoff != null && !sd.retry.backoff.isEmpty()) {
                        retry.put("backoff", sd.retry.backoff);
                    }
                    if (sd.retry.multiplier != null) {
                        retry.put("multiplier", sd.retry.multiplier);
                    }
                    if (sd.retry.maxDelay != null) {
                        retry.put("maxDelay", sd.retry.maxDelay);
                    }
                    stepMap.put("retry", retry);
                }
                stepsNode.put(name, stepMap);
            }
            root.put("steps", stepsNode);
        }

        if (workflows != null && !workflows.isEmpty()) {
            Map<String, Object> wfNode = new LinkedHashMap<>();
            List<String> wfNames = new ArrayList<>(workflows.keySet());
            Collections.sort(wfNames);
            for (String wfName : wfNames) {
                WorkflowDef wf = workflows.get(wfName);
                Map<String, Object> wfMap = new LinkedHashMap<>();
                if (wf.root != null && !wf.root.isEmpty()) {
                    wfMap.put("root", wf.root);
                }
                if (wf.edges != null && !wf.edges.isEmpty()) {
                    List<Map<String, Object>> edgesList = new ArrayList<>();
                    for (EdgeDef e : wf.edges) {
                        Map<String, Object> em = new LinkedHashMap<>();
                        em.put("from", e.from);
                        em.put("to", e.to);
                        if (e.guard != null && !e.guard.isEmpty()) {
                            em.put("guard", e.guard);
                        }
                        if (e.condition != null && !e.condition.isEmpty()) {
                            em.put("condition", e.condition);
                        }
                        if (e.kind != null && !e.kind.isEmpty() && !"normal".equals(e.kind)) {
                            em.put("kind", e.kind);
                        }
                        if (e.onFailure != null) {
                            Map<String, Object> of = new LinkedHashMap<>();
                            if (e.onFailure.strategy != null && !e.onFailure.strategy.isEmpty()) {
                                of.put("strategy", e.onFailure.strategy);
                            }
                            if (e.onFailure.alternativeTarget != null && !e.onFailure.alternativeTarget.isEmpty()) {
                                of.put("alternativeTarget", e.onFailure.alternativeTarget);
                            }
                            if (e.onFailure.attempts != null) {
                                of.put("attempts", e.onFailure.attempts);
                            }
                            if (e.onFailure.delay != null) {
                                of.put("delay", e.onFailure.delay);
                            }
                            if (!of.isEmpty()) {
                                em.put("onFailure", of);
                            }
                        }
                        edgesList.add(em);
                    }
                    wfMap.put("edges", edgesList);
                }
                wfNode.put(wfName, wfMap);
            }
            root.put("workflows", wfNode);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        // indicatorIndent must be smaller than indent per SnakeYAML contract
        options.setIndicatorIndent(1);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }
}
