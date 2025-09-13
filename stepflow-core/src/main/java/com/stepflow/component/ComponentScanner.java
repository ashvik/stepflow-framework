package com.stepflow.component;

import com.stepflow.execution.Guard;
import com.stepflow.execution.Step;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automatic component discovery and registration system for Step and Guard implementations.
 * 
 * <p>ComponentScanner provides intelligent discovery of Step and Guard components within
 * specified packages, supporting both development environments (exploded directories) and
 * production deployments (JAR files). It handles complex classpath scenarios gracefully
 * and provides flexible registration strategies.
 * 
 * <h2>Core Features</h2>
 * 
 * <ul>
 *   <li><strong>Multi-Environment Support:</strong> Scans exploded directories and JAR files</li>
 *   <li><strong>Flexible Registration:</strong> Multiple naming strategies for component lookup</li>
 *   <li><strong>Annotation-Aware:</strong> Respects {@code @StepComponent} and {@code @GuardComponent} names</li>
 *   <li><strong>Graceful Failure:</strong> Continues scanning despite individual class loading errors</li>
 *   <li><strong>Classloader Compatibility:</strong> Works with various classloader configurations</li>
 *   <li><strong>Caching Strategy:</strong> Efficient component resolution and reuse</li>
 * </ul>
 * 
 * <h2>Component Registration Strategy</h2>
 * 
 * <p>For each discovered component, multiple registration keys are created to maximize
 * lookup flexibility:
 * 
 * <p><strong>Default Registration Keys:</strong>
 * <ol>
 *   <li><strong>Simple Class Name:</strong> {@code "OrderValidationStep"}</li>
 *   <li><strong>Lower Camel Case:</strong> {@code "orderValidationStep"}</li>
 *   <li><strong>Fully Qualified Class Name:</strong> {@code "com.example.steps.OrderValidationStep"}</li>
 * </ol>
 * 
 * <p><strong>Annotation-Based Registration:</strong>
 * <ol>
 *   <li><strong>Annotation Name:</strong> Value from {@code @StepComponent(name = "validateOrder")}</li>
 *   <li><strong>Annotation Lower Camel:</strong> {@code "validateorder"} (if annotation name provided)</li>
 * </ol>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <p><strong>Basic Component Scanning:</strong>
 * <pre>
 * ComponentScanner scanner = new ComponentScanner();
 * 
 * // Scan multiple packages
 * scanner.scanPackages("com.example.steps", "com.example.guards", "com.thirdparty.components");
 * 
 * // Retrieve component registries
 * Map&lt;String, Class&lt;? extends Step&gt;&gt; stepRegistry = scanner.getStepRegistry();
 * Map&lt;String, Class&lt;? extends Guard&gt;&gt; guardRegistry = scanner.getGuardRegistry();
 * 
 * System.out.println("Discovered " + stepRegistry.size() + " step components");
 * System.out.println("Discovered " + guardRegistry.size() + " guard components");
 * </pre>
 * 
 * <p><strong>Component Resolution Examples:</strong>
 * <pre>
 * // Different ways to resolve the same component
 * Class&lt;? extends Step&gt; stepClass1 = scanner.getStepClass("OrderValidationStep");     // Simple name
 * Class&lt;? extends Step&gt; stepClass2 = scanner.getStepClass("orderValidationStep");     // Lower camel
 * Class&lt;? extends Step&gt; stepClass3 = scanner.getStepClass("validateOrder");          // Annotation name
 * Class&lt;? extends Step&gt; stepClass4 = scanner.getStepClass("com.example.steps.OrderValidationStep"); // FQCN
 * 
 * // All references point to the same class
 * assert stepClass1 == stepClass2;
 * assert stepClass2 == stepClass3;
 * assert stepClass3 == stepClass4;
 * </pre>
 * 
 * <p><strong>Component Implementation Examples:</strong>
 * <pre>
 * // Standard component without annotation
 * public class OrderValidationStep implements Step {
 *     {@code @Override}
 *     public StepResult execute(ExecutionContext ctx) {
 *         // implementation
 *     }
 * }
 * // Registered as: "OrderValidationStep", "orderValidationStep", FQCN
 * 
 * // Component with custom annotation name
 * {@code @StepComponent(name = "validateOrder")}
 * public class OrderValidationStep implements Step {
 *     // implementation
 * }
 * // Registered as: "OrderValidationStep", "orderValidationStep", FQCN, "validateOrder", "validateorder"
 * 
 * // Guard component example
 * {@code @GuardComponent(name = "BusinessHoursGuard")}
 * public class BusinessOperatingHoursGuard implements Guard {
 *     {@code @Override}
 *     public boolean evaluate(ExecutionContext ctx) {
 *         // implementation
 *     }
 * }
 * // Registered as: "BusinessOperatingHoursGuard", "businessOperatingHoursGuard", FQCN, "BusinessHoursGuard", "businesshoursguard"
 * </pre>
 * 
 * <h2>Scanning Environments</h2>
 * 
 * <p><strong>Development Environment (Exploded Classes):</strong>
 * <pre>
 * // Typical IDE or Maven target directory structure
 * target/classes/
 *   └── com/example/steps/
 *       ├── OrderValidationStep.class
 *       ├── PaymentProcessingStep.class
 *       └── NotificationStep.class
 * 
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages("com.example.steps");
 * // Discovers all .class files in the directory tree
 * </pre>
 * 
 * <p><strong>Production Environment (JAR Files):</strong>
 * <pre>
 * // Application JAR or dependency JARs
 * my-app.jar!/
 *   └── com/example/steps/
 *       ├── OrderValidationStep.class
 *       ├── PaymentProcessingStep.class
 *       └── NotificationStep.class
 * 
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages("com.example.steps");
 * // Scans JAR entries matching the package structure
 * </pre>
 * 
 * <p><strong>Mixed Environment (Dependencies + Local):</strong>
 * <pre>
 * // Scans components from multiple sources
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages(
 *     "com.example.steps",           // Local application components
 *     "com.thirdparty.workflow",     // Third-party library components
 *     "org.company.shared.steps"     // Shared company components
 * );
 * </pre>
 * 
 * <h2>Error Handling and Robustness</h2>
 * 
 * <p><strong>Graceful Error Handling:</strong>
 * <pre>
 * // Scanner continues despite individual class loading failures
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages("com.example.steps");
 * 
 * // Even if some classes fail to load (missing dependencies, etc.),
 * // other classes in the same package are still discovered and registered
 * Map&lt;String, Class&lt;? extends Step&gt;&gt; steps = scanner.getStepRegistry();
 * 
 * // Scanner logs warnings for failed classes but doesn't throw exceptions
 * </pre>
 * 
 * <p><strong>Classloader Scenarios:</strong>
 * <pre>
 * // Scanner handles various classloader configurations:
 * // - Thread context classloader
 * // - System classloader
 * // - Custom classloaders (Spring Boot, OSGi, etc.)
 * // - Parent-first and parent-last delegation models
 * </pre>
 * 
 * <h2>Integration Patterns</h2>
 * 
 * <p><strong>Engine Integration:</strong>
 * <pre>
 * // Typical usage within StepFlow Engine
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages("com.example.workflow");
 * 
 * // Engine uses scanner to resolve step types from configuration
 * FlowConfig config = loadFlowConfig("workflow.yaml");
 * for (Map.Entry&lt;String, StepDef&gt; entry : config.steps.entrySet()) {
 *     String stepName = entry.getKey();
 *     String stepType = entry.getValue().type;
 *     
 *     Class&lt;? extends Step&gt; stepClass = scanner.getStepClass(stepType);
 *     if (stepClass == null) {
 *         throw new IllegalStateException("Step type not found: " + stepType);
 *     }
 *     
 *     // Engine can now instantiate the step
 * }
 * </pre>
 * 
 * <p><strong>Dynamic Component Loading:</strong>
 * <pre>
 * // Runtime component discovery
 * ComponentScanner scanner = new ComponentScanner();
 * 
 * // Scan base packages at startup
 * scanner.scanPackages("com.example.core");
 * 
 * // Later, scan additional plugin packages
 * scanner.scanPackages("com.example.plugins");
 * 
 * // All components are available for resolution
 * Class&lt;? extends Step&gt; pluginStep = scanner.getStepClass("PluginStep");
 * </pre>
 * 
 * <h2>Performance Considerations</h2>
 * 
 * <ul>
 *   <li><strong>Scanning Cost:</strong> Package scanning is performed once during initialization</li>
 *   <li><strong>Memory Usage:</strong> Component classes are cached for efficient lookup</li>
 *   <li><strong>Startup Time:</strong> Large package scans may impact application startup</li>
 *   <li><strong>Class Loading:</strong> Classes are loaded during scanning, not during lookup</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li><strong>Package Organization:</strong> Group related components in logical packages</li>
 *   <li><strong>Naming Conventions:</strong> Use clear, descriptive class names</li>
 *   <li><strong>Annotation Usage:</strong> Use {@code @StepComponent} names for YAML-friendly identifiers</li>
 *   <li><strong>Error Monitoring:</strong> Monitor logs for component discovery warnings</li>
 *   <li><strong>Testing:</strong> Verify component discovery in different deployment environments</li>
 * </ul>
 * 
 * <h2>Troubleshooting</h2>
 * 
 * <p><strong>Component Not Found Issues:</strong>
 * <pre>
 * // Debug component discovery
 * ComponentScanner scanner = new ComponentScanner();
 * scanner.scanPackages("com.example.steps");
 * 
 * Map&lt;String, Class&lt;? extends Step&gt;&gt; steps = scanner.getStepRegistry();
 * System.out.println("Discovered steps:");
 * steps.forEach((name, clazz) -> 
 *     System.out.println("  " + name + " -> " + clazz.getName()));
 * 
 * // Check if expected component is registered
 * if (scanner.getStepClass("MyStep") == null) {
 *     System.err.println("MyStep not found. Check package scanning and class name.");
 * }
 * </pre>
 * 
 * @see Step for step component interface
 * @see Guard for guard component interface
 * @see com.stepflow.core.annotations.StepComponent for step annotation
 * @see com.stepflow.core.annotations.GuardComponent for guard annotation
 * @see Engine for component usage in workflow execution
 * 
 * @since 1.0.0
 * @author StepFlow Team
 */
