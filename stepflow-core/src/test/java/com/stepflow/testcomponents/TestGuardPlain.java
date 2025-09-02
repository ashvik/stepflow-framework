package com.stepflow.testcomponents;

import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

public class TestGuardPlain implements Guard {
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        return true;
    }
}

