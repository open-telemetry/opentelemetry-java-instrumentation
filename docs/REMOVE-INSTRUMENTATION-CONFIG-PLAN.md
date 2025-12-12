# Plan: Remove InstrumentationConfig Interface

## Overview

This document outlines the plan to remove the `InstrumentationConfig` interface and consolidate on:
1. **Primary**: `DeclarativeConfigUtil` - for accessing configuration from `OpenTelemetry` instance
2. **Secondary**: `ConfigProperties` - for SDK autoconfigure compatibility (SPI interfaces)

## Current State

### InstrumentationConfig Interface
**Location**: `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/InstrumentationConfig.java`

Provides methods:
- `getString(name)`, `getString(name, default)`
- `getBoolean(name, default)`
- `getInt(name, default)`, `getLong(name, default)`, `getDouble(name, default)`
- `getDuration(name, default)`
- `getList(name)`, `getList(name, default)`
- `getMap(name, default)`
- `isDeclarative()` - whether declarative config is in use
- `getDeclarativeConfig(node)` - get declarative config for a node
- `getConfigProvider()` - get the ConfigProvider

### Implementations
1. **ConfigPropertiesBridge** (`spring-boot-autoconfigure/.../properties/ConfigPropertiesBridge.java`)
   - Wraps `ConfigProperties` and optionally `ConfigProvider`
   - Used as a Spring bean in autoconfiguration

### Key Usage Patterns

1. **Spring Boot Autoconfiguration**: `InstrumentationConfig` is a Spring bean injected into instrumentation configurations
2. **Helper utilities**: `InstrumentationConfigUtil` wraps config access for HTTP client/server builders
3. **Enduser config**: `EnduserConfig` reads properties via `InstrumentationConfig`
4. **Deprecated property warnings**: `DeprecatedConfigProperties` reads via `InstrumentationConfig`

---

## Migration Plan

### Phase 1: Migrate instrumentation-api-incubator Internal Uses

#### 1.1 EnduserConfig
**File**: `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/EnduserConfig.java`

**Current**:
```java
EnduserConfig(InstrumentationConfig instrumentationConfig) {
  this.idEnabled = instrumentationConfig.getBoolean("otel.instrumentation.common.enduser.id.enabled", false);
  // ...
}
```

**Target**: Change constructor to accept `OpenTelemetry` and use `DeclarativeConfigUtil`:
```java
EnduserConfig(OpenTelemetry openTelemetry) {
  this.idEnabled = DeclarativeConfigUtil.getBoolean(openTelemetry, "common", "enduser", "id_enabled")
      .orElse(false);
  // ...
}
```

**Note**: Need to define the declarative config schema mapping for enduser settings.

---

### Phase 2: Migrate Spring Boot Autoconfigure

#### 2.1 Remove InstrumentationConfig Bean
**Files**:
- `OpenTelemetryAutoConfiguration.java`

**Current**: Creates `InstrumentationConfig` beans in multiple configurations:
```java
@Bean
public InstrumentationConfig instrumentationConfig(ConfigProperties properties, ConfigProvider configProvider) {
  return new ConfigPropertiesBridge(properties, configProvider);
}
```

**Target**: Remove `InstrumentationConfig` bean creation. Consumers should use `OpenTelemetry` directly.

#### 2.2 InstrumentationConfigUtil
**File**: `spring-boot-autoconfigure/.../properties/InstrumentationConfigUtil.java`

**Current**:
```java
public static <T, REQUEST, RESPONSE> T configureClientBuilder(
    OpenTelemetry openTelemetry,
    InstrumentationConfig config,
    T builder,
    Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
  getBuilder.apply(builder).configure(openTelemetry);
  return builder;
}

public static boolean isStatementSanitizationEnabled(InstrumentationConfig config, String key) {
  return config.getBoolean(key, config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true));
}
```

