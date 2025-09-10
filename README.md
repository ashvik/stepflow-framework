# StepFlow - Java Workflow Engine

🚀 **StepFlow** is a lightweight, annotation-driven Java workflow engine that makes it easy to build, configure, validate, and execute complex business workflows using simple YAML configurations and dependency injection.

## 🌟 Key Features

- **📝 Simple YAML Configuration**: Define workflows in readable YAML instead of complex code
- **✅ Built-in Validation**: Comprehensive workflow validation with detailed error reporting (cycle detection, edge ordering)
- **🏷️ Annotation-Based**: Use `@StepComponent`, `@GuardComponent`, `@Inject`, and `@ConfigValue` for clean, declarative code
- **💉 Dependency Injection**: Automatic injection of configuration and runtime data into steps and guards
- **🔄 Smart Retry Logic**: Built-in retry mechanisms with conditional guards at step and edge levels
- **🛡️ Dual-Level Guard System**: Step-level execution gates + edge-level routing decisions with configurable failure strategies
- **🔧 POJO Support**: Seamless conversion between Maps and Java POJOs
- **📦 Component Discovery**: Automatic step and guard registration via classpath scanning
- **🎯 Type Safety**: Strong typing with automatic type conversion and hierarchical configuration
- **⚡ High Performance**: Lightweight engine with minimal overhead and guard caching
- **🧪 Testing Friendly**: Easy to test with mock components, isolated guard testing, and SimpleEngine API
- **🚦 Fail-Fast Development**: Detect configuration issues before runtime execution

## 📁 Clean Project Structure

```
stepflow-core-mvp/
├── stepflow-core/              # 🔧 Core framework
│   └── src/main/java/com/stepflow/core/
│       ├── Engine.java         # Workflow execution engine
│       ├── SimpleEngine.java   # Simplified API
│       ├── ComponentScanner.java # Auto-discovery
│       └── annotations/        # @StepComponent, @Inject, etc.
│
├── stepflow-examples/          # 📚 Simple examples
│   ├── src/main/java/com/stepflow/examples/
│   │   ├── QuickStartExample.java    # Main demo
│   │   └── steps/              # Clean step implementations
│   └── src/main/resources/
│       ├── ultra-simple.yaml   # 5-line workflows
│       └── simple-ecommerce.yaml # Business workflows
└── README.md                   # This file
```

## ⚡ Quick Start

### 1. Ultra-Simple Approach (5 lines!)
```yaml
# examples/simple.yaml
steps:
  validate: { type: "validateStep" }
  check-stock: { type: "inventoryStep" }
  charge-payment: { type: "paymentStep" }
  send-email: { type: "notificationStep" }

workflows:
  quick-order:
    root: validate
    edges:
      - from: validate
        to: check-stock
      - from: check-stock
        to: charge-payment  
      - from: charge-payment
        to: send-email
      - from: send-email
        to: SUCCESS
```

```java
// Create engine with validation
SimpleEngine engine = SimpleEngine.create("classpath:examples/simple.yaml", "com.stepflow.examples");

// Validate configuration before execution (recommended)
ValidationResult validation = engine.validateConfiguration();
if (!validation.isValid()) {
    System.err.println("Configuration errors: " + validation.getErrorSummary());
    return;
}

// Execute workflow
StepResult result = engine.execute("quick-order", orderData);
```

### 2. Structured Approach (Business-friendly)
```yaml
# examples/moderate.yaml
steps:
  validate-order:
    type: "validateStep"
    config:
      min_amount: 10.0
  check-inventory: 
    type: "inventoryStep"
  process-payment:
    type: "paymentStep"
    retry:
      maxAttempts: 3
      delay: 1000
  send-confirmation:
    type: "notificationStep"

workflows:
  process-order:
    root: validate-order
    edges:
      - from: validate-order
        to: check-inventory
      - from: check-inventory  
        to: process-payment
        guard: inStockGuard
      - from: check-inventory
        to: FAILURE
        # Fallback when out of stock
      - from: process-payment
        to: send-confirmation
      - from: send-confirmation
        to: SUCCESS
```

