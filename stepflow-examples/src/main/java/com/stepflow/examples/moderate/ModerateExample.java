package com.stepflow.examples.moderate;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.StepResult;

import java.util.HashMap;
import java.util.Map;

public class ModerateExample {
    public static void main(String[] args) {
        SimpleEngine engine = SimpleEngine.create("classpath:examples/moderate.yaml", "com.stepflow.examples");

        Map<String, Object> input = new HashMap<>();
        input.put("amount", 150.0);

        StepResult result = engine.execute("orderFlow", input);
        System.out.println("Status: " + result.status);
    }
}

