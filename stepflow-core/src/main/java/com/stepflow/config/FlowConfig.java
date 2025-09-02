package com.stepflow.config;

import java.util.*;

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
        StringBuilder yaml = new StringBuilder();

        // Settings
        if (settings != null && !settings.isEmpty()) {
            yaml.append("settings:\n");
            appendMap(yaml, settings, 2);
            yaml.append('\n');
        }

        // Defaults
        if (defaults != null && !defaults.isEmpty()) {
            yaml.append("defaults:\n");
            for (Map.Entry<String, Map<String, Object>> def : defaults.entrySet()) {
                yaml.append("  ").append(def.getKey()).append(":\n");
                appendMap(yaml, def.getValue(), 4);
            }
            yaml.append('\n');
        }

        // Steps
        if (steps != null && !steps.isEmpty()) {
            yaml.append("steps:\n");
            for (Map.Entry<String, StepDef> entry : steps.entrySet()) {
                StepDef sd = entry.getValue();
                yaml.append("  ").append(entry.getKey()).append(":\n");
                if (sd.type != null) yaml.append("    type: \"").append(sd.type).append("\"\n");
                if (sd.config != null && !sd.config.isEmpty()) {
                    yaml.append("    config:\n");
                    appendMap(yaml, sd.config, 6);
                }
                if (sd.guards != null && !sd.guards.isEmpty()) {
                    yaml.append("    guards:\n");
                    for (String g : sd.guards) {
                        yaml.append("      - \"").append(g).append("\"\n");
                    }
                }
                if (sd.retry != null) {
                    yaml.append("    retry:\n");
                    yaml.append("      maxAttempts: ").append(sd.retry.maxAttempts).append('\n');
                    yaml.append("      delay: ").append(sd.retry.delay).append('\n');
                    if (sd.retry.guard != null && !sd.retry.guard.isEmpty()) {
                        yaml.append("      guard: \"").append(sd.retry.guard).append("\"\n");
                    }
                }
            }
            yaml.append('\n');
        }

        // Workflows
        if (workflows != null && !workflows.isEmpty()) {
            yaml.append("workflows:\n");
            for (Map.Entry<String, WorkflowDef> entry : workflows.entrySet()) {
                WorkflowDef wf = entry.getValue();
                yaml.append("  ").append(entry.getKey()).append(":\n");
                if (wf.root != null) yaml.append("    root: \"").append(wf.root).append("\"\n");
                if (wf.edges != null && !wf.edges.isEmpty()) {
                    yaml.append("    edges:\n");
                    for (EdgeDef edge : wf.edges) {
                        yaml.append("      - from: \"").append(edge.from).append("\"\n");
                        yaml.append("        to: \"").append(edge.to).append("\"\n");
                        if (edge.guard != null && !edge.guard.isEmpty()) {
                            yaml.append("        guard: \"").append(edge.guard).append("\"\n");
                        }
                        if (edge.condition != null && !edge.condition.isEmpty()) {
                            yaml.append("        condition: \"").append(edge.condition).append("\"\n");
                        }
                        if (edge.kind != null && !edge.kind.isEmpty() && !"normal".equals(edge.kind)) {
                            yaml.append("        kind: \"").append(edge.kind).append("\"\n");
                        }
                        if (edge.onFailure != null) {
                            yaml.append("        onFailure:\n");
                            if (edge.onFailure.strategy != null && !edge.onFailure.strategy.isEmpty()) {
                                yaml.append("          strategy: \"").append(edge.onFailure.strategy).append("\"\n");
                            }
                            if (edge.onFailure.alternativeTarget != null && !edge.onFailure.alternativeTarget.isEmpty()) {
                                yaml.append("          alternativeTarget: \"").append(edge.onFailure.alternativeTarget).append("\"\n");
                            }
                            if (edge.onFailure.attempts != null) {
                                yaml.append("          attempts: ").append(edge.onFailure.attempts).append('\n');
                            }
                            if (edge.onFailure.delay != null) {
                                yaml.append("          delay: ").append(edge.onFailure.delay).append('\n');
                            }
                        }
                    }
                }
            }
        }

        return yaml.toString();
    }

    private void appendMap(StringBuilder yaml, Map<String, Object> map, int indent) {
        if (map == null || map.isEmpty()) return;
        String pad = " ".repeat(indent);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                yaml.append(pad).append(e.getKey()).append(":\n");
                //noinspection unchecked
                appendMap(yaml, (Map<String, Object>) v, indent + 2);
            } else if (v instanceof List) {
                yaml.append(pad).append(e.getKey()).append(":\n");
                for (Object item : (List<?>) v) {
                    yaml.append(pad).append("- ").append(stringify(item)).append('\n');
                }
            } else {
                yaml.append(pad).append(e.getKey()).append(": ").append(stringify(v)).append('\n');
            }
        }
    }

    private String stringify(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        String s = String.valueOf(v);
        if (s.matches("[A-Za-z0-9_.-]+")) return s;
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }
}