### 3. Programmatic Approach (Code-first)
```java
SimpleEngine engine = SimpleEngine
    .workflow("my-order")
    .step("validate")
        .using("validateStep")
        .with("min_amount", 10.0)
        .toIf("ValidOrder", "payment") // returns EdgeBuilder now
            .onFailureSkip()
            .endEdgeToStep()            // continue adding edges from the same step
        .to("manual_review").onFailureStop().endEdge()
    .step("payment")
        .using("paymentStep") 
        .with("gateway", "stripe")
        .toSuccess()
    .build();
    
// Validate programmatically created workflow
ValidationResult result = engine.validateConfiguration();
```

You can also build and validate in one go:
```java
SimpleEngine engine = SimpleEngine
    .workflow("checkout")
    .step("enrich").using("enrichStep")
        .toIf("VipCustomer", "premium")
            .onFailureSkip()
            .endEdgeToStep()
        .to("standard").endEdge()
    .step("standard").using("standardStep").toSuccess()
    .step("premium").using("premiumStep").toSuccess()
    .buildValidated(); // throws on invalid config
```

## ✅ Built-in Validation System

StepFlow includes comprehensive validation to catch configuration issues **before** runtime execution, enabling fail-fast development and robust production deployments.

### 🔍 What Gets Validated

- **🔄 Cycle Detection**: Identifies circular dependencies in workflow graphs
- **📍 Edge Ordering**: Ensures unguarded edges are positioned last and unique per step  
- **🔗 Reference Validation**: Verifies all referenced steps and guards exist
- **🏗️ Structure Validation**: Checks workflow completeness and reachability

### 🚀 Validation Usage Patterns

#### **Pattern 1: Basic Validation**
```java
SimpleEngine engine = SimpleEngine.create("workflow.yaml");
ValidationResult result = engine.validateConfiguration();

if (!result.isValid()) {
    System.err.println("Configuration issues found:");
    System.err.println(result.getErrorSummary());
    // Fix issues before execution
} else {
    // Safe to execute workflows
    engine.execute("workflowName", context);
}
```

#### **Pattern 2: Validation-First Execution (Recommended)**
```java
try {
    SimpleEngine engine = SimpleEngine.create("workflow.yaml");
    engine.validateConfigurationOrThrow(); // Fail fast if invalid
    
    // Configuration is valid - safe to execute
    StepResult result = engine.execute("workflowName", context);
} catch (ValidationException e) {
    System.err.println("Validation failed: " + e.getDetailedMessage());
    // Handle validation errors
}
```

#### **Pattern 3: Single Workflow Validation**
```java
SimpleEngine engine = SimpleEngine.create("workflow.yaml");

// Validate specific workflow during development
ValidationResult result = engine.validateWorkflow("orderProcessing");
if (!result.isValid()) {
    System.err.println("Issues in orderProcessing workflow:");
    for (ValidationError error : result.getErrors()) {
        System.err.println("  • " + error.getMessage());
        System.err.println("    Location: " + error.getLocation());
    }
}
```

#### **Pattern 4: Custom Validation Rules**
```java
// Add custom validators for business rules
SimpleEngine engine = SimpleEngine.builder()
    .withExternalYamls("workflow.yaml")
    .withPackages("com.example")
    .withCustomValidation(validationBuilder -> validationBuilder
        .addValidator(new CycleDetectionValidator())
        .addValidator(new EdgeOrderingValidator()) 
        .addValidator(new BusinessRuleValidator())
        .enableCaching(true))
    .build();

ValidationResult result = engine.validateConfiguration();
```

### ⚠️ Common Validation Errors

#### **Cycle Detection Error:**
```
ERROR: Circular dependency detected in workflow 'orderProcessing': 
       validate → process → audit → validate
Details:
  cyclePath: [validate, process, audit, validate]
  involvedEdges: [validate → process, process → audit [guard: auditRequired], audit → validate]
```

