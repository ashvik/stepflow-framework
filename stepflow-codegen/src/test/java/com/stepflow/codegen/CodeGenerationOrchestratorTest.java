package com.stepflow.codegen;

import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationOptions;
import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationResult;
import com.stepflow.codegen.model.ComponentMetadata.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CodeGenerationOrchestratorTest {
    
    @TempDir
    Path tempDir;
    
    private CodeGenerationOrchestrator orchestrator;
    
    @BeforeEach
    void setUp() {
        orchestrator = new CodeGenerationOrchestrator();
    }
    
    @Test
    void testComplexYamlGeneration() throws IOException {
        // Create test YAML file
        String yamlContent = """
            settings:
              notifications:
                template: "order-confirmation"
                
            defaults:
              step:
                timeout_ms: 3000
              orderValueAboveGuard:
                threshold: 100.0
                
            steps:
              validate:
                type: "validateStep"
                config:
                  strict: true
                  maxRetries: 5
              process:
                type: "processStep"
                guards:
                  - "inputValidation"
                  - "authCheck"
                retry:
                  maxAttempts: 3
                  delay: 1000
                  guard: "shouldRetry"
              sendNotification:
                type: "notificationStep"
                config:
                  template: "{{ settings.notifications.template }}"
                  async: true
                  
            workflows:
              complexFlow:
                root: "validate"
                edges:
                  - from: "validate"
                    to: "process"
                  - from: "process"
                    to: "sendNotification"
                    guard: "orderValueAboveGuard"
                  - from: "sendNotification"
                    to: "SUCCESS"
            """;
        
        Path yamlFile = tempDir.resolve("complex-flow.yaml");
        Files.write(yamlFile, yamlContent.getBytes());
        
        // Setup generation options
        GenerationOptions options = new GenerationOptions();
        options.setOutputDirectory(tempDir.resolve("generated").toString());
        options.setGroupId("com.test");
        options.setArtifactId("test-components");
        options.setVersion("1.0.0");
        options.setProjectName("Test Components");
        options.setCreateZip(true);
        
        // Run generation
        GenerationResult result = orchestrator.generateFromYamlFiles(
            Arrays.asList(yamlFile.toString()), 
            options
        );
        
        // Verify result
        assertTrue(result.isSuccess(), "Generation should succeed");
        assertNotNull(result.getOutputPath(), "Output path should be set");
        assertNotNull(result.getZipPath(), "ZIP path should be set");
        assertNotNull(result.getGeneratedComponents(), "Components should be generated");
        
        // Verify generated components
        assertEquals(6, result.getGeneratedComponents().size(), "Should generate 6 components");
        
        long stepCount = result.getGeneratedComponents().stream()
            .filter(c -> c.getType() == ComponentType.STEP)
            .count();
        long guardCount = result.getGeneratedComponents().stream()
            .filter(c -> c.getType() == ComponentType.GUARD)
            .count();
            
        assertEquals(3, stepCount, "Should generate 3 steps");
        assertEquals(3, guardCount, "Should generate 3 guards");
        
        // Verify files exist
        Path outputDir = Path.of(result.getOutputPath());
        assertTrue(Files.exists(outputDir.resolve("pom.xml")), "POM should exist");
        assertTrue(Files.exists(outputDir.resolve("README.md")), "README should exist");
        assertTrue(Files.exists(Path.of(result.getZipPath())), "ZIP should exist");
        
        // Verify step files
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/steps/ValidateStep.java")));
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/steps/ProcessStep.java")));
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/steps/SendNotificationStep.java")));
        
        // Verify guard files
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/guards/InputValidationGuard.java")));
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/guards/AuthCheckGuard.java")));
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/guards/OrderValueAboveGuard.java")));
        
        // Verify POM content
        String pomContent = Files.readString(outputDir.resolve("pom.xml"));
        assertTrue(pomContent.contains("<groupId>com.test</groupId>"));
        assertTrue(pomContent.contains("<artifactId>test-components</artifactId>"));
        assertTrue(pomContent.contains("<version>1.0.0</version>"));
        
        // Verify generated Step content
        String stepContent = Files.readString(outputDir.resolve("src/main/java/com/stepflow/generated/steps/ProcessStep.java"));
        assertTrue(stepContent.contains("@StepComponent(name = \"process\")"));
        assertTrue(stepContent.contains("implements Step"));
        assertTrue(stepContent.contains("@ConfigValue"));
        assertTrue(stepContent.contains("@Inject"));
        assertTrue(stepContent.contains("List<Guard> guards"));
        assertTrue(stepContent.contains("TODO: Implement"));
        
        // Verify generated Guard content
        String guardContent = Files.readString(outputDir.resolve("src/main/java/com/stepflow/generated/guards/OrderValueAboveGuard.java"));
        assertTrue(guardContent.contains("@GuardComponent(name = \"orderValueAboveGuard\")"));
        assertTrue(guardContent.contains("implements Guard"));
        assertTrue(guardContent.contains("@ConfigValue"));
        assertTrue(guardContent.contains("evaluate(ExecutionContext ctx)"));
        
        System.out.println("✅ Complex YAML generation test passed!");
        System.out.println("📁 Generated project at: " + result.getOutputPath());
        System.out.println("📦 ZIP created at: " + result.getZipPath());
    }
    
    @Test
    void testSimpleYamlGeneration() throws IOException {
        // Create simple test YAML
        String yamlContent = """
            steps:
              simple:
                type: "simpleStep"
                
            workflows:
              simpleFlow:
                root: "simple"
                edges:
                  - from: "simple"
                    to: "SUCCESS"
            """;
        
        Path yamlFile = tempDir.resolve("simple-flow.yaml");
        Files.write(yamlFile, yamlContent.getBytes());
        
        GenerationOptions options = new GenerationOptions();
        options.setOutputDirectory(tempDir.resolve("simple-generated").toString());
        options.setCreateZip(false);
        
        GenerationResult result = orchestrator.generateFromYamlFiles(
            Arrays.asList(yamlFile.toString()), 
            options
        );
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getGeneratedComponents().size());
        assertEquals(ComponentType.STEP, result.getGeneratedComponents().get(0).getType());
        
        Path outputDir = Path.of(result.getOutputPath());
        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/stepflow/generated/steps/SimpleStep.java")));
        
        System.out.println("✅ Simple YAML generation test passed!");
    }
    
    @Test
    void testInvalidYamlHandling() throws IOException {
        String invalidYaml = "invalid: yaml: content: [";
        
        Path yamlFile = tempDir.resolve("invalid.yaml");
        Files.write(yamlFile, invalidYaml.getBytes());
        
        GenerationOptions options = new GenerationOptions();
        options.setOutputDirectory(tempDir.resolve("invalid-output").toString());
        
        GenerationResult result = orchestrator.generateFromYamlFiles(
            Arrays.asList(yamlFile.toString()), 
            options
        );
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("failed"));
        
        System.out.println("✅ Invalid YAML handling test passed!");
    }
}