package com.stepflow.testcomponents;

import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;

public class TestStepPlain implements Step {
    @Override
    public StepResult execute(ExecutionContext ctx) {
        return StepResult.success();
    }
}

