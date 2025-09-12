package com.stepflow.engine;

import com.stepflow.config.*;
import com.stepflow.execution.*;
import com.stepflow.component.*;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepFlow Workflow Execution Engine - The heart of the StepFlow framework.
 * 
 * <h2>Overview</h2>
 * The Engine is responsible for executing YAML-defined workflows by orchestrating Steps, Guards, 
 * and managing execution flow through complex decision trees. It supports sophisticated branching 
 * logic, failure handling strategies, and retry mechanisms.
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li><strong>Workflow Orchestration:</strong> Executes workflows defined in {@link FlowConfig.WorkflowDef} using directed graphs</li>
 *   <li><strong>Component Management:</strong> Resolves and instantiates Step/Guard components via {@link ComponentScanner}</li>
 *   <li><strong>Dependency Injection:</strong> Injects configuration, context data, and settings via {@link DependencyInjector}</li>
 *   <li><strong>Decision Making:</strong> Evaluates step-level and edge-level guards to determine execution flow</li>
 *   <li><strong>Error Handling:</strong> Supports multiple failure strategies (STOP, SKIP, ALTERNATIVE, RETRY, CONTINUE)</li>
 *   <li><strong>Safety Guarantees:</strong> Detects cycles, dead-ends, and prevents infinite loops</li>
 * </ul>
 *
 * <h2>Execution Model</h2>
 * 
 * <h3>1. Simple Linear Flow</h3>
 * <pre>
 * validate → process → notify → SUCCESS
 * </pre>
 * Engine walks sequentially through each step until reaching a terminal state (SUCCESS/FAILURE).
 *
 * <h3>2. Conditional Branching (Single Edge per Step)</h3>
 * <pre>
 * validate → process [if PaymentReady] → SUCCESS
 *         → FAILURE [if InvalidOrder]
 * </pre>
 * Engine evaluates guards and takes the first path where guard passes (or no guard exists).
 *
 * <h3>3. Complex Multi-Edge Scenarios with Validation</h3>
 * <pre>
 * enrich → charge [if PaymentReady] onFailure: SKIP
 *       → audit [if ShouldAudit] onFailure: SKIP
 *       → fallback [default - no guard, MUST BE LAST]
 * </pre>
 * <strong>Edge Evaluation Order:</strong> Edges are evaluated in YAML declaration order. 
 * The first edge whose guard passes (or has no guard) is selected. Subsequent edges are ignored.
 * 
 * <strong>Validation Rules:</strong> The Engine enforces that:
 * <ul>
 *   <li>Each step can have at most one default (no-guard) edge</li>
 *   <li>Default edges must be declared last to prevent unreachable edges</li>
 *   <li>All edges after a default edge are considered unreachable and cause validation failure</li>
 * </ul>
 *
 * <h3>4. Advanced Failure Handling</h3>
 * <pre>
 * charge → SUCCESS [default success path]
 *       → FAILURE [if HardFailureGuard] onFailure: STOP
 *       → fallback [if SoftFailureGuard] onFailure: ALTERNATIVE → notify
 *       → retry [if TransientFailure] onFailure: RETRY attempts=3, delay=500ms
 * </pre>
 *
 * <h2>Guard System</h2>
 * 
 * <h3>Step-Level Guards</h3>
 * <pre>
 * steps:
 *   processPayment:
 *     type: "chargeStep"
 *     guards: ["ValidCard", "SufficientFunds"]  # ALL must pass
 * </pre>
 * <strong>Behavior:</strong> If ANY step-level guard fails, the step is SKIPPED (not executed), 
 * and the engine continues routing from the same node using the available edges.
 *
 * <h3>Edge-Level Guards</h3>
 * <pre>
 * edges:
 *   - from: "process"
 *     to: "charge"
 *     guard: "PaymentReady"      # Single guard per edge
 *     onFailure:
 *       strategy: "ALTERNATIVE"
 *       alternativeTarget: "manual_review"
 * </pre>
 * <strong>Behavior:</strong> Controls which outgoing transition is taken. First matching guard wins.
 *
 * <h2>Failure Handling Strategies</h2>
 * 
 * When an edge guard fails, the configured onFailure strategy determines the next action:
 *
 * <ul>
 *   <li><strong>STOP (default):</strong> Terminate workflow with FAILURE status</li>
 *   <li><strong>SKIP:</strong> Ignore this edge and try the next available edge</li>
 *   <li><strong>CONTINUE:</strong> Ignore guard failure and proceed to target step anyway</li>
 *   <li><strong>ALTERNATIVE:</strong> Redirect to alternativeTarget step instead of original target</li>
 *   <li><strong>RETRY:</strong> Re-evaluate guard up to 'attempts' times with 'delay' between retries</li>
 * </ul>
 *
 * <h2>Retry Mechanisms</h2>
 * 
 * <h3>Step-Level Retry (Engine-Driven)</h3>
 * <pre>
 * steps:
 *   unstableService:
 *     type: "apiCallStep"
 *     retry:
 *       maxAttempts: 3
 *       delay: 1000
 *       guard: "ShouldRetry"  # Engine evaluates this guard between attempts
 * </pre>
 * Engine retries step execution when retry.guard is defined and step fails.
 *
 * <h3>Edge-Level Retry (Guard Re-evaluation)</h3>
 * <pre>
 * edges:
 *   - from: "check"
 *     to: "proceed"
 *     guard: "ResourceAvailable"
 *     onFailure:
 *       strategy: "RETRY"
 *       attempts: 5
 *       delay: 2000
 * </pre>
 * Engine re-evaluates the edge guard multiple times before giving up.
 *
 * <h2>Execution Flow Examples</h2>
 * 
 * <h3>Example 1: E-commerce Checkout with Multiple Paths</h3>
 * <pre>
 * checkout workflow:
 *   validate → enrich → charge → SUCCESS
 *           ↘       ↗       ↘
 *            FAILURE      fallback → notify → SUCCESS
 * 
 * Edge evaluation from 'enrich':
 *   1. enrich → charge [if PaymentReady] ✓ SELECTED
 *   2. enrich → audit [if ShouldAudit] ✗ SKIPPED (PaymentReady passed first)
 *   3. enrich → fallback [no guard] ✗ SKIPPED (PaymentReady passed first)
 * </pre>
 *
 * <h3>Example 2: Guard Failure with SKIP Strategy</h3>
 * <pre>
 * Edge evaluation from 'process':
 *   1. process → premium [if VipCustomer] ✗ FAILED → onFailure: SKIP
 *   2. process → standard [if ValidOrder] ✓ SELECTED
 *   3. process → reject [no guard] ✗ SKIPPED (ValidOrder passed)
 * </pre>
 *
 * <h3>Example 3: ALTERNATIVE Strategy</h3>
 * <pre>
 * Edge evaluation from 'payment':
 *   1. payment → charge [if CreditCardValid] ✗ FAILED 
 *      → onFailure: ALTERNATIVE → manual_review ✓ REDIRECTED
 * </pre>
 *
 * <h2>Cycle Detection</h2>
 * Engine tracks visited steps and immediately fails if a cycle is detected:
 * <pre>
 * A → B → C → A  [CYCLE DETECTED - workflow fails]
 * </pre>
 *
 * <h2>Dead-End Detection</h2>
 * If a step has no outgoing edges and isn't terminal (SUCCESS/FAILURE), workflow fails:
 * <pre>
 * validate → process → [no outgoing edges] = DEAD-END
 * </pre>
 *
 * <h2>Configuration Integration</h2>
 * 
 * <h3>Settings and Defaults</h3>
 * <pre>
 * settings:
 *   notifications:
 *     template: "order-confirmation"
 * 
 * defaults:
 *   step:
 *     timeout_ms: 3000
 *   validateStep:
 *     strict_mode: true
 * </pre>
 * Engine merges global settings with step-specific config during dependency injection.
 *
 * <h3>Template Variables</h3>
 * <pre>
 * steps:
 *   notify:
 *     config:
 *       template: "{{ settings.notifications.template }}"
 * </pre>
 * Templates are resolved during configuration loading, not at runtime.
 *
 * <h2>Thread Safety</h2>
 * Engine instances are NOT thread-safe. Each workflow execution should use a separate 
 * ExecutionContext. Multiple engines can share the same FlowConfig safely.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(V + E) where V=steps, E=edges</li>
 *   <li><strong>Space Complexity:</strong> O(V) for cycle detection</li>
 *   <li><strong>Component Caching:</strong> Step/Guard classes are resolved once and cached</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * FlowConfig config = // loaded from YAML
 * Engine engine = new Engine(config, "com.example.steps", "com.example.guards");
 * 
 * ExecutionContext context = new ExecutionContext();
 * context.put("orderId", "ORD-12345");
 * context.put("amount", 99.99);
 * 
 * StepResult result = engine.run("checkout", context);
 * if (result.isSuccess()) {
 *     System.out.println("Order processed: " + result.context.get("confirmationId"));
 * } else {
 *     System.err.println("Order failed: " + result.message);
 * }
 * </pre>
 *
 * @see FlowConfig for workflow definition structure
 * @see ExecutionContext for shared data management  
 * @see Step for step implementation interface
 * @see Guard for conditional logic interface
 * @see ComponentScanner for component discovery
 * @see DependencyInjector for configuration injection
 */