#### **Edge Ordering Violation:**
```
ERROR: Step 'process' has unguarded edge at position 1, but 2 guarded edge(s) come after it
Details:
  unguardedEdge: process → notify [no guard]
  violatingEdges: [process → audit [guard: auditRequired]]
  expectedPosition: last

Fix: Move unguarded edges to the end of the step's edge list
```

#### **Corrected Edge Ordering:**
```yaml
# ❌ Wrong - unguarded edge not last
edges:
  - from: process
    to: notify          # Unguarded edge
  - from: process  
    to: audit
    guard: auditGuard   # Guarded edge after unguarded

# ✅ Correct - unguarded edge last  
edges:
  - from: process
    to: audit
    guard: auditGuard   # Guarded edges first
  - from: process
    to: notify          # Unguarded fallback last
```

## 🛠️ Build & Run

```bash
# Build everything
mvn clean package

# Run the demo
java -jar stepflow-examples/target/stepflow-examples-0.2.0-SNAPSHOT-jar-with-dependencies.jar

# Or run directly
cd stepflow-examples
mvn exec:java -Dexec.mainClass="com.stepflow.examples.simple.SimpleExample"
```

## 📣 Logging

- Core uses a logging abstraction via `slf4j-api` only. No logging implementation is bundled.
- To see logs, add a binding in your application (e.g., `logback-classic`, `slf4j-simple`, or `log4j-slf4j2-impl`).
- Example (Maven, in your app):
  - Add dependency `org.slf4j:slf4j-simple` or `ch.qos.logback:logback-classic` and configure level as desired.
- Engine emits concise `info` on lifecycle, and `debug` for detailed guard/edge decisions and DI operations.

## 📝 Creating Custom Steps & Guards

### 🔧 Custom Steps
Super simple with annotations:

```java
@StepComponent(name = "myCustomStep")
public class MyCustomStep implements Step {
    
    @ConfigValue(value = "timeout", defaultValue = "5000", required = false)
    private Integer timeout;
    
    @Inject(value = "user_id", required = false)
    private String userId;
    
    @Override
    public StepResult execute(ExecutionContext ctx) {
        // Your logic here
        System.out.println("Hello from " + userId + " with timeout: " + timeout + "ms");
        ctx.put("processedBy", "myCustomStep");
        return StepResult.success(ctx);
    }
}
```

### 🛡️ Custom Guards  
Equally simple with rich configuration support:

```java
@GuardComponent(name = "businessRuleGuard")
public class BusinessRuleGuard implements Guard {
    
    @ConfigValue("rule_name")
    private String ruleName;
    
    @ConfigValue(value = "threshold", defaultValue = "100.0")
    private Double threshold;
    
    @Inject("customerTier") 
    private String customerTier;
    
    // Field matching from context
    private Double orderValue;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        // Rich business logic with injected config & context
        if ("VIP".equals(customerTier)) {
            return orderValue >= threshold * 0.8;  // VIP discount
        }
        return orderValue >= threshold;
    }
}
```

### 📋 YAML Configuration

```yaml
steps:
  # Step definition
  my-step:
    type: "myCustomStep"
    guards: ["MyBusinessGuard"]    # Use guard in step
    config:
      timeout: 3000
      
  # Guard definition with config
  MyBusinessGuard:
    type: "businessRuleGuard"
    config:
      rule_name: "order_validation"
      threshold: 250.0

workflows:
  my-workflow:
    root: my-step
    edges:
      - from: my-step
        to: SUCCESS
        guard: "MyBusinessGuard"    # Same guard used in edge
```

### 🎯 Inline Definitions
```yaml
steps:
  my-step: 
    type: "myCustomStep"
    guards: ["businessRuleGuard"]    # Direct class reference
    config: { timeout: 3000 }

workflows:
  my-workflow:
    root: my-step
    edges:
      - from: my-step
        to: SUCCESS
```

## 🎯 Key Features

### ✅ **Simple & Intuitive**
- List of steps = workflow
- Business-friendly configuration
- No technical jargon required
- Built-in validation with clear error messages

