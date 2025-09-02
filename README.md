# StepFlow - Java Workflow Engine

🚀 **StepFlow** is a lightweight, annotation-driven Java workflow engine that makes it easy to build, configure, and execute complex business workflows using simple YAML configurations and dependency injection.

## 🌟 Key Features

- **📝 Simple YAML Configuration**: Define workflows in readable YAML instead of complex code
- **🏷️ Annotation-Based**: Use `@StepComponent`, `@Inject`, and `@ConfigValue` for clean, declarative code
- **💉 Dependency Injection**: Automatic injection of configuration and runtime data
- **🔄 Smart Retry Logic**: Built-in retry mechanisms with conditional guards
- **🛡️ Guard System**: Conditional workflow routing based on business logic
- **🔧 POJO Support**: Seamless conversion between Maps and Java POJOs
- **📦 Component Discovery**: Automatic step and guard registration via classpath scanning
- **🎯 Type Safety**: Strong typing with automatic type conversion
- **⚡ High Performance**: Lightweight engine with minimal overhead
- **🧪 Testing Friendly**: Easy to test with mock components and SimpleEngine API

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
workflows:
  quick-order:
    - validate
    - check-stock
    - charge-payment
    - send-email
    - done
```

```java
// Run it
SimpleEngine engine = SimpleEngine.create("classpath:examples/simple.yaml", "com.stepflow.examples");
StepResult result = engine.execute("quick-order", orderData);
```

### 2. Structured Approach (Business-friendly)
```yaml
# examples/moderate.yaml
workflows:
  process-order:
    steps:
      - validate-order:
          requires: ["customerId", "productId"]
          min_amount: 10.0
          on_failure: "reject-order"
      
      - check-inventory:
          on_failure: "out-of-stock"
      
      - process-payment:
          retry: 3
          on_failure: "payment-failed"
      
      - send-confirmation
      - complete-order
```

### 3. Programmatic Approach (Code-first)
```java
SimpleEngine engine = SimpleEngine
    .workflow("my-order")
    .step("validate")
        .using("ValidationStep")
        .with("min_amount", 10.0)
        .then("payment")
    .step("payment")
        .using("PaymentStep")
        .with("gateway", "stripe")
        .end()
    .build();
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
@StepComponent(value = "MyStep", aliases = {"my-step", "custom"})
public class MyCustomStep implements Step {
    
    @ConfigValue(value = "timeout", defaultValue = "5000", required = false)
    private Integer timeout;
    
    @Inject(value = "user_id", required = false)
    private String userId;
    
    @Override
    public StepResult execute(ExecutionContext ctx) {
        // Your logic here
        System.out.println("Hello from " + userId);
        return StepResult.success(ctx);
    }
}
```

Then use it in YAML:
```yaml
workflows:
  my-workflow:
    - my-step:
        timeout: 3000
```

## 🎯 Key Features

### ✅ **Simple & Intuitive**
- List of steps = workflow
- Business-friendly configuration
- No technical jargon required

### ✅ **Powerful & Flexible**
- Conditional branching
- Retry mechanisms
- Dynamic configuration
- Dependency injection

### ✅ **Developer Friendly**
- Annotation-based components
- Auto-discovery of steps
- Type-safe configuration
- Comprehensive error messages

### ✅ **Production Ready**
- Robust error handling
- Configurable timeouts
- Comprehensive logging
- Battle-tested patterns

## 📚 Examples

The `QuickStartExample` shows three approaches:

1. **Ultra-simple**: Perfect for learning and prototypes
2. **Programmatic**: Perfect for dynamic workflows
3. **Structured**: Perfect for business processes

Each example runs a complete order processing workflow with validation, inventory checks, payment processing, and notifications.

## 🚦 Edge Guard Failure Strategies

Edges can define how to react when an edge guard evaluates to false:

```yaml
workflows:
  process-order:
    edges:
      - from: s1
        to: s2
        guard: someGuard
        kind: normal
        onFailure:
          strategy: ALTERNATIVE   # STOP | SKIP | ALTERNATIVE | RETRY | CONTINUE
          alternativeTarget: s3   # required when strategy=ALTERNATIVE
          attempts: 3             # used when strategy=RETRY
          delay: 200              # ms, used when strategy=RETRY
```

Semantics:
- STOP: fail workflow immediately
- SKIP: ignore this edge, try next edge
- ALTERNATIVE: redirect to `alternativeTarget`
- RETRY: re-evaluate guard up to `attempts` with `delay` ms; fail if still false
- CONTINUE: ignore guard failure and take the edge anyway

## 🔄 Migration from Complex Config

**Old way (47+ lines):**
```yaml
steps:
  validateOrder:
    type: examples.ValidateOrder
    config:
      required_fields: "customerId,productId"
workflows:
  place:
    root: validateOrder
    edges:
      - from: validateOrder
        to: addOrder
        guard: guards.IsValid
      - from: validateOrder
        kind: terminal
        to: FAILED
        guard: guards.IsInvalid
      # ... 40+ more lines
```

**New way (5 lines):**
```yaml
workflows:
  place:
    - validate
    - add-order
    - done
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
