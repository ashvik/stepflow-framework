package com.stepflow.examples.fluent;

import com.stepflow.core.SimpleEngine;
import com.stepflow.execution.ExecutionContext;
import com.stepflow.execution.StepResult;

import java.util.Map;

/**
 * Comprehensive fluent builder usage with inline docs.
 * Demonstrates settings/defaults, root selection, step-level guards,
 * step retry, edge guards, onFailure strategies, terminal helpers, and validation.
 *
 * Components used come from the examples package:
 *  - Steps: validateStep, processStep, notificationStep, unstable, internalRetry
 *  - Guards: paymentSucceeded/paymentSucceededGuard, orderValueAbove, shouldRetryGuard
 */
public class FluentApiShowcase {
    public static void main(String[] args) {
        // Build a workflow programmatically and validate immediately (throws on invalid config)
        SimpleEngine engine = SimpleEngine
            .workflow(
                "fluentShowcase",
                // Scan examples package for @StepComponent/@GuardComponent
                "com.stepflow.examples"
            )
            // Top-level settings (available to @ConfigValue via globalPath if used)
            .setting("notifications.channel", "email")
            .setting("notifications.template", "order-confirmation")

            // Defaults for categories and specific components
            .defaults("step", Map.of("timeout_ms", 3000))       // defaults.step.*
            .defaults("guard", Map.of("caching", true))         // defaults.guard.* (example only)
            // Configure guard defaults: orderValueAbove(threshold)
            .defaultsFor("orderValueAbove", Map.of("threshold", 50.0))

            // Define steps and workflow graph
            .step("validate").using("validateStep")
                .root()                             // mark this step as workflow root (optional if first)
                .guards("orderValueAbove")         // ALL must pass; otherwise step is skipped (not failed)
                .with("strict_mode", true)          // step-specific config
                .then("process")                     // unguarded transition

            .step("process").using("processStep")
                // Demonstrate setting values used by guards later
                .with("status", "SUCCESS")
                // FIRST match wins; ordering matters
                .toIf("paymentSucceeded", "notify")
                    .onFailureSkip()   // guard failed → skip this edge, evaluate next
                    .endEdgeToStep()
                // Fallback: if payment not successful, go to FAILURE terminal (default path must be last)
                .toFailure()

            .step("notify").using("notificationStep")
                .with("channel", "email") // overrides settings if step reads config directly
                .toSuccess()               // terminal helper

            // Optional: a small branch demonstrating edge RETRY using a guard
            .step("gate").using("processStep")
                .with("status", "SUCCESS")
                .toIf("paymentSucceeded", "unstableStep")
                    .onFailureRetry(3, 50) // re-evaluate guard up to 3 times with delay
                    .endEdgeToStep()
                .toSuccess()

            // Engine-driven step retry (retry.guard)
            .step("unstableStep").using("unstable")
                .retry(3, 50, "shouldRetryGuard")
                .toSuccess()

            .buildValidated(); // throws on invalid config

        // Execute the workflow
        ExecutionContext input = new ExecutionContext();
        input.put("amount", 120.0); // satisfies orderValueAbove(threshold=50.0)
        StepResult result = engine.execute("fluentShowcase", input);
        System.out.println("Status: " + result.status + (result.message != null ? (" Message: " + result.message) : ""));

        // Optional: print a human-readable plan and reachability hints
        engine.analyzeWorkflow("fluentShowcase");
    }
}
