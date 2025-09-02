package com.stepflow.engine;

import com.stepflow.config.FlowConfig;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for edge guard failure handling strategies. */
public class EdgeFailureStrategyTest {

    private Engine engineWith(FlowConfig cfg) {
        return new Engine(cfg, "com.stepflow.testcomponents");
    }

    private FlowConfig.StepDef step(String type) {
        FlowConfig.StepDef s = new FlowConfig.StepDef();
        s.type = type;
        return s;
    }

    /**
     * Scenario: A single outgoing edge from A to B has a failing guard and uses the default STOP strategy.
     * Expectation: Workflow halts immediately and returns FAILURE; no further edges are considered.
     */
    @Test
    void stopStrategyFailsImmediately() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        cfg.steps.put("A", step("testStepPlain"));
        cfg.steps.put("B", step("testStepPlain"));

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "falseGuard"; // default STOP
        wf.edges.add(e1);
        cfg.workflows.put("w", wf);

        StepResult r = engineWith(cfg).run("w", new ExecutionContext());
        assertTrue(r.isFailure());
        assertTrue(r.message.contains("STOP"));
    }

    /**
     * Scenario: First edge from A to B has a failing guard and onFailure=SKIP, second edge A->SUCCESS exists.
     * Expectation: Engine skips the failing edge, takes the next available edge, and the workflow succeeds.
     */
    @Test
    void skipStrategyTriesNextEdge() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        cfg.steps.put("A", step("testStepPlain"));
        cfg.steps.put("B", step("testStepPlain"));

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "falseGuard";
        e1.onFailure = new FlowConfig.OnFailure(); e1.onFailure.strategy = "SKIP";
        wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "A"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = engineWith(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    /**
     * Scenario: Edge A->B has a failing guard and onFailure=ALTERNATIVE with alternativeTarget=C.
     * Expectation: Engine redirects to C when the guard fails; C->SUCCESS completes the workflow.
     */
    @Test
    void alternativeStrategyRedirects() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        cfg.steps.put("A", step("testStepPlain"));
        cfg.steps.put("C", step("testStepPlain"));

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "falseGuard";
        e1.onFailure = new FlowConfig.OnFailure(); e1.onFailure.strategy = "ALTERNATIVE"; e1.onFailure.alternativeTarget = "C";
        wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "C"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = engineWith(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    /**
     * Scenario: Edge A->B uses a guard that becomes true on the second attempt; onFailure=RETRY with attempts=3.
     * Expectation: Engine retries guard evaluation; the second attempt passes and the workflow succeeds.
     */
    @Test
    void retryStrategyHonorsAttemptsAndDelayAndSucceeds() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();
        cfg.steps.put("A", step("testStepPlain"));
        cfg.steps.put("B", step("testStepPlain"));

        // Declare guard as step with config so it can read passesOnAttempt
        FlowConfig.StepDef g = new FlowConfig.StepDef();
        g.type = "eventuallyTrue";
        g.config = new HashMap<>();
        g.config.put("passesOnAttempt", 2);
        cfg.steps.put("eventuallyTrue", g);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "eventuallyTrue";
        e1.onFailure = new FlowConfig.OnFailure(); e1.onFailure.strategy = "RETRY"; e1.onFailure.attempts = 3; e1.onFailure.delay = 0L;
        wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "B"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = engineWith(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }

    /**
     * Scenario: Edge A->B has a failing guard but onFailure=CONTINUE.
     * Expectation: Engine ignores the guard failure, proceeds to B, and then to SUCCESS.
     */
    @Test
    void continueStrategyIgnoresGuardFailure() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>(); cfg.workflows = new HashMap<>();
        cfg.steps.put("A", step("testStepPlain"));
        cfg.steps.put("B", step("testStepPlain"));
        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef(); wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "B"; e1.guard = "falseGuard";
        e1.onFailure = new FlowConfig.OnFailure(); e1.onFailure.strategy = "CONTINUE";
        wf.edges.add(e1);
        FlowConfig.EdgeDef e2 = new FlowConfig.EdgeDef(); e2.from = "B"; e2.to = "SUCCESS"; wf.edges.add(e2);
        cfg.workflows.put("w", wf);

        StepResult r = engineWith(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }
}
