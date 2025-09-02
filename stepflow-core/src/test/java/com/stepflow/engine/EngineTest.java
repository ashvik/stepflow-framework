package com.stepflow.engine;

import com.stepflow.config.FlowConfig;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Engine} covering routing, guards, retry, dead-ends and cycles.
 */
public class EngineTest {

    private Engine newEngine(FlowConfig cfg) {
        return new Engine(cfg, "com.stepflow.testcomponents");
    }

    @Test
    void linearSuccessFlow() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();

        FlowConfig.StepDef a = new FlowConfig.StepDef(); a.type = "testStepPlain";
        FlowConfig.StepDef b = new FlowConfig.StepDef(); b.type = "testStepPlain";
        cfg.steps.put("A", a);
        cfg.steps.put("B", b);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A";
        wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "B"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    @Test
    void edgeGuardSelectsFirstEligible() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        FlowConfig.StepDef a = new FlowConfig.StepDef(); a.type = "testStepPlain";
        FlowConfig.StepDef b = new FlowConfig.StepDef(); b.type = "testStepPlain";
        FlowConfig.StepDef c = new FlowConfig.StepDef(); c.type = "testStepPlain";
        cfg.steps.put("A", a); cfg.steps.put("B", b); cfg.steps.put("C", c);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "myGuard"; wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "A"; e2.to = "C"; wf.edges.add(e2);
        FlowConfig.EdgeDef e3 = new FlowConfig.EdgeDef(); e3.from = "B"; e3.to = "SUCCESS"; wf.edges.add(e3);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    @Test
    void stepLevelGuardSkipsStepAndRoutes() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        FlowConfig.StepDef a = new FlowConfig.StepDef(); a.type = "testStepPlain"; a.guards.add("falseGuard");
        FlowConfig.StepDef b = new FlowConfig.StepDef(); b.type = "testStepPlain";
        cfg.steps.put("A", a); cfg.steps.put("B", b);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "B"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    @Test
    void engineRetryWithGuard() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();

        FlowConfig.StepDef a = new FlowConfig.StepDef();
        a.type = "unstableTest";
        Map<String, Object> c = new HashMap<>(); c.put("succeedOnAttempt", 2); a.config = c;
        FlowConfig.RetryConfig rc = new FlowConfig.RetryConfig();
        rc.maxAttempts = 3; rc.delay = 0; rc.guard = "shouldRetry";
        a.retry = rc;
        cfg.steps.put("A", a);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "SUCCESS"; wf.edges.add(e1);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    @Test
    void deadEndFails() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        FlowConfig.StepDef a = new FlowConfig.StepDef(); a.type = "testStepPlain"; cfg.steps.put("A", a);
        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isFailure());
        assertTrue(r.message.contains("No eligible transition"));
    }

    @Test
    void cycleDetected() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        FlowConfig.StepDef a = new FlowConfig.StepDef(); a.type = "testStepPlain"; cfg.steps.put("A", a);
        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "A"; wf.edges.add(e1);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isFailure());
        assertTrue(r.message.contains("Circular"));
    }
}

