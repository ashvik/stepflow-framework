package com.stepflow.examples.steps;

import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.StepComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;

@StepComponent(name = "internalRetry")
public class InternalRetryStep implements Step {
    @ConfigValue(value = "maxAttempts", required = false, defaultValue = "3")
    private int maxAttempts;

    @ConfigValue(value = "delayMs", required = false, defaultValue = "0")
    private long delayMs;

    @ConfigValue(value = "succeedOnAttempt", required = false, defaultValue = "2")
    private int succeedOnAttempt;

    @Override
    public StepResult execute(ExecutionContext ctx) {
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            if (attempt >= succeedOnAttempt) {
                return StepResult.success("InternalRetryStep succeeded at attempt " + attempt, ctx);
            }
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        return StepResult.failure("InternalRetryStep failed after " + maxAttempts + " attempts");
    }
}

