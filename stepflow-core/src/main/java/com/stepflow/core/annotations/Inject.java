package com.stepflow.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields or constructor parameters for dependency injection.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    /**
     * The key to inject from the execution context or configuration.
     * If not specified, the field/parameter name will be used.
     */
    String value() default "";
    
    /**
     * Whether this injection is required. If true and the value is not found,
     * an exception will be thrown. If false, null will be injected.
     */
    boolean required() default true;
    
    /**
     * Default value to use if the injected value is not found and required=false.
     */
    String defaultValue() default "";
}