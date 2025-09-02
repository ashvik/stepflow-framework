package com.stepflow.testcomponents;

import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

@GuardComponent(name = "eventuallyTrue")
public class EventuallyTrueGuard implements Guard {
    @ConfigValue(value = "passesOnAttempt", required = false, defaultValue = "2")
    private int passesOnAttempt;

    private static final String ATTEMPT_KEY = "__eventuallyTrue_attempt";

    @Override
    public boolean evaluate(ExecutionContext ctx) {
        int attempt = ctx.getInt(ATTEMPT_KEY, 0) + 1;
        ctx.put(ATTEMPT_KEY, attempt);
        return attempt >= Math.max(1, passesOnAttempt);
    }
}

