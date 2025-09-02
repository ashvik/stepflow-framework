package com.stepflow.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DependencyResolver} basic class resolution behavior.
 */
public class DependencyResolverTest {

    @Test
    void resolvesKnownClasses() {
        DependencyResolver r = new DependencyResolver();
        assertNotNull(r.resolveStepClass("com.stepflow.testcomponents.TestStepPlain"));
        assertNotNull(r.resolveGuardClass("com.stepflow.testcomponents.TestGuardPlain"));
    }

    @Test
    void returnsNullForUnknownClasses() {
        DependencyResolver r = new DependencyResolver();
        assertNull(r.resolveStepClass("com.example.DoesNotExist"));
        assertNull(r.resolveGuardClass("com.example.Nope"));
    }

    @Test
    void componentResolutionReturnsPackageAndEmptyLists() {
        DependencyResolver r = new DependencyResolver();
        DependencyResolver.ComponentResolution cr = r.resolveAllComponents("com.example");
        assertEquals("com.example", cr.getPackageName());
        assertTrue(cr.getStepClasses().isEmpty());
        assertTrue(cr.getGuardClasses().isEmpty());
    }
}

