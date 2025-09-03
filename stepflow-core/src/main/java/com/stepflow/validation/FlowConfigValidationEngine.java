package com.stepflow.validation;

import com.stepflow.config.FlowConfig;
import com.stepflow.validation.validators.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extensible validation engine for FlowConfig instances.
 * 
 * <p>This engine coordinates multiple validators to provide comprehensive FlowConfig validation.
 * It supports:
 * <ul>
 *   <li><strong>Extensible Validation:</strong> Easy addition of new validators through plugin pattern</li>
 *   <li><strong>Prioritized Execution:</strong> Validators run in priority order</li>
 *   <li><strong>Fail-Fast Support:</strong> Critical validators can stop validation on first failure</li>
 *   <li><strong>Detailed Reporting:</strong> Comprehensive error details with context</li>
 *   <li><strong>Performance Tracking:</strong> Validation timing and statistics</li>
 *   <li><strong>Caching:</strong> Optional result caching for repeated validations</li>
 * </ul>
 * 
 * <p>Usage Examples:
 * 
 * <p><strong>Basic Validation:</strong>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.createDefault();
 * ValidationResult result = engine.validate(flowConfig);
 * 
 * if (!result.isValid()) {
 *     System.err.println(result.getErrorSummary());
 * }
 * </pre>
 * 
 * <p><strong>Custom Validators:</strong>
 * <pre>
 * FlowConfigValidationEngine engine = FlowConfigValidationEngine.builder()
 *     .addValidator(new CycleDetectionValidator())
 *     .addValidator(new EdgeOrderingValidator())
 *     .addValidator(new CustomBusinessRuleValidator())
 *     .enableCaching(true)
 *     .build();
 * </pre>
 * 
 * <p><strong>Validation with Fail-Fast:</strong>
 * <pre>
 * ValidationResult result = engine.validate(flowConfig, true); // Stop on first critical error
 * </pre>
 */
