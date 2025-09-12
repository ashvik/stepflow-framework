package com.stepflow.engine;

import com.stepflow.config.FlowConfig;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EngineExponentialBackoffTest {

    private Engine newEngine(FlowConfig cfg) {
        return new Engine(cfg, "com.stepflow.testcomponents");
    }

    @Test
    void engineRetriesWithExponentialBackoffAndSucceeds() {
        FlowConfig cfg = new FlowConfig();
        cfg.steps = new HashMap<>();
        cfg.workflows = new HashMap<>();

        FlowConfig.StepDef a = new FlowConfig.StepDef();
        a.type = "unstableTest";
        Map<String, Object> c = new HashMap<>();
        c.put("succeedOnAttempt", 3);
        a.config = c;

        FlowConfig.RetryConfig rc = new FlowConfig.RetryConfig();
        rc.maxAttempts = 3; // will succeed on 3rd attempt
        rc.delay = 1;       // base delay 1ms
        rc.backoff = "EXPONENTIAL";
        rc.multiplier = 2.0;
        rc.maxDelay = 10L;  // cap (not really hit here)
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

