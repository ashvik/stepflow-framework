package com.stepflow.config;

import java.util.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration class that holds workflow definitions.
 */
public class FlowConfig {
    
    public Map<String, StepDef> steps = new HashMap<>();
    public Map<String, WorkflowDef> workflows = new HashMap<>();
    public Map<String, Object> settings = new HashMap<>();
    public Map<String, Map<String, Object>> defaults = new HashMap<>();
    
    public static class StepDef {
        public String type;
        public Map<String, Object> config = new HashMap<>();
        public List<String> guards = new ArrayList<>();
        public RetryConfig retry;
    }
    
    public static class WorkflowDef {
        public String root;
        public List<EdgeDef> edges = new ArrayList<>();
    }
    
    public static class EdgeDef {
        public String from;
        public String to;
        public String guard;
        public String condition;
        public String kind = "normal";
        public OnFailure onFailure; // Strategy for guard failure handling
    }
    
    public static class RetryConfig {
        public int maxAttempts = 3;
        public long delay = 1000;
        public String guard;
    }

    /**
     * Edge guard failure handling configuration.
     */
    public static class OnFailure {
        /** Strategy name: STOP (default), SKIP, ALTERNATIVE, RETRY, CONTINUE */
        public String strategy = "STOP";
        /** Used when strategy=ALTERNATIVE to redirect to a fallback step */
        public String alternativeTarget;
        /** Used when strategy=RETRY: number of retry attempts */
        public Integer attempts;
        /** Used when strategy=RETRY: delay in milliseconds between attempts */
        public Long delay;
    }
    
    public String toFormattedYaml() {
        // Build a YAML-friendly tree using linked maps/lists to preserve order
        Map<String, Object> root = new LinkedHashMap<>();

        if (settings != null && !settings.isEmpty()) {
            root.put("settings", new LinkedHashMap<>(settings));
        }

        if (defaults != null && !defaults.isEmpty()) {
            // Ensure nested maps are linked for stable output
            Map<String, Object> defs = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : defaults.entrySet()) {
                defs.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            root.put("defaults", defs);
        }

        if (steps != null && !steps.isEmpty()) {
            Map<String, Object> stepsNode = new LinkedHashMap<>();
            // Use natural ordering for determinism in output
            List<String> names = new ArrayList<>(steps.keySet());
            Collections.sort(names);
            for (String name : names) {
                StepDef sd = steps.get(name);
                Map<String, Object> stepMap = new LinkedHashMap<>();
                if (sd.type != null && !sd.type.isEmpty()) {
                    stepMap.put("type", sd.type);
                }
                if (sd.config != null && !sd.config.isEmpty()) {
                    stepMap.put("config", new LinkedHashMap<>(sd.config));
                }
                if (sd.guards != null && !sd.guards.isEmpty()) {
                    stepMap.put("guards", new ArrayList<>(sd.guards));
                }
                if (sd.retry != null) {
                    Map<String, Object> retry = new LinkedHashMap<>();
                    retry.put("maxAttempts", sd.retry.maxAttempts);
                    retry.put("delay", sd.retry.delay);
                    if (sd.retry.guard != null && !sd.retry.guard.isEmpty()) {
                        retry.put("guard", sd.retry.guard);
                    }
                    stepMap.put("retry", retry);
                }
                stepsNode.put(name, stepMap);
            }
            root.put("steps", stepsNode);
        }

        if (workflows != null && !workflows.isEmpty()) {
            Map<String, Object> wfNode = new LinkedHashMap<>();
            List<String> wfNames = new ArrayList<>(workflows.keySet());
            Collections.sort(wfNames);
            for (String wfName : wfNames) {
                WorkflowDef wf = workflows.get(wfName);
                Map<String, Object> wfMap = new LinkedHashMap<>();
                if (wf.root != null && !wf.root.isEmpty()) {
                    wfMap.put("root", wf.root);
                }
                if (wf.edges != null && !wf.edges.isEmpty()) {
                    List<Map<String, Object>> edgesList = new ArrayList<>();
                    for (EdgeDef e : wf.edges) {
                        Map<String, Object> em = new LinkedHashMap<>();
                        em.put("from", e.from);
                        em.put("to", e.to);
                        if (e.guard != null && !e.guard.isEmpty()) {
                            em.put("guard", e.guard);
                        }
                        if (e.condition != null && !e.condition.isEmpty()) {
                            em.put("condition", e.condition);
                        }
                        if (e.kind != null && !e.kind.isEmpty() && !"normal".equals(e.kind)) {
                            em.put("kind", e.kind);
                        }
                        if (e.onFailure != null) {
                            Map<String, Object> of = new LinkedHashMap<>();
                            if (e.onFailure.strategy != null && !e.onFailure.strategy.isEmpty()) {
                                of.put("strategy", e.onFailure.strategy);
                            }
                            if (e.onFailure.alternativeTarget != null && !e.onFailure.alternativeTarget.isEmpty()) {
                                of.put("alternativeTarget", e.onFailure.alternativeTarget);
                            }
                            if (e.onFailure.attempts != null) {
                                of.put("attempts", e.onFailure.attempts);
                            }
                            if (e.onFailure.delay != null) {
                                of.put("delay", e.onFailure.delay);
                            }
                            if (!of.isEmpty()) {
                                em.put("onFailure", of);
                            }
                        }
                        edgesList.add(em);
                    }
                    wfMap.put("edges", edgesList);
                }
                wfNode.put(wfName, wfMap);
            }
            root.put("workflows", wfNode);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        // indicatorIndent must be smaller than indent per SnakeYAML contract
        options.setIndicatorIndent(1);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }
}
