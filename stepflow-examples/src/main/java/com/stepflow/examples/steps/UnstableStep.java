package com.stepflow.examples.steps;

import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.StepComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;

@StepComponent(name = "unstable")
public class UnstableStep implements Step {
    @ConfigValue(value = "succeedOnAttempt", required = false, defaultValue = "2")
    private int succeedOnAttempt;

    private int attempt = 0;

    @Override
    public StepResult execute(ExecutionContext ctx) {
        attempt++;
        if (attempt < succeedOnAttempt) {
            return StepResult.failure("UnstableStep failing at attempt " + attempt);
        }
        return StepResult.success("UnstableStep succeeded at attempt " + attempt, ctx);
    }
}

