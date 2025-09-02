package com.stepflow.execution;

/**
 * Interface for workflow guards.
 */
public interface Guard {
    boolean evaluate(ExecutionContext ctx);
}