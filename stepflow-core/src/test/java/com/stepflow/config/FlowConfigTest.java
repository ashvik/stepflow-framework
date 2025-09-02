package com.stepflow.config;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FlowConfig} formatting and basic structure population.
 */
public class FlowConfigTest {

    @Test
    void toFormattedYamlContainsSections() {
        FlowConfig cfg = new FlowConfig();
        cfg.settings = new HashMap<>();
        cfg.settings.put("env", "test");

        cfg.defaults = new HashMap<>();
        Map<String, Object> defaultsStep = new HashMap<>();
        defaultsStep.put("timeout_ms", 1000);
        cfg.defaults.put("step", defaultsStep);

        cfg.steps = new HashMap<>();
        FlowConfig.StepDef s = new FlowConfig.StepDef();
        s.type = "testStepPlain";
        s.config = Map.of("key", "value");
        s.guards = new ArrayList<>(List.of("myGuard"));
        FlowConfig.RetryConfig rc = new FlowConfig.RetryConfig();
        rc.maxAttempts = 2; rc.delay = 50; rc.guard = "retryGuard";
        s.retry = rc;
        cfg.steps.put("A", s);

        cfg.workflows = new HashMap<>();
        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A";
        FlowConfig.EdgeDef e = new FlowConfig.EdgeDef();
        e.from = "A"; e.to = "SUCCESS"; e.guard = "myGuard"; e.kind = "terminal";
        wf.edges = new ArrayList<>(List.of(e));
        cfg.workflows.put("w", wf);

        String out = cfg.toFormattedYaml();
        assertTrue(out.contains("settings:"));
        assertTrue(out.contains("defaults:"));
        assertTrue(out.contains("steps:"));
        assertTrue(out.contains("workflows:"));
        assertTrue(out.contains("type: \"testStepPlain\""));
        assertTrue(out.contains("guards:"));
        assertTrue(out.contains("retry:"));
        assertTrue(out.contains("edges:"));
    }
}

