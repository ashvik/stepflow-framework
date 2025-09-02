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
 * ComponentScanner discovers and registers Step and Guard implementations from given packages.
 *
 * <p>Key properties:
 * - Supports scanning both exploded directories and JAR files on the classpath.
 * - Registers components by multiple keys: simple name, lowerCamel, and FQCN.
 * - Respects optional @StepComponent/@GuardComponent names, also registering lowerCamel variants.
 *
 * <p>Design goals:
 * - Small, focused methods with clear responsibilities and documentation.
 * - Graceful failure: scanning errors are contained; partial discovery does not abort the process.
 */
public class ComponentScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentScanner.class);

    private final Map<String, Class<? extends Step>> stepRegistry = new HashMap<>();
    private final Map<String, Class<? extends Guard>> guardRegistry = new HashMap<>();

    /**
     * Scans one or more package names and registers discovered Step/Guard components.
     *
     * @param packages one or more package names (e.g., "com.example.workflows")
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