### ✅ **Powerful & Flexible**
- Conditional branching with guard system
- Smart retry mechanisms (engine-driven and step-level)
- Dynamic configuration and dependency injection
- Complex edge routing with failure strategies

### ✅ **Developer Friendly**
- Annotation-based components (`@StepComponent`, `@Inject`, `@ConfigValue`)
- Auto-discovery of steps and guards
- Type-safe configuration with POJO support
- Comprehensive validation and error reporting
- Fail-fast development workflow

### ✅ **Production Ready**
- Comprehensive validation (cycle detection, edge ordering)
- Robust error handling with multiple failure strategies
- Configurable timeouts and retry policies
- Performance optimized with caching support
- Battle-tested patterns and extensive logging

## 📚 Examples

The `QuickStartExample` shows three approaches:

1. **Ultra-simple**: Perfect for learning and prototypes
2. **Programmatic**: Perfect for dynamic workflows
3. **Structured**: Perfect for business processes

Each example runs a complete order processing workflow with validation, inventory checks, payment processing, and notifications.

## 🛡️ Guard System - Complete Guide

StepFlow provides a sophisticated **dual-level guard system** for conditional execution and workflow routing. Guards enable business logic to control when steps execute and which paths workflows take.

### 🎯 Two Types of Guards

#### 1. **Step-Level Guards** (Execution Gates)
Control whether a step executes at all. **ALL** guards must pass.

```yaml
steps:
  processPayment:
    type: "paymentStep"
    guards: ["ValidCard", "SufficientFunds", "FraudCheck"]  # ALL must pass
    config:
      gateway: "stripe"
```

**Behavior:** If ANY guard fails → Step **SKIPPED** (returns SUCCESS, continues routing)

#### 2. **Edge-Level Guards** (Routing Decisions)  
Control which transition path is taken. **FIRST** match wins.

```yaml
workflows:
  checkout:
    edges:
      - from: "payment"
        to: "premium_flow"
        guard: "VIPCustomer"           # First priority
        onFailure: { strategy: "SKIP" }
      - from: "payment"
        to: "standard_flow" 
        guard: "ValidPayment"          # Second priority  
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "manual_review" }
      - from: "payment"
        to: "basic_flow"               # Default fallback (no guard)
```

**Behavior:** Edges evaluated in **YAML order** → First passing guard wins → Remaining edges skipped

### 🔧 Guard Configuration & Implementation

#### Configurable Guards with Dependency Injection
```yaml
# Define guards as configurable components
steps:
  OrderValueGuard:
    type: "OrderThresholdGuard"
    config:
      threshold: 100.0
      mode: "strict"
      customer_tiers: ["VIP", "PREMIUM"]
      
  # Use guard in step
  validateOrder:
    type: "orderStep"
    guards: ["OrderValueGuard"]    # References configured guard
```

```java
@GuardComponent(name = "OrderThresholdGuard")
public class OrderValueGuard implements Guard {
    
    // Configuration injection
    @ConfigValue("threshold")
    private Double threshold;
    
    @ConfigValue(value = "mode", defaultValue = "lenient")
    private String mode;
    
    // Context injection  
    @Inject("customerTier")
    private String customerTier;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        Double orderValue = (Double) ctx.get("orderValue");
        
        // Adjust threshold based on customer tier
        double effectiveThreshold = threshold;
        if ("VIP".equals(customerTier)) {
            effectiveThreshold *= 0.8;  // 20% discount for VIP
        }
        
        return orderValue >= effectiveThreshold;
    }
}
```

### 🔄 Retry Guards (Conditional Retry)

Guards can control retry behavior at both levels:

#### Step-Level Retry
```yaml
steps:
  unstableAPI:
    type: "apiCallStep"
    retry:
      maxAttempts: 3
      delay: 1000
      guard: "ShouldRetryGuard"    # Engine evaluates between attempts
```

**Logic:** Step fails → Evaluate retry guard → If passes, wait & retry → If fails, stop immediately

