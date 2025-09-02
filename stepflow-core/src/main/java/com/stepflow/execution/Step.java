package com.stepflow.execution;

/**
 * Interface for workflow steps.
 */
public interface Step {
    StepResult execute(ExecutionContext ctx);
}