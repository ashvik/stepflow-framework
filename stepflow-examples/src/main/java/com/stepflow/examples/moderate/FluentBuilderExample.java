package com.stepflow.examples.moderate;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.StepResult;

import java.util.HashMap;
import java.util.Map;

public class FluentBuilderExample {
    public static void main(String[] args) {
        // Build a workflow fluently with step-level guard and engine-driven retry
        SimpleEngine engine = SimpleEngine
            .workflow("fluentFlow", "com.stepflow.examples")
            .step("validate")
                .using("validateStep")
                .then("unstable")
            .step("unstable")
                .using("unstable")
                .retry(3, 50, "shouldRetryGuard")
                .then("notify")
            .step("notify")
                .using("notificationStep")
                .guard("paymentSucceededGuard")
            .end()
            .build();

        System.out.println(engine.getFlowConfig().toFormattedYaml());
        engine.analyzeWorkflow("fluentFlow");
        Map<String, Object> input = new HashMap<>();
        input.put("amount", 100.0);

        StepResult result = engine.execute("fluentFlow", input);
        System.out.println("Status: " + result.status);
    }
}

