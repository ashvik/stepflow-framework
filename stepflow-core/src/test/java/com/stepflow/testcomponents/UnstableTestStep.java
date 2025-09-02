package com.stepflow.testcomponents;

import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.StepComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;

@StepComponent(name = "unstableTest")
public class UnstableTestStep implements Step {
    @ConfigValue(value = "succeedOnAttempt", required = false, defaultValue = "2")
    private int succeedOnAttempt;

    private int attempt = 0;

    @Override
    public StepResult execute(ExecutionContext ctx) {
        attempt++;
        if (attempt < succeedOnAttempt) {
            return StepResult.failure("fail " + attempt);
        }
        return StepResult.success();
    }
}

