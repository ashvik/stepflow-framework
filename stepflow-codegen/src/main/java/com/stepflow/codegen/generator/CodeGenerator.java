package com.stepflow.codegen.generator;

import com.stepflow.codegen.model.ComponentMetadata;
import com.stepflow.codegen.model.ComponentMetadata.ComponentType;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Java source code from component metadata using Velocity templates
 */
public class CodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
    
    private final VelocityEngine velocityEngine;
    
    public CodeGenerator() {
        this.velocityEngine = initializeVelocityEngine();
    }
    
    /**
     * Generates Java source code for a component
     */
    public String generateComponent(ComponentMetadata metadata) {
        try {
            String templateName = metadata.getType() == ComponentType.STEP ? 
                "templates/Step.java.vm" : "templates/Guard.java.vm";
                
            Template template = velocityEngine.getTemplate(templateName);
            VelocityContext context = createVelocityContext(metadata);
            
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            
            String generatedCode = writer.toString();
            logger.debug("Generated {} lines of code for {}", 
                        generatedCode.split("\n").length, metadata.getName());
                        
            return generatedCode;
            
        } catch (Exception e) {
            logger.error("Failed to generate component: {}", metadata.getName(), e);
            throw new RuntimeException("Code generation failed for " + metadata.getName(), e);
        }
    }
    
    /**
     * Generates a Main class for the generated project
     */
    public String generateMain(List<ComponentMetadata> components, ProjectConfig projectConfig, String defaultWorkflowName) {
        try {
            Template template = velocityEngine.getTemplate("templates/Main.java.vm");
            VelocityContext context = createMainContext(components, projectConfig, defaultWorkflowName);
            
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            
            return writer.toString();
            
        } catch (Exception e) {
            logger.error("Failed to generate Main class", e);
            throw new RuntimeException("Main class generation failed", e);
        }
    }
    
    /**
     * Generates a Maven POM file for the generated project
     */
    public String generatePom(List<ComponentMetadata> components, ProjectConfig projectConfig) {
        try {
            Template template = velocityEngine.getTemplate("templates/pom.xml.vm");
            VelocityContext context = createPomContext(components, projectConfig);
            
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            
            return writer.toString();
            
        } catch (Exception e) {
            logger.error("Failed to generate POM", e);
            throw new RuntimeException("POM generation failed", e);
        }
    }
    
    /**
     * Generates README file with component documentation
     */
    public String generateReadme(List<ComponentMetadata> components, ProjectConfig projectConfig) {
        StringBuilder readme = new StringBuilder();
        
        readme.append("# ").append(projectConfig.getProjectName()).append("\n\n");
        readme.append("Generated StepFlow components from YAML configuration.\n\n");
        readme.append("**Generated on:** ").append(getCurrentTimestamp()).append("\n");
        readme.append("**Source files:** ").append(String.join(", ", projectConfig.getSourceFiles())).append("\n\n");
        
        // Steps section
        List<ComponentMetadata> steps = components.stream()
            .filter(c -> c.getType() == ComponentType.STEP)
            .collect(Collectors.toList());
            
        if (!steps.isEmpty()) {
            readme.append("## Generated Steps\n\n");
            for (ComponentMetadata step : steps) {
                readme.append("### ").append(step.getClassName()).append("\n");
                readme.append("- **Component Name:** `").append(step.getName()).append("`\n");
                readme.append("- **Package:** `").append(step.getPackageName()).append("`\n");
                readme.append("- **Used in workflows:** ").append(String.join(", ", step.getUsedInWorkflows())).append("\n");
                
                if (step.hasConfig()) {
                    readme.append("- **Configuration parameters:**\n");
                    step.getConfig().forEach((key, value) -> {
                        readme.append("  - `").append(key).append("`: ").append(value).append("\n");
                    });
                }
                
                if (step.hasGuards()) {
                    readme.append("- **Required guards:** ").append(String.join(", ", step.getRequiredGuards())).append("\n");
                }
                
                if (step.hasRetry()) {
                    readme.append("- **Retry configuration:** ")
                          .append(step.getRetry().getMaxAttempts()).append(" attempts, ")
                          .append(step.getRetry().getDelay()).append("ms delay\n");
                }
                
                readme.append("\n");
            }
        }
        
        // Guards section
        List<ComponentMetadata> guards = components.stream()
            .filter(c -> c.getType() == ComponentType.GUARD)
            .collect(Collectors.toList());
            
        if (!guards.isEmpty()) {
            readme.append("## Generated Guards\n\n");
            for (ComponentMetadata guard : guards) {
                readme.append("### ").append(guard.getClassName()).append("\n");
                readme.append("- **Component Name:** `").append(guard.getName()).append("`\n");
                readme.append("- **Package:** `").append(guard.getPackageName()).append("`\n");
                readme.append("- **Used in workflows:** ").append(String.join(", ", guard.getUsedInWorkflows())).append("\n");
                
                if (guard.hasConfig()) {
                    readme.append("- **Configuration parameters:**\n");
                    guard.getConfig().forEach((key, value) -> {
                        readme.append("  - `").append(key).append("`: ").append(value).append("\n");
                    });
                }
                
                readme.append("\n");
            }
        }
        
        readme.append("## Usage\n\n");
        readme.append("1. Implement the TODO methods in the generated classes\n");
        readme.append("2. Customize the business logic according to your requirements\n");
        readme.append("3. Run `mvn compile` to build the project\n");
        readme.append("4. Run `mvn test` to execute unit tests\n");
        readme.append("5. Use the components in your StepFlow workflows\n\n");
        
        readme.append("## Notes\n\n");
        readme.append("- All generated classes contain TODO comments indicating where to implement business logic\n");
        readme.append("- Configuration parameters are automatically injected using `@ConfigValue` annotations\n");
        readme.append("- Guard classes include fail-safe behavior (return false on exceptions)\n");
        readme.append("- Step classes include proper error handling and logging\n");
        
        return readme.toString();
    }
    
    private VelocityEngine initializeVelocityEngine() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        engine.init();
        return engine;
    }
    
    private VelocityContext createVelocityContext(ComponentMetadata metadata) {
        VelocityContext context = new VelocityContext();
        
        // Basic component information
        context.put("packageName", metadata.getPackageName());
        context.put("className", metadata.getClassName());
        context.put("componentName", metadata.getName());
        context.put("annotationName", metadata.getAnnotationName());
        context.put("usedInWorkflows", String.join(", ", metadata.getUsedInWorkflows()));
        context.put("generationTimestamp", getCurrentTimestamp());
        
        // Configuration
        context.put("hasConfig", metadata.hasConfig());
        context.put("hasGuards", metadata.hasGuards());
        context.put("hasComplexLogic", metadata.isHasComplexLogic());
        
        if (metadata.hasConfig()) {
            List<ConfigEntry> configEntries = createConfigEntries(metadata.getConfig());
            context.put("configEntries", configEntries);
        }
        
        return context;
    }
    
    private VelocityContext createMainContext(List<ComponentMetadata> components, ProjectConfig projectConfig, String defaultWorkflowName) {
        VelocityContext context = new VelocityContext();
        
        context.put("groupId", projectConfig.getGroupId());
        context.put("artifactId", projectConfig.getArtifactId());
        context.put("version", projectConfig.getVersion());
        context.put("projectName", projectConfig.getProjectName());
        context.put("generationTimestamp", getCurrentTimestamp());
        context.put("sourceFiles", String.join(", ", projectConfig.getSourceFiles()));
        
        List<ComponentMetadata> steps = components.stream()
            .filter(c -> c.getType() == ComponentType.STEP)
            .collect(Collectors.toList());
        List<ComponentMetadata> guards = components.stream()
            .filter(c -> c.getType() == ComponentType.GUARD)
            .collect(Collectors.toList());
            
        context.put("steps", steps);
        context.put("guards", guards);
        
        // Create comma-separated lists for documentation
        String stepsList = steps.stream()
            .map(s -> s.getName() + " (" + s.getClassName() + ")")
            .collect(Collectors.joining(", "));
        String guardsList = guards.stream()
            .map(g -> g.getName() + " (" + g.getClassName() + ")")
            .collect(Collectors.joining(", "));
            
        context.put("stepsList", stepsList);
        context.put("guardsList", guardsList);
        
        // Set default workflow name from parameter
        context.put("defaultWorkflowName", defaultWorkflowName != null ? defaultWorkflowName : "orderProcessingFlow");
        
        return context;
    }
    
    private VelocityContext createPomContext(List<ComponentMetadata> components, ProjectConfig projectConfig) {
        VelocityContext context = new VelocityContext();
        
        context.put("groupId", projectConfig.getGroupId());
        context.put("artifactId", projectConfig.getArtifactId());
        context.put("version", projectConfig.getVersion());
        context.put("projectName", projectConfig.getProjectName());
        context.put("generationTimestamp", getCurrentTimestamp());
        context.put("sourceFiles", String.join(", ", projectConfig.getSourceFiles()));
        
        List<ComponentMetadata> steps = components.stream()
            .filter(c -> c.getType() == ComponentType.STEP)
            .collect(Collectors.toList());
        List<ComponentMetadata> guards = components.stream()
            .filter(c -> c.getType() == ComponentType.GUARD)
            .collect(Collectors.toList());
            
        context.put("steps", steps);
        context.put("guards", guards);
        
        return context;
    }
    
    private List<ConfigEntry> createConfigEntries(Map<String, Object> config) {
        return config.entrySet().stream()
            .map(entry -> new ConfigEntry(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }
    
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * Configuration entry for template generation
     */
    public static class ConfigEntry {
        private final String key;
        private final Object value;
        private final String javaType;
        private final String fieldName;
        private final String capitalizedFieldName;
        private final boolean required;
        private final String defaultValue;
        private final String description;
        
        public ConfigEntry(String key, Object value) {
            this.key = key;
            this.value = value;
            this.javaType = inferJavaType(value);
            this.fieldName = camelCase(key);
            this.capitalizedFieldName = capitalize(fieldName);
            this.required = true; // Default to required
            this.defaultValue = value != null ? value.toString() : null;
            this.description = "Configuration parameter for " + key;
        }
        
        private String inferJavaType(Object value) {
            if (value == null) return "String";
            if (value instanceof Boolean) return "Boolean";
            if (value instanceof Integer) return "Integer";
            if (value instanceof Long) return "Long";
            if (value instanceof Double || value instanceof Float) return "Double";
            if (value instanceof List) return "List<String>";
            if (value instanceof Map) return "Map<String, Object>";
            return "String";
        }
        
        private String camelCase(String str) {
            if (str == null || str.isEmpty()) return str;
            String[] parts = str.split("[_\\-\\s]+");
            if (parts.length == 1) {
                return str.toLowerCase();
            }
            StringBuilder result = new StringBuilder(parts[0].toLowerCase());
            for (int i = 1; i < parts.length; i++) {
                result.append(capitalize(parts[i].toLowerCase()));
            }
            return result.toString();
        }
        
        private String capitalize(String str) {
            if (str == null || str.isEmpty()) return str;
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        
        // Getters
        public String getKey() { return key; }
        public Object getValue() { return value; }
        public String getJavaType() { return javaType; }
        public String getFieldName() { return fieldName; }
        public String getCapitalizedFieldName() { return capitalizedFieldName; }
        public boolean isRequired() { return required; }
        public String getDefaultValue() { return defaultValue; }
        public String getDescription() { return description; }
    }
    
    /**
     * Project configuration for generated code
     */
    public static class ProjectConfig {
        private String groupId = "com.stepflow.generated";
        private String artifactId = "stepflow-generated-components";
        private String version = "1.0.0-SNAPSHOT";
        private String projectName = "Generated StepFlow Components";
        private List<String> sourceFiles = new ArrayList<>();
        
        // Getters and setters
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public List<String> getSourceFiles() { return sourceFiles; }
        public void setSourceFiles(List<String> sourceFiles) { this.sourceFiles = sourceFiles; }
    }
}