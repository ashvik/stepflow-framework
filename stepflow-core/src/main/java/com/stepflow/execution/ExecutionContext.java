package com.stepflow.execution;

import java.util.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Execution context that extends HashMap and provides convenient typed getters.
 * Supports all primitive types, collections, and generic types.
 */
public class ExecutionContext extends HashMap<String, Object> {
    
    private final Map<String, Object> metadata = new HashMap<>();
    
    // ======================================================================================
    // PRIMITIVE TYPE GETTERS
    // ======================================================================================
    
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
    
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
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
    
    public boolean hasValue(String key) {
        return containsKey(key) && get(key) != null;
    }
    
    public boolean isEmpty(String key) {
        Object value = get(key);
        if (value == null) return true;
        
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        
        return false;
    }
    
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