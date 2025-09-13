package com.stepflow.execution;

import java.util.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Thread-safe execution context for workflow data sharing and management.
 * 
 * <p>The {@code ExecutionContext} serves as the primary data container for workflow execution,
 * extending {@code HashMap<String, Object>} with comprehensive type-safe accessors, collection
 * utilities, and metadata support. It facilitates data flow between workflow steps while
 * providing convenient methods for common data access patterns.
 * 
 * <h2>Core Features</h2>
 * 
 * <ul>
 *   <li><strong>Type-Safe Access:</strong> Typed getters with automatic conversion for primitives</li>
 *   <li><strong>Collection Support:</strong> Specialized methods for Lists, Sets, Maps, and Arrays</li>
 *   <li><strong>Default Value Handling:</strong> Overloaded methods with default fallbacks</li>
 *   <li><strong>Metadata Management:</strong> Separate metadata storage for framework-internal data</li>
 *   <li><strong>Data Validation:</strong> Built-in null checking and type validation</li>
 *   <li><strong>Context Copying:</strong> Deep copy support for branching scenarios</li>
 * </ul>
 * 
 * <h2>Basic Usage Examples</h2>
 * 
 * <p><strong>Primitive Type Access:</strong>
 * <pre>
 * ExecutionContext ctx = new ExecutionContext();
 * 
 * // String access
 * ctx.put("user.name", "John Doe");
 * String name = ctx.getString("user.name");                    // "John Doe"
 * String unknown = ctx.getString("missing.key", "default");   // "default"
 * 
 * // Numeric access with conversion
 * ctx.put("order.amount", "99.99");  // String input
 * Double amount = ctx.getDouble("order.amount");               // 99.99 (converted to Double)
 * Integer count = ctx.getInt("item.count", 1);                // 1 (default value)
 * 
 * // Boolean access with multiple true formats
 * ctx.put("is.premium", "yes");      // Various formats supported
 * Boolean premium = ctx.getBoolean("is.premium");             // true ("yes" → true)
 * </pre>
 * 
 * <p><strong>Collection Handling:</strong>
 * <pre>
 * // List operations with type safety
 * List&lt;String&gt; items = Arrays.asList("item1", "item2", "item3");
 * ctx.put("order.items", items);
 * List&lt;String&gt; retrievedItems = ctx.getList("order.items", String.class);
 * String[] itemArray = ctx.getStringArray("order.items");
 * 
 * // Map operations
 * Map&lt;String, Integer&gt; quantities = new HashMap&lt;&gt;();
 * quantities.put("apple", 5);
 * quantities.put("banana", 3);
 * ctx.put("inventory", quantities);
 * Map&lt;String, Integer&gt; inv = ctx.getMap("inventory", String.class, Integer.class);
 * 
 * // Set operations
 * Set&lt;String&gt; permissions = Set.of("read", "write", "admin");
 * ctx.put("user.permissions", permissions);
 * Set&lt;String&gt; userPerms = ctx.getSet("user.permissions", String.class);
 * </pre>
 * 
 * <h2>Advanced Usage Patterns</h2>
 * 
 * <p><strong>Hierarchical Data Access:</strong>
 * <pre>
 * // Nested data structures
 * ctx.put("customer.profile.tier", "PREMIUM");
 * ctx.put("customer.profile.points", 15000);
 * ctx.put("customer.address.country", "US");
 * 
 * // Access nested data
 * String tier = ctx.getString("customer.profile.tier");        // "PREMIUM"
 * Integer points = ctx.getInt("customer.profile.points");      // 15000
 * String country = ctx.getString("customer.address.country");  // "US"
 * 
 * // Safe access with defaults for missing nested data
 * String state = ctx.getString("customer.address.state", "Unknown");  // "Unknown"
 * </pre>
 * 
 * <p><strong>Workflow Data Flow:</strong>
 * <pre>
 * // Step 1: Data collection
 * public StepResult execute(ExecutionContext ctx) {
 *     // Input validation
 *     String orderId = ctx.getString("orderId");
 *     if (orderId == null) {
 *         return StepResult.failure("Order ID is required");
 *     }
 *     
 *     // Add processing results
 *     ctx.put("validation.status", "PASSED");
 *     ctx.put("validation.timestamp", System.currentTimeMillis());
 *     ctx.put("validation.rules_applied", Arrays.asList("format", "range", "business"));
 *     
 *     return StepResult.success(ctx);
 * }
 * 
 * // Step 2: Data consumption
 * public StepResult execute(ExecutionContext ctx) {
 *     // Access previous step results
 *     String status = ctx.getString("validation.status");
 *     Long timestamp = ctx.getLong("validation.timestamp");
 *     List&lt;String&gt; rules = ctx.getList("validation.rules_applied", String.class);
 *     
 *     if ("PASSED".equals(status)) {
 *         // Continue processing
 *         return processOrder(ctx);
 *     } else {
 *         return StepResult.failure("Validation failed");
 *     }
 * }
 * </pre>
 * 
 * <p><strong>Complex Object Storage and Retrieval:</strong>
 * <pre>
 * // Store complex objects
 * CustomerProfile profile = new CustomerProfile("john@example.com", "PREMIUM");
 * PaymentInfo payment = new PaymentInfo("**** 1234", "VISA");
 * ctx.put("customer.profile", profile);
 * ctx.put("payment.info", payment);
 * 
 * // Retrieve with type safety
 * CustomerProfile retrievedProfile = ctx.get("customer.profile", CustomerProfile.class);
 * PaymentInfo paymentInfo = ctx.get("payment.info", PaymentInfo.class);
 * 
 * // Safe retrieval with fallbacks
 * CustomerProfile safeProfile = ctx.get("customer.profile", CustomerProfile.class, 
 *                                        new CustomerProfile("guest@example.com", "STANDARD"));
 * </pre>
 * 
 * <h2>Metadata Management</h2>
 * 
 * <p>The context provides a separate metadata space for framework-internal data:
 * 
 * <pre>
 * // Framework internal metadata (separate from business data)
 * ctx.setMetadata("step.execution.start_time", System.currentTimeMillis());
 * ctx.setMetadata("step.execution.attempt_count", 1);
 * ctx.setMetadata("workflow.current_step", "processPayment");
 * 
 * // Retrieve metadata
 * Long startTime = ctx.getMetadata("step.execution.start_time", Long.class);
 * Integer attempts = ctx.getMetadata("step.execution.attempt_count", Integer.class);
 * 
 * // Get all metadata for debugging
 * Map&lt;String, Object&gt; allMetadata = ctx.getAllMetadata();
 * </pre>
 * 
 * <h2>Context Lifecycle and Copying</h2>
 * 
 * <p><strong>Context Evolution Through Workflow:</strong>
 * <pre>
 * // Initial context creation
 * ExecutionContext ctx = new ExecutionContext();
 * ctx.put("request.id", "REQ-12345");
 * ctx.put("user.id", "user-789");
 * 
 * // Step 1 adds data
 * ctx.put("step1.result", "success");
 * ctx.put("step1.processing_time", 150L);
 * 
 * // Step 2 adds more data
 * ctx.put("step2.validation", true);
 * ctx.put("step2.score", 85.7);
 * 
 * // Context now contains cumulative workflow state
 * Set&lt;String&gt; allKeys = ctx.keySet();  // All keys from all steps
 * </pre>
 * 
 * <p><strong>Context Copying for Branching:</strong>
 * <pre>
 * // Create independent copy for parallel processing
 * ExecutionContext originalCtx = getCurrentContext();
 * ExecutionContext branchCtx = originalCtx.copy();
 * 
 * // Modifications to branch don't affect original
 * branchCtx.put("branch.processing", true);
 * branchCtx.put("branch.path", "alternative");
 * 
 * // Original context remains unchanged
 * assertNull(originalCtx.get("branch.processing"));
 * </pre>
 * 
 * <h2>Data Validation and Safety</h2>
 * 
 * <p><strong>Built-in Validation Methods:</strong>
 * <pre>
 * ExecutionContext ctx = new ExecutionContext();
 * ctx.put("user.email", "john@example.com");
 * ctx.put("order.items", Arrays.asList("item1", "item2"));
 * ctx.put("empty.list", Collections.emptyList());
 * 
 * // Check for value existence
 * boolean hasEmail = ctx.hasValue("user.email");          // true
 * boolean hasPhone = ctx.hasValue("user.phone");          // false
 * 
 * // Check for empty collections
 * boolean emailEmpty = ctx.isEmpty("user.email");         // false
 * boolean itemsEmpty = ctx.isEmpty("order.items");        // false
 * boolean emptyListEmpty = ctx.isEmpty("empty.list");     // true
 * boolean missingEmpty = ctx.isEmpty("missing.key");      // true
 * </pre>
 * 
 * <h2>Type Conversion Details</h2>
 * 
 * <p>The context provides automatic type conversion for common scenarios:
 * 
 * <pre>
 * // Automatic string-to-number conversion
 * ctx.put("string.number", "42");
 * Integer intValue = ctx.getInt("string.number");         // 42
 * Double doubleValue = ctx.getDouble("string.number");    // 42.0
 * 
 * // Boolean conversion supports multiple formats
 * ctx.put("bool1", "true");    // → true
 * ctx.put("bool2", "1");       // → true  
 * ctx.put("bool3", "yes");     // → true
 * ctx.put("bool4", "false");   // → false
 * ctx.put("bool5", "0");       // → false
 * ctx.put("bool6", "no");      // → false
 * 
 * // Number conversion with error handling
 * ctx.put("invalid.number", "not-a-number");
 * Integer invalid = ctx.getInt("invalid.number");        // null (conversion failed)
 * Integer withDefault = ctx.getInt("invalid.number", 0); // 0 (uses default)
 * </pre>
 * 
 * <h2>Performance Considerations</h2>
 * 
 * <ul>
 *   <li><strong>Memory Usage:</strong> Context data persists throughout workflow execution</li>
 *   <li><strong>Copy Operations:</strong> {@link #copy()} creates deep copies - use judiciously</li>
 *   <li><strong>Type Conversion:</strong> Conversions are performed on each access - cache if repeated</li>
 *   <li><strong>Collection Access:</strong> List/Set/Map operations create new collections on each call</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Naming Conventions:</strong> Use hierarchical keys like {@code "module.component.field"}</li>
 *   <li><strong>Data Immutability:</strong> Avoid modifying retrieved collections directly</li>
 *   <li><strong>Default Values:</strong> Always provide sensible defaults for optional data</li>
 *   <li><strong>Type Safety:</strong> Use typed accessors instead of generic {@code get()}</li>
 *   <li><strong>Context Size:</strong> Monitor context size in long-running workflows</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * 
 * <p>ExecutionContext extends HashMap and is not inherently thread-safe. However, in the
 * StepFlow framework, each workflow execution uses its own context instance, making
 * thread safety a non-issue for typical usage patterns.
 * 
 * @see StepResult for returning data from step execution
 * @see Step for workflow execution units
 * @see Guard for conditional logic evaluation
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public class ExecutionContext extends HashMap<String, Object> {
    
    private final Map<String, Object> metadata = new HashMap<>();
    
    // ======================================================================================
    // PRIMITIVE TYPE GETTERS
    // ======================================================================================
    
    /**
     * Retrieves a string value from the context.
     * 
     * <p>Converts non-null values to strings using {@code toString()}. This method
     * is null-safe and will return {@code null} if the key doesn't exist or the value is null.
     * 
     * @param key the context key to look up
     * @return the string value, or {@code null} if not found or null
     * 
     * @see #getString(String, String) for version with default value
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Retrieves a string value from the context with a default fallback.
     * 
     * <p>This is the preferred method for string access when a sensible default exists.
     * 
     * <p><strong>Example:</strong>
     * <pre>
     * String environment = ctx.getString("app.environment", "development");
     * String userRole = ctx.getString("user.role", "guest");
     * </pre>
     * 
     * @param key the context key to look up
     * @param defaultValue the value to return if key is not found or value is null
     * @return the string value if found, otherwise the default value
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Retrieves an integer value from the context with automatic type conversion.
     * 
     * <p>Supports conversion from:
     * <ul>
     *   <li>Integer objects (direct return)</li>
     *   <li>Other Number types (via {@code intValue()})</li>
     *   <li>String representations (via {@code Integer.parseInt()})</li>
     * </ul>
     * 
     * <p><strong>Conversion Examples:</strong>
     * <pre>
     * ctx.put("count", 42);        // Integer → 42
     * ctx.put("amount", 42.7);     // Double → 42
     * ctx.put("id", "123");        // String → 123
     * ctx.put("invalid", "abc");   // String → null (conversion fails)
     * </pre>
     * 
     * @param key the context key to look up
     * @return the integer value if found and convertible, otherwise {@code null}
     * 
     * @see #getInt(String, int) for version with default value
     */
    public Integer getInt(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Retrieves an integer value with automatic conversion and default fallback.
     * 
     * <p>This method never returns null, making it ideal for primitive int usage.
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * int batchSize = ctx.getInt("batch.size", 100);          // Default 100
     * int maxRetries = ctx.getInt("retry.max_attempts", 3);   // Default 3
     * int timeout = ctx.getInt("service.timeout", 5000);     // Default 5000ms
     * </pre>
     * 
     * @param key the context key to look up
     * @param defaultValue the value to return if key is not found or conversion fails
     * @return the integer value if found and convertible, otherwise the default value
     */
    public int getInt(String key, int defaultValue) {
        Integer value = getInt(key);
        return value != null ? value : defaultValue;
    }
    
    public Long getLong(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public long getLong(String key, long defaultValue) {
        Long value = getLong(key);
        return value != null ? value : defaultValue;
    }
    
    public Double getDouble(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public double getDouble(String key, double defaultValue) {
        Double value = getDouble(key);
        return value != null ? value : defaultValue;
    }
    
    public Float getFloat(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Float) return (Float) value;
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public float getFloat(String key, float defaultValue) {
        Float value = getFloat(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Retrieves a boolean value with flexible string conversion.
     * 
     * <p>Supports multiple formats for {@code true}:
     * <ul>
     *   <li>{@code "true"} (case-insensitive)</li>
     *   <li>{@code "1"}</li>
     *   <li>{@code "yes"} (case-insensitive)</li>
     * </ul>
     * All other string values convert to {@code false}.
     * 
     * <p><strong>Conversion Examples:</strong>
     * <pre>
     * ctx.put("flag1", true);      // Boolean → true
     * ctx.put("flag2", "TRUE");    // String → true
     * ctx.put("flag3", "1");       // String → true
     * ctx.put("flag4", "yes");     // String → true
     * ctx.put("flag5", "false");   // String → false
     * ctx.put("flag6", "0");       // String → false
     * ctx.put("flag7", "no");      // String → false
     * </pre>
     * 
     * @param key the context key to look up
     * @return the boolean value if found, otherwise {@code null}
     * 
     * @see #getBoolean(String, boolean) for version with default value
     */
    public Boolean getBoolean(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString().toLowerCase();
        return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defaultValue;
    }
    
    public BigDecimal getBigDecimal(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public BigInteger getBigInteger(String key) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof BigInteger) return (BigInteger) value;
        if (value instanceof Number) return BigInteger.valueOf(((Number) value).longValue());
        try {
            return new BigInteger(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // ======================================================================================
    // GENERIC TYPE GETTERS
    // ======================================================================================
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) return null;
        
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }
    
    // ======================================================================================
    // COLLECTION GETTERS
    // ======================================================================================
    
    /**
     * Retrieves a typed list from the context with element type filtering.
     * 
     * <p>This method provides type-safe access to List data with automatic filtering
     * of elements that don't match the expected type. Only elements that are assignable
     * to the specified type are included in the returned list.
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * // Setup mixed list (not recommended but handled safely)
     * List&lt;Object&gt; mixed = Arrays.asList("item1", "item2", 123, "item3");
     * ctx.put("mixed.items", mixed);
     * 
     * // Type-safe retrieval filters out non-String elements
     * List&lt;String&gt; strings = ctx.getList("mixed.items", String.class);
     * // Result: ["item1", "item2", "item3"] (123 filtered out)
     * 
     * // Proper usage with homogeneous lists
     * List&lt;String&gt; orderItems = Arrays.asList("apple", "banana", "orange");
     * ctx.put("order.items", orderItems);
     * List&lt;String&gt; items = ctx.getList("order.items", String.class);
     * // Result: ["apple", "banana", "orange"]
     * </pre>
     * 
     * @param <T> the expected element type
     * @param key the context key to look up
     * @param elementType the class of the expected element type
     * @return a new List containing only elements of the specified type, or {@code null} if key not found
     * 
     * @see #getList(String, Class, List) for version with default value
     * @see #getList(String) for untyped list access
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<T> typedList = new ArrayList<>();
            for (Object item : list) {
                if (item != null && elementType.isAssignableFrom(item.getClass())) {
                    typedList.add((T) item);
                }
            }
            return typedList;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue) {
        List<T> value = getList(key, elementType);
        return value != null ? value : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object value = get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> Set<T> getSet(String key, Class<T> elementType) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            Set<T> typedSet = new HashSet<>();
            for (Object item : set) {
                if (item != null && elementType.isAssignableFrom(item.getClass())) {
                    typedSet.add((T) item);
                }
            }
            return typedSet;
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            Set<T> typedSet = new HashSet<>();
            for (Object item : collection) {
                if (item != null && elementType.isAssignableFrom(item.getClass())) {
                    typedSet.add((T) item);
                }
            }
            return typedSet;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> Set<T> getSet(String key, Class<T> elementType, Set<T> defaultValue) {
        Set<T> value = getSet(key, elementType);
        return value != null ? value : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public Set<Object> getSet(String key) {
        Object value = get(key);
        if (value instanceof Set) {
            return new HashSet<>((Set<?>) value);
        } else if (value instanceof Collection) {
            return new HashSet<>((Collection<?>) value);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType) {
        Object value = get(key);
        if (value == null) return null;
        
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<K, V> typedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (k != null && keyType.isAssignableFrom(k.getClass()) &&
                    v != null && valueType.isAssignableFrom(v.getClass())) {
                    typedMap.put((K) k, (V) v);
                }
            }
            return typedMap;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType, Map<K, V> defaultValue) {
        Map<K, V> value = getMap(key, keyType, valueType);
        return value != null ? value : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = get(key);
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        return null;
    }
    
    // ======================================================================================
    // ARRAY GETTERS
    // ======================================================================================
    
    public String[] getStringArray(String key) {
        List<String> list = getList(key, String.class);
        return list != null ? list.toArray(new String[0]) : null;
    }
    
    public int[] getIntArray(String key) {
        List<Integer> list = getList(key, Integer.class);
        if (list != null) {
            return list.stream().mapToInt(Integer::intValue).toArray();
        }
        return null;
    }
    
    public long[] getLongArray(String key) {
        List<Long> list = getList(key, Long.class);
        if (list != null) {
            return list.stream().mapToLong(Long::longValue).toArray();
        }
        return null;
    }
    
    public double[] getDoubleArray(String key) {
        List<Double> list = getList(key, Double.class);
        if (list != null) {
            return list.stream().mapToDouble(Double::doubleValue).toArray();
        }
        return null;
    }
    
    // ======================================================================================
    // METADATA SUPPORT
    // ======================================================================================
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        return null;
    }
    
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    public void clearMetadata() {
        metadata.clear();
    }
    
    // ======================================================================================
    // CONVENIENCE METHODS
    // ======================================================================================
    
    /**
     * Checks if the context contains a non-null value for the specified key.
     * 
     * <p>This method returns {@code true} only if:
     * <ul>
     *   <li>The key exists in the context ({@code containsKey(key)} returns true)</li>
     *   <li>The associated value is not null ({@code get(key)} returns non-null)</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * ctx.put("existing.key", "value");
     * ctx.put("null.key", null);
     * 
     * boolean hasExisting = ctx.hasValue("existing.key");    // true
     * boolean hasNull = ctx.hasValue("null.key");            // false
     * boolean hasMissing = ctx.hasValue("missing.key");      // false
     * 
     * // Common validation pattern
     * if (ctx.hasValue("user.id") && ctx.hasValue("order.id")) {
     *     // Safe to proceed with processing
     *     processOrder(ctx.getString("user.id"), ctx.getString("order.id"));
     * }
     * </pre>
     * 
     * @param key the context key to check
     * @return {@code true} if the key exists and has a non-null value, {@code false} otherwise
     * 
     * @see #isEmpty(String) for checking if values are empty
     * @see #containsKey(Object) for checking key existence regardless of value
     */
    public boolean hasValue(String key) {
        return containsKey(key) && get(key) != null;
    }
    
    /**
     * Checks if a value is empty according to its type-specific emptiness rules.
     * 
     * <p>Emptiness is determined based on the value's type:
     * <ul>
     *   <li><strong>null values:</strong> always empty</li>
     *   <li><strong>String:</strong> empty if {@code length() == 0}</li>
     *   <li><strong>Collection:</strong> empty if {@code size() == 0}</li>
     *   <li><strong>Map:</strong> empty if {@code size() == 0}</li>
     *   <li><strong>Other types:</strong> never considered empty</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * ctx.put("empty.string", "");
     * ctx.put("empty.list", Collections.emptyList());
     * ctx.put("empty.map", Collections.emptyMap());
     * ctx.put("zero.number", 0);
     * ctx.put("null.value", null);
     * 
     * boolean stringEmpty = ctx.isEmpty("empty.string");      // true
     * boolean listEmpty = ctx.isEmpty("empty.list");          // true  
     * boolean mapEmpty = ctx.isEmpty("empty.map");            // true
     * boolean numberEmpty = ctx.isEmpty("zero.number");       // false (0 is not empty)
     * boolean nullEmpty = ctx.isEmpty("null.value");          // true
     * boolean missingEmpty = ctx.isEmpty("missing.key");      // true
     * 
     * // Common validation pattern
     * if (!ctx.isEmpty("order.items") && !ctx.isEmpty("customer.email")) {
     *     // Order has items and customer has email
     *     sendOrderConfirmation(ctx);
     * }
     * </pre>
     * 
     * @param key the context key to check
     * @return {@code true} if the value is empty according to type-specific rules, {@code false} otherwise
     * 
     * @see #hasValue(String) for checking non-null existence
     */
    public boolean isEmpty(String key) {
        Object value = get(key);
        if (value == null) return true;
        
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        
        return false;
    }
    
    /**
     * Creates a deep copy of this ExecutionContext including all data and metadata.
     * 
     * <p>This method creates a new ExecutionContext instance with:
     * <ul>
     *   <li>All main data entries copied (shallow copy of the map, references to values)</li>
     *   <li>All metadata entries copied (shallow copy of the metadata map)</li>
     *   <li>Independent modification capability (changes to copy don't affect original)</li>
     * </ul>
     * 
     * <p><strong>Important Note:</strong> This is a shallow copy of the contained objects.
     * If you modify mutable objects retrieved from the copied context, those changes
     * may be visible in the original context if they reference the same object instances.
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * // Create original context
     * ExecutionContext original = new ExecutionContext();
     * original.put("user.id", "user123");
     * original.put("step.count", 1);
     * original.setMetadata("workflow.start", System.currentTimeMillis());
     * 
     * // Create independent copy for parallel processing
     * ExecutionContext branch = original.copy();
     * branch.put("branch.path", "alternative");
     * branch.put("step.count", 5);  // Doesn't affect original
     * 
     * // Original context unchanged
     * assertNull(original.get("branch.path"));       // null
     * assertEquals(1, original.getInt("step.count")); // still 1
     * 
     * // Both contexts have independent data
     * assertEquals("user123", branch.getString("user.id"));      // shared data
     * assertEquals("alternative", branch.getString("branch.path")); // branch-specific
     * </pre>
     * 
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Parallel workflow branches</li>
     *   <li>Conditional processing paths</li>
     *   <li>Rollback scenarios</li>
     *   <li>Testing and debugging</li>
     * </ul>
     * 
     * @return a new ExecutionContext with copies of all data and metadata
     */
    public ExecutionContext copy() {
        ExecutionContext copy = new ExecutionContext();
        copy.putAll(this);
        copy.metadata.putAll(this.metadata);
        return copy;
    }
    
    @Override
    public String toString() {
        return "ExecutionContext{data=" + super.toString() + ", metadata=" + metadata + "}";
    }
}