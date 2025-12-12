# ConfigProperties Usages in OpenTelemetry Java Instrumentation

This document catalogs all usages of `io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties` 
in the codebase to help plan the migration away from `DeclarativeConfigPropertiesBridge`.

## Overview

`ConfigProperties` is an interface from the OpenTelemetry SDK autoconfigure module that provides
access to configuration values in a key-value style (like system properties or environment variables).

The `DeclarativeConfigPropertiesBridge` exists to convert YAML declarative config back to 
`ConfigProperties` format so that existing code continues to work with both configuration styles.

## Category 1: Public SPI Interfaces

These are **public APIs** that extensions implement. Changing these would be a breaking change.

### InstrumentationModule.defaultEnabled(ConfigProperties)

**Location**: `javaagent-extension-api/src/main/java/.../InstrumentationModule.java`

```java
public boolean defaultEnabled(ConfigProperties config) {
  return config.getBoolean("otel.instrumentation.common.default-enabled", true);
}
```

**Overridden by ~20+ instrumentation modules**:
- `SpringWsInstrumentationModule`
- `SpringSecurityConfigServletInstrumentationModule`
- `SpringSecurityConfigWebFluxInstrumentationModule`
- `SpringBootActuatorInstrumentationModule`
- `SpringBatchInstrumentationModule`
- `MicrometerInstrumentationModule`
- `MyBatisInstrumentationModule`
- `JwsInstrumentationModule`
- `DataSourceInstrumentationModule`
- `JaxrsAnnotationsInstrumentationModule` (2.0 and 3.0)
- `JaxrsInstrumentationModule` (1.0)
- `LambdaInstrumentationModule`
- `UrlClassLoaderInstrumentationModule`
- `ApplicationLoggingInstrumentationModule`
- `EclipseOsgiInstrumentationModule`
- `ReflectionInstrumentationModule`
- `ClassLoaderInstrumentationModule`
- `DropwizardMetricsInstrumentationModule`
- And more...

---

### IgnoredTypesConfigurer.configure(IgnoredTypesBuilder, ConfigProperties)

**Location**: `javaagent-extension-api/src/main/java/.../IgnoredTypesConfigurer.java`

```java
void configure(IgnoredTypesBuilder builder, ConfigProperties config);
```

**Implementations**:
- `GlobalIgnoredTypesConfigurer`
- `CommonLibraryIgnoredTypesConfigurer`
- `AdditionalLibraryIgnoredTypesConfigurer`
- `UserExcludedClassesConfigurer`
- `UserExcludedClassLoadersConfigurer`
- `SpringIntegrationIgnoredTypesConfigurer`
- `IgnoredTestTypesConfigurer` (testing)

---

### AgentExtension.extend(AgentBuilder, ConfigProperties)

**Location**: `javaagent-tooling/src/main/java/.../AgentExtension.java`

```java
AgentBuilder extend(AgentBuilder agentBuilder, ConfigProperties config);
```

**Implementations**:
- `InstrumentationLoader`
- `TestAgentExtension` (testing)

---

### BootstrapPackagesConfigurer.configure(BootstrapPackagesBuilder, ConfigProperties)

**Location**: `javaagent-tooling/src/main/java/.../BootstrapPackagesConfigurer.java`

```java
void configure(BootstrapPackagesBuilder builder, ConfigProperties config);
```

---

## Category 2: Internal Tooling

These are internal usages within javaagent-tooling that can be migrated more easily.

### AgentInstaller

**File**: `javaagent-tooling/src/main/java/.../AgentInstaller.java`

Uses `ConfigProperties` for:
- `copyNecessaryConfigToSystemProperties(ConfigProperties config)` - copies specific properties to system properties
- `setBootstrapPackages(ConfigProperties config, ...)` - configures bootstrap packages
- `configureIgnoredTypes(ConfigProperties config, ...)` - configures ignored types
- `runAfterAgentListeners(..., ConfigProperties sdkConfigProperties)` - reads `otel.javaagent.internal.force-synchronous-agent-listeners`

---

### AgentTracerProviderConfigurer

**File**: `javaagent-tooling/src/main/java/.../AgentTracerProviderConfigurer.java`

```java
public SdkTracerProviderBuilder configure(SdkTracerProviderBuilder builder, ConfigProperties config) {
  // Reads: otel.javaagent.enabled, otel.instrumentation.common.experimental.suppress-thread-attributes
  // otel.traces.exporter
}
```

---

### AgentConfig

**File**: `javaagent-tooling/src/main/java/.../AgentConfig.java`

```java
public static boolean isInstrumentationEnabled(ConfigProperties config, Iterable<String> names, boolean defaultEnabled)
public static boolean isDebugModeEnabled(ConfigProperties config)
```

---

### InstrumentationModuleInstaller

**File**: `javaagent-tooling/src/main/java/.../InstrumentationModuleInstaller.java`

Uses `ConfigProperties` to check if modules are enabled via `AgentConfig.isInstrumentationEnabled()`.

---

### MuzzleMatcher

**File**: `javaagent-tooling/src/main/java/.../MuzzleMatcher.java`

Uses `ConfigProperties` for debug mode check.

---

### UserExcludedClassesConfigurer / UserExcludedClassLoadersConfigurer

Read from config:
- `otel.javaagent.exclude-classes`
- `otel.javaagent.exclude-class-loaders`

---

### AdditionalLibraryIgnoredTypesConfigurer

Reads: `otel.instrumentation.common.additional-library-ignores.enabled`

---

### Field injection configuration

**File**: `FieldBackedImplementationConfiguration.java`

Reads: `otel.javaagent.experimental.field-injection.enabled`

---

