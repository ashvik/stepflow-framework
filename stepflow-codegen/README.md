# StepFlow Code Generator

A powerful reverse engineering tool that generates Java Step and Guard classes from StepFlow YAML configurations. This module analyzes your workflow definitions and creates complete, production-ready Java components with proper annotations, configuration injection, and packaging.

## 🎯 Features

- **🔍 Smart YAML Analysis**: Extracts components, configurations, and dependencies from complex YAML files
- **🏗️ Complete Code Generation**: Creates fully annotated Step and Guard classes with proper imports
- **⚙️ Configuration Injection**: Automatic `@ConfigValue` annotations for all configuration parameters
- **🛡️ Guard Integration**: Handles step-level guards and edge-level guard conditions
- **🔄 Retry Logic**: Generates retry configuration from YAML specifications
- **📦 Maven Integration**: Creates complete Maven projects with proper dependencies
- **🗜️ ZIP Distribution**: Packages generated code into distributable archives
- **📚 Documentation**: Generates comprehensive README files with component details
- **🚀 CLI & Plugin**: Available as both command-line tool and Maven plugin

## 🚀 Quick Start

### Command Line Usage

```bash
# Generate from single YAML file
java -jar stepflow-codegen.jar workflow.yaml

# Generate with custom configuration
java -jar stepflow-codegen.jar \
  --output ./my-components \
  --group-id com.company \
  --artifact-id workflow-components \
  --version 2.0.0 \
  workflow1.yaml workflow2.yaml
```

### Maven Plugin Usage

Add to your `pom.xml`:

```xml
<plugin>
    <groupId>com.stepflow</groupId>
    <artifactId>stepflow-codegen</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <configuration>
        <yamlFiles>
            <yamlFile>src/main/resources/workflows/order-processing.yaml</yamlFile>
            <yamlFile>src/main/resources/workflows/user-management.yaml</yamlFile>
        </yamlFiles>
        <groupId>com.company.workflows</groupId>
        <artifactId>generated-components</artifactId>
        <createZip>true</createZip>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## 📋 Input YAML Format

The generator supports the full StepFlow V4 DSL format:

```yaml
settings:
  database:
    url: "jdbc:postgresql://localhost:5432/app"
    timeout: 30000

defaults:
  step:
    timeout_ms: 5000
    retries: 3
  guard:
    caching: true

steps:
  validateOrder:
    type: "validateOrderStep"
    config:
      strictMode: true
      maxItems: 50
    guards:
      - "authGuard"
      - "rateLimitGuard" 
    retry:
      maxAttempts: 5
      delay: 2000
      guard: "shouldRetry"

workflows:
  orderFlow:
    root: "validateOrder"
    edges:
      - from: "validateOrder"
        to: "processPayment"
        guard: "paymentAvailable"
```

## 🏗️ Generated Code Structure

### Generated Step Class

```java
@StepComponent(name = "validateOrder")
public class ValidateOrderStep implements Step {
    
    @ConfigValue(value = "strictMode", defaultValue = "true")
    private Boolean strictMode;
    
    @ConfigValue(value = "maxItems", defaultValue = "50") 
    private Integer maxItems;
    
    @Inject
    private List<Guard> guards;
    
    @Override
    public StepResult execute(ExecutionContext ctx) {
        // Guard validation
        for (Guard guard : guards) {
            if (!guard.evaluate(ctx)) {
                return StepResult.failure("Guard validation failed");
            }
        }
        
        // TODO: Implement business logic
        return StepResult.success(ctx);
    }
}
```

### Generated Guard Class

```java
@GuardComponent(name = "paymentAvailable")
public class PaymentAvailableGuard implements Guard {
    
    @ConfigValue(value = "caching", defaultValue = "true")
    private Boolean caching;
    
