package com.stepflow.codegen;

import com.stepflow.codegen.generator.CodeGenerator;
import com.stepflow.codegen.generator.CodeGenerator.ProjectConfig;
import com.stepflow.codegen.model.ComponentMetadata;
import com.stepflow.codegen.model.ComponentMetadata.ComponentType;
import com.stepflow.codegen.parser.YamlAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Main orchestrator for the code generation process
 */
public class CodeGenerationOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(CodeGenerationOrchestrator.class);
    
    private final YamlAnalyzer yamlAnalyzer;
    private final CodeGenerator codeGenerator;
    
    public CodeGenerationOrchestrator() {
        this.yamlAnalyzer = new YamlAnalyzer();
        this.codeGenerator = new CodeGenerator();
    }
    
    /**
     * Main entry point for code generation process
     */
    public GenerationResult generateFromYamlFiles(List<String> yamlFilePaths, GenerationOptions options) {
        logger.info("Starting code generation from {} YAML files", yamlFilePaths.size());
        
        try {
            // 1. Analyze all YAML files to extract component metadata
            List<ComponentMetadata> allComponents = analyzeYamlFiles(yamlFilePaths);
            
            if (allComponents.isEmpty()) {
                logger.warn("No components found in provided YAML files");
                return GenerationResult.failure("No components found to generate");
            }
            
            logger.info("Found {} components to generate ({} steps, {} guards)", 
                       allComponents.size(),
                       allComponents.stream().filter(c -> c.getType() == ComponentType.STEP).count(),
                       allComponents.stream().filter(c -> c.getType() == ComponentType.GUARD).count());
            
            // 2. Setup project configuration
            ProjectConfig projectConfig = createProjectConfig(options, yamlFilePaths);
            
            // 3. Extract default workflow name from first YAML file
            String defaultWorkflowName = extractDefaultWorkflowName(yamlFilePaths);
            
            // 4. Generate source code
            GeneratedProject project = generateSourceCode(allComponents, projectConfig, defaultWorkflowName);
            
            // 5. Write files to output directory
            Path outputDir = writeProjectFiles(project, options.getOutputDirectory(), yamlFilePaths);
            
            // 6. Create ZIP distribution if requested
            String zipPath = null;
            if (options.isCreateZip()) {
                zipPath = createZipDistribution(outputDir, options.getZipFileName());
            }
            
            GenerationResult result = GenerationResult.success(
                "Generated " + allComponents.size() + " components successfully",
                outputDir.toString(),
                zipPath,
                allComponents
            );
            
            logger.info("Code generation completed successfully. Output: {}", outputDir);
            return result;
            
        } catch (Exception e) {
            logger.error("Code generation failed", e);
            return GenerationResult.failure("Code generation failed: " + e.getMessage());
        }
    }
    
    private List<ComponentMetadata> analyzeYamlFiles(List<String> yamlFilePaths) throws IOException {
        return yamlFilePaths.stream()
            .flatMap(filePath -> {
                try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
                    return yamlAnalyzer.analyze(inputStream).stream();
                } catch (IOException e) {
                    logger.error("Failed to analyze YAML file: {}", filePath, e);
                    throw new RuntimeException("Failed to analyze YAML: " + filePath, e);
                }
            })
            .collect(Collectors.toList());
    }
    
    private ProjectConfig createProjectConfig(GenerationOptions options, List<String> yamlFilePaths) {
        ProjectConfig config = new ProjectConfig();
        
        if (options.getGroupId() != null) config.setGroupId(options.getGroupId());
        if (options.getArtifactId() != null) config.setArtifactId(options.getArtifactId());
        if (options.getVersion() != null) config.setVersion(options.getVersion());
        if (options.getProjectName() != null) config.setProjectName(options.getProjectName());
        
        // Extract just the filenames for documentation
        List<String> fileNames = yamlFilePaths.stream()
            .map(path -> Paths.get(path).getFileName().toString())
            .collect(Collectors.toList());
        config.setSourceFiles(fileNames);
        
        return config;
    }
    
    private GeneratedProject generateSourceCode(List<ComponentMetadata> components, ProjectConfig projectConfig, String defaultWorkflowName) {
        GeneratedProject project = new GeneratedProject();
        
        // Generate POM
        project.setPomXml(codeGenerator.generatePom(components, projectConfig));
        
        // Generate README
        project.setReadme(codeGenerator.generateReadme(components, projectConfig));
        
        // Generate Main class
        String mainClassContent = codeGenerator.generateMain(components, projectConfig, defaultWorkflowName);
        String mainClassPath = "src/main/java/" + projectConfig.getGroupId().replace('.', '/') + "/Main.java";
        project.addSourceFile(mainClassPath, mainClassContent);
        
        // Generate component source files
        for (ComponentMetadata component : components) {
            String sourceCode = codeGenerator.generateComponent(component);
            String relativePath = getRelativeSourcePath(component);
            project.addSourceFile(relativePath, sourceCode);
        }
        
        return project;
    }
    
    private Path writeProjectFiles(GeneratedProject project, String outputDirectory, List<String> yamlFilePaths) throws IOException {
        Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);
        
        // Write POM
        Files.write(outputDir.resolve("pom.xml"), project.getPomXml().getBytes());
        
        // Write README
        Files.write(outputDir.resolve("README.md"), project.getReadme().getBytes());
        
        // Write source files
        for (GeneratedProject.SourceFile sourceFile : project.getSourceFiles()) {
            Path sourceFilePath = outputDir.resolve(sourceFile.getRelativePath());
            Files.createDirectories(sourceFilePath.getParent());
            Files.write(sourceFilePath, sourceFile.getContent().getBytes());
        }
        
        // Create resources directory and copy YAML files
        Path resourcesDir = outputDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        // Copy the first YAML file as workflow.yaml (main workflow configuration)
        if (!yamlFilePaths.isEmpty()) {
            Path firstYamlFile = Paths.get(yamlFilePaths.get(0));
            Path targetYamlFile = resourcesDir.resolve("workflow.yaml");
            Files.copy(firstYamlFile, targetYamlFile);
            logger.info("Copied {} to generated resources as workflow.yaml", firstYamlFile.getFileName());
            
            // If there are multiple YAML files, copy them with their original names
            if (yamlFilePaths.size() > 1) {
                for (int i = 1; i < yamlFilePaths.size(); i++) {
                    Path yamlFile = Paths.get(yamlFilePaths.get(i));
                    Path targetFile = resourcesDir.resolve(yamlFile.getFileName());
                    Files.copy(yamlFile, targetFile);
                    logger.info("Copied {} to generated resources", yamlFile.getFileName());
                }
            }
        }
        
        return outputDir;
    }
    
    private String createZipDistribution(Path outputDir, String zipFileName) throws IOException {
        Path zipPath = outputDir.getParent().resolve(zipFileName);
        
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(outputDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = outputDir.relativize(file).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(relativePath);
                        zipOut.putNextEntry(entry);
                        Files.copy(file, zipOut);
                        zipOut.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to add file to ZIP: " + file, e);
                    }
                });
        }
        
        logger.info("Created ZIP distribution: {}", zipPath);
        return zipPath.toString();
    }
    
    private String extractDefaultWorkflowName(List<String> yamlFilePaths) {
        if (yamlFilePaths.isEmpty()) {
            return "defaultWorkflow";
        }
        
        try (InputStream inputStream = Files.newInputStream(Paths.get(yamlFilePaths.get(0)))) {
            var yamlData = yamlAnalyzer.analyzeYamlData(new org.yaml.snakeyaml.Yaml().load(inputStream));
            // Extract workflow names from analyzed components or use the first workflow from raw YAML
            
            // Load raw YAML to get workflow names
            try (InputStream rawStream = Files.newInputStream(Paths.get(yamlFilePaths.get(0)))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawYaml = new org.yaml.snakeyaml.Yaml().load(rawStream);
                @SuppressWarnings("unchecked")
                Map<String, Object> workflows = (Map<String, Object>) rawYaml.get("workflows");
                if (workflows != null && !workflows.isEmpty()) {
                    String firstWorkflow = workflows.keySet().iterator().next();
                    logger.debug("Extracted default workflow name: {}", firstWorkflow);
                    return firstWorkflow;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract workflow name from YAML: {}", e.getMessage());
        }
        
        return "orderProcessingFlow"; // fallback
    }
    
    private String getRelativeSourcePath(ComponentMetadata component) {
        String packagePath = component.getPackageName().replace('.', '/');
        return "src/main/java/" + packagePath + "/" + component.getClassName() + ".java";
    }
    
    /**
     * Options for code generation
     */
    public static class GenerationOptions {
        private String outputDirectory = "./generated-stepflow-project";
        private String groupId;
        private String artifactId;
        private String version;
        private String projectName;
        private boolean createZip = true;
        private String zipFileName = "stepflow-generated-components.zip";
        
        // Getters and setters
        public String getOutputDirectory() { return outputDirectory; }
        public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
        
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public boolean isCreateZip() { return createZip; }
        public void setCreateZip(boolean createZip) { this.createZip = createZip; }
        
        public String getZipFileName() { return zipFileName; }
        public void setZipFileName(String zipFileName) { this.zipFileName = zipFileName; }
    }
    
    /**
     * Result of code generation process
     */
    public static class GenerationResult {
        private final boolean success;
        private final String message;
        private final String outputPath;
        private final String zipPath;
        private final List<ComponentMetadata> generatedComponents;
        
        private GenerationResult(boolean success, String message, String outputPath, 
                               String zipPath, List<ComponentMetadata> generatedComponents) {
            this.success = success;
            this.message = message;
            this.outputPath = outputPath;
            this.zipPath = zipPath;
            this.generatedComponents = generatedComponents;
        }
        
        public static GenerationResult success(String message, String outputPath, String zipPath, 
                                             List<ComponentMetadata> components) {
            return new GenerationResult(true, message, outputPath, zipPath, components);
        }
        
        public static GenerationResult failure(String message) {
            return new GenerationResult(false, message, null, null, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getOutputPath() { return outputPath; }
        public String getZipPath() { return zipPath; }
        public List<ComponentMetadata> getGeneratedComponents() { return generatedComponents; }
    }
    
    /**
     * Internal representation of generated project
     */
    private static class GeneratedProject {
        private String pomXml;
        private String readme;
        private List<SourceFile> sourceFiles = new java.util.ArrayList<>();
        
        public void addSourceFile(String relativePath, String content) {
            sourceFiles.add(new SourceFile(relativePath, content));
        }
        
        static class SourceFile {
            private final String relativePath;
            private final String content;
            
            public SourceFile(String relativePath, String content) {
                this.relativePath = relativePath;
                this.content = content;
            }
            
            public String getRelativePath() { return relativePath; }
            public String getContent() { return content; }
        }
        
        // Getters and setters
        public String getPomXml() { return pomXml; }
        public void setPomXml(String pomXml) { this.pomXml = pomXml; }
        
        public String getReadme() { return readme; }
        public void setReadme(String readme) { this.readme = readme; }
        
        public List<SourceFile> getSourceFiles() { return sourceFiles; }
    }
}