## Category 3: SDK Autoconfigure SPIs

These are standard OpenTelemetry SDK SPIs that use `ConfigProperties`.

### ResourceProvider implementations

- `DistroResourceProvider.createResource(ConfigProperties config)`
- `TestResourceProvider` (testing)

### TracerProviderConfigurer

- `AgentTracerProviderConfigurer`

### AutoConfigurationCustomizerProvider implementations

Various customizers that receive `ConfigProperties`.

---

## Category 4: Spring Boot Autoconfigure

### OpenTelemetryAutoConfiguration

**File**: `spring-boot-autoconfigure/src/main/java/.../OpenTelemetryAutoConfiguration.java`

Exposes `ConfigProperties` as a Spring bean:
```java
@Bean
public ConfigProperties otelProperties(ConfigProvider configProvider) {
  return new DeclarativeConfigPropertiesBridgeBuilder()
      .buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
}
```

### SpringConfigProperties

**File**: `spring-boot-autoconfigure/src/main/java/.../SpringConfigProperties.java`

Implements `ConfigProperties` to bridge Spring Boot properties to OTel configuration.

### ConfigPropertiesBridge

**File**: `spring-boot-autoconfigure/src/main/java/.../ConfigPropertiesBridge.java`

Wraps `ConfigProperties` with additional `ConfigProvider` access for Spring Boot.

---

## Category 5: Instrumentation Installers (AgentListener/BeforeAgentListener)

These use `AutoConfiguredOpenTelemetrySdk` which provides access to both `ConfigProperties` 
AND `ConfigProvider`. **Some can be migrated** to use `ConfigProvider` directly.

### JarAnalyzerInstaller ✅ Migrated

**File**: `runtime-telemetry-java8/javaagent/src/main/java/.../JarAnalyzerInstaller.java`

Reads:
- `otel.instrumentation.runtime-telemetry.package-emitter.enabled` → `java.runtime_telemetry.package_emitter.enabled`
- `otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second` → `java.runtime_telemetry.package_emitter.jars_per_second`

### OshiMetricsInstaller ✅ Migrated

**File**: `oshi/javaagent/src/main/java/.../OshiMetricsInstaller.java`

Reads:
- `otel.instrumentation.common.default-enabled` → `java.common.default_enabled`
- `otel.instrumentation.oshi.enabled` → `java.oshi.enabled`

### JmxMetricInsightInstaller ✅ Migrated

**File**: `jmx-metrics/javaagent/src/main/java/.../JmxMetricInsightInstaller.java`

Reads (with mapping `otel.jmx` → `otel.instrumentation.jmx` → `java.jmx`):
- `otel.jmx.enabled` → `java.jmx.enabled`
- `otel.jmx.config` → `java.jmx.config`
- `otel.jmx.target.system` → `java.jmx.target_system`
- `otel.jmx.discovery.delay` → `java.jmx.discovery_delay`

---

## Category 6: Test Infrastructure

### EmptyConfigProperties

**File**: `javaagent-tooling/src/main/java/.../EmptyConfigProperties.java`

Empty implementation used in testing and benchmarks.

### Test mocks

Various tests mock `ConfigProperties` for unit testing.

---

## Summary Statistics

| Category | Count | Migration Difficulty |
|----------|-------|---------------------|
| Public SPI Interfaces | 4 interfaces, ~30+ implementations | **High** - Breaking API change |
| Internal Tooling | ~15 usages | **Medium** - Internal refactoring |
| SDK Autoconfigure SPIs | ~5 usages | **Low** - Standard SDK patterns |
| Spring Boot Autoconfigure | ~5 usages | **Medium** - Spring integration |
| Instrumentation Installers | 3 migrated | **Low** - ✅ All done |
| Test Infrastructure | ~20 usages | **Low** - Test-only changes |

---

## Migration Options

### Option A: Keep ConfigProperties Forever

Keep the bridge indefinitely as a compatibility layer. This is the lowest risk approach.

**Pros**: No breaking changes, minimal effort
**Cons**: Maintains complexity, two config systems

### Option B: Deprecate and Migrate Public SPIs

1. Add new SPI methods that take `DeclarativeConfigProperties` or `ConfigProvider`
2. Keep old methods with `@Deprecated` annotation
3. Update all implementations over time
4. Remove deprecated methods in a future major version

**Pros**: Clean migration path
**Cons**: Long transition period, more code to maintain

### Option C: Change SPI Parameter Type (Breaking)

Change all SPIs to use `DeclarativeConfigProperties` directly.

**Pros**: Clean codebase, single config system
**Cons**: Breaking change for all extensions

---

## Key Properties Read from ConfigProperties

| Property | Used By |
|----------|---------|
| `otel.instrumentation.common.default-enabled` | InstrumentationModule |
| `otel.javaagent.enabled` | AgentTracerProviderConfigurer |
| `otel.javaagent.debug` | AgentConfig |
| `otel.javaagent.exclude-classes` | UserExcludedClassesConfigurer |
| `otel.javaagent.exclude-class-loaders` | UserExcludedClassLoadersConfigurer |
| `otel.instrumentation.common.additional-library-ignores.enabled` | AdditionalLibraryIgnoredTypesConfigurer |
| `otel.instrumentation.common.experimental.suppress-thread-attributes` | AgentTracerProviderConfigurer |
| `otel.instrumentation.experimental.span-suppression-strategy` | AgentInstaller |
| `otel.javaagent.experimental.field-injection.enabled` | FieldBackedImplementationConfiguration |
| `otel.javaagent.internal.force-synchronous-agent-listeners` | AgentInstaller |
| `otel.traces.exporter` | AgentTracerProviderConfigurer |
