package com.stepflow.execution;

/**
 * Interface for defining conditional logic that controls workflow execution and routing.
 * 
 * <p>Guards are boolean-valued functions that enable conditional workflow behavior.
 * They serve as decision points that determine whether steps execute or which paths
 * workflows take. The StepFlow framework supports guards at two levels: step-level
 * guards that control step execution, and edge-level guards that control routing decisions.
 * 
 * <h2>Guard Types and Usage</h2>
 * 
 * <p><strong>Step-Level Guards (Execution Gates):</strong>
 * <ul>
 *   <li>Control whether a step executes at all</li>
 *   <li>ALL guards must return {@code true} for step to execute</li>
 *   <li>If any guard returns {@code false}, step is SKIPPED (not FAILED)</li>
 *   <li>Workflow continues with routing from the same node</li>
 * </ul>
 * 
 * <p><strong>Edge-Level Guards (Routing Decisions):</strong>
 * <ul>
 *   <li>Control which outgoing transition is taken from a step</li>
 *   <li>Evaluated in YAML declaration order</li>
 *   <li>FIRST guard that returns {@code true} wins</li>
 *   <li>Support sophisticated failure handling strategies</li>
 * </ul>
 * 
 * <h2>Implementation Examples</h2>
 * 
 * <p><strong>Simple Business Rule Guard:</strong>
 * <pre>
 * {@code @GuardComponent(name = "VIPCustomerGuard")}
 * public class VIPCustomerGuard implements Guard {
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         String customerTier = ctx.getString("customer.tier");
 *         return "VIP".equals(customerTier) || "PREMIUM".equals(customerTier);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Configurable Guard with Dependency Injection:</strong>
 * <pre>
 * {@code @GuardComponent(name = "OrderValueGuard")}
 * public class OrderValueGuard implements Guard {
 *     
 *     {@code @ConfigValue("min_order_value")}
 *     private Double minimumValue;
 *     
 *     {@code @ConfigValue(value = "currency", defaultValue = "USD")}
 *     private String currency;
 *     
 *     {@code @Inject("customer.tier")}
 *     private String customerTier;
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         Double orderValue = ctx.getDouble("order.value", 0.0);
 *         
 *         // Apply tier-based discounts to threshold
 *         double effectiveThreshold = minimumValue;
 *         if ("VIP".equals(customerTier)) {
 *             effectiveThreshold *= 0.8;  // 20% lower threshold for VIP
 *         }
 *         
 *         return orderValue >= effectiveThreshold;
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Complex Multi-Condition Guard:</strong>
 * <pre>
 * {@code @GuardComponent(name = "BusinessHoursGuard")}
 * public class BusinessHoursGuard implements Guard {
 *     
 *     {@code @ConfigValue("business_hours.start")}
 *     private String startTime;  // "09:00"
 *     
 *     {@code @ConfigValue("business_hours.end")}
 *     private String endTime;    // "17:00"
 *     
 *     {@code @ConfigValue(value = "weekend_processing", defaultValue = "false")}
 *     private Boolean weekendProcessing;
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         LocalTime now = LocalTime.now();
 *         LocalDate today = LocalDate.now();
 *         
 *         // Check weekend processing
 *         if (isWeekend(today) && !weekendProcessing) {
 *             return false;
 *         }
 *         
 *         // Check business hours
 *         LocalTime start = LocalTime.parse(startTime);
 *         LocalTime end = LocalTime.parse(endTime);
 *         
 *         boolean withinHours = now.isAfter(start) && now.isBefore(end);
 *         
 *         // VIP customers can process outside business hours
 *         String customerTier = ctx.getString("customer.tier");
 *         boolean isVip = "VIP".equals(customerTier);
 *         
 *         return withinHours || isVip;
 *     }
 *     
 *     private boolean isWeekend(LocalDate date) {
 *         DayOfWeek day = date.getDayOfWeek();
 *         return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
 *     }
 * }
 * </pre>
 * 
 * <h2>Step-Level Guard Usage</h2>
 * 
 * <p><strong>YAML Configuration:</strong>
 * <pre>
 * steps:
 *   processPayment:
 *     type: "paymentProcessingStep"
 *     guards: ["VIPCustomerGuard", "OrderValueGuard", "BusinessHoursGuard"]
 *     config:
 *       gateway: "stripe"
 * </pre>
 * 
 * <p><strong>Execution Behavior:</strong>
 * <ol>
 *   <li>Engine evaluates all guards: VIPCustomerGuard AND OrderValueGuard AND BusinessHoursGuard</li>
 *   <li>If ALL return {@code true}: step executes normally</li>
 *   <li>If ANY returns {@code false}: step is SKIPPED, workflow continues with edge evaluation</li>
 * </ol>
 * 
 * <h2>Edge-Level Guard Usage</h2>
 * 
 * <p><strong>YAML Configuration:</strong>
 * <pre>
 * workflows:
 *   orderProcessing:
 *     root: validate
 *     edges:
 *       # Multiple paths from validate step - evaluated in order
 *       - from: validate
 *         to: premiumProcessing
 *         guard: "VIPCustomerGuard"
 *         onFailure: { strategy: "SKIP" }
 *       - from: validate  
 *         to: expeditedProcessing
 *         guard: "HighValueOrderGuard"
 *         onFailure: { strategy: "SKIP" }
 *       - from: validate
 *         to: standardProcessing
 *         # No guard - default fallback
 * </pre>
 * 
 * <p><strong>Routing Decision Process:</strong>
 * <ol>
 *   <li>Evaluate VIPCustomerGuard: if {@code true} → go to premiumProcessing</li>
 *   <li>If VIP guard fails: evaluate HighValueOrderGuard: if {@code true} → go to expeditedProcessing</li>
 *   <li>If both guards fail: take default path to standardProcessing</li>
 * </ol>
 * 
 * <h2>Advanced Guard Patterns</h2>
 * 
 * <p><strong>Retry Guards (Conditional Retry):</strong>
 * <pre>
 * {@code @GuardComponent(name = "ShouldRetryGuard")}
 * public class ShouldRetryGuard implements Guard {
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         String errorType = ctx.getString("last_error.type");
 *         Integer attemptCount = ctx.getInt("retry.current_attempt", 0);
 *         
 *         // Don't retry permanent failures
 *         if ("AUTHENTICATION_ERROR".equals(errorType) || 
 *             "INVALID_DATA".equals(errorType)) {
 *             return false;
 *         }
 *         
 *         // Limit retries for certain error types
 *         if ("RATE_LIMIT".equals(errorType) && attemptCount >= 2) {
 *             return false;
 *         }
 *         
 *         // Retry transient errors
 *         return "NETWORK_ERROR".equals(errorType) || 
 *                "TIMEOUT".equals(errorType) ||
 *                "SERVICE_UNAVAILABLE".equals(errorType);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Time-Based Guards:</strong>
 * <pre>
 * {@code @GuardComponent(name = "MaintenanceWindowGuard")}
 * public class MaintenanceWindowGuard implements Guard {
 *     
 *     {@code @ConfigValue("maintenance.start_hour")}
 *     private Integer maintenanceStart;  // 2 (2 AM)
 *     
 *     {@code @ConfigValue("maintenance.duration_hours")}
 *     private Integer maintenanceDuration;  // 2 hours
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         LocalTime now = LocalTime.now();
 *         int currentHour = now.getHour();
 *         
 *         int maintenanceEnd = (maintenanceStart + maintenanceDuration) % 24;
 *         
 *         // Check if we're in maintenance window
 *         boolean inMaintenanceWindow;
 *         if (maintenanceStart < maintenanceEnd) {
 *             inMaintenanceWindow = currentHour >= maintenanceStart && currentHour < maintenanceEnd;
 *         } else {
 *             // Maintenance window crosses midnight
 *             inMaintenanceWindow = currentHour >= maintenanceStart || currentHour < maintenanceEnd;
 *         }
 *         
 *         // Return false during maintenance (prevents execution)
 *         return !inMaintenanceWindow;
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Resource Availability Guards:</strong>
 * <pre>
 * {@code @GuardComponent(name = "DatabaseHealthGuard")}
 * public class DatabaseHealthGuard implements Guard {
 *     
 *     {@code @ConfigValue("health_check.timeout_ms")}
 *     private Integer healthCheckTimeout;
 *     
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         try {
 *             DatabaseHealthChecker checker = getDatabaseHealthChecker();
 *             HealthStatus status = checker.checkHealth(healthCheckTimeout);
 *             
 *             // Store health info in context for debugging
 *             ctx.put("database.health.status", status.getStatus());
 *             ctx.put("database.health.response_time", status.getResponseTime());
 *             ctx.put("database.health.checked_at", System.currentTimeMillis());
 *             
 *             return status.isHealthy();
 *         } catch (Exception e) {
 *             ctx.put("database.health.error", e.getMessage());
 *             return false;  // Assume unhealthy on exception
 *         }
 *     }
 * }
 * </pre>
 * 
 * <h2>Performance and Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Keep It Simple:</strong> Guards should be fast, simple boolean logic</li>
 *   <li><strong>Avoid Side Effects:</strong> Guards should not modify system state</li>
 *   <li><strong>Cache Expensive Operations:</strong> Use context to cache expensive lookups</li>
 *   <li><strong>Handle Exceptions:</strong> Catch exceptions and return appropriate boolean</li>
 *   <li><strong>Document Dependencies:</strong> Clearly document required context data</li>
 * </ul>
 * 
 * <h2>Testing Patterns</h2>
 * 
 * <p><strong>Unit Testing Guards:</strong>
 * <pre>
 * {@code @Test}
 * public void testVIPCustomerGuard() {
 *     // Arrange
 *     VIPCustomerGuard guard = new VIPCustomerGuard();
 *     ExecutionContext ctx = new ExecutionContext();
 *     
 *     // Test VIP customer
 *     ctx.put("customer.tier", "VIP");
 *     assertTrue(guard.evaluate(ctx));
 *     
 *     // Test regular customer
 *     ctx.put("customer.tier", "STANDARD");
 *     assertFalse(guard.evaluate(ctx));
 *     
 *     // Test premium customer
 *     ctx.put("customer.tier", "PREMIUM");
 *     assertTrue(guard.evaluate(ctx));
 * }
 * </pre>
 * 
 * @see Step for workflow execution units
 * @see ExecutionContext for shared data access
 * @see com.stepflow.core.annotations.GuardComponent for guard registration
 * @see com.stepflow.core.annotations.Inject for runtime data injection
 * @see com.stepflow.core.annotations.ConfigValue for configuration injection
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public interface Guard {
    /**
     * Evaluates the guard condition using the current execution context.
     * 
     * <p>This method is called by the StepFlow engine to determine whether:
     * <ul>
     *   <li><strong>Step-level:</strong> The protected step should execute</li>
     *   <li><strong>Edge-level:</strong> The associated transition should be taken</li>
 * </ul>
     * 
     * <p>The implementation should:
     * <ul>
     *   <li>Read required data from the ExecutionContext</li>
     *   <li>Access injected configuration and dependencies</li>
     *   <li>Perform the boolean evaluation logic</li>
     *   <li>Optionally add diagnostic information to the context</li>
     *   <li>Return {@code true} for "allow" or {@code false} for "deny"</li>
     * </ul>
     * 
     * <h3>Context Access Patterns:</h3>
     * <pre>
     * // Safe data access with defaults
     * String customerTier = ctx.getString("customer.tier", "STANDARD");
     * Double orderValue = ctx.getDouble("order.value", 0.0);
     * Boolean isWeekend = ctx.getBoolean("date.is_weekend", false);
     * 
     * // Complex object access
     * CustomerProfile profile = ctx.get("customer.profile", CustomerProfile.class);
     * List&lt;String&gt; permissions = ctx.getList("user.permissions", String.class);
     * </pre>
     * 
     * <h3>Diagnostic Information:</h3>
     * <pre>
     * // Adding debug information for troubleshooting
     * ctx.put("guard.evaluation.timestamp", System.currentTimeMillis());
     * ctx.put("guard.evaluation.criteria_met", criteriaResults);
     * ctx.put("guard.evaluation.decision_factors", decisionFactors);
     * </pre>
     * 
     * <h3>Error Handling:</h3>
     * <p>Guards should handle exceptions gracefully and return appropriate boolean values:
     * <pre>
     * {@code @Override}
     * public boolean evaluate(ExecutionContext ctx) {
     *     try {
     *         // Guard logic here
     *         return performEvaluation(ctx);
     *     } catch (DataAccessException e) {
     *         // Log error and decide on safe default
     *         logger.warn("Guard evaluation failed due to data access error", e);
     *         ctx.put("guard.error", e.getMessage());
     *         return false;  // Fail-safe: deny when uncertain
     *     } catch (Exception e) {
     *         // Unexpected error - log and fail safe
     *         logger.error("Unexpected error in guard evaluation", e);
     *         return false;
     *     }
     * }
     * </pre>
     * 
     * <h3>Performance Considerations:</h3>
     * <ul>
     *   <li>Guards are evaluated frequently and should be fast</li>
     *   <li>Avoid expensive operations like database calls if possible</li>
     *   <li>Cache expensive computations in the ExecutionContext</li>
     *   <li>Use injected configuration for static values</li>
     * </ul>
     * 
     * <h3>Thread Safety:</h3>
     * <p>Guard instances are created per evaluation and are not shared between threads.
     * However, any shared resources accessed should be thread-safe.
     * 
     * @param ctx the execution context containing workflow data and previous step results.
     *           Must not be null.
     * @return {@code true} if the condition is met (allow execution/transition),
     *         {@code false} if the condition is not met (deny execution/transition)
     * @throws RuntimeException if an unrecoverable error occurs. The framework
     *                         will treat this as {@code false} (deny).
     */
    boolean evaluate(ExecutionContext ctx);
}