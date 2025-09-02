package com.stepflow.examples.guards;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.Guard;

@GuardComponent(name = "paymentSucceeded")
public class PaymentSucceededGuard implements Guard {
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        String status = ctx.getString("payment.status", "");
        return "SUCCESS".equalsIgnoreCase(status);
    }
}