**Target**: Remove `InstrumentationConfig` parameter (already not used in configure methods):
```java
public static <T, REQUEST, RESPONSE> T configureClientBuilder(
    OpenTelemetry openTelemetry,
    T builder,
    Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
  getBuilder.apply(builder).configure(openTelemetry);
  return builder;
}

public static boolean isStatementSanitizationEnabled(OpenTelemetry openTelemetry, String key) {
  // Use DeclarativeConfigUtil for key, fall back to common setting
  return DeclarativeConfigUtil.getBoolean(openTelemetry, /* path for key */)
      .or(() -> DeclarativeConfigUtil.getBoolean(openTelemetry, "common", "db_statement_sanitizer", "enabled"))
      .orElse(true);
}
```

#### 2.3 Instrumentation Auto-Configurations

Update all auto-configuration classes that inject `InstrumentationConfig`:

| File | Change |
|------|--------|
| `SpringWebfluxInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `SpringWebMvc5InstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `SpringWebMvc6InstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `RestClientInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `RestClientBeanPostProcessor.java` | Remove `InstrumentationConfig` field |
| `WebClientBeanPostProcessor.java` | Remove `InstrumentationConfig` parameter |
| `JdbcInstrumentationSpringBoot4AutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `MongoClientInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `MongoClientInstrumentationSpringBoot4AutoConfiguration.java` | Remove `InstrumentationConfig` parameter |
| `DataSourcePostProcessor.java` | Remove `InstrumentationConfig` field |
| `R2dbcInstrumentingPostProcessor.java` | Remove `InstrumentationConfig` field |
| `SchedulingInstrumentationAspectTest.java` | Update test to not use `InstrumentationConfig` |

---

### Phase 3: Migrate Javaagent Extension API

#### 3.1 DeprecatedConfigProperties
**File**: `javaagent-extension-api/src/main/java/io/opentelemetry/javaagent/bootstrap/internal/DeprecatedConfigProperties.java`

**Current**:
```java
public static boolean getBoolean(
    InstrumentationConfig config, String deprecatedPropertyName, String newPropertyName, boolean defaultValue) {
  warnIfUsed(config, deprecatedPropertyName, newPropertyName);
  boolean value = config.getBoolean(deprecatedPropertyName, defaultValue);
  return config.getBoolean(newPropertyName, value);
}
```

**Target**: Change to use `ConfigProperties`:
```java
public static boolean getBoolean(
    ConfigProperties config, String deprecatedPropertyName, String newPropertyName, boolean defaultValue) {
  warnIfUsed(config, deprecatedPropertyName, newPropertyName);
  boolean value = config.getBoolean(deprecatedPropertyName, defaultValue);
  return config.getBoolean(newPropertyName, value);
}
```

---

### Phase 4: Update Tests

Update all test files that reference `InstrumentationConfig`:

| File | Change |
|------|--------|
| `AbstractJdbcInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `AbstractR2DbcInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `AbstractSpringWebInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `AbstractKafkaInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `DeclarativeConfigTest.java` | Update assertions |
| `SpringWebMvcInstrumentation5AutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `SpringWebMvcInstrumentation6AutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `RestClientInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `SchedulingInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `SpringWebfluxInstrumentationAutoConfigurationTest.java` | Remove `InstrumentationConfig.class` from context |
| `WebClientBeanPostProcessorTest.java` | Update to not use `InstrumentationConfig` |

---

### Phase 5: Delete InstrumentationConfig

#### 5.1 Delete Files
- `instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/InstrumentationConfig.java`
- `spring-boot-autoconfigure/.../properties/ConfigPropertiesBridge.java`

---

## Decision Points / Open Questions

### 1. Declarative Config Schema for Properties

Currently, `InstrumentationConfig` uses flat property names like `otel.instrumentation.common.enduser.id.enabled`. 

For `DeclarativeConfigUtil`, we need to define the hierarchical path in declarative config YAML:
```yaml
instrumentation:
  java:
    common:
      enduser:
        id_enabled: true
```

