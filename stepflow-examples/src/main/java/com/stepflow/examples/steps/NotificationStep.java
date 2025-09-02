package com.stepflow.examples.steps;

import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;
import com.stepflow.core.annotations.StepComponent;
import com.stepflow.core.annotations.ConfigValue;

@StepComponent(name = "notify")
public class NotificationStep implements Step {
    @ConfigValue(value = "template", globalPath = "notifications.template", required = false, defaultValue = "default-template")
    private String template;

    @Override
    public StepResult execute(ExecutionContext ctx) {
        // In a real system, send an email/notification here
        ctx.put("notification.template", template);
        return StepResult.success(ctx);
    }
}

