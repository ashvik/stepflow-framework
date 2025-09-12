package com.stepflow.engine;

import com.stepflow.config.FlowConfig;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies engine retries steps when retry config is present without a guard (unconditional retry).
 */
class EngineUnconditionalRetryTest {

    private Engine newEngine(FlowConfig cfg) {
        return new Engine(cfg, "com.stepflow.testcomponents");
    }

    @Test
    void engineRetriesWithoutGuardAndSucceeds() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();

        FlowConfig.StepDef a = new FlowConfig.StepDef();
        a.type = "unstableTest"; // UnstableTestStep succeeds on configured attempt
        Map<String, Object> c = new HashMap<>();
        c.put("succeedOnAttempt", 2);
        a.config = c;

        FlowConfig.RetryConfig rc = new FlowConfig.RetryConfig();
        rc.maxAttempts = 3; // should succeed on 2nd attempt
        rc.delay = 0;
        rc.guard = null; // unconditional retry
        a.retry = rc;

        cfg.steps.put("A", a);

        FlowConfig.WorkflowDef wf = new FlowConfig.WorkflowDef();
        wf.root = "A"; wf.edges = new ArrayList<>();
        FlowConfig.EdgeDef e1 = new FlowConfig.EdgeDef(); e1.from = "A"; e1.to = "SUCCESS"; wf.edges.add(e1);
        cfg.workflows.put("w", wf);

        StepResult r = newEngine(cfg).run("w", new ExecutionContext());
        assertTrue(r.isSuccess());
    }
}

