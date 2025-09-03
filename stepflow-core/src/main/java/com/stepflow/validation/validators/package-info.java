/**
 * Built-in validators for FlowConfig validation.
 * 
 * <p>This package contains the standard validators that check various aspects of workflow definitions:
 * <ul>
 *   <li>{@link com.stepflow.validation.validators.CycleDetectionValidator} - Detects circular dependencies</li>
 *   <li>{@link com.stepflow.validation.validators.EdgeOrderingValidator} - Validates guard ordering rules</li>
 * </ul>
 * 
 * <p>Custom validators can be created by implementing {@link com.stepflow.validation.FlowConfigValidator}
 * and following the same patterns used by the built-in validators.
 * 
 * @since 0.2.0
 */
package com.stepflow.validation.validators;