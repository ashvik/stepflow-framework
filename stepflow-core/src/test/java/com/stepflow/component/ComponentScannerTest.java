package com.stepflow.component;

import com.stepflow.execution.Guard;
import com.stepflow.execution.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ComponentScanner} covering discovery and name registration.
 */
public class ComponentScannerTest {

    @Test
    void scansStepsAndGuardsInTestPackage() {
        ComponentScanner scanner = new ComponentScanner();
        scanner.scanPackages("com.stepflow.testcomponents");

        // Annotated step should be resolvable by annotation name and class simple name variants
        Class<? extends Step> stepAnn = scanner.getStepClass("myStep");
        assertNotNull(stepAnn, "Annotated step should be found by its declared name");
        assertEquals("TestStepAnnotated", stepAnn.getSimpleName());

        // Plain step without annotation should be resolvable by simple name (various cases)
        assertNotNull(scanner.getStepClass("TestStepPlain"));
        assertNotNull(scanner.getStepClass("testStepPlain"));

        // Annotated guard
        Class<? extends Guard> guardAnn = scanner.getGuardClass("myGuard");
        assertNotNull(guardAnn, "Annotated guard should be found by its declared name");
        assertEquals("TestGuardAnnotated", guardAnn.getSimpleName());

        // Plain guard
        assertNotNull(scanner.getGuardClass("TestGuardPlain"));
        assertNotNull(scanner.getGuardClass("testGuardPlain"));
    }
}

