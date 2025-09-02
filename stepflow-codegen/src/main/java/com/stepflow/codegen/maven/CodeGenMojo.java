package com.stepflow.codegen.maven;

import com.stepflow.codegen.CodeGenerationOrchestrator;
import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationOptions;
import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maven plugin for generating StepFlow components from YAML files
 */
@Mojo(
    name = "generate", 
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
public class CodeGenMojo extends AbstractMojo {
    
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    
    @Parameter(property = "stepflow.codegen.yamlFiles", required = true)
    private List<File> yamlFiles;
    
    @Parameter(property = "stepflow.codegen.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/stepflow")
    private File outputDirectory;
    
    @Parameter(property = "stepflow.codegen.groupId", defaultValue = "${project.groupId}")
    private String groupId;
    
    @Parameter(property = "stepflow.codegen.artifactId", defaultValue = "${project.artifactId}-generated")
    private String artifactId;
    
    @Parameter(property = "stepflow.codegen.version", defaultValue = "${project.version}")
    private String version;
    
    @Parameter(property = "stepflow.codegen.projectName", defaultValue = "Generated StepFlow Components")
    private String projectName;
    
    @Parameter(property = "stepflow.codegen.createZip", defaultValue = "false")
    private boolean createZip;
    
    @Parameter(property = "stepflow.codegen.zipFileName", defaultValue = "stepflow-generated-components.zip")
    private String zipFileName;
    
    @Parameter(property = "stepflow.codegen.addToSources", defaultValue = "true")
    private boolean addToSources;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Starting StepFlow code generation...");
        
        // Validate YAML files
        validateYamlFiles();
        
        // Setup generation options
        GenerationOptions options = new GenerationOptions();
        options.setOutputDirectory(outputDirectory.getAbsolutePath());
        options.setGroupId(groupId);
        options.setArtifactId(artifactId);
        options.setVersion(version);
        options.setProjectName(projectName);
        options.setCreateZip(createZip);
        options.setZipFileName(zipFileName);
        
        // Convert File list to String list
        List<String> yamlFilePaths = yamlFiles.stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.toList());
        
        try {
            // Run code generation
            CodeGenerationOrchestrator orchestrator = new CodeGenerationOrchestrator();
            GenerationResult result = orchestrator.generateFromYamlFiles(yamlFilePaths, options);
            
            if (result.isSuccess()) {
                getLog().info("✅ " + result.getMessage());
                getLog().info("📁 Output directory: " + result.getOutputPath());
                
                if (result.getZipPath() != null) {
                    getLog().info("📦 ZIP distribution: " + result.getZipPath());
                }
                
                // Add generated sources to Maven compilation
                if (addToSources && result.getGeneratedComponents() != null) {
                    String sourceDir = outputDirectory.getAbsolutePath() + "/src/main/java";
                    project.addCompileSourceRoot(sourceDir);
                    getLog().info("📂 Added to compile sources: " + sourceDir);
                }
                
                // Log component summary
                if (result.getGeneratedComponents() != null) {
                    long stepCount = result.getGeneratedComponents().stream()
                        .filter(c -> c.getType() == com.stepflow.codegen.model.ComponentMetadata.ComponentType.STEP)
                        .count();
                    long guardCount = result.getGeneratedComponents().stream()
                        .filter(c -> c.getType() == com.stepflow.codegen.model.ComponentMetadata.ComponentType.GUARD)
                        .count();
                        
                    getLog().info("📊 Generated: " + stepCount + " steps, " + guardCount + " guards");
                }
                
            } else {
                throw new MojoFailureException("Code generation failed: " + result.getMessage());
            }
            
        } catch (Exception e) {
            getLog().error("Code generation failed", e);
            throw new MojoExecutionException("Code generation failed", e);
        }
    }
    
    private void validateYamlFiles() throws MojoFailureException {
        if (yamlFiles == null || yamlFiles.isEmpty()) {
            throw new MojoFailureException("No YAML files specified. Use <yamlFiles> configuration or -Dstepflow.codegen.yamlFiles property");
        }
        
        for (File yamlFile : yamlFiles) {
            if (!yamlFile.exists()) {
                throw new MojoFailureException("YAML file not found: " + yamlFile.getAbsolutePath());
            }
            
            if (!yamlFile.canRead()) {
                throw new MojoFailureException("Cannot read YAML file: " + yamlFile.getAbsolutePath());
            }
        }
        
        getLog().info("Found " + yamlFiles.size() + " YAML file(s) to process");
    }
}