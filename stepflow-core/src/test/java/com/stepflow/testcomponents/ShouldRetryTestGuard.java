package com.stepflow.testcomponents;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

@GuardComponent(name = "shouldRetry")
public class ShouldRetryTestGuard implements Guard {
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        return true;
    }
}