**Decision needed**: Confirm the schema structure for all properties currently accessed via `InstrumentationConfig`.

### 2. ConfigProperties for SPI Interfaces

Several SPI interfaces in `javaagent-extension-api` use `ConfigProperties`:
- `InstrumentationModule.defaultEnabled(ConfigProperties)`
- `IgnoredTypesConfigurer.configure(..., ConfigProperties)`
- `AgentExtension.extend(..., ConfigProperties)`
- `BootstrapPackagesConfigurer.configure(..., ConfigProperties)`

These are **external APIs** and cannot be easily changed. They will continue to use `ConfigProperties`.

**Decision**: Leave SPI interfaces using `ConfigProperties`. Only migrate internal code to `DeclarativeConfigUtil`.

### 3. Spring Boot ConfigProperties Bean

Spring Boot autoconfigure exposes a `ConfigProperties` bean. This should remain for:
- Backward compatibility with user customizations
- SPI interfaces that need `ConfigProperties`

---

## Migration Order

1. **Phase 1**: `EnduserConfig` (internal, limited blast radius)
2. **Phase 2**: Spring Boot auto-configurations (bulk of changes)
3. **Phase 3**: Javaagent extension API (`DeprecatedConfigProperties`)
4. **Phase 4**: Tests
5. **Phase 5**: Delete `InstrumentationConfig` interface and `ConfigPropertiesBridge`

---

## Files Summary

### Files to Modify

| Module | File | Change Type |
|--------|------|-------------|
| instrumentation-api-incubator | `EnduserConfig.java` | Change constructor signature |
| spring-boot-autoconfigure | `OpenTelemetryAutoConfiguration.java` | Remove `InstrumentationConfig` beans |
| spring-boot-autoconfigure | `InstrumentationConfigUtil.java` | Remove `InstrumentationConfig` param |
| spring-boot-autoconfigure | `SpringWebfluxInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure | `WebClientBeanPostProcessor.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure | `SpringWebMvc5InstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure (Spring3) | `SpringWebMvc6InstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure (Spring3) | `RestClientInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure (Spring3) | `RestClientBeanPostProcessor.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure (Spring4) | `JdbcInstrumentationSpringBoot4AutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure | `MongoClientInstrumentationAutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure (Spring4) | `MongoClientInstrumentationSpringBoot4AutoConfiguration.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure | `DataSourcePostProcessor.java` | Remove `InstrumentationConfig` |
| spring-boot-autoconfigure | `R2dbcInstrumentingPostProcessor.java` | Remove `InstrumentationConfig` |
| javaagent-extension-api | `DeprecatedConfigProperties.java` | Change to `ConfigProperties` |

### Files to Delete

| Module | File |
|--------|------|
| instrumentation-api-incubator | `InstrumentationConfig.java` |
| spring-boot-autoconfigure | `ConfigPropertiesBridge.java` |

### Test Files to Update

| Module | File |
|--------|------|
| spring-boot-autoconfigure/testing | `AbstractJdbcInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/testing | `AbstractR2DbcInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/testing | `AbstractSpringWebInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/testing | `AbstractKafkaInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/testDeclarativeConfig | `DeclarativeConfigTest.java` |
| spring-boot-autoconfigure/test | `SpringWebMvcInstrumentation5AutoConfigurationTest.java` |
| spring-boot-autoconfigure/testSpring3 | `SpringWebMvcInstrumentation6AutoConfigurationTest.java` |
| spring-boot-autoconfigure/testSpring3 | `RestClientInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/test | `SchedulingInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/test | `SchedulingInstrumentationAspectTest.java` |
| spring-boot-autoconfigure/test | `SpringWebfluxInstrumentationAutoConfigurationTest.java` |
| spring-boot-autoconfigure/test | `WebClientBeanPostProcessorTest.java` |