public class Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(Engine.class);

    private final FlowConfig config;
    private final ComponentScanner componentScanner;
    private final DependencyInjector dependencyInjector;
    
    public Engine(FlowConfig config, String... scanPackages) {
        this.config = config;
        this.componentScanner = new ComponentScanner();
        this.dependencyInjector = new DependencyInjector();
        
        if (scanPackages != null && scanPackages.length > 0) {
            componentScanner.scanPackages(scanPackages);
        }
        
        // Validate workflow edge configurations
        validateWorkflowEdges();
    }
    
    /**
     * Executes a complete workflow by name, orchestrating step execution and managing flow transitions.
     * 
     * <p>This is the primary entry point for workflow execution. It validates the workflow exists,
     * initializes execution state, and delegates to {@link #executeWorkflow} for the actual orchestration.
     * 
     * <h3>Execution Process:</h3>
     * <ol>
     *   <li>Validates workflow exists in {@link FlowConfig#workflows}</li>
     *   <li>Logs workflow start with root step information</li>
     *   <li>Delegates to {@link #executeWorkflow} for step-by-step execution</li>
     *   <li>Returns final result with accumulated context data</li>
     * </ol>
     * 
     * <h3>Result Interpretation:</h3>
     * <ul>
     *   <li><strong>SUCCESS:</strong> Workflow completed successfully, reached terminal state</li>
     *   <li><strong>FAILURE:</strong> Workflow failed due to step failure, guard failure, cycle, or dead-end</li>
     *   <li><strong>Context Data:</strong> Accumulated data from all executed steps available in result.context</li>
     * </ul>
     * 
     * <h3>Error Scenarios:</h3>
     * <ul>
     *   <li>Workflow not found in configuration → immediate FAILURE</li>
     *   <li>Step execution failure → propagated FAILURE with step details</li>
     *   <li>Cycle detection → FAILURE with cycle information</li>
     *   <li>Dead-end reached → FAILURE with unreachable step details</li>
     * </ul>
     * 
     * <h3>Example Usage:</h3>
     * <pre>
     * ExecutionContext context = new ExecutionContext();
     * context.put("userId", "12345");
     * context.put("amount", 99.99);
     * 
     * StepResult result = engine.run("checkout", context);
     * 
     * if (result.isSuccess()) {
     *     String orderId = (String) result.context.get("orderId");
     *     logger.info("Order {} processed successfully", orderId);
     * } else {
     *     logger.error("Checkout failed: {}", result.message);
     *     // Access failure context for debugging
     *     Map&lt;String, Object&gt; errorDetails = result.context;
     * }
     * </pre>
     * 
     * @param workflowName the name of the workflow in {@link FlowConfig#workflows}. Must not be null or empty.
     * @param context execution data shared among steps. Initial context data is preserved and augmented 
     *                with step results. Must not be null.
     * @return a {@link StepResult} representing overall workflow outcome:
     *         <ul>
     *           <li>SUCCESS status with accumulated context if workflow completes normally</li>
     *           <li>FAILURE status with error message and partial context if workflow fails</li>
     *         </ul>
     * @throws RuntimeException if workflow execution encounters unrecoverable errors (component scanning, dependency injection)
     * @see #executeWorkflow for detailed execution mechanics
     */
    public StepResult run(String workflowName, ExecutionContext context) {
        FlowConfig.WorkflowDef workflow = config.workflows.get(workflowName);
        if (workflow == null) {
            LOGGER.warn("Workflow not found: {}. Available: {}", workflowName, config.workflows.keySet());
            return new StepResult(StepResult.Status.FAILURE, "Workflow not found: " + workflowName);
        }
        LOGGER.info("Starting workflow: {} (root={})", workflowName, workflow.root);
        return executeWorkflow(workflow, context);
    }

    public FlowConfig getConfig() {
        return config;
    }
    
    /**
     * Core workflow execution engine that orchestrates step-by-step progression through the workflow graph.
     * 
     * <p>This method implements the main execution loop, managing state transitions, cycle detection,
     * and coordinating between step execution and flow navigation. It continues until reaching a 
     * terminal state (SUCCESS/FAILURE) or encountering an error condition.
     * 
     * <h3>Execution Algorithm:</h3>
     * <ol>
     *   <li><strong>Initialize:</strong> Start from workflow.root, prepare cycle detection</li>
     *   <li><strong>Main Loop:</strong> While current step is not terminal:
     *     <ul>
     *       <li>Check for cycles (visited set)</li>
     *       <li>Execute current step via {@link #executeStep}</li>
     *       <li>Merge step result context into main context</li>
     *       <li>Find next step via {@link #findNextStep}</li>
     *       <li>Handle transition result (NEXT/FAIL/NONE)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Terminate:</strong> Return SUCCESS when terminal reached</li>
     * </ol>
     * 
     * <h3>State Management:</h3>
     * <ul>
     *   <li><strong>Current Step:</strong> Tracks the currently executing step</li>
     *   <li><strong>Visited Set:</strong> Prevents infinite cycles by tracking visited steps</li>
     *   <li><strong>Context Accumulation:</strong> Merges results from each step into shared context</li>
     *   <li><strong>Last Visited:</strong> Used for transition selection and error reporting</li>
     * </ul>
     * 
     * <h3>Termination Conditions:</h3>
     * <ul>
     *   <li><strong>SUCCESS Terminal:</strong> Reached "SUCCESS" step → workflow succeeds</li>
     *   <li><strong>FAILURE Terminal:</strong> Reached "FAILURE" step → workflow succeeds (controlled failure)</li>
     *   <li><strong>Step Failure:</strong> Any step returns FAILURE status → workflow fails immediately</li>
     *   <li><strong>Cycle Detection:</strong> Revisited a step → workflow fails with cycle error</li>
     *   <li><strong>No Transition:</strong> No eligible outgoing edge → workflow fails with dead-end error</li>
     *   <li><strong>Transition Failure:</strong> Edge guard handling returns FAIL → workflow fails</li>
     * </ul>
     * 
     * <h3>Error Propagation:</h3>
     * <pre>
     * Step Execution Flow:
     * 
     * executeStep() → SUCCESS → findNextStep() → NEXT → continue
     *               → FAILURE → return FAILURE (terminate)
     *               
     * findNextStep() → NEXT → transition to next step
     *                → FAIL → return FAILURE (terminate)  
     *                → NONE → return FAILURE (dead-end)
     * </pre>
     * 
     * <h3>Context Data Flow:</h3>
     * <pre>
     * Initial Context → Step 1 → Result Context → Merged Context
     *                         → Step 2 → Result Context → Merged Context
     *                         → Step N → Result Context → Final Context
     * </pre>
     * 
     * <h3>Cycle Detection Example:</h3>
     * <pre>
     * Workflow: A → B → C → A
     * 
     * Execution:
     * 1. Execute A, visited = {A}
     * 2. Execute B, visited = {A, B}  
     * 3. Execute C, visited = {A, B, C}
     * 4. Attempt A, A in visited = CYCLE DETECTED → FAILURE
     * </pre>
     * 
     * <h3>Multi-Edge Transition Example:</h3>
     * <pre>
     * From step 'process' with edges:
     * 1. process → premium [if VIPCustomer] 
     * 2. process → standard [if ValidOrder]
     * 3. process → reject [no guard]
     * 
     * findNextStep() evaluates in order:
     * - If VIPCustomer passes → go to 'premium' 
     * - Else if ValidOrder passes → go to 'standard'
     * - Else → go to 'reject' (no guard always passes)
     * </pre>
     * 
     * @param workflow the workflow definition containing root step and edge definitions. Must not be null.
     * @param context shared execution context that accumulates data from each step. Must not be null.
     * @return SUCCESS status with accumulated context if terminal reached normally,
     *         FAILURE status with error details if execution fails due to:
     *         <ul>
     *           <li>Cycle detection</li>
     *           <li>Step execution failure</li>
     *           <li>No eligible transitions (dead-end)</li>
     *           <li>Edge guard failure handling</li>
     *         </ul>
     * @see #executeStep for individual step execution details
     * @see #findNextStep for transition selection mechanics  
     * @see #isTerminal for terminal state definitions
     */
    private StepResult executeWorkflow(FlowConfig.WorkflowDef workflow, ExecutionContext context) {
        String current = workflow.root;
        String lastVisited = null;
        Set<String> visited = new HashSet<>();

        while (current != null && !isTerminal(current)) {
            if (visited.contains(current)) {
                LOGGER.error("Cycle detected at step: {}", current);
                return StepResult.failure("Circular dependency detected at: " + current);
            }
            visited.add(current);

            LOGGER.debug("Executing step: {}", current);
            StepResult result = executeStep(current, context);
            if (result.status == StepResult.Status.FAILURE) {
                LOGGER.error("Step failed: {} message={} contextKeys={}", current, result.message, result.context.keySet());
                return result;
            }

            context.putAll(result.context);

            lastVisited = current;
            NextSelection sel = findNextStep(workflow, lastVisited, context);
            if (sel.action == NextSelection.Kind.FAIL) {
                String msg = sel.failureMessage != null ? sel.failureMessage :
                        ("Transition failed from step: " + lastVisited);
                LOGGER.warn("Transition failure after step {}: {}", lastVisited, msg);
                return StepResult.failure(msg);
            }
            if (sel.action == NextSelection.Kind.NEXT) {
                LOGGER.debug("Transition: {} -> {}", lastVisited, sel.next);
                current = sel.next;
            } else if (sel.action == NextSelection.Kind.NONE) {
                LOGGER.error("No eligible transition from step: {}", lastVisited);
                return StepResult.failure("No eligible transition from step: " + lastVisited);
            } else { // SKIP shouldn't leak here; treat as no eligible
                LOGGER.error("Unexpected SKIP at selection phase from step: {}", lastVisited);
                return StepResult.failure("No eligible transition from step: " + lastVisited);
            }
        }

        LOGGER.info("Workflow completed successfully. Terminal reached or no steps.");
        return StepResult.success(context);
    }
    
    /**
     * Executes a single step with comprehensive guard evaluation, dependency injection, and retry logic.
     * 
     * <p>This method handles the complete lifecycle of individual step execution, including validation,
     * guard evaluation, component instantiation, dependency injection, and optional retry mechanisms.
     * It serves as the bridge between the workflow orchestration layer and the actual business logic.
     * 
     * <h3>Execution Sequence:</h3>
     * <ol>
     *   <li><strong>Step Definition Lookup:</strong> Find step configuration in {@link FlowConfig#steps}</li>
     *   <li><strong>Step-Level Guard Evaluation:</strong> Check all guards in step.guards (ALL must pass)</li>
     *   <li><strong>Component Resolution:</strong> Locate Step implementation class via {@link ComponentScanner}</li>
     *   <li><strong>Component Instantiation:</strong> Create new step instance using default constructor</li>
     *   <li><strong>Configuration Merging:</strong> Merge defaults with step-specific config</li>
     *   <li><strong>Dependency Injection:</strong> Inject context data, config values, and settings</li>
     *   <li><strong>Execution with Retry:</strong> Execute step with optional engine-driven retry</li>
     * </ol>
     * 
     * <h3>Step-Level Guards Behavior:</h3>
     * <pre>
     * Step Configuration:
     * validate:
     *   type: "orderValidateStep" 
     *   guards: ["ValidCustomer", "ValidPayment", "InStock"]
     * 
     * Guard Evaluation:
     * - ValidCustomer.evaluate(context) → true
     * - ValidPayment.evaluate(context) → true  
     * - InStock.evaluate(context) → false ❌
     * 
     * Result: Step SKIPPED (returns SUCCESS with skip message)
     * Engine continues with routing from same node using available edges.
     * </pre>
     * 
     * <h3>Configuration Merging Process:</h3>
     * <pre>
     * 1. Global Defaults:     defaults.step.*
     * 2. Type-Specific:       defaults.orderValidateStep.*
     * 3. Step-Specific:       steps.validate.config.*
     * 
     * Final Config = step.config overrides type-specific overrides global
     * </pre>
     * 
     * <h3>Dependency Injection Sources:</h3>
     * <ul>
     *   <li><strong>@Inject:</strong> ExecutionContext first, then step config</li>
     *   <li><strong>Field Matching:</strong> Context values by field name (non-annotated fields)</li>
     *   <li><strong>Config Properties:</strong> Step config values by property name</li>
     *   <li><strong>@ConfigValue:</strong> Step config first, then global settings path</li>
     * </ul>
     * 
     * <h3>Engine-Driven Retry Logic:</h3>
     * 
     * <h4>Retry WITH Guard (Conditional Retry):</h4>
     * <pre>
     * Step with Retry Guard:
     * unstableAPI:
     *   type: "apiCallStep"
     *   retry:
     *     maxAttempts: 3
     *     delay: 1000  
     *     guard: "ShouldRetry"  # Engine evaluates this guard between attempts
     * 
     * Retry Process:
     * 1. Execute step → FAILURE
     * 2. Evaluate ShouldRetry guard → true → continue retry
     * 3. Wait 1000ms
     * 4. Execute step → FAILURE  
     * 5. Evaluate ShouldRetry guard → true → continue retry
     * 6. Wait 1000ms
     * 7. Execute step → SUCCESS → Return SUCCESS ✅
     * 
     * Alternative outcome - Guard blocks retry:
     * 1. Execute step → FAILURE
     * 2. Evaluate ShouldRetry guard → false → stop retrying
     * 3. Return FAILURE immediately (no more attempts) ❌
     * 
     * Use Case: Retry based on error type (transient vs permanent failures)
     * </pre>
     * 
     * <h4>Retry WITHOUT Guard (Unconditional Retry):</h4>
     * <pre>
     * Step with Simple Retry:
     * databaseQuery:
     *   type: "dbQueryStep"
     *   retry:
     *     maxAttempts: 5
     *     delay: 500
     *     # NO guard specified - engine always retries on failure
     * 
     * Retry Process:
     * 1. Execute step → FAILURE (database timeout)
     * 2. No guard to evaluate → proceed with retry
     * 3. Wait 500ms
     * 4. Execute step → FAILURE (still timeout)
     * 5. No guard to evaluate → proceed with retry  
     * 6. Wait 500ms
     * 7. Execute step → FAILURE (still timeout)
     * 8. No guard to evaluate → proceed with retry
     * 9. Wait 500ms
     * 10. Execute step → SUCCESS (database recovered) → Return SUCCESS ✅
     * 
     * Alternative outcome - All attempts fail:
     * 1. Execute step → FAILURE
     * 2-3. Wait 500ms, Execute → FAILURE
     * 4-5. Wait 500ms, Execute → FAILURE  
     * 6-7. Wait 500ms, Execute → FAILURE
     * 8-9. Wait 500ms, Execute → FAILURE
     * 10. Max attempts reached → Return FAILURE from last attempt ❌
     * 
     * Use Case: Retry for known transient issues (network, database timeouts)
     * </pre>
     * 
     * <h4>No Retry Configuration:</h4>
     * <pre>
     * Step without Retry:
     * validateInput:
     *   type: "validationStep"
     *   # No retry section - failures are immediate
     * 
     * Execution Process:
     * 1. Execute step → SUCCESS → Return SUCCESS ✅
     * OR
     * 1. Execute step → FAILURE → Return FAILURE immediately ❌
     * 
     * Use Case: Business logic that shouldn't be retried (validation, user input)
     * </pre>
     * 
     * <h3>Retry Configuration Decision Matrix:</h3>
     * <pre>
     * ┌─────────────────┬──────────────────┬─────────────────────────────────┐
     * │ Configuration   │ Retry Behavior   │ Use Case                        │
     * ├─────────────────┼──────────────────┼─────────────────────────────────┤
     * │ No retry config │ Never retry      │ Validation, business rules      │
     * │ retry.guard: X  │ Conditional      │ Smart retry based on error type │
     * │ retry (no guard)│ Always retry     │ Transient failures (network)    │
     * └─────────────────┴──────────────────┴─────────────────────────────────┘
     * </pre>
     * 
     * <h3>Error Scenarios:</h3>
     * <ul>
     *   <li><strong>Step Definition Missing:</strong> Returns FAILURE with "Step not found" message</li>
     *   <li><strong>Step Implementation Missing:</strong> Returns FAILURE with "Implementation not found" message</li>
     *   <li><strong>Instantiation Error:</strong> Returns FAILURE with exception details</li>
     *   <li><strong>Dependency Injection Error:</strong> Returns FAILURE with injection failure details</li>
     *   <li><strong>Step Execution Exception:</strong> Returns FAILURE with exception message</li>
     *   <li><strong>Null Result:</strong> Treated as FAILURE with "returned null result" message</li>
     * </ul>
     * 
     * <h3>Success Scenarios:</h3>
     * <ul>
     *   <li><strong>Guards Pass, Step Succeeds:</strong> Returns step's SUCCESS result with context</li>
     *   <li><strong>Guards Fail:</strong> Returns SUCCESS with "Step skipped due to guard condition" message</li>
     *   <li><strong>Retry Succeeds:</strong> Returns SUCCESS from successful retry attempt</li>
     * </ul>
     * 
     * @param stepName the name of the step in {@link FlowConfig#steps}. Must not be null.
     * @param context the current execution context containing shared data. Must not be null.
     * @return SUCCESS status if step executes successfully or is skipped due to guards,
     *         FAILURE status if step definition missing, implementation not found, 
     *         instantiation fails, dependency injection fails, or step execution fails.
     *         Result context contains any data produced by the step.
     * @throws RuntimeException if unrecoverable errors occur during component scanning or injection
     * @see #executeWithOptionalRetry for retry mechanism details
     * @see #evaluateGuards for guard evaluation logic
     * @see DependencyInjector#injectDependencies for injection details
     */
    private StepResult executeStep(String stepName, ExecutionContext context) {
        FlowConfig.StepDef stepDef = config.steps.get(stepName);
        if (stepDef == null) {
            LOGGER.error("Step definition not found: {}", stepName);
            return StepResult.failure("Step not found: " + stepName);
        }

        // Step-level guards gate step execution
        if (!evaluateGuards(stepDef.guards, context)) {
            LOGGER.debug("Step {} skipped due to guard condition(s): {}", stepName, stepDef.guards);
            return StepResult.success("Step skipped due to guard condition");
        }

        Class<? extends Step> stepClass = componentScanner.getStepClass(stepDef.type);
        if (stepClass == null) {
            LOGGER.error("Step implementation not found for type: {} (step={})", stepDef.type, stepName);
            return StepResult.failure("Step implementation not found: " + stepDef.type);
        }

        try {
            Step step = stepClass.getDeclaredConstructor().newInstance();
            Map<String, Object> effectiveConfig = buildEffectiveConfig(stepName, stepDef.config, false);
            dependencyInjector.injectDependencies(step, context, effectiveConfig, config.settings);
            return executeWithOptionalRetry(step, stepDef.retry, context);
        } catch (Exception e) {
            LOGGER.error("Step execution failed for {}: {}", stepName, e.toString());
            return StepResult.failure("Step execution failed: " + e.getMessage());
        }
    }

    /**
     * Executes a step once or with engine-driven retry.
     *
     * Behavior:
     * - No retry config: execute once
     * - Retry with guard: execute, if failed and guard passes → retry up to maxAttempts with delay
     * - Retry without guard: execute, if failed → always retry up to maxAttempts with delay
     */
    private StepResult executeWithOptionalRetry(Step step, FlowConfig.RetryConfig retry, ExecutionContext context) {
        if (retry == null) {
            StepResult result = step.execute(context);
            return result != null ? result : StepResult.failure("Step returned null result");
        }

        int attempts = 0;
        int max = computeMaxAttempts(retry);
        boolean hasGuard = retry.guard != null && !retry.guard.isEmpty();
        StepResult lastResult = null;

        while (attempts < max) {
            StepResult r = step.execute(context);
            if (r != null && r.status == StepResult.Status.SUCCESS) {
                if (attempts > 0) {
                    LOGGER.info("Step succeeded after {} attempt(s)", attempts + 1);
                }
                return r;
            }

            lastResult = (r != null) ? r : StepResult.failure("Step returned null result");
            attempts++;
            if (attempts >= max) break;

            if (hasGuard) {
                if (!evaluateSingleGuard(retry.guard, context)) {
                    LOGGER.debug("Retry guard '{}' blocked further attempts at attempt {}", retry.guard, attempts);
                    break;
                }
            }

            long delayMs = computeRetryDelay(retry, attempts); // attempts is 1-based for next retry
            if (delayMs > 0) {
                try {
                    LOGGER.debug("Retrying in {} ms (attempt {}/{})", delayMs, attempts + 1, max);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        LOGGER.warn("Step failed after {} attempt(s)", attempts);
        return lastResult != null ? lastResult : StepResult.failure("Step failed after retries");
    }

    /** Determines the total number of executions allowed based on retry config. */
    private int computeMaxAttempts(FlowConfig.RetryConfig retry) {
        if (retry == null) return 1;
        if (retry.retries != null && retry.retries >= 0) {
            // retries means additional attempts after the initial
            return Math.max(1, retry.retries + 1);
        }
        return Math.max(1, retry.maxAttempts);
    }

    /** Computes delay for the next retry attempt (attemptIndex is 1-based for the next try). */
    private long computeRetryDelay(FlowConfig.RetryConfig retry, int attemptIndex) {
        long base = Math.max(0L, retry.delay);
        String strategy = retry.backoff != null ? retry.backoff.trim().toUpperCase() : null;
        if ("EXPONENTIAL".equals(strategy)) {
            double multiplier = (retry.multiplier != null && retry.multiplier > 0.0) ? retry.multiplier : 2.0;
            double factor = Math.pow(multiplier, Math.max(0, attemptIndex - 1));
            long computed = (long) Math.min((double) Long.MAX_VALUE, Math.round(base * factor));
            if (retry.maxDelay != null) {
                computed = Math.min(computed, Math.max(0L, retry.maxDelay));
            }
            return computed;
        }
        return base;
    }
    
    /**
     * Evaluates a list of guard names; returns false if any evaluate to false.
     */
    private boolean evaluateGuards(List<String> guardNames, ExecutionContext context) {
        if (guardNames == null || guardNames.isEmpty()) {
            return true;
        }
        for (String guardName : guardNames) {
            if (!evaluateSingleGuard(guardName, context)) {
                LOGGER.debug("Guard '{}' returned false", guardName);
                return false;
            }
        }
        return true;
    }
    
    /**
     * Determines the next step transition by evaluating all outgoing edges from the current step.
     * 
     * <p>This method implements the core decision-making logic for workflow navigation. It examines
     * all edges originating from the current step, evaluates their guards in declaration order,
     * and applies failure handling strategies when guards fail. This is where complex multi-edge
     * scenarios are resolved into a single transition decision.
     * 
     * <h3>Edge Selection Algorithm:</h3>
     * <ol>
     *   <li><strong>Collect Outgoing Edges:</strong> Find all edges where edge.from == currentStep</li>
     *   <li><strong>Evaluate In Order:</strong> Process edges in YAML declaration order</li>
     *   <li><strong>For Each Edge:</strong>
     *     <ul>
     *       <li>If no guard → SELECT this edge immediately (first match wins)</li>
     *       <li>If guard passes → SELECT this edge immediately</li> 
     *       <li>If guard fails → Apply onFailure strategy</li>
     *     </ul>
     *   </li>
     *   <li><strong>No Selection Made:</strong> Return NONE (triggers dead-end error)</li>
     * </ol>
     * 
     * <h3>Multi-Edge Evaluation Example:</h3>
     * <pre>
     * From step 'checkout' with edges:
     * edges:
     *   - from: checkout
     *     to: premium_flow  
     *     guard: VIPCustomer
     *     onFailure: { strategy: SKIP }
     *   - from: checkout  
     *     to: standard_flow
     *     guard: ValidPayment
     *     onFailure: { strategy: ALTERNATIVE, alternativeTarget: manual_review }
     *   - from: checkout
     *     to: basic_flow
     *     # no guard - always matches
     * 
     * Evaluation Process:
     * 1. VIPCustomer.evaluate() → false → onFailure: SKIP → try next edge
     * 2. ValidPayment.evaluate() → true → SELECT standard_flow ✓
     * 3. basic_flow edge → SKIPPED (previous edge already selected)
     * 
     * Result: NextSelection.next("standard_flow")
     * </pre>
     * 
     * <h3>Guard Failure Handling Strategies:</h3>
     * 
     * <h4>STOP Strategy (Default):</h4>
     * <pre>
     * guard: PaymentValid
     * onFailure: { strategy: STOP }
     * 
     * PaymentValid fails → Return NextSelection.fail("Edge guard failed with STOP")
     * → Workflow terminates with FAILURE
     * </pre>
     * 
     * <h4>SKIP Strategy:</h4>
     * <pre>
     * guard: OptionalFeature
     * onFailure: { strategy: SKIP }
     * 
     * OptionalFeature fails → Return NextSelection.skip()
     * → Continue evaluating next edge in list
     * </pre>
     * 
     * <h4>CONTINUE Strategy:</h4>
     * <pre>
     * guard: BestEffortCheck  
     * onFailure: { strategy: CONTINUE }
     * 
     * BestEffortCheck fails → Return NextSelection.next(edge.to)
     * → Proceed to target step despite guard failure
     * </pre>
     * 
     * <h4>ALTERNATIVE Strategy:</h4>
     * <pre>
     * guard: AutoProcess
     * onFailure: 
     *   strategy: ALTERNATIVE
     *   alternativeTarget: manual_review
     * 
     * AutoProcess fails → Return NextSelection.next("manual_review")
     * → Redirect to fallback step instead of original target
     * </pre>
     * 
     * <h4>RETRY Strategy:</h4>
     * <pre>
     * guard: ResourceAvailable
     * onFailure:
     *   strategy: RETRY
     *   attempts: 3
     *   delay: 500
     * 
     * ResourceAvailable fails → Retry guard evaluation up to 3 times
     * → If any retry succeeds: Return NextSelection.next(edge.to)
     * → If all retries fail: Return NextSelection.fail("Guard failed after retry")
     * </pre>
     * 
     * <h3>Complete Workflow Scenario Walkthrough:</h3>
     * <pre>
     * E-Commerce Order Processing Workflow with Multiple Decision Points:
     * 
     * steps:
     *   validate:        type: "validateStep"
     *   fraud_check:     type: "fraudAnalysisStep" 
     *   premium_process: type: "vipProcessingStep"
     *   express_process: type: "expressShippingStep"
     *   standard_process:type: "standardProcessingStep"
     *   payment:         type: "paymentStep"
     *   inventory:       type: "inventoryStep"
     *   shipping:        type: "shippingStep"
     *   notification:    type: "notificationStep"
     * 
     * workflows:
     *   order_processing:
     *     root: validate
     *     edges:
     *       # From validate - Customer classification
     *       - from: validate
     *         to: fraud_check
     *         guard: HighRiskOrder
     *         onFailure: { strategy: SKIP }
     *       - from: validate  
     *         to: premium_process
     *         guard: VIPCustomer
     *         onFailure: { strategy: SKIP }
     *       - from: validate
     *         to: express_process  
     *         guard: ExpressShipping
     *         onFailure: { strategy: ALTERNATIVE, alternativeTarget: standard_process }
     *       - from: validate
     *         to: standard_process
     *         # fallback - no guard
     *       
     *       # From fraud_check - Security analysis
     *       - from: fraud_check
     *         to: premium_process
     *         guard: FraudClearVIP
     *         onFailure: { strategy: SKIP }
     *       - from: fraud_check
     *         to: standard_process
     *         guard: FraudClearStandard
     *         onFailure: { strategy: STOP }
     *       
     *       # From premium_process - VIP path
     *       - from: premium_process
     *         to: payment
     *         guard: VIPPaymentReady
     *         onFailure: { strategy: RETRY, attempts: 2, delay: 1000 }
     *       
     *       # From express_process - Express path
     *       - from: express_process
     *         to: inventory
     *         guard: ExpressInventoryCheck
     *         onFailure: { strategy: ALTERNATIVE, alternativeTarget: standard_process }
     *       
     *       # From standard_process - Standard path
     *       - from: standard_process
     *         to: payment
     *         guard: StandardPaymentReady
     *         onFailure: { strategy: CONTINUE }
     *       
     *       # From payment - Payment processing
     *       - from: payment
     *         to: inventory
     *         guard: PaymentSuccess
     *         onFailure: { strategy: STOP }
     *       
     *       # From inventory - Inventory management
     *       - from: inventory
     *         to: shipping
     *         guard: InStock
     *         onFailure: { strategy: STOP }
     *       
     *       # From shipping - Final steps
     *       - from: shipping
     *         to: notification
     *         # no guard - always proceed
     *       
     *       # From notification - Completion
     *       - from: notification
     *         to: SUCCESS
     *         # no guard - workflow completes
     * 
     * 
     * COMPLETE EXECUTION SCENARIOS:
     * =============================
     * 
     * Scenario 1: VIP Customer with High-Risk Order
     * ---------------------------------------------
     * Context: { customerId: "VIP123", riskScore: 85, vipStatus: true }
     * 
     * Step 1: validate
     *   findNextStep("validate"):
     *     1. HighRiskOrder.evaluate() → true (riskScore > 80) → SELECT fraud_check ✓
     *     2. VIPCustomer edge → SKIPPED (HighRiskOrder selected first)
     *     → TRANSITION: validate → fraud_check
     * 
     * Step 2: fraud_check  
     *   findNextStep("fraud_check"):
     *     1. FraudClearVIP.evaluate() → true (manual review passed) → SELECT premium_process ✓
     *     2. FraudClearStandard edge → SKIPPED
     *     → TRANSITION: fraud_check → premium_process
     * 
     * Step 3: premium_process
     *   findNextStep("premium_process"):
     *     1. VIPPaymentReady.evaluate() → false (processing delay)
     *        onFailure: RETRY attempts=2, delay=1000
     *        Retry 1: VIPPaymentReady.evaluate() → false
     *        Retry 2: VIPPaymentReady.evaluate() → true → SELECT payment ✓
     *     → TRANSITION: premium_process → payment
     * 
     * Step 4: payment
     *   findNextStep("payment"):
     *     1. PaymentSuccess.evaluate() → true → SELECT inventory ✓
     *     → TRANSITION: payment → inventory
     * 
     * Step 5: inventory
     *   findNextStep("inventory"):
     *     1. InStock.evaluate() → true → SELECT shipping ✓
     *     → TRANSITION: inventory → shipping
     * 
     * Step 6: shipping
     *   findNextStep("shipping"):
     *     1. No guard → SELECT notification ✓
     *     → TRANSITION: shipping → notification
     * 
     * Step 7: notification
     *   findNextStep("notification"):
     *     1. No guard → SELECT SUCCESS ✓
     *     → TRANSITION: notification → SUCCESS
     * 
     * WORKFLOW COMPLETED: SUCCESS ✅
     * Path: validate → fraud_check → premium_process → payment → inventory → shipping → notification → SUCCESS
     * 
     * 
     * Scenario 2: Express Shipping Customer - Inventory Issue
     * ------------------------------------------------------
     * Context: { customerId: "CUST456", expressShipping: true, vipStatus: false }
     * 
     * Step 1: validate
     *   findNextStep("validate"):
     *     1. HighRiskOrder.evaluate() → false → onFailure: SKIP → continue
     *     2. VIPCustomer.evaluate() → false → onFailure: SKIP → continue
     *     3. ExpressShipping.evaluate() → true → SELECT express_process ✓
     *     4. standard_process edge → SKIPPED
     *     → TRANSITION: validate → express_process
     * 
     * Step 2: express_process
     *   findNextStep("express_process"):
     *     1. ExpressInventoryCheck.evaluate() → false (express stock unavailable)
     *        onFailure: ALTERNATIVE alternativeTarget=standard_process
     *        → SELECT standard_process ✓
     *     → TRANSITION: express_process → standard_process (redirected)
     * 
     * Step 3: standard_process
     *   findNextStep("standard_process"):
     *     1. StandardPaymentReady.evaluate() → false (payment method expired)
     *        onFailure: CONTINUE → SELECT payment ✓ (proceed despite failure)
     *     → TRANSITION: standard_process → payment
     * 
     * Step 4: payment
     *   findNextStep("payment"):
     *     1. PaymentSuccess.evaluate() → true (backup payment worked) → SELECT inventory ✓
     *     → TRANSITION: payment → inventory
     * 
     * Step 5: inventory
     *   findNextStep("inventory"):
     *     1. InStock.evaluate() → true → SELECT shipping ✓
     *     → TRANSITION: inventory → shipping
     * 
     * Step 6: shipping
     *   findNextStep("shipping"):
     *     1. No guard → SELECT notification ✓
     *     → TRANSITION: shipping → notification
     * 
     * Step 7: notification
     *   findNextStep("notification"):
     *     1. No guard → SELECT SUCCESS ✓
     *     → TRANSITION: notification → SUCCESS
     * 
     * WORKFLOW COMPLETED: SUCCESS ✅
     * Path: validate → express_process → standard_process → payment → inventory → shipping → notification → SUCCESS
     * 
     * 
     * Scenario 3: Standard Customer - Payment Failure
     * -----------------------------------------------
     * Context: { customerId: "CUST789", vipStatus: false, expressShipping: false }
     * 
     * Step 1: validate
     *   findNextStep("validate"):
     *     1. HighRiskOrder.evaluate() → false → onFailure: SKIP → continue
     *     2. VIPCustomer.evaluate() → false → onFailure: SKIP → continue
     *     3. ExpressShipping.evaluate() → false → onFailure: ALTERNATIVE alternativeTarget=standard_process
     *        → SELECT standard_process ✓
     *     4. standard_process edge → SKIPPED (ALTERNATIVE already selected)
     *     → TRANSITION: validate → standard_process (via ALTERNATIVE)
     * 
     * Step 2: standard_process
     *   findNextStep("standard_process"):
     *     1. StandardPaymentReady.evaluate() → true → SELECT payment ✓
     *     → TRANSITION: standard_process → payment
     * 
     * Step 3: payment
     *   findNextStep("payment"):
     *     1. PaymentSuccess.evaluate() → false (insufficient funds)
     *        onFailure: STOP → FAIL ❌
     *     → RETURN: NextSelection.fail("Edge guard failed with STOP for edge: payment → inventory")
     * 
     * WORKFLOW TERMINATED: FAILURE ❌
     * Path: validate → standard_process → payment → FAILURE
     * Reason: Payment failed and onFailure strategy was STOP
     * 
     * 
     * Scenario 4: Fraud Detection Blocks Standard Processing
     * ----------------------------------------------------
     * Context: { customerId: "SUSP999", riskScore: 95, vipStatus: false }
     * 
     * Step 1: validate
     *   findNextStep("validate"):
     *     1. HighRiskOrder.evaluate() → true → SELECT fraud_check ✓
     *     → TRANSITION: validate → fraud_check
     * 
     * Step 2: fraud_check
     *   findNextStep("fraud_check"):
     *     1. FraudClearVIP.evaluate() → false → onFailure: SKIP → continue
     *     2. FraudClearStandard.evaluate() → false (suspicious activity detected)
     *        onFailure: STOP → FAIL ❌
     *     → RETURN: NextSelection.fail("Edge guard failed with STOP for edge: fraud_check → standard_process")
     * 
     * WORKFLOW TERMINATED: FAILURE ❌
     * Path: validate → fraud_check → FAILURE
     * Reason: Fraud check failed and onFailure strategy was STOP
     * 
     * 
     * Key Insights from Complete Scenarios:
     * ====================================
     * 
     * 1. EDGE EVALUATION ORDER MATTERS:
     *    - HighRiskOrder is checked before VIPCustomer
     *    - First matching guard wins, remaining edges skipped
     * 
     * 2. FAILURE STRATEGIES CREATE DIFFERENT PATHS:
     *    - SKIP: Try next edge in same step
     *    - ALTERNATIVE: Redirect to different target
     *    - CONTINUE: Proceed despite guard failure
     *    - RETRY: Re-evaluate guard with delays
     *    - STOP: Terminate workflow immediately
     * 
     * 3. COMPLEX ROUTING IS DETERMINISTIC:
     *    - Same context always produces same path
     *    - Guards evaluated in YAML declaration order
     *    - Fallback edges (no guard) provide safety nets
     * 
     * 4. WORKFLOW ROBUSTNESS:
     *    - Multiple paths to same destination (payment)
     *    - Graceful degradation (express → standard)
     *    - Controlled failure points with clear reasons
     * </pre>
     * 
     * <h3>Error Conditions:</h3>
     * <ul>
     *   <li><strong>No Outgoing Edges:</strong> Returns NONE → triggers dead-end error</li>
     *   <li><strong>All Guards Fail with STOP:</strong> Returns FAIL → terminates workflow</li>
     *   <li><strong>All Guards Fail with SKIP:</strong> Returns NONE → triggers dead-end error</li>
     *   <li><strong>ALTERNATIVE Missing Target:</strong> Returns FAIL with configuration error</li>
     *   <li><strong>Guard Evaluation Exception:</strong> Treated as guard failure</li>
     * </ul>
     * 
     * <h3>Performance Notes:</h3>
     * <ul>
     *   <li><strong>Short-Circuit Evaluation:</strong> Stops at first successful transition</li>
     *   <li><strong>Guard Caching:</strong> Guard components are cached by ComponentScanner</li>
     *   <li><strong>Order Matters:</strong> Earlier edges take precedence over later ones</li>
     * </ul>
     * 
     * @param workflow the workflow definition containing all edge definitions. Must not be null.
     * @param currentStep the step from which to find outgoing transitions. Must not be null.
     * @param context the execution context for guard evaluation. Must not be null.
     * @return NextSelection indicating the transition decision:
     *         <ul>
     *           <li>NEXT with target step name if a valid transition is found</li>
     *           <li>FAIL with error message if guard failure handling requires workflow termination</li>
     *           <li>NONE if no outgoing edges exist or all applicable edges are skipped</li>
     *         </ul>
     * @see #handleGuardFailure for failure strategy implementation details
     * @see #evaluateGuard for guard evaluation mechanics
     */
    private NextSelection findNextStep(FlowConfig.WorkflowDef workflow, String currentStep, ExecutionContext context) {
        List<FlowConfig.EdgeDef> outgoing = new ArrayList<>();
        for (FlowConfig.EdgeDef e : workflow.edges) {
            if (e.from != null && e.from.equals(currentStep)) {
                outgoing.add(e);
            }
        }

        for (FlowConfig.EdgeDef edge : outgoing) {
            // If no guard, take it immediately
            if (edge.guard == null || edge.guard.isEmpty()) {
                return NextSelection.next(edge.to);
            }

            boolean guardPassed = evaluateGuard(edge.guard, context);
            if (guardPassed) {
                LOGGER.debug("Edge guard '{}' passed: {} -> {}", edge.guard, currentStep, edge.to);
                return NextSelection.next(edge.to);
            }

            // Guard failed, apply onFailure strategy (default STOP)
            NextSelection handled = handleGuardFailure(edge, context);
            if (handled.action == NextSelection.Kind.NEXT) {
                LOGGER.debug("Edge guard failed; onFailure transitioned to {}", handled.next);
                return handled; // CONTINUE or ALTERNATIVE or RETRY success
            } else if (handled.action == NextSelection.Kind.FAIL) {
                LOGGER.warn("Edge guard failed; onFailure=FAIL: {} -> {} (reason: {})", edge.from, edge.to, handled.failureMessage);
                return handled; // STOP or RETRY exhausted without success
            } else {
                // SKIP: move to next edge
                continue;
            }
        }
        return NextSelection.none();
    }

    /** Applies the configured onFailure strategy for a guard-failing edge. */
    private NextSelection handleGuardFailure(FlowConfig.EdgeDef edge, ExecutionContext context) {
        String strategy = (edge.onFailure != null && edge.onFailure.strategy != null)
                ? edge.onFailure.strategy.trim().toUpperCase()
                : "STOP";
        switch (strategy) {
            case "CONTINUE":
                // Ignore guard failure and continue to target
                return NextSelection.next(edge.to);
            case "SKIP":
                // Bypass this edge; try the next one
                return NextSelection.skip();
            case "ALTERNATIVE":
                String alt = (edge.onFailure != null) ? edge.onFailure.alternativeTarget : null;
                if (alt == null || alt.isEmpty()) {
                    return NextSelection.fail("Edge guard failed and no alternativeTarget configured for edge: "
                            + edge.from + " -> " + edge.to);
                }
                return NextSelection.next(alt);
            case "RETRY":
                int attempts = (edge.onFailure != null && edge.onFailure.attempts != null) ? Math.max(1, edge.onFailure.attempts) : 3;
                long delay = (edge.onFailure != null && edge.onFailure.delay != null) ? Math.max(0L, edge.onFailure.delay) : 1000L;
                for (int i = 0; i < attempts; i++) {
                    if (i > 0 && delay > 0) {
                        try {
                            LOGGER.debug("Retrying edge guard '{}' in {} ms (attempt {}/{})", edge.guard, delay, i + 1, attempts);
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                    boolean ok = evaluateGuard(edge.guard, context);
                    if (ok) {
                        return NextSelection.next(edge.to);
                    }
                }
                return NextSelection.fail("Edge guard failed after retry for edge: " + edge.from + " -> " + edge.to);
            case "STOP":
            default:
                return NextSelection.fail("Edge guard failed with STOP for edge: " + edge.from + " -> " + edge.to);
        }
    }

    /** Returns true if the guard evaluates to true for the given name. */
    private boolean evaluateGuard(String guardName, ExecutionContext context) {
        return evaluateSingleGuard(guardName, context);
    }
    
    /**
     * Returns true if the given step name is a terminal marker.
     */
    private boolean isTerminal(String stepName) {
        return "SUCCESS".equals(stepName) || "FAILURE".equals(stepName);
    }

    /**
     * Evaluates a single named guard by instantiating its component and invoking {@link Guard#evaluate}.
     */
    private boolean evaluateSingleGuard(String guardName, ExecutionContext context) {
        if (guardName == null || guardName.isEmpty()) return true;
        FlowConfig.StepDef guardDef = config.steps.get(guardName);
        try {
            Class<? extends Guard> guardClass;
            Map<String, Object> effectiveConfig;
            if (guardDef != null) {
                guardClass = componentScanner.getGuardClass(guardDef.type);
                if (guardClass == null) {
                    LOGGER.warn("Guard implementation not found for type '{}' (guard={})", guardDef.type, guardName);
                    return false;
                }
                effectiveConfig = buildEffectiveConfig(guardName, guardDef.config, true);
            } else {
                // Fallback: resolve guard directly by class-like name (lowerCamel, UpperCamel, or FQCN)
                guardClass = componentScanner.getGuardClass(guardName);
                if (guardClass == null) {
                    LOGGER.warn("Guard component not found: {}", guardName);
                    return false;
                }
                effectiveConfig = buildEffectiveConfig(guardName, null, true);
            }

            Guard guard = guardClass.getDeclaredConstructor().newInstance();
            dependencyInjector.injectDependencies(guard, context, effectiveConfig, config.settings);
            return guard.evaluate(context);
        } catch (Exception e) {
            LOGGER.error("Guard evaluation failed for '{}': {}", guardName, e.toString());
            return false;
        }
    }

    private Map<String, Object> buildEffectiveConfig(String name, Map<String, Object> stepConfig, boolean isGuard) {
        Map<String, Object> effective = new HashMap<>();
        if (config.defaults != null) {
            // Category-wide defaults
            Map<String, Object> cat = config.defaults.get(isGuard ? "guard" : "step");
            if (cat != null) {
                effective.putAll(cat);
            }
            // Per-name defaults
            Map<String, Object> byName = config.defaults.get(name);
            if (byName != null) {
                effective.putAll(byName);
            }
        }
        if (stepConfig != null) {
            effective.putAll(stepConfig);
        }
        return effective;
    }
    
    public void analyzeWorkflow(String workflowName) {
        FlowConfig.WorkflowDef workflow = config.workflows.get(workflowName);
        if (workflow == null) {
            LOGGER.warn("Workflow not found: {}. Available: {}", workflowName, config.workflows.keySet());
            return;
        }

        LOGGER.info("\n📊 Workflow Execution Plan: {}", workflowName);
        LOGGER.info("============================================");
        LOGGER.info("Root: {}", workflow.root);
        LOGGER.info("Total edges: {}", workflow.edges.size());
        if (workflow.root == null || workflow.root.isEmpty()) {
            LOGGER.warn("Workflow '{}' has no root defined; execution would immediately succeed with no steps.", workflowName);
        } else if (!config.steps.containsKey(workflow.root)) {
            LOGGER.warn("Workflow '{}' root '{}' is not defined in steps.", workflowName, workflow.root);
        }

        // Build ordered set of steps: start from root, then preserve edge declaration order
        LinkedHashSet<String> orderedSteps = new LinkedHashSet<>();
        if (workflow.root != null) orderedSteps.add(workflow.root);
        for (FlowConfig.EdgeDef e : workflow.edges) {
            if (!isTerminal(e.from)) orderedSteps.add(e.from);
            if (!isTerminal(e.to)) orderedSteps.add(e.to);
        }

            LOGGER.info("\n🧩 Steps (with type, config, step-guards, retry): ");
            for (String stepName : orderedSteps) {
                FlowConfig.StepDef sd = config.steps.get(stepName);
                String type = sd != null ? sd.type : "<unresolved>";
                Map<String, Object> effCfg = sd != null ? buildEffectiveConfig(stepName, sd.config, false) : Collections.emptyMap();
                LOGGER.info("- step: {}", stepName);
                LOGGER.info("  type: {}", type);
                LOGGER.info("  config: {}", (effCfg == null ? "{}" : effCfg));
                if (sd == null) {
                LOGGER.warn("  ⚠ step '{}' is referenced but not defined.", stepName);
            } else if (sd.type == null || sd.type.isEmpty() || componentScanner.getStepClass(sd.type) == null) {
                LOGGER.warn("  ⚠ step '{}' implementation unresolved for type '{}'.", stepName, sd.type);
            }
            // Step-level guards
                List<String> stepGuards = (sd != null && sd.guards != null) ? sd.guards : Collections.emptyList();
                if (!stepGuards.isEmpty()) {
                    LOGGER.info("  step-guards:");
                    for (String g : stepGuards) {
                        GuardInfo gi = getGuardInfo(g);
                        LOGGER.info("    - name: {}, type: {}, config: {}", g,
                                (gi.clazz != null ? gi.clazz.getSimpleName() : "<unresolved>"),
                            (gi.effectiveConfig == null ? "{}" : gi.effectiveConfig));
                    if (gi.clazz == null) {
                        LOGGER.warn("      ⚠ guard '{}' implementation unresolved.", g);
                    }
                }
            }
            // Retry info
            if (sd != null && sd.retry != null) {
                LOGGER.info("  retry:");
                LOGGER.info("    maxAttempts: {}", sd.retry.maxAttempts);
                LOGGER.info("    delayMs: {}", sd.retry.delay);
                if (sd.retry.guard != null && !sd.retry.guard.isEmpty()) {
                    GuardInfo gi = getGuardInfo(sd.retry.guard);
                    LOGGER.info("    guard: {} (type: {})", sd.retry.guard, (gi.clazz != null ? gi.clazz.getSimpleName() : "<unresolved>"));
                    if (gi.clazz == null) {
                        LOGGER.warn("      ⚠ retry guard '{}' implementation unresolved.", sd.retry.guard);
                    }
                } else {
                    LOGGER.info("    guard: <none> (step may handle retry internally)");
                }
            }
            // Retry info
            if (sd != null && sd.retry != null) {
                LOGGER.info("  retry:");
                LOGGER.info("    maxAttempts: {}", sd.retry.maxAttempts);
                LOGGER.info("    delay: {}", sd.retry.delay);
                if (sd.retry.guard != null && !sd.retry.guard.isEmpty()) {
                    GuardInfo gi = getGuardInfo(sd.retry.guard);
                    LOGGER.info("    guard: {} (type: {})", sd.retry.guard, (gi.clazz != null ? gi.clazz.getSimpleName() : "<unresolved>"));
                    if (gi.clazz == null) {
                        LOGGER.warn("      ⚠ retry guard '{}' implementation unresolved.", sd.retry.guard);
                    }
                }
                if (sd.retry.backoff != null && !sd.retry.backoff.isEmpty()) {
                    LOGGER.info("    backoff: {}", sd.retry.backoff);
                }
                if (sd.retry.multiplier != null) {
                    LOGGER.info("    multiplier: {}", sd.retry.multiplier);
                }
                if (sd.retry.maxDelay != null) {
                    LOGGER.info("    maxDelay: {}", sd.retry.maxDelay);
                }
            }
        }

        LOGGER.info("\n➡ Transitions (with edge-guards and onFailure): ");
        for (FlowConfig.EdgeDef edge : workflow.edges) {
            String path = edge.from + " -> " + edge.to;
            StringBuilder info = new StringBuilder();
            boolean hasGuard = edge.guard != null && !edge.guard.isEmpty();
            if (hasGuard) {
                GuardInfo gi = getGuardInfo(edge.guard);
                info.append(" [edge-guard: ").append(edge.guard)
                        .append(", type: ")
                        .append(gi.clazz != null ? gi.clazz.getSimpleName() : "<unresolved>")
                        .append("]");
                if (gi.clazz == null) {
                    LOGGER.warn("  ⚠ edge guard '{}' implementation unresolved on {}", edge.guard, path);
                }
                // Only meaningful when a guard exists
                if (edge.onFailure != null && edge.onFailure.strategy != null) {
                    info.append(" [onFailure: ").append(edge.onFailure.strategy);
                    if ("ALTERNATIVE".equalsIgnoreCase(edge.onFailure.strategy) && edge.onFailure.alternativeTarget != null) {
                        info.append(" -> ").append(edge.onFailure.alternativeTarget);
                    } else if ("RETRY".equalsIgnoreCase(edge.onFailure.strategy)) {
                        Integer at = edge.onFailure.attempts; Long dl = edge.onFailure.delay;
                        info.append(" attempts=").append(at != null ? at : 3).append(", delayMs=").append(dl != null ? dl : 1000);
                    }
                    info.append("]");
                } else {
                    info.append(" [onFailure: STOP]");
                }
            }
            LOGGER.info("- {}{}", path, info);
        }

        // Flow-like tree view from root
        LOGGER.info("\n🧭 Flow (tree):\n");
        printFlowTree(workflow);

        // Reachability analysis
        LOGGER.info("\n🔎 Reachability analysis:");
        Set<String> reachable = new LinkedHashSet<>();
        if (workflow.root != null && !workflow.root.isEmpty()) {
            Deque<String> dq = new ArrayDeque<>();
            dq.add(workflow.root);
            while (!dq.isEmpty()) {
                String n = dq.poll();
                if (n == null || reachable.contains(n) || isTerminal(n)) continue;
                reachable.add(n);
                for (FlowConfig.EdgeDef e : workflow.edges) {
                    if (n.equals(e.from) && e.to != null && !isTerminal(e.to)) {
                        dq.add(e.to);
                    }
                }
            }
        }
        // Dead-ends: reachable nodes with no outgoing edges
        List<String> deadEnds = new ArrayList<>();
        for (String n : reachable) {
            boolean hasOutgoing = false;
            for (FlowConfig.EdgeDef e : workflow.edges) {
                if (n.equals(e.from)) { hasOutgoing = true; break; }
            }
            if (!hasOutgoing) deadEnds.add(n);
        }
        if (deadEnds.isEmpty()) {
            LOGGER.info("- Dead-ends: none");
        } else {
            LOGGER.info("- Dead-ends (no outgoing edges): {}", deadEnds);
        }
        // Unreachable steps: defined steps not in reachable set (and not terminal)
        List<String> unreachable = new ArrayList<>();
        for (String s : config.steps.keySet()) {
            if (!isTerminal(s) && (reachable.isEmpty() || !reachable.contains(s))) {
                // Only consider steps that appear in this workflow’s graph (from/to/root)
                boolean referenced = (workflow.root != null && workflow.root.equals(s));
                if (!referenced) {
                    for (FlowConfig.EdgeDef e : workflow.edges) {
                        if (s.equals(e.from) || s.equals(e.to)) { referenced = true; break; }
                    }
                }
                if (referenced) unreachable.add(s);
            }
        }
        if (unreachable.isEmpty()) {
            LOGGER.info("- Unreachable steps: none");
        } else {
            LOGGER.info("- Unreachable steps (not reachable from root): {}", unreachable);
        }

        LOGGER.info("\n✅ Analysis complete.\n");
    }

    /** Helper: describes a guard by name, resolving class and effective config if available. */
    private GuardInfo getGuardInfo(String guardName) {
        GuardInfo info = new GuardInfo();
        info.name = guardName;
        FlowConfig.StepDef def = config.steps.get(guardName);
        if (def != null) {
            info.clazz = componentScanner.getGuardClass(def.type);
            info.effectiveConfig = buildEffectiveConfig(guardName, def.config, true);
            return info;
        }
        // Fallback: resolve by class-like name via scanner
        info.clazz = componentScanner.getGuardClass(guardName);
        info.effectiveConfig = buildEffectiveConfig(guardName, null, true);
        return info;
    }

    /** Renders a flow-like tree with ASCII connectors starting from root. */
    private void printFlowTree(FlowConfig.WorkflowDef workflow) {
        Map<String, List<FlowConfig.EdgeDef>> outgoing = new LinkedHashMap<>();
        for (FlowConfig.EdgeDef e : workflow.edges) {
            outgoing.computeIfAbsent(e.from, k -> new ArrayList<>()).add(e);
        }
        Set<String> seen = new HashSet<>();
        renderNode(workflow.root, outgoing, seen, "");
    }

    private void renderNode(String node,
                            Map<String, List<FlowConfig.EdgeDef>> outgoing,
                            Set<String> seen,
                            String prefix) {
        if (node == null) return;
        FlowConfig.StepDef sd = config.steps.get(node);
        if (sd != null && sd.guards != null && !sd.guards.isEmpty()) {
            LOGGER.info(prefix + node + "  [step-guards: " + String.join(", ", sd.guards) + "]");
        } else {
            LOGGER.info(prefix + node);
        }
        if (isTerminal(node)) return;
        if (seen.contains(node)) {
            LOGGER.info(prefix + "  ↩ (cycle)");
            return;
        }
        seen.add(node);
        List<FlowConfig.EdgeDef> edges = outgoing.getOrDefault(node, Collections.emptyList());
        for (int i = 0; i < edges.size(); i++) {
            FlowConfig.EdgeDef e = edges.get(i);
            boolean last = (i == edges.size() - 1);
            String connector = last ? "└─" : "├─";
            StringBuilder sb = new StringBuilder();
            boolean hasGuard = e.guard != null && !e.guard.isEmpty();
            if (hasGuard) {
                GuardInfo gi = getGuardInfo(e.guard);
                sb.append(" [edge-guard: ").append(e.guard).append(", type: ")
                        .append(gi.clazz != null ? gi.clazz.getSimpleName() : "<unresolved>")
                        .append("]");
                if (e.onFailure != null && e.onFailure.strategy != null) {
                    sb.append(" [onFailure: ").append(e.onFailure.strategy);
                    if ("ALTERNATIVE".equalsIgnoreCase(e.onFailure.strategy) && e.onFailure.alternativeTarget != null) {
                        sb.append(" -> ").append(e.onFailure.alternativeTarget);
                    } else if ("RETRY".equalsIgnoreCase(e.onFailure.strategy)) {
                        Integer at = e.onFailure.attempts; Long dl = e.onFailure.delay;
                        sb.append(" attempts=").append(at != null ? at : 3).append(", delayMs=").append(dl != null ? dl : 1000);
                    }
                    sb.append("]");
                } else {
                    sb.append(" [onFailure: STOP]");
                }
            }
            LOGGER.info(prefix + connector + "→ " + e.to + sb);
            if (!isTerminal(e.to)) {
                String childPrefix = prefix + (last ? "   " : "│  ");
                renderNode(e.to, outgoing, seen, childPrefix);
            }
        }
    }

    /** Small DTO for analysis rendering. */
    private static class GuardInfo {
        String name;
        Class<? extends Guard> clazz;
        Map<String, Object> effectiveConfig;
    }

    /** Selection result when choosing the next edge. */
    private static class NextSelection {
        enum Kind { NEXT, SKIP, FAIL, NONE }
        final Kind action;
        final String next;
        final String failureMessage;

        private NextSelection(Kind action, String next, String failureMessage) {
            this.action = action; this.next = next; this.failureMessage = failureMessage;
        }
        static NextSelection next(String step) { return new NextSelection(Kind.NEXT, step, null); }
        static NextSelection skip() { return new NextSelection(Kind.SKIP, null, null); }
        static NextSelection fail(String msg) { return new NextSelection(Kind.FAIL, null, msg); }
        static NextSelection none() { return new NextSelection(Kind.NONE, null, null); }
    }

    /**
     * Validates workflow edge configurations to ensure correct routing behavior.
     * 
     * <p>This method enforces the following rules:
     * <ul>
     *   <li><strong>At most one default edge per step:</strong> Each step can have at most one outgoing edge without a guard</li>
     *   <li><strong>Default edges must be last:</strong> No-guard edges must be declared after all guarded edges from the same step</li>
     *   <li><strong>No unreachable edges:</strong> All edges after a no-guard edge from the same step are unreachable</li>
     * </ul>
     * 
     * <p>These rules prevent common configuration errors where edges become unreachable due to 
     * earlier no-guard edges always being selected first.
     * 
     * <h3>Valid Configuration Example:</h3>
     * <pre>
     * edges:
     *   - from: "checkout"
     *     to: "premium_flow"
     *     guard: "VIPCustomer"
     *     onFailure: { strategy: SKIP }
     *   - from: "checkout"
     *     to: "express_flow" 
     *     guard: "ExpressShipping"
     *     onFailure: { strategy: SKIP }
     *   - from: "checkout"
     *     to: "standard_flow"
     *     # Default fallback - no guard, must be last
     * </pre>
     * 
     * <h3>Invalid Configuration Example:</h3>
     * <pre>
     * edges:
     *   - from: "checkout"
     *     to: "premium_flow"
     *     guard: "VIPCustomer"
     *   - from: "checkout"
     *     to: "standard_flow"
     *     # No guard - always matches first
     *   - from: "checkout"
     *     to: "audit_flow"
     *     guard: "RequiresAudit"  # UNREACHABLE - standard_flow always selected first
     * </pre>
     * 
     * @throws IllegalArgumentException if validation fails with details about the specific violation
     */
    private void validateWorkflowEdges() {
        if (config.workflows == null) {
            return;
        }

        for (Map.Entry<String, FlowConfig.WorkflowDef> workflowEntry : config.workflows.entrySet()) {
            String workflowName = workflowEntry.getKey();
            FlowConfig.WorkflowDef workflow = workflowEntry.getValue();
            
            if (workflow.edges == null || workflow.edges.isEmpty()) {
                continue;
            }

            // Group edges by their 'from' step
            Map<String, List<FlowConfig.EdgeDef>> edgesByStep = new LinkedHashMap<>();
            for (FlowConfig.EdgeDef edge : workflow.edges) {
                if (edge.from != null) {
                    edgesByStep.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
                }
            }

            // Validate each step's outgoing edges
            for (Map.Entry<String, List<FlowConfig.EdgeDef>> stepEntry : edgesByStep.entrySet()) {
                String stepName = stepEntry.getKey();
                List<FlowConfig.EdgeDef> outgoingEdges = stepEntry.getValue();
                
                validateStepEdges(workflowName, stepName, outgoingEdges);
            }
        }
    }

    /**
     * Validates outgoing edges for a single step.
     * 
     * @param workflowName the name of the workflow containing the step
     * @param stepName the name of the step being validated
     * @param outgoingEdges list of edges originating from this step
     * @throws IllegalArgumentException if validation fails
     */
    private void validateStepEdges(String workflowName, String stepName, List<FlowConfig.EdgeDef> outgoingEdges) {
        if (outgoingEdges.size() <= 1) {
            // Single edge or no edges - no validation needed
            return;
        }

        boolean foundDefaultEdge = false;
        int defaultEdgeIndex = -1;

        // Check each edge in declaration order
        for (int i = 0; i < outgoingEdges.size(); i++) {
            FlowConfig.EdgeDef edge = outgoingEdges.get(i);
            boolean isDefaultEdge = edge.guard == null || edge.guard.trim().isEmpty();

            if (isDefaultEdge) {
                if (foundDefaultEdge) {
                    // Multiple default edges from same step
                    throw new IllegalArgumentException(
                        String.format("Workflow '%s': Step '%s' has multiple edges without guards. " +
                                     "Only one default (no-guard) edge is allowed per step at index %d and %d.",
                                     workflowName, stepName, defaultEdgeIndex, i));
                }
                foundDefaultEdge = true;
                defaultEdgeIndex = i;

                // Check if there are any edges after this default edge
                if (i < outgoingEdges.size() - 1) {
                    List<String> unreachableTargets = new ArrayList<>();
                    for (int j = i + 1; j < outgoingEdges.size(); j++) {
                        unreachableTargets.add(outgoingEdges.get(j).to);
                    }
                    throw new IllegalArgumentException(
                        String.format("Workflow '%s': Step '%s' has unreachable edges after default edge at index %d. " +
                                     "Default (no-guard) edges must be declared last. Unreachable targets: %s",
                                     workflowName, stepName, i, unreachableTargets));
                }
            }
        }

        LOGGER.debug("Validated edges for step '{}' in workflow '{}': {} edges, default edge at index {}",
                    stepName, workflowName, outgoingEdges.size(), defaultEdgeIndex);
    }
}
