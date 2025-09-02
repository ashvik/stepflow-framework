package com.stepflow.resource;

import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.URL;
import java.util.Map;

/**
 * Loads YAML resources from various sources.
 */
public class YamlResourceLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlResourceLoader.class);
    
    private final Yaml yaml = new Yaml();
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadYaml(String resourcePath) {
        try {
            InputStream inputStream = getInputStream(resourcePath);
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            
            LOGGER.debug("Loading YAML from {}", resourcePath);
            return yaml.load(inputStream);
        } catch (Exception e) {
            LOGGER.error("Failed to load YAML from {}: {}", resourcePath, e.toString());
            throw new RuntimeException("Failed to load YAML from: " + resourcePath, e);
        }
    }
    
    private InputStream getInputStream(String resourcePath) throws IOException {
        if (resourcePath.startsWith("classpath:")) {
            String path = resourcePath.substring("classpath:".length());
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                LOGGER.warn("Classpath resource not found: {}", path);
            }
            return is;
        } else if (resourcePath.startsWith("file:")) {
            String path = resourcePath.substring("file:".length());
            LOGGER.debug("Opening file: {}", path);
            return new FileInputStream(path);
        } else if (resourcePath.startsWith("http://") || resourcePath.startsWith("https://")) {
            URL url = new URL(resourcePath);
            LOGGER.debug("Opening URL: {}", url);
            return url.openStream();
        } else {
            // Default to classpath
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                LOGGER.warn("Classpath resource not found: {}", resourcePath);
            }
            return is;
        }
    }
}
