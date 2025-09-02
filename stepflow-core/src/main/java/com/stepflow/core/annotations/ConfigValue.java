package com.stepflow.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject configuration values from step config or global settings in YAML.
 * Lookup order: 1) Step config, 2) Global settings (if globalPath specified), 3) Default value
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigValue {
    /**
     * The configuration key to inject from step config.
     * If not specified, the field/parameter name will be used.
     */
    String value() default "";
    
    /**
     * Path to look for this value in global settings (e.g., "validation.min_order_value").
     * If step config doesn't have the value, will look here.
     */
    String globalPath() default "";
    
    /**
     * Whether this configuration value is required.
     */
    boolean required() default true;
    
    /**
     * Default value to use if the configuration value is not found and required=false.
     */
    String defaultValue() default "";
}