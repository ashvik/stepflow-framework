package com.stepflow.config;

import com.stepflow.execution.ExecutionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles dependency injection for steps and guards.
 *
 * <p>Injection sources and precedence:
 * - @Inject: pulls from ExecutionContext first, then step config; honors required/defaultValue.
 * - Context by field name: sets matching fields from ExecutionContext when not annotated.
 * - Config by name: field or setter injection for entries in merged step config (skips annotated fields).
 * - @ConfigValue: pulls from step config, then global settings path; with optional defaultValue and coercion.
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
