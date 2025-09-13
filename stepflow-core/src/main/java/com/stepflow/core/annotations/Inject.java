package com.stepflow.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks fields for dependency injection from ExecutionContext or step configuration.
 * 
 * <p>The {@code @Inject} annotation provides runtime dependency injection for Step and Guard
 * components, enabling them to access data from the workflow execution context or their
 * configuration. This annotation supports flexible data binding with fallback mechanisms
 * and optional validation.
 * 
 * <h2>Injection Sources and Priority</h2>
 * 
 * <p>When resolving values for {@code @Inject} fields, the framework follows this priority order:
 * <ol>
 *   <li><strong>ExecutionContext:</strong> First, check the current workflow execution context</li>
 *   <li><strong>Step Configuration:</strong> Then, check the step's merged configuration (step config + defaults)</li>
 *   <li><strong>Default Value:</strong> Finally, use the annotation's {@link #defaultValue()} if provided</li>
 * </ol>
 * 
 * <h2>Basic Usage Examples</h2>
 * 
 * <p><strong>Inject by Field Name:</strong>
 * <pre>
 * {@code @StepComponent(name = "orderProcessor")}
 * public class OrderProcessingStep implements Step {
 *     {@code @Inject}  // Injects context.get("userId")
 *     private String userId;
 *     
 *     {@code @Inject}  // Injects context.get("orderAmount")
 *     private Double orderAmount;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // userId and orderAmount are automatically injected
 *         return processOrder(userId, orderAmount);
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Inject with Custom Key:</strong>
 * <pre>
 * public class PaymentStep implements Step {
 *     {@code @Inject("payment.gateway")}  // Injects context.get("payment.gateway")
 *     private String paymentGateway;
 *     
 *     {@code @Inject("customer_id")}      // Injects context.get("customer_id")
 *     private String customerId;
 * }
 * </pre>
 * 
 * <p><strong>Optional Injection with Defaults:</strong>
 * <pre>
 * public class NotificationStep implements Step {
 *     {@code @Inject(value = "notification.template", required = false, defaultValue = "default-template")}
 *     private String template;
 *     
 *     {@code @Inject(value = "retry.max_attempts", required = false, defaultValue = "3")}
 *     private Integer maxRetries;  // Automatically converted from String "3" to Integer
 * }
 * </pre>
 * 
 * <h2>Advanced Usage Scenarios</h2>
 * 
 * <p><strong>Context Data Flow:</strong>
 * <pre>
 * // Workflow execution context setup
 * ExecutionContext context = new ExecutionContext();
 * context.put("userId", "user123");
 * context.put("order.value", 299.99);
 * context.put("shipping.address", shippingAddress);
 * 
 * // Step configuration in YAML
 * steps:
 *   validateOrder:
 *     type: "orderValidationStep"
 *     config:
 *       min_amount: 10.0
 *       max_amount: 10000.0
 * 
 * // Step implementation with mixed injection sources
 * {@code @StepComponent(name = "orderValidationStep")}
 * public class OrderValidationStep implements Step {
 *     {@code @Inject}                    // From context: "user123"
 *     private String userId;
 *     
 *     {@code @Inject("order.value")}     // From context: 299.99
 *     private Double orderValue;
 *     
 *     {@code @ConfigValue("min_amount")} // From step config: 10.0
 *     private Double minAmount;
 *     
 *     {@code @Inject(value = "payment.method", required = false, defaultValue = "credit_card")}
 *     private String paymentMethod;      // Default value if not in context
 * }
 * </pre>
 * 
 * <p><strong>Complex Object Injection:</strong>
 * <pre>
 * public class ShippingStep implements Step {
 *     {@code @Inject("shipping.address")}          // Inject complex POJO
 *     private ShippingAddress address;
 *     
 *     {@code @Inject("order.items")}               // Inject collections
 *     private List&lt;OrderItem&gt; items;
 *     
 *     {@code @Inject("customer.preferences")}      // Inject maps
 *     private Map&lt;String, String&gt; preferences;
 * }
 * </pre>
 * 
 * <h2>Type Conversion and Coercion</h2>
 * 
 * <p>The framework automatically handles type conversion for common scenarios:
 * 
 * <pre>
 * public class ConfigurableStep implements Step {
 *     {@code @Inject(value = "timeout", defaultValue = "5000")}
 *     private Integer timeout;        // String "5000" → Integer 5000
 *     
 *     {@code @Inject(value = "enabled", defaultValue = "true")}
 *     private Boolean enabled;        // String "true" → Boolean true
 *     
 *     {@code @Inject(value = "threshold", defaultValue = "99.5")}
 *     private Double threshold;       // String "99.5" → Double 99.5
 * }
 * </pre>
 * 
 * <h2>Error Handling</h2>
 * 
 * <p><strong>Required Field Validation:</strong>
 * <pre>
 * public class CriticalStep implements Step {
 *     {@code @Inject("critical.data")}  // required=true by default
 *     private String criticalData;       // Throws IllegalStateException if missing
 *     
 *     {@code @Inject(value = "optional.data", required = false)}
 *     private String optionalData;       // null if missing, no exception
 * }
 * </pre>
 * 
 * <p><strong>Exception Details:</strong>
 * When a required injection fails, the framework throws {@code IllegalStateException} with details:
 * <pre>
 * IllegalStateException: Required @Inject value not found for key 'critical.data' 
 *                       on field 'criticalData' in com.example.CriticalStep
 * </pre>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Use Descriptive Keys:</strong> Choose clear, hierarchical keys like {@code "order.customer.id"}</li>
 *   <li><strong>Provide Sensible Defaults:</strong> Use {@code defaultValue} for optional configuration</li>
 *   <li><strong>Validate Critical Data:</strong> Use {@code required=true} for essential workflow data</li>
 *   <li><strong>Document Dependencies:</strong> Clearly document what data your steps expect</li>
 *   <li><strong>Type Safety:</strong> Use specific types rather than Object for better type checking</li>
 * </ul>
 * 
 * <h2>Integration with StepFlow Components</h2>
 * 
 * <p>This annotation works seamlessly with other StepFlow annotations:
 * 
 * <pre>
 * {@code @StepComponent(name = "comprehensiveStep")}
 * public class ComprehensiveStep implements Step {
 *     {@code @Inject("user.id")}                    // Runtime data from context
 *     private String userId;
 *     
 *     {@code @ConfigValue("processing.mode")}       // Configuration from YAML
 *     private String mode;
 *     
 *     {@code @Inject(value = "feature.enabled", required = false, defaultValue = "false")}
 *     private Boolean featureEnabled;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // All fields automatically injected before execution
 *         if (featureEnabled) {
 *             return processWithFeature(userId, mode);
 *         } else {
 *             return processStandard(userId, mode);
 *         }
 *     }
 * }
 * </pre>
 * 
 * @see ConfigValue For injecting static configuration values
 * @see StepComponent For marking step implementations
 * @see GuardComponent For marking guard implementations
 * @see ExecutionContext For the runtime data container
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    /**
     * The key to inject from the execution context or step configuration.
     * 
     * <p>If not specified, the field name will be used as the lookup key.
     * The framework will search for this key first in the ExecutionContext,
     * then in the step's merged configuration (step config merged with defaults).
     * 
     * <p><strong>Examples:</strong>
     * <pre>
     * {@code @Inject}                    // Uses field name as key
     * private String userId;             // Looks for "userId"
     * 
     * {@code @Inject("customer.id")}     // Uses explicit key
     * private String customerId;         // Looks for "customer.id"
     * 
     * {@code @Inject("order_total")}     // Snake case key
     * private Double total;              // Looks for "order_total"
     * </pre>
     * 
     * @return the lookup key, or empty string to use the field name
     */
    String value() default "";
    
    /**
     * Whether this injection is required for successful step execution.
     * 
     * <p>When {@code true} (default), the framework will throw an 
     * {@code IllegalStateException} if the value cannot be found in either
     * the ExecutionContext or step configuration, and no {@link #defaultValue()}
     * is provided.
     * 
     * <p>When {@code false}, missing values will result in {@code null}
     * being injected, or the {@link #defaultValue()} if specified.
     * 
     * <p><strong>Required Injection Example:</strong>
     * <pre>
     * {@code @Inject("user.id")}  // required=true by default
     * private String userId;      // Throws exception if "user.id" not found
     * </pre>
     * 
     * <p><strong>Optional Injection Example:</strong>
     * <pre>
     * {@code @Inject(value = "user.preference", required = false)}
     * private String preference;  // null if "user.preference" not found
     * </pre>
     * 
     * @return {@code true} if the injection is required, {@code false} if optional
     * @see #defaultValue() for providing fallback values for optional injections
     */
    boolean required() default true;
    
    /**
     * Default value to use when the injection key is not found and {@link #required()} is {@code false}.
     * 
     * <p>This value is only used when:
     * <ul>
     *   <li>The key is not found in ExecutionContext</li>
     *   <li>The key is not found in step configuration</li>
     *   <li>{@link #required()} is {@code false}</li>
     *   <li>This defaultValue is not empty</li>
     * </ul>
     * 
     * <p>The framework will attempt to convert the string default value to the
     * target field type using standard type coercion:
     * <ul>
     *   <li>{@code String} → no conversion</li>
     *   <li>{@code Integer/int} → {@code Integer.parseInt()}</li>
     *   <li>{@code Long/long} → {@code Long.parseLong()}</li>
     *   <li>{@code Double/double} → {@code Double.parseDouble()}</li>
     *   <li>{@code Boolean/boolean} → {@code Boolean.parseBoolean()}</li>
     * </ul>
     * 
     * <p><strong>Examples:</strong>
     * <pre>
     * {@code @Inject(value = "timeout", required = false, defaultValue = "5000")}
     * private Integer timeout;        // 5000 if "timeout" not found
     * 
     * {@code @Inject(value = "enabled", required = false, defaultValue = "true")}
     * private Boolean enabled;        // true if "enabled" not found
     * 
     * {@code @Inject(value = "mode", required = false, defaultValue = "standard")}
     * private String mode;            // "standard" if "mode" not found
     * </pre>
     * 
     * <p><strong>Note:</strong> If {@link #required()} is {@code true}, this value is ignored
     * and an exception will be thrown if the key is not found.
     * 
     * @return the default value as a string, or empty string for no default
     */
    String defaultValue() default "";
}