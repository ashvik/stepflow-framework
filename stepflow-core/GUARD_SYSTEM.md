# StepFlow Guard System - Complete Guide

The StepFlow framework provides a sophisticated guard system that enables conditional execution and workflow routing based on runtime context and business logic. This document provides comprehensive coverage of the guard system architecture, configuration options, and usage patterns.

## Table of Contents

1. [Guard System Overview](#guard-system-overview)
2. [Guard Types and Usage](#guard-types-and-usage)
3. [Guard Implementation](#guard-implementation)
4. [Guard Configuration](#guard-configuration)
5. [Guard Resolution Process](#guard-resolution-process)
6. [Configuration Merging](#configuration-merging)
7. [Dependency Injection](#dependency-injection)
8. [Advanced Patterns](#advanced-patterns)
9. [Error Handling and Debugging](#error-handling-and-debugging)
10. [Best Practices](#best-practices)

## Guard System Overview

The StepFlow guard system provides **two distinct types** of conditional logic:

1. **Step-Level Guards**: Gate whether a step executes at all (ALL must pass)
2. **Edge-Level Guards**: Control which transition path is taken after step execution (FIRST match wins)

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Guard System Architecture                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    ┌─────────────────┐   ┌──────────────┐ │
│  │ Step-Level   │    │ Component       │   │ Edge-Level   │ │
│  │ Guards       │───▶│ Scanner         │◀──│ Guards       │ │
│  │              │    │ & Resolver      │   │              │ │
│  └──────────────┘    └─────────────────┘   └──────────────┘ │
│         │                       │                    │      │
│         ▼                       ▼                    ▼      │
│  ┌──────────────┐    ┌─────────────────┐   ┌──────────────┐ │
│  │ Execution    │    │ Dependency      │   │ Routing      │ │
│  │ Gate         │    │ Injection       │   │ Decision     │ │
│  │ (AND Logic)  │    │ System          │   │ (First Match)│ │
│  └──────────────┘    └─────────────────┘   └──────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Guard Types and Usage

### 1. Step-Level Guards (Execution Gates)

Step-level guards act as **execution gates** - ALL guards must pass for the step to execute.

```yaml
steps:
  processPayment:
    type: "paymentStep"
    guards: ["ValidCard", "SufficientFunds", "FraudCheck"]  # ALL must pass
    config:
      gateway: "stripe"
      timeout_ms: 5000
```

**Behavior:**
- If ANY guard fails: Step is **SKIPPED** (returns SUCCESS with skip message)
- If ALL guards pass: Step **EXECUTES** normally
- Engine continues routing from the same node using available edges

### 2. Edge-Level Guards (Routing Decisions)

Edge-level guards control **which transition path** is taken after step execution.

```yaml
workflows:
  orderFlow:
    edges:
      - from: "checkout"
        to: "premium_flow"
        guard: "VIPCustomer"           # Edge-level guard
        onFailure: { strategy: "SKIP" }
      
      - from: "checkout"
        to: "standard_flow"
        guard: "ValidPayment"          # Another edge-level guard
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "manual_review" }
      
      - from: "checkout"
        to: "basic_flow"
        # No guard = default fallback (MUST be last)
```

**Behavior:**
- Edges evaluated in **YAML declaration order**
- **First matching guard wins** (or first no-guard edge)
- Remaining edges are **skipped**
- Failure handling controlled by `onFailure` strategies

### 3. Retry Guards (Conditional Retry)

Guards can control retry logic at both step and edge levels.

#### Step-Level Retry Guard
```yaml
steps:
  unstableService:
    type: "apiCallStep"
    retry:
      maxAttempts: 3
      delay: 1000
      guard: "ShouldRetry"    # Engine evaluates this between attempts
```

**Behavior:**
1. Step fails → Engine evaluates `ShouldRetry` guard
2. If guard passes → Wait delay, retry step execution
3. If guard fails → Stop retrying, return failure immediately

#### Edge-Level Retry
```yaml
workflows:
  myFlow:
    edges:
      - from: "check"
        to: "proceed"
        guard: "ResourceAvailable"
        onFailure:
          strategy: "RETRY"
          attempts: 5
          delay: 2000
```

**Behavior:**
1. Guard fails → Re-evaluate guard up to `attempts` times
2. If any retry succeeds → Take the transition
3. If all retries fail → Apply fallback behavior (usually STOP)

## Guard Implementation

### Basic Guard Interface

All guards implement the simple `Guard` interface:

```java
// src/main/java/com/stepflow/execution/Guard.java
package com.stepflow.execution;

public interface Guard {
    boolean evaluate(ExecutionContext ctx);
}
```

### Implementation Examples

#### 1. Simple Guard with Annotations
```java
package com.example.guards;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.Guard;
import com.stepflow.execution.ExecutionContext;

@GuardComponent(name = "ValidCard")  // Optional: for auto-discovery
public class ValidCardGuard implements Guard {
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        String cardNumber = (String) ctx.get("cardNumber");
        return cardNumber != null && cardNumber.length() >= 13;
    }
}
```

#### 2. Configurable Guard with Dependency Injection
```java
package com.example.guards;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.core.annotations.ConfigValue;
import com.stepflow.core.annotations.Inject;
import com.stepflow.execution.Guard;
import com.stepflow.execution.ExecutionContext;

@GuardComponent(name = "OrderValueThreshold")
public class OrderValueGuard implements Guard {
    
    // Configuration injection
    @ConfigValue("threshold")
    private Double threshold;
    
    @ConfigValue(value = "mode", defaultValue = "strict")
    private String mode;
    
    // Context value injection
    @Inject("customerTier")
    private String customerTier;
    
    // Direct field matching (no annotation needed)
    private boolean strictMode;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        Double orderValue = (Double) ctx.get("orderValue");
        
        if (orderValue == null) {
            return false;
        }
        
        // Apply tier-based threshold adjustment
        double effectiveThreshold = threshold;
        if ("VIP".equals(customerTier)) {
            effectiveThreshold *= 0.8;  // 20% lower threshold for VIP
        }
        
        if ("lenient".equals(mode)) {
            effectiveThreshold *= 0.9;  // 10% lower for lenient mode
        }
        
        return orderValue >= effectiveThreshold;
    }
}
```

#### 3. Complex Business Logic Guard
```java
package com.example.guards;

import com.stepflow.core.annotations.GuardComponent;
import com.stepflow.execution.Guard;
import com.stepflow.execution.ExecutionContext;
import java.time.LocalTime;
import java.util.List;

@GuardComponent
public class BusinessHoursGuard implements Guard {
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        LocalTime now = LocalTime.now();
        LocalTime businessStart = LocalTime.of(9, 0);
        LocalTime businessEnd = LocalTime.of(17, 0);
        
        boolean isBusinessHours = now.isAfter(businessStart) && now.isBefore(businessEnd);
        
        // Allow VIP customers outside business hours
        String customerTier = (String) ctx.get("customerTier");
        boolean isVIP = "VIP".equals(customerTier) || "PREMIUM".equals(customerTier);
        
        return isBusinessHours || isVIP;
    }
}
```

## Guard Configuration

### 1. Direct Guard Definition

Define guards as step definitions with their own configuration:

```yaml
steps:
  # Regular step
  processOrder:
    type: "orderStep"
    guards: ["OrderValueGuard", "FraudCheckGuard"]
    config:
      processor: "main"
  
  # Guard definitions
  OrderValueGuard:
    type: "OrderValueThreshold"
    config:
      threshold: 100.0
      mode: "strict"
      
  FraudCheckGuard:
    type: "FraudAnalysisGuard"
    config:
      risk_threshold: 0.7
      enabled_checks: ["velocity", "geolocation", "device"]
```

### 2. Configuration Hierarchy

The framework supports hierarchical configuration with override precedence:

```yaml
# Global settings (lowest priority)
settings:
  fraud_enabled: true
  default_threshold: 50.0

# Category-wide defaults
defaults:
  guard:                    # All guards
    enabled: true
    timeout_ms: 3000
  
  OrderValueGuard:          # Specific guard type
    mode: "lenient"
    fallback_threshold: 25.0

# Step-specific config (highest priority)
steps:
  MyOrderGuard:
    type: "OrderValueGuard"
    config:
      threshold: 200.0      # Overrides default_threshold and fallback_threshold
      mode: "strict"        # Overrides defaults.OrderValueGuard.mode
      # enabled and timeout_ms inherited from defaults.guard
```

**Final effective configuration for MyOrderGuard:**
```json
{
  "enabled": true,           // from defaults.guard
  "timeout_ms": 3000,       // from defaults.guard
  "fallback_threshold": 25.0, // from defaults.OrderValueGuard
  "threshold": 200.0,       // from steps.MyOrderGuard.config (highest priority)
  "mode": "strict"          // from steps.MyOrderGuard.config (overrides default)
}
```

### 3. Using Guards in Different Contexts

#### Step-Level Usage
```yaml
steps:
  criticalStep:
    type: "businessStep"
    guards: ["SecurityGuard", "BusinessHoursGuard"]  # ALL must pass
    config:
      operation: "transfer"
```

#### Edge-Level Usage
```yaml
workflows:
  paymentFlow:
    edges:
      - from: "validate"
        to: "premium_processing"
        guard: "VIPCustomer"
        onFailure: { strategy: "SKIP" }
      
      - from: "validate"
        to: "standard_processing"
        guard: "ValidAccount"
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "manual_review" }
      
      - from: "validate"
        to: "basic_processing"
        # Default fallback - no guard
```

#### Retry Guard Usage
```yaml
steps:
  apiCall:
    type: "externalApiStep"
    retry:
      maxAttempts: 5
      delay: 1000
      guard: "ShouldRetryGuard"  # Evaluates error conditions
```

## Guard Resolution Process

The engine uses a **two-phase resolution process** for guards:

### Phase 1: Step Definition Lookup
```java
// From Engine.java:1204-1213
FlowConfig.StepDef guardDef = config.steps.get(guardName);
if (guardDef != null) {
    guardClass = componentScanner.getGuardClass(guardDef.type);
    effectiveConfig = buildEffectiveConfig(guardName, guardDef.config, true);
}
```

### Phase 2: Direct Class Resolution (Fallback)
```java
// From Engine.java:1214-1222
else {
    // Fallback: resolve guard directly by class-like name
    guardClass = componentScanner.getGuardClass(guardName);
    effectiveConfig = buildEffectiveConfig(guardName, null, true);
}
```

### Resolution Examples

#### 1. Named Guard (Phase 1)
```yaml
# Guard reference
guards: ["MyBusinessGuard"]

# Guard definition
steps:
  MyBusinessGuard:
    type: "BusinessLogicGuard"
    config:
      rule_set: "premium"
```

**Resolution:** `MyBusinessGuard` → `steps.MyBusinessGuard` → `BusinessLogicGuard` class

#### 2. Direct Class Reference (Phase 2)
```yaml
# Direct class reference (no step definition)
guards: ["VIPCustomer"]
```

**Resolution:** `VIPCustomer` → ComponentScanner finds `VIPCustomerGuard` class or annotated component

#### 3. Fully Qualified Class Name (Phase 2)
```yaml
guards: ["com.example.guards.ComplexBusinessGuard"]
```

**Resolution:** Direct class loading by fully qualified name

## Configuration Merging

The framework implements a **hierarchical configuration merging system**:

### Merging Order (Low to High Priority)
1. `defaults.guard.*` - Global guard defaults
2. `defaults.{guardName}.*` - Guard-specific defaults  
3. `steps.{guardName}.config.*` - Explicit guard configuration

### Example Configuration Merge
```yaml
settings:
  global_timeout: 5000

defaults:
  guard:
    enabled: true
    retry_attempts: 2
    
  FraudGuard:
    sensitivity: "medium"
    check_velocity: true

steps:
  MyFraudGuard:
    type: "FraudGuard"
    config:
      sensitivity: "high"      # Overrides defaults.FraudGuard.sensitivity
      custom_rule: "vip_bypass"
```

**Final Configuration:**
```json
{
  "enabled": true,           // defaults.guard
  "retry_attempts": 2,       // defaults.guard
  "check_velocity": true,    // defaults.FraudGuard
  "sensitivity": "high",     // steps.MyFraudGuard.config (overrides default "medium")
  "custom_rule": "vip_bypass" // steps.MyFraudGuard.config
}
```

## Dependency Injection

The StepFlow framework provides comprehensive dependency injection for guards:

### Injection Sources (in order of precedence)

1. **@Inject Annotation**: ExecutionContext first, then step config
2. **Field Name Matching**: Context values by field name
3. **@ConfigValue Annotation**: Step config first, then global settings path

### Injection Examples

```java
@GuardComponent
public class ComprehensiveGuard implements Guard {
    
    // Direct context injection
    @Inject("userId")
    private String userId;
    
    // Config value with path navigation
    @ConfigValue("settings.fraud.enabled")  
    private Boolean fraudEnabled;
    
    // Config value with default
    @ConfigValue(value = "threshold", defaultValue = "100.0")
    private Double threshold;
    
    // Field name matching from context
    private String customerTier;  // Matches ctx.get("customerTier")
    
    // Field name matching from config
    private boolean strictMode;   // Matches config.get("strictMode")
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        // All fields are injected before this method is called
        return fraudEnabled && userId != null && 
               (Double) ctx.get("orderValue") >= threshold;
    }
}
```

### Configuration Context
```yaml
settings:
  fraud:
    enabled: true

steps:
  MyComprehensiveGuard:
    type: "ComprehensiveGuard"
    config:
      threshold: 250.0
      strictMode: true
```

### Execution Context
```java
ExecutionContext ctx = new ExecutionContext();
ctx.put("userId", "user123");
ctx.put("customerTier", "VIP");
ctx.put("orderValue", 300.0);
```

**Injected Values:**
- `userId`: "user123" (from context via @Inject)
- `fraudEnabled`: true (from settings.fraud.enabled via @ConfigValue)
- `threshold`: 250.0 (from config via @ConfigValue)
- `customerTier`: "VIP" (from context via field matching)
- `strictMode`: true (from config via field matching)

## Advanced Patterns

### 1. Multi-Level Guard Composition

```yaml
steps:
  # Composite validation step
  validateOrder:
    type: "validationStep"
    guards: ["PreValidation", "BusinessRules", "PostValidation"]
    
  # Individual guard components
  PreValidation:
    type: "InputValidationGuard"
    config:
      required_fields: ["customerId", "productId", "quantity"]
      
  BusinessRules:
    type: "BusinessLogicGuard"
    config:
      rule_set: "order_validation"
      strict_mode: true
      
  PostValidation:
    type: "SecurityGuard"
    config:
      check_fraud: true
      check_limits: true

workflows:
  orderProcessing:
    edges:
      - from: "validateOrder"
        to: "process"
        guard: "PaymentReadyGuard"  # Additional edge guard
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "manual_review" }
```

### 2. Dynamic Guard Configuration

```yaml
settings:
  environment: "production"
  feature_flags:
    advanced_fraud_check: true
    geo_validation: false

defaults:
  guard:
    environment: "{{ settings.environment }}"
    
steps:
  DynamicGuard:
    type: "EnvironmentAwareGuard"
    config:
      prod_threshold: 1000.0
      dev_threshold: 100.0
      use_advanced_fraud: "{{ settings.feature_flags.advanced_fraud_check }}"
```

### 3. Conditional Guard Chains

```yaml
workflows:
  smartRouting:
    edges:
      # VIP path with multiple conditions
      - from: "entry"
        to: "vip_fast_track" 
        guard: "VIPStatus"
        onFailure: { strategy: "SKIP" }
        
      # High-value order path
      - from: "entry"
        to: "premium_processing"
        guard: "HighValueOrder"
        onFailure: { strategy: "SKIP" }
        
      # Fraud check required path
      - from: "entry"
        to: "fraud_analysis"
        guard: "FraudRiskGuard"
        onFailure: { strategy: "SKIP" }
        
      # Default standard processing
      - from: "entry"
        to: "standard_processing"
        # No guard - fallback
```

### 4. Guard-Based Retry Logic

```yaml
steps:
  resilientApiCall:
    type: "externalApiStep"
    retry:
      maxAttempts: 5
      delay: 1000
      guard: "IntelligentRetryGuard"
      
  IntelligentRetryGuard:
    type: "SmartRetryGuard"
    config:
      retry_on_errors: ["timeout", "connection_reset", "rate_limit"]
      backoff_strategy: "exponential"
      max_delay: 30000
```

```java
@GuardComponent
public class SmartRetryGuard implements Guard {
    
    @ConfigValue("retry_on_errors")
    private List<String> retryableErrors;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        String lastError = (String) ctx.get("lastError");
        Integer attemptCount = (Integer) ctx.get("attemptCount");
        
        // Don't retry after too many attempts
        if (attemptCount != null && attemptCount > 3) {
            return false;
        }
        
        // Only retry on specific error types
        return retryableErrors.contains(lastError);
    }
}
```

## Error Handling and Debugging

### Guard Evaluation Errors

When guards fail to evaluate due to exceptions, the framework handles them gracefully:

```java
// From Engine.java:1227-1230
try {
    Guard guard = guardClass.getDeclaredConstructor().newInstance();
    dependencyInjector.injectDependencies(guard, context, effectiveConfig, config.settings);
    return guard.evaluate(context);
} catch (Exception e) {
    LOGGER.error("Guard evaluation failed for '{}': {}", guardName, e.toString());
    return false;  // Treat exceptions as guard failure
}
```

### Common Error Scenarios

#### 1. Guard Implementation Not Found
```
ERROR: Guard implementation not found for type 'MyCustomGuard' (guard=orderGuard)
```

**Solution:** Ensure guard class is in scanned packages and implements Guard interface.

#### 2. Configuration Injection Failure
```
ERROR: Guard evaluation failed for 'MyGuard': No such field 'invalidField'
```

**Solution:** Check field names match configuration keys or use @ConfigValue annotation.

#### 3. Runtime Evaluation Exception
```
ERROR: Guard evaluation failed for 'FraudGuard': NullPointerException
```

**Solution:** Add null checks in guard logic and validate required context values.

### Debugging Guard Behavior

Enable debug logging to trace guard evaluation:

```properties
# logback.xml or application.properties
com.stepflow.engine.Engine=DEBUG
```

**Debug Output:**
```
DEBUG: Guard 'VIPCustomer' returned false
DEBUG: Edge guard failed; onFailure=SKIP: checkout -> premium_flow
DEBUG: Guard 'ValidPayment' passed: checkout -> standard_flow
DEBUG: Transition: checkout -> standard_flow
```

## Best Practices

### 1. Guard Design Principles

#### Keep Guards Simple and Focused
```java
// ✅ Good - Single responsibility
@GuardComponent
public class MinimumOrderValueGuard implements Guard {
    @ConfigValue("min_value")
    private Double minValue;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        Double orderValue = (Double) ctx.get("orderValue");
        return orderValue != null && orderValue >= minValue;
    }
}

// ❌ Bad - Multiple responsibilities
@GuardComponent  
public class ComplexBusinessGuard implements Guard {
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        // Validates order, checks inventory, verifies payment, 
        // checks business hours, validates customer tier...
        // Too many responsibilities!
        return complexBusinessLogic(ctx);
    }
}
```

#### Make Guards Stateless
```java
// ✅ Good - Stateless, uses only injected config and context
@GuardComponent
public class StatelessGuard implements Guard {
    @ConfigValue("threshold")
    private Double threshold;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        return ((Double) ctx.get("value")) >= threshold;
    }
}

// ❌ Bad - Maintains state between evaluations
@GuardComponent
public class StatefulGuard implements Guard {
    private int evaluationCount = 0;  // State maintained across calls
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        evaluationCount++;  // Side effect
        return evaluationCount < 3;
    }
}
```

### 2. Configuration Best Practices

#### Use Meaningful Names
```yaml
# ✅ Good - Clear, descriptive names
steps:
  PaymentValidationGuard:
    type: "CreditCardValidationGuard"
    config:
      check_expiry: true
      validate_cvv: true

# ❌ Bad - Unclear names
steps:
  Guard1:
    type: "G1"
    config:
      x: true
      y: true
```

#### Organize Guards Logically
```yaml
# ✅ Good - Logical grouping
steps:
  # Security Guards
  FraudDetectionGuard:
    type: "FraudAnalysisGuard"
    
  SecurityCheckGuard:
    type: "SecurityValidationGuard"
    
  # Business Logic Guards  
  BusinessHoursGuard:
    type: "OperatingHoursGuard"
    
  CustomerTierGuard:
    type: "TierValidationGuard"
    
  # Payment Guards
  PaymentMethodGuard:
    type: "PaymentValidationGuard"
    
  SufficientFundsGuard:
    type: "BalanceCheckGuard"
```

### 3. Edge Guard Strategy Guidelines

#### Use Appropriate Failure Strategies
```yaml
workflows:
  orderProcessing:
    edges:
      # Optional features - SKIP if not available
      - from: "process"
        to: "loyalty_points"
        guard: "LoyaltyEligible"
        onFailure: { strategy: "SKIP" }
        
      # Critical validations - STOP if failed
      - from: "process"
        to: "payment"
        guard: "PaymentValid"
        onFailure: { strategy: "STOP" }
        
      # Degraded service - ALTERNATIVE path
      - from: "process"
        to: "express_shipping"
        guard: "ExpressAvailable"
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "standard_shipping" }
        
      # Transient failures - RETRY
      - from: "process"
        to: "external_service"
        guard: "ServiceAvailable"
        onFailure: { strategy: "RETRY", attempts: 3, delay: 1000 }
```

### 4. Performance Considerations

#### Cache Expensive Operations
```java
@GuardComponent
public class CachedGuard implements Guard {
    
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        String key = (String) ctx.get("cacheKey");
        
        return CACHE.computeIfAbsent(key, k -> {
            // Expensive operation only computed once
            return performExpensiveCheck(ctx);
        });
    }
    
    private boolean performExpensiveCheck(ExecutionContext ctx) {
        // Complex logic here
        return true;
    }
}
```

#### Optimize Guard Ordering
```yaml
# ✅ Good - Fast guards first, expensive guards last
steps:
  validateOrder:
    guards: [
      "SimpleValidationGuard",    # Fast field checks
      "BusinessRulesGuard",       # Medium complexity
      "ExternalApiGuard"          # Expensive network call
    ]
```

### 5. Testing Guards

#### Unit Test Guards Independently
```java
@Test
void testOrderValueGuard() {
    // Arrange
    OrderValueGuard guard = new OrderValueGuard();
    guard.threshold = 100.0;  // Set via reflection or constructor
    
    ExecutionContext ctx = new ExecutionContext();
    ctx.put("orderValue", 150.0);
    
    // Act
    boolean result = guard.evaluate(ctx);
    
    // Assert
    assertTrue(result);
}

@Test
void testGuardWithInjection() {
    // Arrange
    OrderValueGuard guard = new OrderValueGuard();
    Map<String, Object> config = Map.of("threshold", 200.0);
    
    DependencyInjector injector = new DependencyInjector();
    ExecutionContext ctx = new ExecutionContext();
    ctx.put("orderValue", 150.0);
    
    // Act
    injector.injectDependencies(guard, ctx, config, Map.of());
    boolean result = guard.evaluate(ctx);
    
    // Assert
    assertFalse(result);  // 150 < 200
}
```

#### Integration Test Guard Workflows
```java
@Test
void testGuardInWorkflow() {
    // Arrange
    SimpleEngine engine = SimpleEngine.create("classpath:test-workflow.yaml", "com.example.guards");
    ExecutionContext ctx = new ExecutionContext();
    ctx.put("orderValue", 50.0);
    
    // Act
    StepResult result = engine.execute("orderWorkflow", ctx);
    
    // Assert
    assertEquals(StepResult.Status.SUCCESS, result.status);
    assertEquals("basic_processing", ctx.get("processingType"));  // Low value went to basic path
}
```

This comprehensive guide covers all aspects of the StepFlow guard system. The guard system provides powerful conditional execution and routing capabilities while maintaining simplicity and testability.