public class FlowConfigValidationEngine {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowConfigValidationEngine.class);
    
    private final List<FlowConfigValidator> validators;
    private final boolean enableCaching;
    private final Map<Integer, ValidationResult> resultCache;
    
    private FlowConfigValidationEngine(Builder builder) {
        this.validators = new ArrayList<>(builder.validators);
        this.validators.sort(Comparator.comparingInt(FlowConfigValidator::getPriority));
        this.enableCaching = builder.enableCaching;
        this.resultCache = enableCaching ? new ConcurrentHashMap<>() : null;
    }
    
    /**
     * Validates a FlowConfig using all registered validators.
     * 
     * @param config the FlowConfig to validate
     * @return comprehensive validation result
     */
    public ValidationResult validate(FlowConfig config) {
        return validate(config, false);
    }
    
    /**
     * Validates a FlowConfig with optional fail-fast behavior.
     * 
     * @param config the FlowConfig to validate
     * @param failFast if true, stop validation on first critical error
     * @return comprehensive validation result
     */
    public ValidationResult validate(FlowConfig config, boolean failFast) {
        if (config == null) {
            return ValidationResult.failure(
                ValidationError.builder(ValidationErrorType.MALFORMED_WORKFLOW)
                    .message("FlowConfig cannot be null")
                    .build()
            );
        }
        
        // Check cache first
        if (enableCaching) {
            int configHash = computeConfigHash(config);
            ValidationResult cached = resultCache.get(configHash);
            if (cached != null) {
                LOGGER.debug("Returning cached validation result for config hash: {}", configHash);
                return cached;
            }
        }
        
        long startTime = System.currentTimeMillis();
        ValidationResult.Builder resultBuilder = ValidationResult.builder();
        
        int validatorsRun = 0;
        int errorsFound = 0;
        int warningsFound = 0;
        String stoppedAtValidator = null;
        
        LOGGER.debug("Starting FlowConfig validation with {} validator(s)", validators.size());
        
        for (FlowConfigValidator validator : validators) {
            try {
                LOGGER.debug("Running validator: {} (priority: {})", 
                           validator.getValidatorName(), validator.getPriority());
                
                long validatorStart = System.currentTimeMillis();
                ValidationResult validatorResult = validator.validate(config);
                long validatorEnd = System.currentTimeMillis();
                
                validatorsRun++;
                errorsFound += validatorResult.getErrors().size();
                warningsFound += validatorResult.getWarnings().size();
                
                resultBuilder.merge(validatorResult);
                
                LOGGER.debug("Validator {} completed in {}ms with {} error(s), {} warning(s)",
                           validator.getValidatorName(), validatorEnd - validatorStart,
                           validatorResult.getErrors().size(), validatorResult.getWarnings().size());
                
                // Check fail-fast conditions
                if (failFast && validator.isFailFast() && validatorResult.hasErrors()) {
                    stoppedAtValidator = validator.getValidatorName();
                    LOGGER.info("Validation stopped early at validator '{}' due to fail-fast", 
                               stoppedAtValidator);
                    break;
                }
                
            } catch (Exception e) {
                LOGGER.error("Validator '{}' threw exception during validation", 
                           validator.getValidatorName(), e);
                
                ValidationError error = ValidationError.builder(ValidationErrorType.GENERIC)
                    .message(String.format("Validator '%s' failed with exception: %s", 
                                          validator.getValidatorName(), e.getMessage()))
                    .cause(e)
                    .detail("validatorName", validator.getValidatorName())
                    .detail("exceptionType", e.getClass().getSimpleName())
                    .build();
                
                resultBuilder.addError(error);
                errorsFound++;
                
                // Continue with other validators even if one fails
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Add validation metadata
        resultBuilder
            .addMetadata("validationStartTime", startTime)
            .addMetadata("validationEndTime", endTime)
            .addMetadata("totalValidationTime", endTime - startTime)
            .addMetadata("validatorsRegistered", validators.size())
            .addMetadata("validatorsRun", validatorsRun)
            .addMetadata("totalErrorsFound", errorsFound)
            .addMetadata("totalWarningsFound", warningsFound)
            .addMetadata("failFastEnabled", failFast);
        
        if (stoppedAtValidator != null) {
            resultBuilder.addMetadata("stoppedAtValidator", stoppedAtValidator);
        }
        
        ValidationResult result = resultBuilder.build();
        
        // Cache the result if caching is enabled
        if (enableCaching) {
            int configHash = computeConfigHash(config);
            resultCache.put(configHash, result);
            LOGGER.debug("Cached validation result for config hash: {}", configHash);
        }
        
        LOGGER.info("FlowConfig validation completed in {}ms: {} error(s), {} warning(s)", 
                   endTime - startTime, errorsFound, warningsFound);
        
        return result;
    }
    
    /**
     * Clears the validation result cache.
     */
    public void clearCache() {
        if (resultCache != null) {
            resultCache.clear();
            LOGGER.debug("Validation result cache cleared");
        }
    }
    
    /**
     * @return information about registered validators
     */
    public List<ValidatorInfo> getValidatorInfo() {
        return validators.stream()
            .map(v -> new ValidatorInfo(v.getValidatorName(), v.getPriority(), 
                                      v.isFailFast(), v.getDescription()))
            .toList();
    }
    
    /**
     * @return cache statistics if caching is enabled
     */
    public Optional<CacheStats> getCacheStats() {
        if (resultCache == null) {
            return Optional.empty();
        }
        
        return Optional.of(new CacheStats(resultCache.size(), 0)); // Hit count would need tracking
    }
    
    private int computeConfigHash(FlowConfig config) {
        // Simple hash based on workflows and steps
        return Objects.hash(
            config.workflows != null ? config.workflows.toString() : "",
            config.steps != null ? config.steps.toString() : "",
            config.settings != null ? config.settings.toString() : "",
            config.defaults != null ? config.defaults.toString() : ""
        );
    }
    
    /**
     * Creates a validation engine with default validators.
     */
    public static FlowConfigValidationEngine createDefault() {
        return builder()
            .addValidator(new CycleDetectionValidator())
            .addValidator(new EdgeOrderingValidator())
            .build();
    }
    
    /**
     * Creates a new builder for constructing validation engines.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Information about a registered validator.
     */
    public static class ValidatorInfo {
        public final String name;
        public final int priority;
        public final boolean failFast;
        public final String description;
        
        ValidatorInfo(String name, int priority, boolean failFast, String description) {
            this.name = name;
            this.priority = priority;
            this.failFast = failFast;
            this.description = description;
        }
        
        @Override
        public String toString() {
            return String.format("ValidatorInfo{name='%s', priority=%d, failFast=%s, description='%s'}", 
                               name, priority, failFast, description);
        }
    }
    
    /**
     * Cache statistics.
     */
    public static class CacheStats {
        public final int size;
        public final long hitCount;
        
        CacheStats(int size, long hitCount) {
            this.size = size;
            this.hitCount = hitCount;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, hitCount=%d}", size, hitCount);
        }
    }
    
    /**
     * Builder for constructing FlowConfigValidationEngine instances.
     */
    public static class Builder {
        private final List<FlowConfigValidator> validators = new ArrayList<>();
        private boolean enableCaching = false;
        
        /**
         * Adds a validator to the engine.
         */
        public Builder addValidator(FlowConfigValidator validator) {
            if (validator != null) {
                this.validators.add(validator);
            }
            return this;
        }
        
        /**
         * Adds multiple validators to the engine.
         */
        public Builder addValidators(FlowConfigValidator... validators) {
            for (FlowConfigValidator validator : validators) {
                addValidator(validator);
            }
            return this;
        }
        
        /**
         * Adds multiple validators from a collection.
         */
        public Builder addValidators(Collection<FlowConfigValidator> validators) {
            validators.forEach(this::addValidator);
            return this;
        }
        
        /**
         * Enables or disables result caching.
         */
        public Builder enableCaching(boolean enableCaching) {
            this.enableCaching = enableCaching;
            return this;
        }
        
        /**
         * Builds the validation engine.
         */
        public FlowConfigValidationEngine build() {
            if (validators.isEmpty()) {
                throw new IllegalStateException("At least one validator must be registered");
            }
            
            return new FlowConfigValidationEngine(this);
        }
    }
}