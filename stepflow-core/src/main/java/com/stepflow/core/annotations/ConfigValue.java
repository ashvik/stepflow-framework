package com.stepflow.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects static configuration values from step configuration or global settings.
 * 
 * <p>The {@code @ConfigValue} annotation is designed for injecting static configuration
 * data that doesn't change during workflow execution, such as timeouts, thresholds,
 * service endpoints, and feature flags. Unlike {@link Inject @Inject}, which prioritizes
 * runtime execution context, {@code @ConfigValue} focuses on configuration-driven values.
 * 
 * <h2>Value Resolution Priority</h2>
 * 
 * <p>The framework resolves {@code @ConfigValue} fields using this hierarchical lookup:
 * <ol>
 *   <li><strong>Step Configuration:</strong> Individual step config from YAML</li>
 *   <li><strong>Global Settings Path:</strong> Global settings using {@link #globalPath()}</li>
 *   <li><strong>Default Value:</strong> Annotation's {@link #defaultValue()}</li>
 * </ol>
 * 
 * <h2>Basic Usage Examples</h2>
 * 
 * <p><strong>Step-Specific Configuration:</strong>
 * <pre>
 * // YAML configuration
 * steps:
 *   validateOrder:
 *     type: "orderValidationStep"
 *     config:
 *       min_amount: 10.0
 *       max_amount: 5000.0
 *       strict_mode: true
 * 
 * // Step implementation
 * {@code @StepComponent(name = "orderValidationStep")}
 * public class OrderValidationStep implements Step {
 *     {@code @ConfigValue("min_amount")}
 *     private Double minAmount;        // Injects 10.0
 *     
 *     {@code @ConfigValue("max_amount")}
 *     private Double maxAmount;        // Injects 5000.0
 *     
 *     {@code @ConfigValue("strict_mode")}
 *     private Boolean strictMode;      // Injects true
 * }
 * </pre>
 * 
 * <p><strong>Global Settings with Path:</strong>
 * <pre>
 * // YAML configuration
 * settings:
 *   database:
 *     timeout: 30000
 *     pool_size: 10
 *   notifications:
 *     email:
 *       template: "order-confirmation"
 *       retry_count: 3
 * 
 * steps:
 *   sendEmail:
 *     type: "emailNotificationStep"
 *     # No step-specific config - will use global settings
 * 
 * // Step implementation using global paths
 * {@code @StepComponent(name = "emailNotificationStep")}
 * public class EmailNotificationStep implements Step {
 *     {@code @ConfigValue(value = "template", globalPath = "notifications.email.template")}
 *     private String template;         // "order-confirmation" from global settings
 *     
 *     {@code @ConfigValue(value = "retry_count", globalPath = "notifications.email.retry_count")}
 *     private Integer retryCount;      // 3 from global settings
 *     
 *     {@code @ConfigValue(value = "timeout", globalPath = "database.timeout")}
 *     private Integer dbTimeout;       // 30000 from global settings
 * }
 * </pre>
 * 
 * <p><strong>Configuration with Defaults:</strong>
 * <pre>
 * public class ConfigurableStep implements Step {
 *     {@code @ConfigValue(value = "batch_size", required = false, defaultValue = "100")}
 *     private Integer batchSize;       // 100 if not configured
 *     
 *     {@code @ConfigValue(value = "enable_caching", required = false, defaultValue = "true")}
 *     private Boolean cachingEnabled;  // true if not configured
 *     
 *     {@code @ConfigValue(value = "api_endpoint", globalPath = "services.api.endpoint", defaultValue = "http://localhost:8080")}
 *     private String apiEndpoint;      // Fallback to localhost if not in step config or global settings
 * }
 * </pre>
 * 
 * <h2>Advanced Configuration Scenarios</h2>
 * 
 * <p><strong>Hierarchical Configuration Override:</strong>
 * <pre>
 * // YAML with global defaults and step overrides
 * settings:
 *   processing:
 *     timeout: 5000
 *     retry_count: 3
 * 
 * defaults:
 *   step:
 *     timeout: 10000      # Default for all steps
 * 
 * steps:
 *   fastStep:
 *     type: "quickProcessingStep"
 *     config:
 *       timeout: 1000     # Overrides both global and default
 *   
 *   standardStep:
 *     type: "standardProcessingStep"
 *     # Will inherit from defaults.step.timeout = 10000
 *   
 *   globalStep:
 *     type: "globalSettingsStep"
 *     # No step config, will use globalPath
 * 
 * // Steps demonstrating different configuration sources
 * public class QuickProcessingStep implements Step {
 *     {@code @ConfigValue("timeout")}   // Gets 1000 (step config)
 *     private Integer timeout;
 * }
 * 
 * public class StandardProcessingStep implements Step {
 *     {@code @ConfigValue("timeout")}   // Gets 10000 (defaults.step)
 *     private Integer timeout;
 * }
 * 
 * public class GlobalSettingsStep implements Step {
 *     {@code @ConfigValue(value = "timeout", globalPath = "processing.timeout")}  // Gets 5000 (global settings)
 *     private Integer timeout;
 * }
 * </pre>
 * 
 * <p><strong>Complex Configuration Objects:</strong>
 * <pre>
 * // YAML with structured configuration
 * settings:
 *   external_services:
 *     payment_gateway:
 *       url: "https://api.stripe.com"
 *       timeout: 15000
 *       retry_policy:
 *         max_attempts: 5
 *         backoff_factor: 2.0
 * 
 * steps:
 *   processPayment:
 *     type: "paymentProcessingStep"
 *     config:
 *       currency: "USD"
 *       processing_fee: 2.50
 * 
 * // Step using multiple global paths
 * public class PaymentProcessingStep implements Step {
 *     {@code @ConfigValue("currency")}
 *     private String currency;                    // "USD" from step config
 *     
 *     {@code @ConfigValue("processing_fee")}
 *     private Double fee;                         // 2.50 from step config
 *     
 *     {@code @ConfigValue(value = "gateway_url", globalPath = "external_services.payment_gateway.url")}
 *     private String gatewayUrl;                  // Stripe URL from global settings
 *     
 *     {@code @ConfigValue(value = "gateway_timeout", globalPath = "external_services.payment_gateway.timeout")}
 *     private Integer gatewayTimeout;             // 15000 from global settings
 *     
 *     {@code @ConfigValue(value = "max_retries", globalPath = "external_services.payment_gateway.retry_policy.max_attempts")}
 *     private Integer maxRetries;                 // 5 from nested global settings
 * }
 * </pre>
 * 
 * <h2>Type Conversion and Validation</h2>
 * 
 * <p>The framework handles automatic type conversion for standard Java types:
 * 
 * <pre>
 * public class TypeConversionExample implements Step {
 *     {@code @ConfigValue(value = "port", defaultValue = "8080")}
 *     private Integer port;           // String "8080" → Integer 8080
 *     
 *     {@code @ConfigValue(value = "timeout", defaultValue = "30.5")}
 *     private Double timeout;         // String "30.5" → Double 30.5
 *     
 *     {@code @ConfigValue(value = "enabled", defaultValue = "false")}
 *     private Boolean enabled;        // String "false" → Boolean false
 *     
 *     {@code @ConfigValue(value = "threshold", defaultValue = "1000")}
 *     private Long threshold;         // String "1000" → Long 1000L
 * }
 * </pre>
 * 
 * <h2>Error Handling and Validation</h2>
 * 
 * <p><strong>Required Configuration Validation:</strong>
 * <pre>
 * public class CriticalConfigStep implements Step {
 *     {@code @ConfigValue("api_key")}        // required=true by default
 *     private String apiKey;                  // Throws exception if missing
 *     
 *     {@code @ConfigValue(value = "optional_feature", required = false)}
 *     private String feature;                 // null if missing, no exception
 * }
 * </pre>
 * 
 * <p>When required configuration is missing, the framework provides detailed error information:
 * <pre>
 * IllegalStateException: Required @ConfigValue 'api_key' not found in step config, 
 *                       global settings path '', or default value for field 'apiKey' 
 *                       in com.example.CriticalConfigStep
 * </pre>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Use Meaningful Names:</strong> Choose descriptive configuration keys</li>
 *   <li><strong>Provide Sensible Defaults:</strong> Always specify {@code defaultValue} for optional config</li>
 *   <li><strong>Document Global Paths:</strong> Clearly document expected global settings structure</li>
 *   <li><strong>Separate Concerns:</strong> Use {@code @ConfigValue} for static config, {@code @Inject} for runtime data</li>
 *   <li><strong>Validate Types:</strong> Use specific types for better validation and IDE support</li>
 *   <li><strong>Group Related Config:</strong> Use hierarchical global paths for related settings</li>
 * </ul>
 * 
 * <h2>Integration Patterns</h2>
 * 
 * <p><strong>Mixed Injection Types:</strong>
 * <pre>
 * {@code @StepComponent(name = "hybridStep")}
 * public class HybridProcessingStep implements Step {
 *     // Static configuration (doesn't change during execution)
 *     {@code @ConfigValue("timeout")}
 *     private Integer timeout;
 *     
 *     {@code @ConfigValue(value = "service_url", globalPath = "services.processing.url")}
 *     private String serviceUrl;
 *     
 *     // Runtime data (changes with each execution)
 *     {@code @Inject("user.id")}
 *     private String userId;
 *     
 *     {@code @Inject("request.payload")}
 *     private Map&lt;String, Object&gt; payload;
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // Use both static config and runtime data
 *         return callService(serviceUrl, userId, payload, timeout);
 *     }
 * }
 * </pre>
 * 
 * @see Inject For injecting runtime execution context data
 * @see StepComponent For marking step implementations
 * @see GuardComponent For marking guard implementations
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigValue {
    /**
     * The configuration key to look for in the step's configuration.
     * 
     * <p>If not specified, the field name will be used as the configuration key.
     * This key is used to lookup values in the step's merged configuration,
     * which includes step-specific config merged with applicable defaults.
     * 
     * <p><strong>Key Resolution Examples:</strong>
     * <pre>
     * {@code @ConfigValue}                  // Uses field name
     * private String timeout;               // Looks for "timeout" in step config
     * 
     * {@code @ConfigValue("max_retries")}   // Uses explicit key
     * private Integer retries;              // Looks for "max_retries" in step config
     * 
     * {@code @ConfigValue("api.endpoint")}  // Hierarchical key
     * private String endpoint;              // Looks for "api.endpoint" in step config
     * </pre>
     * 
     * @return the configuration key, or empty string to use the field name
     */
    String value() default "";
    
    /**
     * Dot-separated path to look for this value in global settings.
     * 
     * <p>This path is used as a fallback when the configuration key is not found
     * in the step's configuration. The path supports nested navigation through
     * the global settings structure using dot notation.
     * 
     * <p><strong>Path Navigation Examples:</strong>
     * <pre>
     * // Global settings structure:
     * settings:
     *   database:
     *     connection:
     *       timeout: 30000
     *       pool_size: 10
     *   services:
     *     payment:
     *       gateway_url: "https://api.stripe.com"
     *       api_version: "2020-08-27"
     * 
     * // Using global paths:
     * {@code @ConfigValue(value = "db_timeout", globalPath = "database.connection.timeout")}
     * private Integer dbTimeout;        // Gets 30000
     * 
     * {@code @ConfigValue(value = "pool_size", globalPath = "database.connection.pool_size")}
     * private Integer poolSize;         // Gets 10
     * 
     * {@code @ConfigValue(value = "gateway", globalPath = "services.payment.gateway_url")}
     * private String gateway;           // Gets "https://api.stripe.com"
     * </pre>
     * 
     * <p><strong>Fallback Behavior:</strong>
     * <ol>
     *   <li>Check step config for the {@link #value()} key</li>
     *   <li>If not found and globalPath is specified, navigate global settings</li>
     *   <li>If still not found, use {@link #defaultValue()} if provided</li>
     *   <li>If required=true and no value found, throw exception</li>
     * </ol>
     * 
     * @return the dot-separated path in global settings, or empty string for no global lookup
     */
    String globalPath() default "";
    
    /**
     * Whether this configuration value is required for the component to function properly.
     * 
     * <p>When {@code true} (default), the framework will throw an exception during
     * component initialization if the configuration value cannot be found through:
     * <ul>
     *   <li>Step configuration lookup using {@link #value()}</li>
     *   <li>Global settings path lookup using {@link #globalPath()}</li>
     *   <li>Default value from {@link #defaultValue()}</li>
     * </ul>
     * 
     * <p>When {@code false}, missing configuration values will result in {@code null}
     * being injected, or the {@link #defaultValue()} if specified.
     * 
     * <p><strong>Required Configuration:</strong>
     * <pre>
     * {@code @ConfigValue("api_key")}  // required=true by default
     * private String apiKey;           // Exception if "api_key" not configured
     * </pre>
     * 
     * <p><strong>Optional Configuration:</strong>
     * <pre>
     * {@code @ConfigValue(value = "cache_enabled", required = false, defaultValue = "true")}
     * private Boolean cacheEnabled;    // Uses default "true" if not configured
     * 
     * {@code @ConfigValue(value = "optional_feature", required = false)}
     * private String feature;          // null if not configured
     * </pre>
     * 
     * @return {@code true} if the configuration value is required, {@code false} if optional
     */
    boolean required() default true;
    
    /**
     * Default value to use when the configuration value cannot be found and {@link #required()} is {@code false}.
     * 
     * <p>This default value is used as the final fallback in the configuration resolution chain:
     * <ol>
     *   <li>Step configuration lookup using {@link #value()}</li>
     *   <li>Global settings lookup using {@link #globalPath()}</li>
     *   <li><strong>Default value (this parameter)</strong></li>
     * </ol>
     * 
     * <p>The framework performs automatic type conversion from the string default value
     * to the target field type using standard coercion rules:
     * 
     * <p><strong>Supported Type Conversions:</strong>
     * <ul>
     *   <li>{@code String} → no conversion needed</li>
     *   <li>{@code Integer/int} → {@code Integer.parseInt(defaultValue)}</li>
     *   <li>{@code Long/long} → {@code Long.parseLong(defaultValue)}</li>
     *   <li>{@code Double/double} → {@code Double.parseDouble(defaultValue)}</li>
     *   <li>{@code Float/float} → {@code Float.parseFloat(defaultValue)}</li>
     *   <li>{@code Boolean/boolean} → {@code Boolean.parseBoolean(defaultValue)}</li>
     * </ul>
     * 
     * <p><strong>Default Value Examples:</strong>
     * <pre>
     * {@code @ConfigValue(value = "timeout", defaultValue = "5000")}
     * private Integer timeout;         // Integer 5000
     * 
     * {@code @ConfigValue(value = "rate_limit", defaultValue = "10.5")}
     * private Double rateLimit;        // Double 10.5
     * 
     * {@code @ConfigValue(value = "debug_mode", defaultValue = "false")}
     * private Boolean debugMode;       // Boolean false
     * 
     * {@code @ConfigValue(value = "environment", defaultValue = "development")}
     * private String environment;      // String "development"
     * </pre>
     * 
     * <p><strong>Important Notes:</strong>
     * <ul>
     *   <li>If {@link #required()} is {@code true}, this value is ignored</li>
     *   <li>Empty string means no default value (null will be injected)</li>
     *   <li>Type conversion failures will result in the raw string being used</li>
     * </ul>
     * 
     * @return the default value as a string, or empty string for no default
     */
    String defaultValue() default "";
}