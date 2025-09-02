package com.stepflow.examples.steps;

import com.stepflow.examples.model.ValidationInfo;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Step;
import com.stepflow.execution.StepResult;
import com.stepflow.core.annotations.StepComponent;

public class ValidateStep implements Step {
    @Override
    public StepResult execute(ExecutionContext ctx) {
        Double amount = ctx.getDouble("amount", 0.0);
        if (amount <= 0) {
            return StepResult.failure("Invalid amount");
        }
        ValidationInfo validationInfo = new ValidationInfo();
        validationInfo.setValid(true);
        ctx.put("validationInfo", validationInfo);
        return StepResult.success(ctx);
    }
}

