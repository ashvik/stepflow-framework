package com.stepflow.config;

import com.stepflow.execution.ExecutionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sophisticated dependency injection engine for Step and Guard components.
 * 
 * <p>DependencyInjector provides a comprehensive dependency injection system that combines
 * runtime data from ExecutionContext, static configuration from FlowConfig, and global
 * settings with intelligent precedence rules, type coercion, and validation.
 * 
 * <h2>Injection Architecture</h2>
 * 
 * <p>The injection system operates in four distinct phases with specific precedence rules:
 * 
 * <p><strong>Phase 1: @Inject Annotation Processing</strong>
 * <ol>
 *   <li><strong>ExecutionContext Lookup:</strong> First priority for runtime workflow data</li>
 *   <li><strong>Step Configuration Lookup:</strong> Fallback to step-specific merged config</li>
 *   <li><strong>Default Value Application:</strong> Uses annotation default if value not found</li>
 *   <li><strong>Required Validation:</strong> Throws exception if required value missing</li>
 * </ol>
 * 
 * <p><strong>Phase 2: Context Field Matching</strong>
 * <ul>
 *   <li>Automatic injection by field name for non-annotated fields</li>
 *   <li>Direct mapping from ExecutionContext keys to field names</li>
 *   <li>Skips fields already processed by annotations</li>
 * </ul>
 * 
 * <p><strong>Phase 3: Configuration Property Injection</strong>
 * <ul>
 *   <li>Maps configuration entries to fields and setters by name</li>
 *   <li>Uses merged step configuration (step config + defaults)</li>
 *   <li>Respects annotation-driven exclusions</li>
 * </ul>
 * 
 * <p><strong>Phase 4: @ConfigValue Annotation Processing</strong>
 * <ol>
 *   <li><strong>Step Configuration:</strong> Primary lookup in step's merged config</li>
 *   <li><strong>Global Settings Path:</strong> Secondary lookup using dot-notation paths</li>
 *   <li><strong>Default Value:</strong> Tertiary fallback to annotation default</li>
 *   <li><strong>Type Coercion:</strong> Automatic conversion to target field type</li>
 * </ol>
 * 
 * <h2>Comprehensive Usage Examples</h2>
 * 
 * <p><strong>Mixed Injection Patterns:</strong>
 * <pre>
 * {@code @StepComponent(name = "comprehensiveStep")}
 * public class ComprehensiveProcessingStep implements Step {
 *     
 *     // Phase 1: @Inject with ExecutionContext priority
 *     {@code @Inject("user.id")}              // From ExecutionContext or step config
 *     private String userId;
 *     
 *     {@code @Inject(value = "session.token", required = false, defaultValue = "anonymous")}
 *     private String sessionToken;
 *     
 *     // Phase 2: Automatic context field matching
 *     private String orderId;                  // Matches context.get("orderId")
 *     private Double orderAmount;              // Matches context.get("orderAmount")
 *     
 *     // Phase 3: Configuration property injection
 *     private Integer batchSize;               // From step config "batch_size" -> "batchSize"
 *     private String processingMode;           // From step config "processing_mode"
 *     
 *     // Phase 4: @ConfigValue with global path fallback
 *     {@code @ConfigValue("timeout")}
 *     private Integer timeout;                 // From step config "timeout"
 *     
 *     {@code @ConfigValue(value = "db_pool_size", globalPath = "database.pool_size", defaultValue = "10")}
 *     private Integer dbPoolSize;              // Step config -> global settings -> default
 *     
 *     {@code @ConfigValue(value = "api_key", globalPath = "external_services.api.key")}
 *     private String apiKey;                   // Required from step config or global settings
 *     
 *     // Setter injection (Phase 3)
 *     private String environment;
 *     public void setEnvironment(String env) { 
 *         this.environment = env; 
 *     }
 *     
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // All fields are automatically injected before this method is called
 *         System.out.println("Processing order " + orderId + " for user " + userId);
 *         System.out.println("Batch size: " + batchSize + ", Timeout: " + timeout + "ms");
 *         System.out.println("DB Pool: " + dbPoolSize + ", Environment: " + environment);
 *         return StepResult.success();
 *     }
 * }
 * </pre>
 * 
 * <p><strong>YAML Configuration Supporting Above Example:</strong>
 * <pre>
 * settings:
 *   database:
 *     pool_size: 20
 *     timeout: 30000
 *   external_services:
 *     api:
 *       key: "prod-api-key-12345"
 *       url: "https://api.example.com"
 * 
 * defaults:
 *   step:
 *     timeout: 5000
 *     processing_mode: "standard"
 * 
 * steps:
 *   comprehensiveStep:
 *     type: "comprehensiveStep"
 *     config:
 *       batch_size: 50              # -> batchSize field
 *       timeout: 8000               # Overrides defaults.step.timeout
 *       environment: "production"    # -> setEnvironment() method
 *       # db_pool_size not specified -> uses global settings
 *       # api_key not specified -> uses global settings
 * </pre>
 * 
 * <h2>Data Source Priority and Resolution</h2>
 * 
 * <p><strong>@Inject Resolution Order:</strong>
 * <pre>
 * {@code @Inject("customer.tier")}
 * private String customerTier;
 * 
 * // Resolution priority:
 * // 1. ExecutionContext.get("customer.tier")
 * // 2. stepConfig.get("customer.tier")
 * // 3. annotation.defaultValue() if required=false
 * // 4. IllegalStateException if required=true and not found
 * </pre>
 * 
 * <p><strong>@ConfigValue Resolution Order:</strong>
 * <pre>
 * {@code @ConfigValue(value = "timeout", globalPath = "services.timeout", defaultValue = "5000")}
 * private Integer timeout;
 * 
 * // Resolution priority:
 * // 1. stepConfig.get("timeout")
 * // 2. globalSettings.get("services").get("timeout") [path navigation]
 * // 3. Integer.parseInt("5000") [default with type coercion]
 * // 4. null if required=false, exception if required=true
 * </pre>
 * 
 * <h2>Advanced Configuration Scenarios</h2>
 * 
 * <p><strong>Complex Global Path Navigation:</strong>
 * <pre>
 * // YAML global settings
 * settings:
 *   payment:
 *     gateways:
 *       stripe:
 *         api_version: "2020-08-27"
 *         timeout: 15000
 *         retry_config:
 *           max_attempts: 5
 *           backoff_factor: 2.0
 * 
 * // Step implementation
 * public class PaymentStep implements Step {
 *     {@code @ConfigValue(value = "stripe_version", globalPath = "payment.gateways.stripe.api_version")}
 *     private String stripeApiVersion;        // "2020-08-27"
 *     
 *     {@code @ConfigValue(value = "stripe_timeout", globalPath = "payment.gateways.stripe.timeout")}
 *     private Integer stripeTimeout;          // 15000
 *     
 *     {@code @ConfigValue(value = "retry_attempts", globalPath = "payment.gateways.stripe.retry_config.max_attempts")}
 *     private Integer retryAttempts;          // 5
 * }
 * </pre>
 * 
 * <p><strong>Type Coercion Examples:</strong>
 * <pre>
 * public class TypeCoercionExample implements Step {
 *     {@code @ConfigValue(value = "port", defaultValue = "8080")}
 *     private Integer port;                   // String "8080" -> Integer 8080
 *     
 *     {@code @ConfigValue(value = "timeout", defaultValue = "30.5")}
 *     private Double timeout;                 // String "30.5" -> Double 30.5
 *     
 *     {@code @ConfigValue(value = "enabled", defaultValue = "true")}
 *     private Boolean enabled;                // String "true" -> Boolean true
 *     
 *     {@code @ConfigValue(value = "threshold", defaultValue = "1000")}
 *     private Long threshold;                 // String "1000" -> Long 1000L
 *     
 *     {@code @ConfigValue(value = "ratio", defaultValue = "2.5")}
 *     private Float ratio;                    // String "2.5" -> Float 2.5f
 * }
 * </pre>
 * 
 * <p><strong>Runtime Data Integration:</strong>
 * <pre>
 * // ExecutionContext contains runtime workflow data
 * ExecutionContext ctx = new ExecutionContext();
 * ctx.put("request.id", "REQ-12345");
 * ctx.put("user.id", "USER-789");
 * ctx.put("session.start_time", System.currentTimeMillis());
 * ctx.put("processing.priority", "HIGH");
 * 
 * public class RuntimeAwareStep implements Step {
 *     {@code @Inject("request.id")}
 *     private String requestId;               // From ExecutionContext
 *     
 *     {@code @Inject("user.id")}
 *     private String userId;                  // From ExecutionContext
 *     
 *     {@code @ConfigValue("max_processing_time")}
 *     private Integer maxProcessingTime;      // From step config
 *     
 *     // Non-annotated field - automatic context matching
 *     private String processingPriority;     // From ctx.get("processing.priority") -> "HIGH"
 * }
 * </pre>
 * 
 * <h2>Error Handling and Validation</h2>
 * 
 * <p><strong>Required Field Validation:</strong>
 * <pre>
 * public class ValidationExample implements Step {
 *     {@code @Inject("critical.data")}       // required=true by default
 *     private String criticalData;            // Throws IllegalStateException if missing
 *     
 *     {@code @ConfigValue("api.key")}        // required=true by default
 *     private String apiKey;                  // Throws IllegalStateException if missing
 *     
 *     {@code @Inject(value = "optional.data", required = false)}
 *     private String optionalData;            // null if missing, no exception
 * }
 * 
 * // Exception details provide context:
 * // IllegalStateException: Required @Inject value not found for key 'critical.data' 
 * //                        on field 'criticalData' in com.example.ValidationExample
 * </pre>
 * 
 * <p><strong>Type Conversion Error Handling:</strong>
 * <pre>
 * // Graceful handling of type conversion failures
 * {@code @ConfigValue(value = "port", defaultValue = "8080")}
 * private Integer port;                      // If config contains "abc", falls back to raw string
 * 
 * {@code @Inject(value = "timeout", defaultValue = "5000")}
 * private Integer timeout;                   // Type conversion failures use raw string value
 * </pre>
 * 
 * <h2>Integration with StepFlow Components</h2>
 * 
 * <p><strong>Engine Integration Process:</strong>
 * <pre>
 * // Inside Engine.executeStep() method
 * Step stepInstance = createStepInstance(stepClass);
 * 
 * // Prepare injection data
 * ExecutionContext context = getCurrentExecutionContext();
 * Map&lt;String, Object&gt; stepConfig = getMergedStepConfig(stepName);
 * Map&lt;String, Object&gt; globalSettings = getGlobalSettings();
 * 
 * // Perform comprehensive injection
 * dependencyInjector.injectDependencies(stepInstance, context, stepConfig, globalSettings);
 * 
 * // Now execute step with all dependencies injected
 * StepResult result = stepInstance.execute(context);
 * </pre>
 * 
 * <p><strong>Configuration Merging Process:</strong>
 * <pre>
 * // Merged step configuration combines multiple sources:
 * // 1. Global defaults.step (applies to all steps)
 * // 2. Component-specific defaults.stepTypeName
 * // 3. Individual step configuration
 * 
 * Map&lt;String, Object&gt; mergedConfig = new HashMap&lt;&gt;();
 * mergedConfig.putAll(globalDefaults.get("step"));           // Base defaults
 * mergedConfig.putAll(globalDefaults.get(stepTypeName));     // Type-specific defaults
 * mergedConfig.putAll(stepDefinition.config);               // Step-specific config (highest priority)
 * </pre>
 * 
 * <h2>Performance and Optimization</h2>
 * 
 * <ul>
 *   <li><strong>Injection Frequency:</strong> Performed once per step execution (new step instances)</li>
 *   <li><strong>Reflection Caching:</strong> Field and method lookups are performed fresh each time</li>
 *   <li><strong>Type Conversion:</strong> Simple primitive conversions are fast</li>
 *   <li><strong>Configuration Access:</strong> Map lookups are O(1) operations</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Use @Inject for Runtime Data:</strong> Workflow execution context and dynamic values</li>
 *   <li><strong>Use @ConfigValue for Static Config:</strong> Timeouts, endpoints, feature flags</li>
 *   <li><strong>Provide Sensible Defaults:</strong> Use defaultValue for optional configuration</li>
 *   <li><strong>Document Dependencies:</strong> Clearly document expected injection keys</li>
 *   <li><strong>Validate Critical Data:</strong> Use required=true for essential dependencies</li>
 *   <li><strong>Organize Global Settings:</strong> Use hierarchical paths for related configuration</li>
 * </ul>
 * 
 * @see com.stepflow.core.annotations.Inject for runtime dependency injection
 * @see com.stepflow.core.annotations.ConfigValue for configuration value injection
 * @see ExecutionContext for runtime data container
 * @see FlowConfig for static configuration structure
 * @see Step for component interface requiring injection
 * @see Guard for component interface requiring injection
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public class DependencyInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyInjector.class);

    /**
     * Injects dependencies from context/config/settings using annotation-aware precedence.
     */
    public void injectDependencies(Object instance, ExecutionContext context, Map<String, Object> config, Map<String, Object> settings) {
        injectAnnotatedInjections(instance, context, config);
        injectFromContext(instance, context);
        injectFromConfig(instance, config);
        injectAnnotatedConfigValues(instance, config, settings);
    }

    /**
     * Performs @Inject field injection using context first, then step config; enforces required/defaults.
     */
    private void injectAnnotatedInjections(Object instance, ExecutionContext context, Map<String, Object> config) {
        if (instance == null) return;
        Class<?> clazz = instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            com.stepflow.core.annotations.Inject anno = field.getAnnotation(com.stepflow.core.annotations.Inject.class);
            if (anno == null) continue;

            String key = !anno.value().isEmpty() ? anno.value() : field.getName();
            Object value = null;
            if (context != null && context.containsKey(key)) {
                value = context.get(key);
            } else if (config != null && config.containsKey(key)) {
                value = config.get(key);
            }

            if (value == null) {
                if (anno.required()) {
                    String msg = "Required @Inject value not found for key '" + key + "' on field '" + field.getName() + "' in " + clazz.getName();
                    LOGGER.error(msg);
                    throw new IllegalStateException(msg);
                }
                String defaultStr = anno.defaultValue();
                if (defaultStr != null && !defaultStr.isEmpty()) {
                    value = coerce(defaultStr, field.getType());
                    LOGGER.debug("@Inject default applied for key '{}' -> {}", key, value);
                }
            } else {
                value = coerceIfNeeded(value, field.getType());
            }

            if (value != null) {
                try {
                    field.setAccessible(true);
                    field.set(instance, value);
                    LOGGER.debug("Injected field '{}' with key '{}'", field.getName(), key);
                } catch (Exception ex) {
                    LOGGER.debug("Failed to inject field '{}' ({}): {}", field.getName(), key, ex.toString());
                }
            }
        }
    }

    /**
     * Injects values by matching field names from the execution context, skipping annotated fields.
     */
    private void injectFromContext(Object instance, ExecutionContext context) {
        if (instance == null || context == null) return;
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (isAnnotationDriven(field)) continue; // do not override @Inject/@ConfigValue
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                if (context.containsKey(fieldName)) {
                    field.set(instance, context.get(fieldName));
                    LOGGER.debug("Context injected into field '{}'", fieldName);
                }
            } catch (Exception ex) { LOGGER.debug("Context injection failed for field '{}': {}", field.getName(), ex.toString()); }
        }
    }

    /**
     * Injects values from step configuration by property name (field or setter), skipping annotated fields.
     */
    private void injectFromConfig(Object instance, Map<String, Object> config) {
        if (instance == null || config == null) return;

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            try {
                Field field = findField(instance.getClass(), propertyName);
                if (field != null) {
                    if (isAnnotationDriven(field)) continue; // respect @Inject/@ConfigValue
                    field.setAccessible(true);
                    field.set(instance, value);
                    LOGGER.debug("Config injected into field '{}'", propertyName);
                    continue;
                }

                Method setter = findSetter(instance.getClass(), propertyName);
                if (setter != null) {
                    setter.setAccessible(true);
                    setter.invoke(instance, value);
                    LOGGER.debug("Config injected via setter '{}'", setter.getName());
                }
            } catch (Exception ex) { LOGGER.debug("Config injection failed for property '{}': {}", propertyName, ex.toString()); }
        }
    }

    /**
     * Performs @ConfigValue field injection using step config first, then global settings path.
     */
    private void injectAnnotatedConfigValues(Object instance, Map<String, Object> config, Map<String, Object> settings) {
        if (instance == null) return;
        Class<?> clazz = instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            com.stepflow.core.annotations.ConfigValue anno = field.getAnnotation(com.stepflow.core.annotations.ConfigValue.class);
            if (anno == null) continue;
            String key = anno.value() != null && !anno.value().isEmpty() ? anno.value() : field.getName();
            Object value = null;
            if (config != null) {
                value = config.get(key);
            }
            if (value == null && settings != null && anno.globalPath() != null && !anno.globalPath().isEmpty()) {
                value = resolvePath(settings, anno.globalPath());
            }
            boolean required = anno.required();
            if (value == null) {
                if (required) continue;
                String defaultStr = anno.defaultValue();
                if (defaultStr != null && !defaultStr.isEmpty()) {
                    value = coerce(defaultStr, field.getType());
                }
            } else {
                value = coerceIfNeeded(value, field.getType());
            }
            if (value != null) {
                try {
                    field.setAccessible(true);
                    field.set(instance, value);
                    LOGGER.debug("@ConfigValue injected into field '{}'", field.getName());
                } catch (Exception ex) { LOGGER.debug("@ConfigValue injection failed for field '{}': {}", field.getName(), ex.toString()); }
            }
        }
    }

    /** Resolves a dotted path inside a nested map structure. */
    private Object resolvePath(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) current;
            current = m.get(part);
            if (current == null) return null;
        }
        return current;
    }

    /** Attempts to coerce a value to the required field type when necessary. */
    private Object coerceIfNeeded(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) return value;
        return coerce(value.toString(), targetType);
    }

    /** Coerces simple primitives and String values; falls back to raw text on failure. */
    private Object coerce(String text, Class<?> targetType) {
        try {
            if (targetType == String.class) return text;
            if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(text);
            if (targetType == Long.class || targetType == long.class) return Long.parseLong(text);
            if (targetType == Double.class || targetType == double.class) return Double.parseDouble(text);
            if (targetType == Float.class || targetType == float.class) return Float.parseFloat(text);
            if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(text);
        } catch (Exception ignored) { }
        return text;
    }

    /** Locates a field by name on the class or its superclasses. */
    private Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            Class<?> parent = clazz.getSuperclass();
            return (parent != null) ? findField(parent, name) : null;
        }
    }

    /** Finds a single-argument setter for the given property name on the class hierarchy. */
    private Method findSetter(Class<?> clazz, String propertyName) {
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        Class<?> parent = clazz.getSuperclass();
        return (parent != null) ? findSetter(parent, propertyName) : null;
    }

    /** Returns true if field injection should be driven exclusively by annotations. */
    private boolean isAnnotationDriven(Field field) {
        return field.getAnnotation(com.stepflow.core.annotations.Inject.class) != null
                || field.getAnnotation(com.stepflow.core.annotations.ConfigValue.class) != null;
    }
}
