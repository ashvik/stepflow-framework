package com.stepflow.config;

import com.stepflow.resource.YamlResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

/**
 * Builder for creating FlowConfig from YAML resources.
 */
public class SimpleWorkflowBuilder {
    
    private final static YamlResourceLoader resourceLoader = new YamlResourceLoader();
    
    public static FlowConfig buildFlowConfig(String resourcePath) {
        Map<String, Object> yamlData = resourceLoader.loadYaml(resourcePath);
        return convertYamlToFlowConfig(yamlData);
    }

    public static FlowConfig convertYamlToFlowConfig(Map<String, Object> yamlData) {
        Yaml yaml = new Yaml();
        return yaml.loadAs(yaml.dump(yamlData), FlowConfig.class);
    }
}