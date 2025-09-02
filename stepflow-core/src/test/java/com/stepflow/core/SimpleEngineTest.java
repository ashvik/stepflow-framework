package com.stepflow.core;

import com.stepflow.execution.StepResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleEngine} covering YAML-based creation and fluent workflow builder.
 */
public class SimpleEngineTest {

    @Test
    void createsFromYamlAndExecutes() {
        SimpleEngine engine = SimpleEngine.create("classpath:testflows/simple-test.yaml", "com.stepflow.testcomponents");
        Map<String, Object> input = new HashMap<>();
        StepResult r = engine.execute("simpleTest", input);
        assertTrue(r.isSuccess());
    }

    @Test
    void fluentBuilderBuildsAndRuns() {
        SimpleEngine engine = SimpleEngine
                .workflow("wf", "com.stepflow.testcomponents")
                .step("A").using("testStepPlain").then("B")
                .step("B").using("testStepPlain").end()
                .build();

        StepResult r = engine.execute("wf", new HashMap<>());
        assertTrue(r.isSuccess());
    }
}