    @Override
    public boolean evaluate(ExecutionContext ctx) {
        try {
            // TODO: Implement guard logic
            return true;
        } catch (Exception e) {
            logger.error("Guard evaluation failed", e);
            return false; // Fail-safe
        }
    }
}
```

## 📊 Analysis Capabilities

The generator performs sophisticated analysis to:

- **Extract all component types** from YAML configurations
- **Merge configuration** from defaults and step-specific settings
- **Identify dependencies** between steps and guards
- **Infer data types** for configuration parameters
- **Detect complex logic** patterns requiring custom implementation
- **Map workflow usage** to understand component relationships

## 🛠️ Configuration Options

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `-o, --output` | Output directory | `./generated-stepflow-project` |
| `-g, --group-id` | Maven group ID | `com.stepflow.generated` |
| `-a, --artifact-id` | Maven artifact ID | `stepflow-generated-components` |
| `-v, --version` | Project version | `1.0.0-SNAPSHOT` |
| `-n, --project-name` | Project display name | Auto-generated |
| `-z, --zip-name` | ZIP file name | `stepflow-generated-components.zip` |
| `--no-zip` | Skip ZIP creation | false |

### Maven Plugin Configuration

```xml
<configuration>
    <yamlFiles>
        <yamlFile>path/to/workflow.yaml</yamlFile>
    </yamlFiles>
    <outputDirectory>${project.build.directory}/generated-sources/stepflow</outputDirectory>
    <groupId>com.example</groupId>
    <artifactId>my-components</artifactId>
    <version>1.0.0</version>
    <createZip>true</createZip>
    <addToSources>true</addToSources>
</configuration>
```

## 🧪 Demo

Run the included demo to see the generator in action:

```bash
cd stepflow-codegen
./demo.sh
```

This will:
1. Build the code generator
2. Run unit tests  
3. Generate code from sample YAML
4. Show generated project structure
5. Attempt compilation of generated code

## 📁 Output Structure

Generated projects follow Maven conventions:

```
generated-project/
├── pom.xml                              # Maven configuration
├── README.md                            # Component documentation  
└── src/main/java/
    ├── com/stepflow/generated/steps/    # Generated Step classes
    │   ├── ValidateOrderStep.java
    │   └── ProcessPaymentStep.java  
    └── com/stepflow/generated/guards/   # Generated Guard classes
        ├── AuthGuard.java
        └── PaymentAvailableGuard.java
```

## 🔧 Development

### Building

```bash
mvn clean compile
```

### Testing

```bash
mvn test
```

### Packaging  

```bash
mvn package
```

This creates:
- `target/stepflow-codegen-{version}-shaded.jar` - Executable CLI
- `target/stepflow-codegen-{version}.zip` - Complete distribution

### Installing as Maven Plugin

```bash
mvn install
```

## 🎯 Use Cases

1. **Legacy Migration**: Convert existing YAML workflows to Java implementations
2. **Rapid Prototyping**: Generate boilerplate code for new workflow components  
3. **Code Standards**: Ensure consistent component structure across teams
4. **Documentation**: Generate component inventories from YAML definitions
5. **Testing**: Create mock implementations for workflow testing

## 🤝 Integration

The generated components integrate seamlessly with:

- **StepFlow Engine**: Direct compatibility with all StepFlow features
- **Spring Framework**: Components work with Spring dependency injection
- **Maven Builds**: Generated projects compile immediately  
- **IDEs**: Full IDE support with proper annotations and imports

## 📝 Notes

- Generated classes contain `TODO` comments indicating where to implement business logic
- All configuration parameters are automatically injected using `@ConfigValue`
- Guard classes include fail-safe behavior (return `false` on exceptions)
- Step classes include proper error handling and logging
- The generator preserves YAML structure and relationships in the generated code

## 🔮 Future Enhancements

- **Template Customization**: Support for custom Velocity templates
- **Multiple Formats**: Support for other configuration formats (JSON, XML)
- **IDE Plugins**: Direct integration with popular IDEs  
- **AI-Assisted Logic**: Generate basic business logic from naming patterns
- **Validation Rules**: Advanced validation for generated code quality

---

**Generated components are starting points** - implement the TODO methods with your specific business logic to complete the workflow components.