public class ComponentScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentScanner.class);

    private final Map<String, Class<? extends Step>> stepRegistry = new HashMap<>();
    private final Map<String, Class<? extends Guard>> guardRegistry = new HashMap<>();

    /**
     * Scans one or more packages for Step and Guard implementations and registers them.
     * 
     * <p>This is the primary entry point for component discovery. The method processes
     * each package recursively, discovering all classes that implement Step or Guard
     * interfaces and registering them under multiple lookup keys.
     * 
     * <p><strong>Package Scanning Process:</strong>
     * <ol>
     *   <li>Convert package names to classpath resource paths</li>
     *   <li>Enumerate matching resources from all available classloaders</li>
     *   <li>Process each resource (directory or JAR) appropriately</li>
     *   <li>Load discovered classes and check for Step/Guard interfaces</li>
     *   <li>Register qualifying classes under multiple naming strategies</li>
     * </ol>
     * 
     * <p><strong>Usage Examples:</strong>
     * <pre>
     * ComponentScanner scanner = new ComponentScanner();
     * 
     * // Single package scan
     * scanner.scanPackages("com.example.steps");
     * 
     * // Multiple package scan
     * scanner.scanPackages("com.example.steps", "com.example.guards", "com.thirdparty.workflow");
     * 
     * // Verify discovered components
     * Map&lt;String, Class&lt;? extends Step&gt;&gt; steps = scanner.getStepRegistry();
     * System.out.println("Found " + steps.size() + " step implementations");
     * </pre>
     * 
     * <p><strong>Error Handling:</strong>
     * The method is designed to be resilient to individual failures:
     * <ul>
     *   <li>Missing packages are logged as warnings but don't stop scanning</li>
     *   <li>Classes that can't be loaded are logged as debug messages and skipped</li>
     *   <li>Non-Step/Guard classes are silently ignored</li>
     *   <li>Classloader access issues are handled gracefully</li>
     * </ul>
     * 
     * @param packages one or more package names to scan recursively (e.g., "com.example.workflows").
     *                Null or empty strings are ignored. Package names should use dot notation.
     * @see #getStepRegistry() to retrieve discovered Step components
     * @see #getGuardRegistry() to retrieve discovered Guard components
     */
    public void scanPackages(String... packages) {
        if (packages == null) return;
        for (String packageName : packages) {
            if (packageName == null || packageName.isEmpty()) continue;
            LOGGER.debug("Scanning package: {}", packageName);
            scanPackage(packageName);
        }
    }

    /**
     * Scans a single package by enumerating matching classpath resources and dispatching per-resource scanners.
     */
    private void scanPackage(String packageName) {
        String path = packageToPath(packageName);

        // Try both TCCL and the defining classloader
        List<ClassLoader> loaders = Arrays.asList(
                Thread.currentThread().getContextClassLoader(),
                ComponentScanner.class.getClassLoader()
        );

        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                Enumeration<URL> resources = loader.getResources(path);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    scanResourceURL(url, packageName, path);
                }
            } catch (Exception e) {
                LOGGER.warn("Error scanning package '{}' with loader {}: {}", packageName, loader, e.toString());
            }
        }
    }

    /**
     * Dispatches scanning based on resource URL protocol.
     */
    private void scanResourceURL(URL url, String packageName, String path) {
        if (url == null) return;
        String protocol = url.getProtocol();
        try {
            if ("file".equalsIgnoreCase(protocol)) {
                File dir = toFile(url);
                if (dir != null && dir.exists()) {
                    scanDirectory(dir, packageName);
                }
            } else if ("jar".equalsIgnoreCase(protocol)) {
                scanJar(url, path);
            } else {
                // Some environments (e.g., Spring Boot) provide nested URL formats; attempt best-effort handling
                if (protocol != null && protocol.toLowerCase().contains("jar")) {
                    scanJar(url, path);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to scan resource '{}': {}", url, e.toString());
        }
    }

    /** Converts a package name to a resource path. */
    private String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    /** Safely converts a file URL to a File, handling URL encoding. */
    private File toFile(URL url) throws UnsupportedEncodingException {
        String file = url.getFile();
        if (file == null) return null;
        String decoded = URLDecoder.decode(file, java.nio.charset.StandardCharsets.UTF_8);
        return new File(decoded);
    }

    /**
     * Recursively scans a file system directory for .class files under a package and registers them.
     */
    private void scanDirectory(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + '.' + file.getName());
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String simple = file.getName().substring(0, file.getName().length() - 6);
                String className = packageName + '.' + simple;
                loadAndRegisterClass(className);
            }
        }
    }

    /**
     * Scans a JAR URL for classes under the provided package path and registers them.
     */
    private void scanJar(URL url, String packagePath) {
        try {
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            try (JarFile jar = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(packagePath)
                            && name.endsWith(".class")
                            && !name.contains("$")) {
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        loadAndRegisterClass(className);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Jar scan failed for '{}': {}", url, e.toString());
        }
    }

    /** Attempts to load a class by name and register it if it is a Step/Guard implementation. */
    private void loadAndRegisterClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            registerClass(clazz);
        } catch (Throwable ignored) {
            // Ignore classes that cannot be loaded in current context
            LOGGER.debug("Skipping class '{}' (cannot be loaded)", className);
        }
    }

    /** Registers a discovered class into the Step/Guard registries using multiple keys. */
    @SuppressWarnings("unchecked")
    private void registerClass(Class<?> clazz) {
        if (clazz == null || clazz.isInterface()) return;

        if (Step.class.isAssignableFrom(clazz)) {
            Class<? extends Step> stepClass = (Class<? extends Step>) clazz;
            registerStepKeys(stepClass);
            registerAnnotatedStepName(stepClass);
        }
        if (Guard.class.isAssignableFrom(clazz)) {
            Class<? extends Guard> guardClass = (Class<? extends Guard>) clazz;
            registerGuardKeys(guardClass);
            registerAnnotatedGuardName(guardClass);
        }
    }

    /** Registers standard keys for a Step: simple, lowerCamel, and FQCN. */
    private void registerStepKeys(Class<? extends Step> stepClass) {
        String simple = stepClass.getSimpleName();
        stepRegistry.put(simple, stepClass);
        stepRegistry.put(lowerCamel(simple), stepClass);
        stepRegistry.put(stepClass.getName(), stepClass);
    }

    /** Registers standard keys for a Guard: simple, lowerCamel, and FQCN. */
    private void registerGuardKeys(Class<? extends Guard> guardClass) {
        String simple = guardClass.getSimpleName();
        guardRegistry.put(simple, guardClass);
        guardRegistry.put(lowerCamel(simple), guardClass);
        guardRegistry.put(guardClass.getName(), guardClass);
    }

    /** Registers annotation-provided name for a Step (if present). */
    private void registerAnnotatedStepName(Class<? extends Step> stepClass) {
        try {
            com.stepflow.core.annotations.StepComponent ann =
                    stepClass.getAnnotation(com.stepflow.core.annotations.StepComponent.class);
            if (ann == null) return;
            String name = ann.name();
            if (name == null || name.isEmpty()) return;
            stepRegistry.put(name, stepClass);
            stepRegistry.put(lowerCamel(name), stepClass);
        } catch (Throwable ignored) { }
    }

    /** Registers annotation-provided name for a Guard (if present). */
    private void registerAnnotatedGuardName(Class<? extends Guard> guardClass) {
        try {
            com.stepflow.core.annotations.GuardComponent ann =
                    guardClass.getAnnotation(com.stepflow.core.annotations.GuardComponent.class);
            if (ann == null) return;
            String name = ann.name();
            if (name == null || name.isEmpty()) return;
            guardRegistry.put(name, guardClass);
            guardRegistry.put(lowerCamel(name), guardClass);
        } catch (Throwable ignored) { }
    }

    /** Returns a copy of the step registry. */
    public Map<String, Class<? extends Step>> getStepRegistry() {
        return new HashMap<>(stepRegistry);
    }

    /** Returns a copy of the guard registry. */
    public Map<String, Class<? extends Guard>> getGuardRegistry() {
        return new HashMap<>(guardRegistry);
    }

    /** Resolves a Step class by multiple common keys. */
    public Class<? extends Step> getStepClass(String name) {
        if (name == null) return null;
        Class<? extends Step> c = stepRegistry.get(name);
        if (c == null) c = stepRegistry.get(upperCamel(name));
        return c;
    }

    /** Resolves a Guard class by multiple common keys. */
    public Class<? extends Guard> getGuardClass(String name) {
        if (name == null) return null;
        Class<? extends Guard> c = guardRegistry.get(name);
        if (c == null) c = guardRegistry.get(upperCamel(name));
        return c;
    }

    /** Converts the first character to lower-case (lowerCamel). */
    private String lowerCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Converts the first character to upper-case (UpperCamel). */
    private String upperCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
