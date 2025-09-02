package com.stepflow.codegen.model;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Objects;

/**
 * Metadata extracted from YAML for generating Step/Guard classes
 */
public class ComponentMetadata {
    private String name;
    private ComponentType type;
    private String packageName;
    private String className;
    private String annotationName;
    private Map<String, Object> config;
    private Set<String> requiredGuards;
    private RetryConfiguration retry;
    private List<String> usedInWorkflows;
    private boolean hasComplexLogic;
    
    public enum ComponentType {
        STEP, GUARD
    }
    
    public static class RetryConfiguration {
        private int maxAttempts;
        private long delay;
        private String guard;
        
        public RetryConfiguration(int maxAttempts, long delay, String guard) {
            this.maxAttempts = maxAttempts;
            this.delay = delay;
            this.guard = guard;
        }
        
        // Getters and setters
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public long getDelay() { return delay; }
        public void setDelay(long delay) { this.delay = delay; }
        
        public String getGuard() { return guard; }
        public void setGuard(String guard) { this.guard = guard; }
    }
    
    // Constructors
    public ComponentMetadata() {}
    
    public ComponentMetadata(String name, ComponentType type) {
        this.name = name;
        this.type = type;
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public ComponentType getType() { return type; }
    public void setType(ComponentType type) { this.type = type; }
    
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    public String getAnnotationName() { return annotationName; }
    public void setAnnotationName(String annotationName) { this.annotationName = annotationName; }
    
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    
    public Set<String> getRequiredGuards() { return requiredGuards; }
    public void setRequiredGuards(Set<String> requiredGuards) { this.requiredGuards = requiredGuards; }
    
    public RetryConfiguration getRetry() { return retry; }
    public void setRetry(RetryConfiguration retry) { this.retry = retry; }
    
    public List<String> getUsedInWorkflows() { return usedInWorkflows; }
    public void setUsedInWorkflows(List<String> usedInWorkflows) { this.usedInWorkflows = usedInWorkflows; }
    
    public boolean isHasComplexLogic() { return hasComplexLogic; }
    public void setHasComplexLogic(boolean hasComplexLogic) { this.hasComplexLogic = hasComplexLogic; }
    
    // Utility methods
    public String getFullyQualifiedClassName() {
        return packageName != null ? packageName + "." + className : className;
    }
    
    public boolean hasConfig() {
        return config != null && !config.isEmpty();
    }
    
    public boolean hasRetry() {
        return retry != null;
    }
    
    public boolean hasGuards() {
        return requiredGuards != null && !requiredGuards.isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentMetadata that = (ComponentMetadata) o;
        return Objects.equals(name, that.name) && type == that.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
    
    @Override
    public String toString() {
        return "ComponentMetadata{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", className='" + className + '\'' +
                ", packageName='" + packageName + '\'' +
                '}';
    }
}