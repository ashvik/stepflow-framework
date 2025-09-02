package com.stepflow.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a StepFlow step component.
 * Classes annotated with this will be automatically discovered and registered.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepComponent {
    /**
     * The name for this step component. 
     * If not provided, the class name will be converted to camelCase with first letter lowercase.
     */
    String name() default "";
}