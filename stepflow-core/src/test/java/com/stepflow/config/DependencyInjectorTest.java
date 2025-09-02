package com.stepflow.config;

import com.stepflow.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DependencyInjector} annotation-aware injection behavior.
 */
public class DependencyInjectorTest {

    @Test
    void injectsAnnotatedInjectFromContext() {
        class Target {
            @com.stepflow.core.annotations.Inject("amount")
            Double amount;
        }
        Target t = new Target();
        ExecutionContext ctx = new ExecutionContext();
        ctx.put("amount", 42.0);

        new DependencyInjector().injectDependencies(t, ctx, Map.of(), Map.of());
        assertEquals(42.0, t.amount);
    }

    @Test
    void injectsAnnotatedInjectFromConfigWhenContextMissing() {
        class Target {
            @com.stepflow.core.annotations.Inject("userId")
            String userId;
        }
        Target t = new Target();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("userId", "abc");

        new DependencyInjector().injectDependencies(t, new ExecutionContext(), cfg, Map.of());
        assertEquals("abc", t.userId);
    }

    @Test
    void injectAnnotatedInjectRequiredMissingThrows() {
        class Target {
            @com.stepflow.core.annotations.Inject(value = "missing", required = true)
            String must;
        }
        Target t = new Target();
        DependencyInjector di = new DependencyInjector();
        assertThrows(IllegalStateException.class, () ->
                di.injectDependencies(t, new ExecutionContext(), Map.of(), Map.of())
        );
    }

    @Test
    void injectsConfigValueFromSettingsAndAppliesDefaults() {
        class Target {
            @com.stepflow.core.annotations.ConfigValue(value = "template", globalPath = "notifications.template", required = false, defaultValue = "default")
            String template;

            @com.stepflow.core.annotations.ConfigValue(value = "maxAttempts", required = false, defaultValue = "3")
            int maxAttempts;
        }
        Map<String, Object> settings = Map.of(
                "notifications", Map.of("template", "order-confirmation")
        );
        Target t = new Target();
        new DependencyInjector().injectDependencies(t, new ExecutionContext(), Map.of(), settings);
        assertEquals("order-confirmation", t.template);
        assertEquals(3, t.maxAttempts);
    }

    @Test
    void genericContextAndConfigInjectionSkipsAnnotatedFieldsAndResolvesPrecedence() {
        class Target {
            // Will be set by generic injection (context then overridden by config)
            String plainValue;

            // Annotation-driven: should not be overridden by generic injectors
            @com.stepflow.core.annotations.ConfigValue(value = "annotated", required = false, defaultValue = "ann-default")
            String annotatedValue;
        }
        ExecutionContext ctx = new ExecutionContext();
        ctx.put("plainValue", "from-context");
        ctx.put("annotated", "ctx-annotated");

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("plainValue", "from-config");
        cfg.put("annotated", "cfg-annotated");

        Target t = new Target();
        new DependencyInjector().injectDependencies(t, ctx, cfg, Map.of());

        // config overrides context for plain fields (due to injection order)
        assertEquals("from-config", t.plainValue);

        // annotated field resolved by @ConfigValue logic only
        assertEquals("cfg-annotated", t.annotatedValue);
    }
}

