package com.stepflow.examples.simple;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.StepResult;

import java.util.HashMap;
import java.util.Map;

public class SimpleExample {
    public static void main(String[] args) {
        SimpleEngine engine = SimpleEngine.create("classpath:examples/simple.yaml", "com.stepflow.examples");

        Map<String, Object> input = new HashMap<>();
        input.put("amount", 42.0);

        StepResult result = engine.execute("simpleFlow", input);
        System.out.println("Status: " + result.status);
    }
}

