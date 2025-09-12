package com.stepflow.examples.tools;

import com.stepflow.config.FlowConfig;
import com.stepflow.engine.Engine;
import com.stepflow.resource.YamlResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.Map;

/**
 * CLI to analyze and print an architectural graph for a workflow
 * using Engine.analyzeWorkflow().
 *
 * Usage:
 *   java -cp <jar> com.stepflow.examples.tools.WorkflowAnalyzer <yamlPath> <workflowName> [scanPackagesCsv]
 *
 * Examples:
 *   java -cp target/stepflow-examples-<ver>-jar-with-dependencies.jar \
 *        com.stepflow.examples.tools.WorkflowAnalyzer \
 *        classpath:examples/complex.yaml complexFlow com.stepflow.examples
 */
public class WorkflowAnalyzer {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: WorkflowAnalyzer <yamlPath> <workflowName> [scanPackagesCsv]");
            System.err.println("Example: WorkflowAnalyzer classpath:examples/complex.yaml complexFlow com.stepflow.examples");
            System.exit(1);
        }

        String yamlPath = args[0];
        String workflowName = args[1];
        String[] scanPackages = args.length >= 3 ? args[2].split(",") : new String[]{"com.stepflow.examples"};

        // Load YAML to FlowConfig
        YamlResourceLoader loader = new YamlResourceLoader();
        Map<String, Object> yamlData = loader.loadYaml(yamlPath);
        Yaml yaml = new Yaml();
        FlowConfig config = yaml.loadAs(yaml.dump(yamlData), FlowConfig.class);

        // Build engine and analyze
        Engine engine = new Engine(config, scanPackages);
        System.out.println("Analyzing workflow '" + workflowName + "' with packages: " + Arrays.toString(scanPackages));
        engine.analyzeWorkflow(workflowName);
    }
}

