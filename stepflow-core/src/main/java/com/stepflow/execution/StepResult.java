package com.stepflow.execution;

import java.util.Map;
import java.util.HashMap;

/**
 * Immutable result object representing the outcome of step execution in a workflow.
 * 
 * <p>StepResult serves as the standardized return type for all Step implementations,
 * encapsulating execution status, descriptive messages, and contextual data. It provides
 * a rich set of factory methods for creating results in various scenarios and supports
 * both simple status reporting and complex data flow patterns.
 * 
 * <h2>Core Components</h2>
 * 
 * <ul>
 *   <li><strong>Status:</strong> SUCCESS, FAILURE, or PENDING execution state</li>
 *   <li><strong>Message:</strong> Optional human-readable description of the outcome</li>
 *   <li><strong>Context:</strong> Data to be merged back into the workflow ExecutionContext</li>
 * </ul>
 * 
 * <h2>Status Types and Semantics</h2>
 * 
 * <p><strong>SUCCESS:</strong> Step completed successfully, workflow should continue
 * <pre>
 * // Simple success
 * return StepResult.success();
 * 
 * // Success with descriptive message
 * return StepResult.success("Order validated successfully");
 * 
 * // Success with data for next steps
 * ctx.put("validation.score", 95);
 * return StepResult.success("High confidence validation", ctx);
 * </pre>
 * 
 * <p><strong>FAILURE:</strong> Step failed, workflow should handle the error
 * <pre>
 * // Simple failure
 * return StepResult.failure();
 * 
 * // Failure with error description
 * return StepResult.failure("Invalid payment method: " + method);
 * 
 * // Failure with error context for debugging
 * ctx.put("error.code", "PAYMENT_001");
 * ctx.put("error.timestamp", System.currentTimeMillis());
 * return StepResult.failure("Payment processing failed", ctx);
 * </pre>
 * 
 * <p><strong>PENDING:</strong> Step initiated but not complete, may be resumed later
 * <pre>
 * // Asynchronous operation initiated
 * ctx.put("async.operation.id", operationId);
 * ctx.put("async.operation.status", "IN_PROGRESS");
 * return StepResult.pending("Payment authorization in progress", ctx);
 * </pre>
 * 
 * <h2>Factory Method Patterns</h2>
 * 
 * <p><strong>Success Patterns:</strong>
 * <pre>
 * // Minimal success
 * StepResult.success()
 * 
 * // Success with message
 * StepResult.success("Data processed successfully")
 * 
 * // Success with context data
 * Map&lt;String, Object&gt; results = new HashMap&lt;&gt;();
 * results.put("processed.count", 150);
 * results.put("processing.duration", 1200L);
 * StepResult.success(results)
 * 
 * // Success with message and context
 * StepResult.success("Batch processing completed", results)
 * 
 * // Success with ExecutionContext (recommended)
 * StepResult.success("Processing completed", ctx)
 * </pre>
 * 
 * <p><strong>Failure Patterns:</strong>
 * <pre>
 * // Simple failure
 * StepResult.failure()
 * 
 * // Failure with error message
 * StepResult.failure("Database connection timeout")
 * 
 * // Failure from caught exception
 * try {
 *     // risky operation
 * } catch (IOException e) {
 *     return StepResult.failure(e);  // Uses exception message
 * }
 * 
 * // Failure with exception and context
 * try {
 *     // operation
 * } catch (ProcessingException e) {
 *     ctx.put("error.details", e.getDetails());
 *     return StepResult.failure(e, ctx);
 * }
 * </pre>
 * 
 * <h2>Advanced Usage Patterns</h2>
 * 
 * <p><strong>Conditional Result Creation:</strong>
 * <pre>
 * public StepResult execute(ExecutionContext ctx) {
 *     String orderId = ctx.getString("order.id");
 *     double amount = ctx.getDouble("order.amount", 0.0);
 *     
 *     if (orderId == null) {
 *         return StepResult.failure("Order ID is required for processing");
 *     }
 *     
 *     if (amount <= 0) {
 *         ctx.put("validation.error", "INVALID_AMOUNT");
 *         return StepResult.failure("Order amount must be positive", ctx);
 *     }
 *     
 *     if (amount > 10000) {
 *         // High-value order - requires manual approval
 *         ctx.put("approval.required", true);
 *         ctx.put("approval.reason", "HIGH_VALUE_ORDER");
 *         return StepResult.pending("Order requires manual approval", ctx);
 *     }
 *     
 *     // Standard processing
 *     ProcessingResult result = processOrder(orderId, amount);
 *     ctx.put("processing.result", result);
 *     ctx.put("processing.timestamp", System.currentTimeMillis());
 *     
 *     return StepResult.success("Order processed successfully", ctx);
 * }
 * </pre>
 * 
 * <p><strong>Rich Data Flow:</strong>
 * <pre>
 * public StepResult execute(ExecutionContext ctx) {
 *     // Perform complex business logic
 *     ValidationResult validation = performValidation(ctx);
 *     
 *     // Add comprehensive result data
 *     ctx.put("validation.overall_score", validation.getScore());
 *     ctx.put("validation.rules_passed", validation.getPassedRules());
 *     ctx.put("validation.rules_failed", validation.getFailedRules());
 *     ctx.put("validation.warnings", validation.getWarnings());
 *     ctx.put("validation.processing_time", validation.getDuration());
 *     
 *     if (validation.isSuccess()) {
 *         return StepResult.success("Validation passed with score " + validation.getScore(), ctx);
 *     } else {
 *         return StepResult.failure("Validation failed: " + validation.getFailureReason(), ctx);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Context Enrichment:</strong>
 * <pre>
 * public StepResult execute(ExecutionContext ctx) {
 *     String customerId = ctx.getString("customer.id");
 *     
 *     // Enrich context with customer data
 *     CustomerProfile profile = customerService.getProfile(customerId);
 *     ctx.put("customer.profile", profile);
 *     ctx.put("customer.tier", profile.getTier());
 *     ctx.put("customer.preferences", profile.getPreferences());
 *     
 *     // Add step execution metadata
 *     ctx.put("enrichment.timestamp", System.currentTimeMillis());
 *     ctx.put("enrichment.source", "customer-service-v2");
 *     
 *     return StepResult.success("Customer data enriched", ctx);
 * }
 * </pre>
 * 
 * <h2>Context Data Management</h2>
 * 
 * <p><strong>Type-Safe Context Access:</strong>
 * <pre>
 * // Creating StepResult with properly typed context data
 * Map&lt;String, Object&gt; contextData = new HashMap&lt;&gt;();
 * contextData.put("user.id", "user123");
 * contextData.put("session.timeout", 3600);
 * contextData.put("permissions", Arrays.asList("read", "write"));
 * 
 * StepResult result = StepResult.success("Authentication completed", contextData);
 * 
 * // Later, accessing the data with type safety
 * String userId = result.getContextValue("user.id", String.class);
 * Integer timeout = result.getContextValue("session.timeout", Integer.class, 1800);  // with default
 * </pre>
 * 
 * <p><strong>Context Modification and Chaining:</strong>
 * <pre>
 * // Create base result
 * StepResult result = StepResult.success("Initial processing completed");
 * 
 * // Add additional context data
 * StepResult enrichedResult = result
 *     .withContext("processing.stage", "validation")
 *     .withContext("processing.timestamp", System.currentTimeMillis());
 * 
 * // Add multiple context entries
 * Map&lt;String, Object&gt; additionalData = new HashMap&lt;&gt;();
 * additionalData.put("validation.score", 95);
 * additionalData.put("validation.rules_applied", 12);
 * 
 * StepResult finalResult = enrichedResult.withContext(additionalData);
 * </pre>
 * 
 * <h2>Error Handling Patterns</h2>
 * 
 * <p><strong>Exception-Based Failures:</strong>
 * <pre>
 * public StepResult execute(ExecutionContext ctx) {
 *     try {
 *         ExternalApiResponse response = callExternalService();
 *         ctx.put("api.response", response);
 *         return StepResult.success("External service call completed", ctx);
 *         
 *     } catch (TimeoutException e) {
 *         // Retriable error
 *         ctx.put("error.type", "TIMEOUT");
 *         ctx.put("error.retry_recommended", true);
 *         return StepResult.failure(e, ctx);
 *         
 *     } catch (AuthenticationException e) {
 *         // Non-retriable error
 *         ctx.put("error.type", "AUTH_FAILURE");
 *         ctx.put("error.retry_recommended", false);
 *         return StepResult.failure("Authentication failed: " + e.getMessage(), ctx);
 *         
 *     } catch (Exception e) {
 *         // Unexpected error
 *         ctx.put("error.type", "UNEXPECTED");
 *         ctx.put("error.class", e.getClass().getSimpleName());
 *         return StepResult.failure("Unexpected error occurred", ctx);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Business Logic Failures:</strong>
 * <pre>
 * public StepResult execute(ExecutionContext ctx) {
 *     BusinessValidationResult validation = validateBusinessRules(ctx);
 *     
 *     if (!validation.isValid()) {
 *         // Business rule violation - not a technical error
 *         ctx.put("business_error.type", validation.getErrorType());
 *         ctx.put("business_error.violations", validation.getViolations());
 *         ctx.put("business_error.suggested_actions", validation.getSuggestedActions());
 *         
 *         return StepResult.failure(validation.getErrorMessage(), ctx);
 *     }
 *     
 *     // Continue with normal processing...
 * }
 * </pre>
 * 
 * <h2>Testing Support</h2>
 * 
 * <p><strong>Result Validation in Tests:</strong>
 * <pre>
 * {@code @Test}
 * public void testStepExecution() {
 *     // Arrange
 *     ExecutionContext ctx = new ExecutionContext();
 *     ctx.put("input.value", "test-data");
 *     
 *     MyStep step = new MyStep();
 *     
 *     // Act
 *     StepResult result = step.execute(ctx);
 *     
 *     // Assert
 *     assertTrue(result.isSuccess());
 *     assertFalse(result.isFailure());
 *     assertFalse(result.isPending());
 *     
 *     assertEquals("Processing completed", result.getMessage());
 *     
 *     // Verify context data
 *     String outputValue = result.getContextValue("output.value", String.class);
 *     assertNotNull(outputValue);
 *     assertEquals("processed-test-data", outputValue);
 * }
 * </pre>
 * 
 * <h2>Performance Considerations</h2>
 * 
 * <ul>
 *   <li><strong>Immutability:</strong> StepResult is immutable - context modifications create new instances</li>
 *   <li><strong>Context Size:</strong> Large context maps can impact memory usage in long workflows</li>
 *   <li><strong>Factory Methods:</strong> Use appropriate factory methods to avoid unnecessary object creation</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Clear Messages:</strong> Provide descriptive messages for debugging and monitoring</li>
 *   <li><strong>Structured Context:</strong> Use consistent key naming conventions</li>
 *   <li><strong>Error Context:</strong> Include relevant error details in the context</li>
 *   <li><strong>Type Safety:</strong> Use typed context accessors when retrieving data</li>
 * </ul>
 * 
 * @see Status for available result states
 * @see ExecutionContext for workflow data container
 * @see Step for step implementation interface
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public class StepResult {
    
    public enum Status {
        SUCCESS, FAILURE, PENDING
    }
    
    public final Status status;
    public final String message;
    public final Map<String, Object> context;
    
    // ======================================================================================
    // CONSTRUCTORS
    // ======================================================================================
    
    public StepResult(Status status) {
        this.status = status;
        this.message = null;
        this.context = new HashMap<>();
    }
    
    public StepResult(Status status, String message) {
        this.status = status;
        this.message = message;
        this.context = new HashMap<>();
    }
    
    public StepResult(Status status, Map<String, Object> context) {
        this.status = status;
        this.message = null;
        this.context = new HashMap<>(context);
    }
    
    public StepResult(Status status, String message, Map<String, Object> context) {
        this.status = status;
        this.message = message;
        this.context = new HashMap<>(context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - SUCCESS
    // ======================================================================================
    
    /**
     * Creates a successful result with no message or context data.
     * 
     * <p>This is the simplest form of success result, suitable for steps that
     * complete successfully without needing to provide additional information
     * or data to subsequent steps.
     * 
     * <p><strong>Usage Example:</strong>
     * <pre>
     * {@code @Override}
     * public StepResult execute(ExecutionContext ctx) {
     *     // Perform cleanup or simple operation
     *     cleanupTempFiles();
     *     return StepResult.success();  // Simple success
     * }
     * </pre>
     * 
     * @return a StepResult with Status.SUCCESS, no message, and empty context
     * 
     * @see #success(String) for success with descriptive message
     * @see #success(ExecutionContext) for success with context data
     */
    public static StepResult success() {
        return new StepResult(Status.SUCCESS);
    }
    
    /**
     * Creates a successful result with a descriptive message.
     * 
     * <p>The message provides human-readable information about what the step
     * accomplished, useful for logging, debugging, and monitoring.
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * return StepResult.success("Order validation completed successfully");
     * return StepResult.success("Processed 150 items in 1.2 seconds");
     * return StepResult.success("Customer data enriched from external service");
     * </pre>
     * 
     * @param message descriptive message about the successful operation
     * @return a StepResult with Status.SUCCESS, the provided message, and empty context
     * 
     * @see #success() for simple success without message
     * @see #success(String, ExecutionContext) for success with message and context
     */
    public static StepResult success(String message) {
        return new StepResult(Status.SUCCESS, message);
    }
    
    /**
     * Creates a successful result with context data to be merged into the workflow.
     * 
     * <p>This method is ideal when the step produces data that subsequent steps
     * need to access. The provided context will be merged into the workflow's
     * main ExecutionContext.
     * 
     * <p><strong>Usage Example:</strong>
     * <pre>
     * public StepResult execute(ExecutionContext ctx) {
     *     ValidationResult validation = performValidation(ctx);
     *     
     *     Map&lt;String, Object&gt; results = new HashMap&lt;&gt;();
     *     results.put("validation.score", validation.getScore());
     *     results.put("validation.passed", validation.isPassed());
     *     results.put("validation.timestamp", System.currentTimeMillis());
     *     
     *     return StepResult.success(results);
     * }
     * </pre>
     * 
     * @param context map of key-value pairs to be added to the workflow context
     * @return a StepResult with Status.SUCCESS, no message, and the provided context
     * 
     * @see #success(ExecutionContext) for using ExecutionContext directly
     * @see #success(String, Map) for success with both message and context
     */
    public static StepResult success(Map<String, Object> context) {
        return new StepResult(Status.SUCCESS, context);
    }
    
    /**
     * Creates a successful result with message and context data.
     */
    public static StepResult success(String message, Map<String, Object> context) {
        return new StepResult(Status.SUCCESS, message, context);
    }
    
    /**
     * Creates a successful result with ExecutionContext.
     */
    public static StepResult success(ExecutionContext context) {
        return new StepResult(Status.SUCCESS, context);
    }
    
    /**
     * Creates a successful result with message and ExecutionContext.
     */
    public static StepResult success(String message, ExecutionContext context) {
        return new StepResult(Status.SUCCESS, message, context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - FAILURE
    // ======================================================================================
    
    /**
     * Creates a failed result with no message.
     */
    public static StepResult failure() {
        return new StepResult(Status.FAILURE);
    }
    
    /**
     * Creates a failed result with an error message describing what went wrong.
     * 
     * <p>This is the most common way to indicate step failure, providing
     * clear information about the error condition for debugging and error handling.
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * // Validation failure
     * return StepResult.failure("Order amount must be greater than zero");
     * 
     * // Business rule violation
     * return StepResult.failure("Customer is not eligible for premium shipping");
     * 
     * // External service failure
     * return StepResult.failure("Payment gateway returned error: " + errorCode);
     * 
     * // Data integrity issue
     * return StepResult.failure("Required field 'customer.email' is missing");
     * </pre>
     * 
     * @param message descriptive error message explaining the failure
     * @return a StepResult with Status.FAILURE, the provided message, and empty context
     * 
     * @see #failure() for simple failure without message
     * @see #failure(String, ExecutionContext) for failure with error context
     * @see #failure(Throwable) for failure from caught exceptions
     */
    public static StepResult failure(String message) {
        return new StepResult(Status.FAILURE, message);
    }
    
    /**
     * Creates a failed result with context data.
     */
    public static StepResult failure(Map<String, Object> context) {
        return new StepResult(Status.FAILURE, context);
    }
    
    /**
     * Creates a failed result with message and context data.
     */
    public static StepResult failure(String message, Map<String, Object> context) {
        return new StepResult(Status.FAILURE, message, context);
    }
    
    /**
     * Creates a failed result with ExecutionContext.
     */
    public static StepResult failure(ExecutionContext context) {
        return new StepResult(Status.FAILURE, context);
    }
    
    /**
     * Creates a failed result with message and ExecutionContext.
     */
    public static StepResult failure(String message, ExecutionContext context) {
        return new StepResult(Status.FAILURE, message, context);
    }
    
    /**
     * Creates a failed result from a caught exception, using the exception's message.
     * 
     * <p>This convenience method extracts the message from the exception and creates
     * a failure result. The original exception is not stored in the result, only its
     * message is used.
     * 
     * <p><strong>Usage Example:</strong>
     * <pre>
     * public StepResult execute(ExecutionContext ctx) {
     *     try {
     *         ExternalApiResponse response = callExternalApi();
     *         // Process response...
     *         return StepResult.success(ctx);
     *     } catch (ApiException e) {
     *         // Convert exception to failure result
     *         return StepResult.failure(e);  // Uses e.getMessage()
     *     }
     * }
     * </pre>
     * 
     * @param throwable the exception that caused the failure
     * @return a StepResult with Status.FAILURE, the exception's message, and empty context
     * 
     * @see #failure(Throwable, ExecutionContext) for including context with exception
     * @see #failure(String) for custom error messages
     */
    public static StepResult failure(Throwable throwable) {
        return new StepResult(Status.FAILURE, throwable.getMessage());
    }
    
    /**
     * Creates a failed result from an exception with context.
     */
    public static StepResult failure(Throwable throwable, Map<String, Object> context) {
        return new StepResult(Status.FAILURE, throwable.getMessage(), context);
    }
    
    /**
     * Creates a failed result from an exception with ExecutionContext.
     */
    public static StepResult failure(Throwable throwable, ExecutionContext context) {
        return new StepResult(Status.FAILURE, throwable.getMessage(), context);
    }
    
    // ======================================================================================
    // STATIC FACTORY METHODS - PENDING
    // ======================================================================================
    
    /**
     * Creates a pending result with no message.
     */
    public static StepResult pending() {
        return new StepResult(Status.PENDING);
    }
    
    /**
     * Creates a pending result with a message.
     */
    public static StepResult pending(String message) {
        return new StepResult(Status.PENDING, message);
    }
    
    /**
     * Creates a pending result with context data.
     */
    public static StepResult pending(Map<String, Object> context) {
        return new StepResult(Status.PENDING, context);
    }
    
    /**
     * Creates a pending result with message and context data.
     */
    public static StepResult pending(String message, Map<String, Object> context) {
        return new StepResult(Status.PENDING, message, context);
    }
    
    /**
     * Creates a pending result with ExecutionContext.
     */
    public static StepResult pending(ExecutionContext context) {
        return new StepResult(Status.PENDING, context);
    }
    
    /**
     * Creates a pending result with message and ExecutionContext.
     */
    public static StepResult pending(String message, ExecutionContext context) {
        return new StepResult(Status.PENDING, message, context);
    }
    
    // ======================================================================================
    // CONVENIENCE METHODS
    // ======================================================================================
    
    /**
     * Checks if this result represents success.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Checks if this result represents failure.
     */
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    /**
     * Checks if this result represents pending state.
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }
    
    /**
     * Gets a value from the context with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = context.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Gets a value from the context with a default value.
     */
    public <T> T getContextValue(String key, Class<T> type, T defaultValue) {
        T value = getContextValue(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Creates a new StepResult with additional context data.
     */
    public StepResult withContext(String key, Object value) {
        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.put(key, value);
        return new StepResult(this.status, this.message, newContext);
    }
    
    /**
     * Creates a new StepResult with additional context data.
     */
    public StepResult withContext(Map<String, Object> additionalContext) {
        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.putAll(additionalContext);
        return new StepResult(this.status, this.message, newContext);
    }
    
    @Override
    public String toString() {
        return "StepResult{status=" + status + ", message='" + message + "', context=" + context + "}";
    }
}