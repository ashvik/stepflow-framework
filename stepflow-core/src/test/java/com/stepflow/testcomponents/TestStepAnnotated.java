package com.stepflow.testcomponents;

import com.stepflow.core.annotations.StepComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;

@StepComponent(name = "myStep")
public class TestStepAnnotated implements Step {
    @Override
    public StepResult execute(ExecutionContext ctx) {
        return StepResult.success();
    }
}

