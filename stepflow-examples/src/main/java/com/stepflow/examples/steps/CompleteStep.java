package com.stepflow.examples.steps;

import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;
import com.stepflow.core.annotations.StepComponent;

@StepComponent(name = "done")
public class CompleteStep implements Step {
    @Override
    public StepResult execute(ExecutionContext ctx) {
        return StepResult.success(ctx);
    }
}

