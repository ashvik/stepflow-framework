package com.stepflow.codegen;

import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationOptions;
import com.stepflow.codegen.CodeGenerationOrchestrator.GenerationResult;
import com.stepflow.codegen.model.ComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command Line Interface for StepFlow Code Generator
 */
public class CodeGeneratorCLI {
    private static final Logger logger = LoggerFactory.getLogger(CodeGeneratorCLI.class);
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        try {
            CLIOptions options = parseArguments(args);
            
            if (options.isHelp()) {
                printUsage();
                System.exit(0);
            }
            
            // Validate input files exist
            for (String filePath : options.getYamlFiles()) {
                if (!Files.exists(Paths.get(filePath))) {
                    System.err.println("Error: YAML file not found: " + filePath);
                    System.exit(1);
                }
            }
            
            // Run code generation
            CodeGenerationOrchestrator orchestrator = new CodeGenerationOrchestrator();
            GenerationResult result = orchestrator.generateFromYamlFiles(options.getYamlFiles(), options.getGenerationOptions());
            
            if (result.isSuccess()) {
                System.out.println("✅ " + result.getMessage());
                System.out.println("📁 Output directory: " + result.getOutputPath());
                
                if (result.getZipPath() != null) {
                    System.out.println("📦 ZIP distribution: " + result.getZipPath());
                }
                
                if (result.getGeneratedComponents() != null && !result.getGeneratedComponents().isEmpty()) {
                    printGeneratedComponents(result.getGeneratedComponents());
                }
                
                System.out.println("\n🚀 Next steps:");
                System.out.println("   1. Navigate to the output directory");
                System.out.println("   2. Implement TODO methods in generated classes");
                System.out.println("   3. Run 'mvn compile' to build the project");
                System.out.println("   4. Use the components in your StepFlow workflows");
                
            } else {
                System.err.println("❌ " + result.getMessage());
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Code generation failed: " + e.getMessage());
            logger.error("CLI execution failed", e);
            System.exit(1);
        }
    }
    
    private static void printGeneratedComponents(List<ComponentMetadata> components) {
        long stepCount = components.stream().filter(c -> c.getType() == ComponentMetadata.ComponentType.STEP).count();
        long guardCount = components.stream().filter(c -> c.getType() == ComponentMetadata.ComponentType.GUARD).count();
        
        System.out.println("\n📊 Generated Components Summary:");
        System.out.println("   📝 Steps: " + stepCount);
        System.out.println("   🛡️  Guards: " + guardCount);
        System.out.println("   📦 Total: " + components.size());
        
        if (components.size() <= 10) {
            System.out.println("\n📋 Component Details:");
            for (ComponentMetadata component : components) {
                String icon = component.getType() == ComponentMetadata.ComponentType.STEP ? "📝" : "🛡️";
                System.out.printf("   %s %s (%s)\n", 
                    icon, component.getClassName(), component.getName());
            }
        }
    }
    
    private static CLIOptions parseArguments(String[] args) {
        CLIOptions options = new CLIOptions();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "-h":
                case "--help":
                    options.setHelp(true);
                    break;
                    
                case "-o":
                case "--output":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setOutputDirectory(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --output");
                    }
                    break;
                    
                case "-g":
                case "--group-id":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setGroupId(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --group-id");
                    }
                    break;
                    
                case "-a":
                case "--artifact-id":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setArtifactId(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --artifact-id");
                    }
                    break;
                    
                case "-v":
                case "--version":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setVersion(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --version");
                    }
                    break;
                    
                case "-n":
                case "--project-name":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setProjectName(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --project-name");
                    }
                    break;
                    
                case "-z":
                case "--zip-name":
                    if (i + 1 < args.length) {
                        options.getGenerationOptions().setZipFileName(args[++i]);
                    } else {
                        throw new IllegalArgumentException("Missing value for --zip-name");
                    }
                    break;
                    
                case "--no-zip":
                    options.getGenerationOptions().setCreateZip(false);
                    break;
                    
                default:
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    } else {
                        // Assume it's a YAML file path
                        options.getYamlFiles().add(arg);
                    }
                    break;
            }
        }
        
        if (options.getYamlFiles().isEmpty() && !options.isHelp()) {
            throw new IllegalArgumentException("At least one YAML file must be specified");
        }
        
        return options;
    }
    
    private static void printUsage() {
        System.out.println("StepFlow Code Generator");
        System.out.println("=======================");
        System.out.println();
        System.out.println("Generates Java Step and Guard classes from StepFlow YAML configurations");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar stepflow-codegen.jar [OPTIONS] <yaml-file1> [yaml-file2] ...");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help              Show this help message");
        System.out.println("  -o, --output DIR        Output directory (default: ./generated-stepflow-project)");
        System.out.println("  -g, --group-id ID       Maven group ID (default: com.stepflow.generated)");
        System.out.println("  -a, --artifact-id ID    Maven artifact ID (default: stepflow-generated-components)");
        System.out.println("  -v, --version VERSION   Project version (default: 1.0.0-SNAPSHOT)");
        System.out.println("  -n, --project-name NAME Project name for documentation");
        System.out.println("  -z, --zip-name FILE     ZIP file name (default: stepflow-generated-components.zip)");
        System.out.println("  --no-zip                Don't create ZIP distribution");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Generate from single YAML file");
        System.out.println("  java -jar stepflow-codegen.jar workflow.yaml");
        System.out.println();
        System.out.println("  # Generate from multiple files with custom output");
        System.out.println("  java -jar stepflow-codegen.jar -o ./my-project workflow1.yaml workflow2.yaml");
        System.out.println();
        System.out.println("  # Generate with custom Maven coordinates");
        System.out.println("  java -jar stepflow-codegen.jar \\");
        System.out.println("    -g com.company -a order-workflow -v 2.0.0 \\");
        System.out.println("    -n \"Order Processing Components\" \\");
        System.out.println("    order-workflow.yaml");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  The generator creates a complete Maven project with:");
        System.out.println("  - Generated Step and Guard Java classes");
        System.out.println("  - Maven POM with proper dependencies");
        System.out.println("  - README with component documentation");
        System.out.println("  - ZIP distribution (optional)");
        System.out.println();
    }
    
    /**
     * Command line options
     */
    private static class CLIOptions {
        private boolean help = false;
        private List<String> yamlFiles = new java.util.ArrayList<>();
        private GenerationOptions generationOptions = new GenerationOptions();
        
        public boolean isHelp() { return help; }
        public void setHelp(boolean help) { this.help = help; }
        
        public List<String> getYamlFiles() { return yamlFiles; }
        public void setYamlFiles(List<String> yamlFiles) { this.yamlFiles = yamlFiles; }
        
        public GenerationOptions getGenerationOptions() { return generationOptions; }
        public void setGenerationOptions(GenerationOptions generationOptions) { this.generationOptions = generationOptions; }
    }
}