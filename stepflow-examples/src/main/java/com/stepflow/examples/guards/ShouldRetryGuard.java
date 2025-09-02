package com.stepflow.examples.guards;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

@GuardComponent(name = "shouldRetryGuard")
public class ShouldRetryGuard implements Guard {
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        // Simple demo: always allow retry when configured
        return true;
    }
}

