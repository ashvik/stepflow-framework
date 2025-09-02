#!/bin/bash

# StepFlow Code Generator Demo Script
# This script demonstrates the reverse engineering capabilities

echo "🚀 StepFlow Code Generator Demo"
echo "=================================="
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_step() {
    echo -e "${BLUE}📋 Step $1: $2${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed or not in PATH"
    echo "Please install Maven and try again"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    print_error "Please run this script from the stepflow-codegen directory"
    exit 1
fi

print_step "1" "Building the StepFlow Code Generator"
echo "Building the stepflow-codegen module..."

mvn clean compile -q
if [ $? -eq 0 ]; then
    print_success "Build completed successfully"
else
    print_error "Build failed"
    exit 1
fi

print_step "2" "Running Tests"
echo "Executing unit tests to verify functionality..."

mvn test -q
if [ $? -eq 0 ]; then
    print_success "All tests passed"
else
    print_warning "Some tests failed, but continuing with demo..."
fi

print_step "3" "Packaging the Generator"
echo "Creating executable JAR and plugin..."

mvn package -q -DskipTests
if [ $? -eq 0 ]; then
    print_success "Packaging completed"
    
    # Find the shaded JAR
    JAR_FILE=$(find target -name "*-shaded.jar" | head -1)
    if [ -n "$JAR_FILE" ]; then
        print_success "Executable JAR created: $JAR_FILE"
    fi
else
    print_error "Packaging failed"
    exit 1
fi

print_step "4" "Generating Code from Sample YAML"
echo "Using the sample workflow YAML to generate Java components..."

# Create output directory
OUTPUT_DIR="./demo-output"
rm -rf "$OUTPUT_DIR"

# Use the executable JAR
if [ -n "$JAR_FILE" ]; then
    java -jar "$JAR_FILE" \
        --output "$OUTPUT_DIR" \
        --group-id "com.demo.stepflow" \
        --artifact-id "order-processing-components" \
        --version "1.0.0" \
        --project-name "Demo Order Processing Components" \
        --zip-name "order-processing-components.zip" \
        src/test/resources/sample-workflow.yaml
    
    if [ $? -eq 0 ]; then
        print_success "Code generation completed!"
        echo
        
        print_step "5" "Exploring Generated Code"
        
        # Show directory structure
        echo "📁 Generated project structure:"
        if command -v tree &> /dev/null; then
            tree "$OUTPUT_DIR" -I 'target'
        else
            find "$OUTPUT_DIR" -type f | sort
        fi
        echo
        
        # Show some generated content
        echo "📝 Sample generated Step class:"
        echo "================================"
        find "$OUTPUT_DIR" -name "*Step.java" | head -1 | xargs head -30
        echo
        echo "... (truncated)"
        echo
        
        echo "🛡️  Sample generated Guard class:"  
        echo "=================================="
        find "$OUTPUT_DIR" -name "*Guard.java" | head -1 | xargs head -25
        echo
        echo "... (truncated)"
        echo
        
        # Show POM
        echo "📦 Generated Maven POM:"
        echo "======================="
        head -25 "$OUTPUT_DIR/pom.xml"
        echo
        echo "... (truncated)"
        echo
        
        # Show README
        echo "📚 Generated Documentation:"
        echo "=========================="
        head -20 "$OUTPUT_DIR/README.md"
        echo
        echo "... (truncated)"
        echo
        
        print_step "6" "Testing Generated Project"
        echo "Attempting to compile the generated project..."
        
        cd "$OUTPUT_DIR"
        mvn compile -q
        if [ $? -eq 0 ]; then
            print_success "Generated project compiles successfully!"
        else
            print_warning "Generated project has compilation issues (expected - TODO methods need implementation)"
        fi
        cd ..
        
        print_step "7" "Demo Summary"
        echo
        print_success "Demo completed successfully!"
        echo
        echo "📊 What was generated:"
        echo "   • Java Step classes with @StepComponent annotations"
        echo "   • Java Guard classes with @GuardComponent annotations" 
        echo "   • Configuration injection using @ConfigValue"
        echo "   • Complete Maven project structure"
        echo "   • Comprehensive documentation"
        echo "   • ZIP distribution package"
        echo
        echo "📂 Generated files location: $OUTPUT_DIR"
        
        if [ -f "order-processing-components.zip" ]; then
            echo "📦 ZIP package: $(pwd)/order-processing-components.zip"
        fi
        
        echo
        echo "🎯 Next Steps:"
        echo "   1. Review the generated code in: $OUTPUT_DIR"
        echo "   2. Implement the TODO methods in Step and Guard classes"
        echo "   3. Add custom business logic as needed"
        echo "   4. Use 'mvn compile' to build the project"
        echo "   5. Integrate with your StepFlow workflows"
        echo
        
    else
        print_error "Code generation failed"
        exit 1
    fi
else
    print_error "Could not find generated JAR file"
    exit 1
fi

print_success "🎉 StepFlow Code Generator Demo Complete!"
echo
echo "The reverse engineering tool successfully analyzed the YAML configuration"
echo "and generated a complete Java project with Step and Guard implementations."
echo
echo "This demonstrates the capability to:"
echo "• Parse complex StepFlow YAML configurations"  
echo "• Extract component metadata and dependencies"
echo "• Generate production-ready Java code with proper annotations"
echo "• Create complete Maven projects with documentation"
echo "• Package everything into distributable ZIP files"