package com.stepflow.examples.guards;

import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

@GuardComponent(name = "orderValueAbove")
public class OrderValueAboveGuard implements Guard {
    @ConfigValue(value = "threshold", required = false, defaultValue = "100.0")
    private Double threshold;

    @Override
    public boolean evaluate(ExecutionContext ctx) {
        Double amount = ctx.getDouble("amount", 0.0);
        return amount != null && amount > (threshold != null ? threshold : 0.0);
    }
}

