package com.stepflow.execution;

/**
 * Core interface for defining workflow execution steps in the StepFlow framework.
 * 
 * <p>A Step represents a unit of work within a workflow that processes data, performs
 * business logic, and produces results. Steps are the building blocks of workflows,
 * connected through edges to form complex business processes.
 * 
 * <h2>Step Lifecycle and Execution</h2>
 * 
 * <p>The framework manages the complete step lifecycle:
 * <ol>
 *   <li><strong>Discovery:</strong> Steps are discovered via {@link ComponentScanner} using {@link com.stepflow.core.annotations.StepComponent @StepComponent}</li>
 *   <li><strong>Instantiation:</strong> New instance created for each execution</li>
 *   <li><strong>Dependency Injection:</strong> Fields annotated with {@link com.stepflow.core.annotations.Inject @Inject} and {@link com.stepflow.core.annotations.ConfigValue @ConfigValue} are populated</li>
 *   <li><strong>Guard Evaluation:</strong> Step-level guards are checked (all must pass)</li>
 *   <li><strong>Execution:</strong> {@link #execute(ExecutionContext)} is called</li>
 *   <li><strong>Result Processing:</strong> {@link StepResult} is processed and context is merged</li>
 * </ol>
 * 
 * <h2>Implementation Examples</h2>
 * 
 * <p><strong>Simple Step:</strong>
 * <pre>
 * {@code @StepComponent(name = "validateOrder")}
 * public class OrderValidationStep implements Step {
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         String orderId = ctx.getString("orderId");
 *         Double amount = ctx.getDouble("amount");
 *         
 *         if (orderId == null || amount == null || amount <= 0) {
 *             return StepResult.failure("Invalid order data");
 *         }
 *         
 *         ctx.put("validation.status", "PASSED");
 *         ctx.put("validation.timestamp", System.currentTimeMillis());
 *         
 *         return StepResult.success("Order validated successfully", ctx);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Step with Dependency Injection:</strong>
 * <pre>
 * {@code @StepComponent(name = "processPayment")}
 * public class PaymentProcessingStep implements Step {
 *     
 *     // Configuration injection
 *     {@code @ConfigValue("gateway.url")}
 *     private String gatewayUrl;
 *     
 *     {@code @ConfigValue(value = "timeout", defaultValue = "30000")}
 *     private Integer timeoutMs;
 *     
 *     // Runtime data injection
 *     {@code @Inject("payment.amount")}
 *     private Double amount;
 *     
 *     {@code @Inject(value = "payment.method", defaultValue = "CREDIT_CARD")}
 *     private String paymentMethod;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // All fields are automatically injected before this method is called
 *         try {
 *             PaymentResult result = processPayment(amount, paymentMethod, gatewayUrl, timeoutMs);
 *             
 *             ctx.put("payment.transaction_id", result.getTransactionId());
 *             ctx.put("payment.status", result.getStatus());
 *             
 *             return StepResult.success(ctx);
 *         } catch (PaymentException e) {
 *             return StepResult.failure("Payment processing failed: " + e.getMessage(), ctx);
 *         }
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Step with Complex Logic and Multiple Outcomes:</strong>
 * <pre>
 * {@code @StepComponent(name = "inventoryCheck")}
 * public class InventoryCheckStep implements Step {
 *     
 *     {@code @Inject("product.id")}
 *     private String productId;
 *     
 *     {@code @Inject("order.quantity")}
 *     private Integer quantity;
 *     
 *     {@code @ConfigValue("inventory.reserve_timeout")}
 *     private Integer reserveTimeoutMinutes;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         InventoryService service = getInventoryService();
 *         
 *         try {
 *             // Check current stock
 *             int availableStock = service.getAvailableStock(productId);
 *             ctx.put("inventory.available_stock", availableStock);
 *             
 *             if (availableStock < quantity) {
 *                 // Insufficient stock - workflow can branch to backorder process
 *                 ctx.put("inventory.status", "INSUFFICIENT");
 *                 ctx.put("inventory.shortage", quantity - availableStock);
 *                 return StepResult.success("Insufficient stock for immediate fulfillment", ctx);
 *             }
 *             
 *             // Reserve the inventory
 *             String reservationId = service.reserveInventory(productId, quantity, reserveTimeoutMinutes);
 *             
 *             ctx.put("inventory.status", "RESERVED");
 *             ctx.put("inventory.reservation_id", reservationId);
 *             ctx.put("inventory.reserved_quantity", quantity);
 *             ctx.put("inventory.reservation_expires", 
 *                     System.currentTimeMillis() + (reserveTimeoutMinutes * 60 * 1000));
 *             
 *             return StepResult.success("Inventory reserved successfully", ctx);
 *             
 *         } catch (InventoryServiceException e) {
 *             // Service failure - this could trigger retry logic
 *             ctx.put("inventory.error", e.getMessage());
 *             ctx.put("inventory.error_code", e.getErrorCode());
 *             return StepResult.failure("Inventory service unavailable", ctx);
 *         }
 *     }
 * }
 * </pre>
 * 
 * <h2>Step Guards and Conditional Execution</h2>
 * 
 * <p>Steps can be protected by guards that must all evaluate to {@code true} for the step to execute:
 * 
 * <pre>
 * // YAML Configuration
 * steps:
 *   premiumProcessing:
 *     type: "premiumOrderStep"
 *     guards: ["VIPCustomerGuard", "HighValueOrderGuard"]
 *     config:
 *       priority_level: "HIGH"
 * 
 * // Step implementation
 * {@code @StepComponent(name = "premiumOrderStep")}
 * public class PremiumOrderProcessingStep implements Step {
 *     
 *     {@code @ConfigValue("priority_level")}
 *     private String priorityLevel;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // This method only executes if both VIPCustomerGuard and HighValueOrderGuard return true
 *         return processPremiumOrder(ctx, priorityLevel);
 *     }
 * }
 * </pre>
 * 
 * <p>When guards fail, the step is skipped and the workflow continues with edge evaluation.
 * 
 * <h2>Error Handling and Retry Patterns</h2>
 * 
 * <p><strong>Built-in Retry Support:</strong>
 * <pre>
 * // YAML Configuration with retry
 * steps:
 *   unstableService:
 *     type: "externalApiStep"
 *     retry:
 *       maxAttempts: 3
 *       delay: 1000
 *       guard: "ShouldRetryGuard"  # Optional conditional retry
 * 
 * {@code @StepComponent(name = "externalApiStep")}
 * public class ExternalApiStep implements Step {
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         try {
 *             ApiResponse response = callExternalApi();
 *             ctx.put("api.response", response);
 *             return StepResult.success(ctx);
 *         } catch (TransientApiException e) {
 *             // This failure can trigger engine-driven retry
 *             return StepResult.failure("Transient API failure: " + e.getMessage());
 *         } catch (PermanentApiException e) {
 *             // This failure should not be retried
 *             return StepResult.failure("Permanent API failure: " + e.getMessage());
 *         }
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Exception Handling Best Practices:</strong>
 * <pre>
 * {@code @Override}
 * public StepResult execute(ExecutionContext ctx) {
 *     try {
 *         // Business logic here
 *         return StepResult.success(ctx);
 *     } catch (BusinessException e) {
 *         // Expected business exception - return controlled failure
 *         ctx.put("error.type", "BUSINESS_RULE_VIOLATION");
 *         ctx.put("error.details", e.getDetails());
 *         return StepResult.failure(e.getMessage(), ctx);
 *     } catch (SystemException e) {
 *         // System exception - might be retriable
 *         ctx.put("error.type", "SYSTEM_ERROR");
 *         ctx.put("error.timestamp", System.currentTimeMillis());
 *         return StepResult.failure("System error occurred", ctx);
 *     } catch (Exception e) {
 *         // Unexpected exception - log and fail
 *         logger.error("Unexpected error in step execution", e);
 *         return StepResult.failure("Unexpected error: " + e.getMessage());
 *     }
 * }
 * </pre>
 * 
 * <h2>Performance and Resource Management</h2>
 * 
 * <ul>
 *   <li><strong>Instance Creation:</strong> New Step instance is created for each execution</li>
 *   <li><strong>Stateless Design:</strong> Steps should be stateless between executions</li>
 *   <li><strong>Resource Cleanup:</strong> Clean up resources within the execute method</li>
 *   <li><strong>Context Size:</strong> Be mindful of data added to ExecutionContext</li>
 * </ul>
 * 
 * <h2>Testing Patterns</h2>
 * 
 * <p><strong>Unit Testing:</strong>
 * <pre>
 * {@code @Test}
 * public void testOrderValidationStep() {
 *     // Arrange
 *     OrderValidationStep step = new OrderValidationStep();
 *     ExecutionContext ctx = new ExecutionContext();
 *     ctx.put("orderId", "ORDER-123");
 *     ctx.put("amount", 99.99);
 *     
 *     // Act
 *     StepResult result = step.execute(ctx);
 *     
 *     // Assert
 *     assertTrue(result.isSuccess());
 *     assertEquals("PASSED", ctx.get("validation.status"));
 * }
 * </pre>
 * 
 * @see StepResult for execution result details
 * @see ExecutionContext for shared data management
 * @see com.stepflow.core.annotations.StepComponent for component registration
 * @see com.stepflow.core.annotations.Inject for dependency injection
 * @see com.stepflow.core.annotations.ConfigValue for configuration injection
 * @see Guard for conditional execution logic
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public interface Step {
    /**
     * Executes the step's business logic using the provided execution context.
     * 
     * <p>This method is called by the StepFlow engine after:
     * <ul>
     *   <li>Creating a new instance of this Step</li>
     *   <li>Performing dependency injection on annotated fields</li>
     *   <li>Evaluating all step-level guards (if any)</li>
     * </ul>
     * 
     * <p>The implementation should:
     * <ul>
     *   <li>Perform the required business logic</li>
     *   <li>Access injected dependencies and configuration</li>
     *   <li>Read required data from the ExecutionContext</li>
     *   <li>Add result data back to the context</li>
     *   <li>Return appropriate {@link StepResult} indicating success or failure</li>
     * </ul>
     * 
     * <h3>Context Data Access Patterns:</h3>
     * <pre>
     * // Reading typed data from context
     * String userId = ctx.getString("user.id");
     * Double amount = ctx.getDouble("order.amount", 0.0);  // with default
     * List&lt;String&gt; items = ctx.getList("order.items", String.class);
     * 
     * // Adding result data to context
     * ctx.put("processing.timestamp", System.currentTimeMillis());
     * ctx.put("validation.result", validationOutcome);
     * ctx.put("step.duration", processingTime);
     * </pre>
     * 
     * <h3>Result Patterns:</h3>
     * <pre>
     * // Success with context data
     * return StepResult.success("Processing completed", ctx);
     * 
     * // Success with just message
     * return StepResult.success("Order validated");
     * 
     * // Failure with error details
     * return StepResult.failure("Invalid payment method: " + method, ctx);
     * 
     * // Failure from exception
     * return StepResult.failure(exception, ctx);
     * </pre>
     * 
     * <h3>Thread Safety:</h3>
     * <p>Step instances are created per execution and are not shared between threads.
     * However, any shared resources (databases, external services) accessed within
     * this method should be thread-safe.
     * 
     * <h3>Exception Handling:</h3>
     * <p>While uncaught exceptions will be handled by the framework, it's recommended
     * to catch expected exceptions and return appropriate {@link StepResult} instances
     * for better error reporting and workflow control.
     * 
     * @param ctx the execution context containing workflow data and providing
     *           access to previous step results. Must not be null.
     * @return a StepResult indicating the outcome of the step execution.
     *         Must not be null - use {@link StepResult#failure()} for unexpected scenarios.
     * @throws RuntimeException if an unrecoverable error occurs. The framework
     *                         will convert this to a StepResult.FAILURE.
     */
    StepResult execute(ExecutionContext ctx);
}