#### Edge-Level Retry
```yaml
workflows:
  paymentFlow:
    edges:
      - from: "validate"
        to: "process"
        guard: "PaymentServiceAvailable" 
        onFailure:
          strategy: "RETRY"
          attempts: 5
          delay: 2000
```

### 📊 Configuration Hierarchy

Guards support **hierarchical configuration** with override precedence:

```yaml
# Global defaults (lowest priority)
settings:
  fraud_enabled: true

defaults:
  guard:                    # All guards inherit
    enabled: true
    timeout_ms: 3000
  OrderValueGuard:          # Specific guard defaults  
    mode: "lenient"

# Step-specific (highest priority)
steps:
  MyOrderGuard:
    type: "OrderValueGuard"
    config:
      threshold: 200.0      # Overrides any defaults
      mode: "strict"        # Overrides defaults.OrderValueGuard.mode
```

**Final config:** `steps.config` → `defaults.{GuardName}` → `defaults.guard` → `settings`

### 🚦 Edge Guard Failure Strategies

Configure sophisticated failure handling for edge guards:

```yaml
workflows:
  smartRouting:
    edges:
      # Optional feature - skip if unavailable
      - from: "process"
        to: "premium_service"
        guard: "PremiumEligible"
        onFailure: { strategy: "SKIP" }
        
      # Critical validation - stop if failed  
      - from: "process"
        to: "payment"
        guard: "SecurityCheck"
        onFailure: { strategy: "STOP" }
        
      # Service degradation - redirect to fallback
      - from: "process"  
        to: "fast_service"
        guard: "ServiceAvailable"
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "slow_service" }
        
      # Transient issue - retry with backoff
      - from: "process"
        to: "external_api"
        guard: "APIHealthy"
        onFailure: { strategy: "RETRY", attempts: 3, delay: 1000 }
        
      # Best effort - continue despite failure
      - from: "process"
        to: "analytics"
        guard: "AnalyticsEnabled"  
        onFailure: { strategy: "CONTINUE" }
        
      # Default path (no guard)
      - from: "process"
        to: "standard_flow"
```

**Strategy Reference:**
- **STOP** (default): Terminate workflow with FAILURE
- **SKIP**: Try next edge from same step  
- **ALTERNATIVE**: Redirect to `alternativeTarget` step
- **RETRY**: Re-evaluate guard with `attempts` & `delay`
- **CONTINUE**: Proceed despite guard failure

### 🎯 Complete Example: E-commerce Checkout

```yaml
settings:
  fraud_threshold: 0.7
  business_hours: { start: "09:00", end: "17:00" }

defaults:
  guard:
    enabled: true
    timeout_ms: 3000

steps:
  # Guard definitions
  FraudGuard:
    type: "FraudDetectionGuard" 
    config:
      threshold: "{{ settings.fraud_threshold }}"
      checks: ["velocity", "geo", "device"]
      
  BusinessHoursGuard:
    type: "OperatingHoursGuard"
    config:
      start_time: "{{ settings.business_hours.start }}"
      end_time: "{{ settings.business_hours.end }}"
      vip_override: true
  
  # Business steps
  validateOrder:
    type: "orderValidationStep"
    guards: ["FraudGuard", "BusinessHoursGuard"]  # ALL must pass
    config:
      strict_mode: true
      
  processPayment:
    type: "paymentStep"
    retry:
      maxAttempts: 3
      delay: 1000
      guard: "PaymentRetryGuard"

workflows:
  ecommerceCheckout:
    root: validateOrder
    edges:
      # Multi-path routing from validation
      - from: validateOrder
        to: expressProcessing
        guard: "VIPCustomer"
        onFailure: { strategy: "SKIP" }
        
      - from: validateOrder  
        to: standardProcessing
        guard: "ValidCustomer"
        onFailure: { strategy: "ALTERNATIVE", alternativeTarget: "manualReview" }
        
      - from: validateOrder
        to: basicProcessing     # Default fallback
        
      # Payment processing with retry
      - from: expressProcessing
        to: processPayment
        guard: "PaymentServiceUp"
        onFailure: { strategy: "RETRY", attempts: 3, delay: 500 }
        
      - from: standardProcessing
        to: processPayment
        
      - from: processPayment
        to: SUCCESS
```

