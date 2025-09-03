# StepFlow - Java Workflow Engine

🚀 **StepFlow** is a lightweight, annotation-driven Java workflow engine that makes it easy to build, configure, validate, and execute complex business workflows using simple YAML configurations and dependency injection.

## 🌟 Key Features

- **📝 Simple YAML Configuration**: Define workflows in readable YAML instead of complex code
- **✅ Built-in Validation**: Comprehensive workflow validation with detailed error reporting (cycle detection, edge ordering)
- **🏷️ Annotation-Based**: Use `@StepComponent`, `@Inject`, and `@ConfigValue` for clean, declarative code
- **💉 Dependency Injection**: Automatic injection of configuration and runtime data
- **🔄 Smart Retry Logic**: Built-in retry mechanisms with conditional guards
- **🛡️ Guard System**: Conditional workflow routing based on business logic
- **🔧 POJO Support**: Seamless conversion between Maps and Java POJOs
- **📦 Component Discovery**: Automatic step and guard registration via classpath scanning
- **🎯 Type Safety**: Strong typing with automatic type conversion
- **⚡ High Performance**: Lightweight engine with minimal overhead
- **🧪 Testing Friendly**: Easy to test with mock components and SimpleEngine API
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
        .then("payment")
    .step("payment")
        .using("paymentStep") 
        .with("gateway", "stripe")
        .end()
    .build();
    
// Validate programmatically created workflow
ValidationResult result = engine.validateConfiguration();
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

## 📝 Creating Custom Steps

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

Then use it in YAML:
```yaml
steps:
  my-step:
    type: "myCustomStep"
    config:
      timeout: 3000

workflows:
  my-workflow:
    root: my-step
    edges:
      - from: my-step
        to: SUCCESS
```

Or with inline step definition:
```yaml
steps:
  my-step: { type: "myCustomStep", config: { timeout: 3000 } }

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

## 🚦 Edge Guard Failure Strategies

Edges can define how to react when an edge guard evaluates to false:

```yaml
steps:
  s1: { type: "step1Impl" }
  s2: { type: "step2Impl" } 
  s3: { type: "step3Impl" }

workflows:
  process-order:
    root: s1
    edges:
      - from: s1
        to: s2
        guard: someGuard
        onFailure:
          strategy: ALTERNATIVE   # STOP | SKIP | ALTERNATIVE | RETRY | CONTINUE
          alternativeTarget: s3   # required when strategy=ALTERNATIVE
      - from: s1
        to: s3
        guard: retryableGuard
        onFailure:
          strategy: RETRY
          attempts: 3             # used when strategy=RETRY
          delay: 200              # ms, used when strategy=RETRY
      - from: s2
        to: SUCCESS
      - from: s3  
        to: SUCCESS
```

**Strategy Semantics:**
- **STOP**: fail workflow immediately (default)
- **SKIP**: ignore this edge, try next edge from same step
- **ALTERNATIVE**: redirect to `alternativeTarget` instead of original target
- **RETRY**: re-evaluate guard up to `attempts` times with `delay` ms between retries
- **CONTINUE**: ignore guard failure and proceed to target step anyway

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
