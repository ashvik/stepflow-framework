package com.stepflow.examples.complex;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.StepResult;

import java.util.HashMap;
import java.util.Map;

public class ComplexExample {
    public static void main(String[] args) {
        SimpleEngine engine = SimpleEngine.create("classpath:examples/complex.yaml", "com.stepflow.examples");

        engine.analyzeWorkflow("complexFlow");
        Map<String, Object> input = new HashMap<>();
        input.put("amount", 50.0);

        StepResult result = engine.execute("complexFlow", input);
        System.out.println("Status: " + result.status);
    }
}