### 📚 Advanced Patterns

#### Pattern 1: Multi-Level Validation
```yaml
steps:
  criticalStep:
    guards: ["L1Security", "L2Business", "L3Compliance"]  # Layered validation
```

#### Pattern 2: Dynamic Guard Selection  
```yaml
steps:
  DynamicGuard:
    type: "EnvironmentAwareGuard"
    config:
      prod_rules: ["strict", "audit"] 
      dev_rules: ["basic"]
      current_env: "{{ settings.environment }}"
```

#### Pattern 3: Guard Composition
```yaml
steps:
  CompositeValidation:
    guards: ["InputGuard", "BusinessGuard", "SecurityGuard"]
    
workflows:
  smartFlow:
    edges:
      - from: CompositeValidation
        to: nextStep
        guard: "AdditionalEdgeGuard"  # Step + Edge guards combined
```

### ❓ Can I Configure Guards Under Steps?

**Yes!** Guards can be defined as step configurations with full config support:

```yaml
steps:
  # Define guard as a configured step
  Guard1:
    type: "myGuardImpl"
    config:
      threshold: 100
      mode: "strict"
      custom_rules: ["vip_bypass", "bulk_discount"]
      
  # Use the configured guard  
  businessStep:
    type: "orderProcessingStep"
    guards: ["Guard1"]           # References the configured guard
    config:
      processor: "main"
      
workflows:
  myFlow:
    edges:
      - from: businessStep
        to: nextStep
        guard: "Guard1"          # Same configured guard used in edge
        onFailure: { strategy: "RETRY", attempts: 2 }
```

**How it works:**
1. **Guard Definition**: `Guard1` step defines guard configuration  
2. **Step Usage**: `businessStep.guards` references configured guard
3. **Edge Usage**: Edge `guard` references same configured guard
4. **Dependency Injection**: Guard gets `threshold`, `mode`, and `custom_rules` injected
5. **Reusability**: Same guard config used in multiple places

**📖 For complete guard system documentation, see [GUARD_SYSTEM.md](stepflow-core/GUARD_SYSTEM.md)**

## 🔄 Migration from Complex Config

**Old way (47+ lines):**
```yaml
steps:
  validateOrder:
    type: examples.ValidateOrder
    config:
      required_fields: "customerId,productId"
  addOrder:
    type: examples.AddOrder
    config:
      timeout: 5000
  processPayment:
    type: examples.ProcessPayment
    
workflows:
  place:
    root: validateOrder
    edges:
      - from: validateOrder
        to: addOrder
        guard: guards.IsValid
      - from: validateOrder
        to: FAILURE
        guard: guards.IsInvalid
      - from: addOrder
        to: processPayment
        guard: guards.InventoryAvailable
      - from: addOrder
        to: FAILURE
      - from: processPayment
        to: SUCCESS
        guard: guards.PaymentSucceeded  
      - from: processPayment
        to: FAILURE
      # ... 30+ more configuration lines
```

**New way (simplified approach):**
```yaml
steps:
  validate: { type: "validateStep" }
  add-order: { type: "addOrderStep" }  
  process-payment: { type: "paymentStep" }

workflows:
  place:
    root: validate
    edges:
      - from: validate
        to: add-order
      - from: add-order
        to: process-payment
      - from: process-payment
        to: SUCCESS
```

**Or even simpler for linear workflows:**
```java
// Programmatic approach - no YAML needed!
SimpleEngine engine = SimpleEngine.workflow("place")
    .step("validate").using("validateStep")
    .then("add-order").using("addOrderStep")
    .then("process-payment").using("paymentStep") 
    .end().build();
```

## 🤝 Contributing

1. Fork the repo
2. Add your feature
3. Keep it simple!
4. Submit a PR

## 📄 License

MIT License - Use it however you want!

---

**StepFlow: Making workflows simple again!** ⚡
