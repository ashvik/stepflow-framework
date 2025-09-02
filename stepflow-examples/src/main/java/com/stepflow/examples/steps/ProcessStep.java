package com.stepflow.examples.steps;

import com.stepflow.core.annotations.Inject;
import com.stepflow.examples.model.ValidationInfo;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;
import com.stepflow.core.annotations.StepComponent;
import com.stepflow.core.annotations.ConfigValue;

public class ProcessStep implements Step {
    @ConfigValue(value = "status", required = false, defaultValue = "SUCCESS")
    private String status;

    @ConfigValue(value = "type", required = false, defaultValue = "NA")
    private String processType;

    @Inject
    private ValidationInfo validationInfo;

    @Override
    public StepResult execute(ExecutionContext ctx) {
        ctx.put("payment.status", status);
        return StepResult.success(ctx);
    }
}

