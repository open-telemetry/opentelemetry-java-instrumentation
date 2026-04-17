# Declarative Config Bridge

Declarative Config Bridge allows instrumentation authors to access configuration in a uniform way,
regardless of the configuration source.

The bridge allows you to read configuration using the system property style when dealing with
declarative configuration.

## Example

As an example, let's look at the inferred spans configuration.
First, there is a configuration method that reads the properties and is unaware of the source of the
configuration:

```java
class InferredSpansConfig {
  static SpanProcessor create(ConfigProperties properties) {
    // read properties here
    boolean backupDiagnosticFiles =
        properties.getBoolean("otel.inferred.spans.backup.diagnostic.files", false);
  }
}
```

The auto configuration **without declarative config** passes the provided properties directly:

```java

@AutoService(AutoConfigurationCustomizerProvider.class)
public class InferredSpansAutoConfig implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer config) {
    config.addTracerProviderCustomizer(
        (providerBuilder, properties) -> {
          providerBuilder.addSpanProcessor(InferredSpansConfig.create(properties));
          return providerBuilder;
        });
  }
}
```

The auto configuration **with declarative config** uses the Declarative Config Bridge to be able to
use common configuration method:

Let's first look at the yaml file that is used to configure the inferred spans processor:

```yaml
file_format: 1.0
tracer_provider:
  processors:
    - inferred_spans:
        backup:
          diagnostic:
            files: true
```

And now the component provider that uses the Declarative Config Bridge:

```java

@AutoService(ComponentProvider.class)
public class InferredSpansComponentProvider implements ComponentProvider {

  @Override
  public String getName() {
    return "inferred_spans";
  }

  @Override
  public SpanProcessor create(DeclarativeConfigProperties config) {
    return InferredSpansConfig.create(
        new DeclarativeConfigPropertiesBridgeBuilder()
            // crop the prefix, because the properties are under the "inferred_spans" processor
            .addMapping("otel.inferred.spans.", "")
            .build(config));
  }

  @Override
  public Class<SpanProcessor> getType() {
    return SpanProcessor.class;
  }
}
```

## InstrumentationDefaults

`InstrumentationDefaults` lets distribution authors define instrumentation property defaults once
and have them work in both configuration modes.
First, there is a single defaults object that is unaware of the source of the configuration:

```java
InstrumentationDefaults defaults = new InstrumentationDefaults();
defaults.setDefault("micrometer", "base_time_unit", "s");
defaults.setDefault("log4j_appender", "experimental_log_attributes", "true");
```

Keys use underscore notation (matching the declarative config model). They are translated to
hyphen notation (`otel.instrumentation.<name>.<key>`) when producing system property keys.

The auto configuration **without declarative config** registers the defaults as a properties
supplier, translating them to `otel.instrumentation.*` keys:

```java
@AutoService(AutoConfigurationCustomizerProvider.class)
public class MyDistroAutoConfig implements AutoConfigurationCustomizerProvider {
  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(defaults::toConfigProperties);
  }
}
```

The auto configuration **with declarative config** registers the defaults as a model customizer,
injecting them under `instrumentation/development.java`.

Let's first look at the yaml file that the defaults effectively merge into:

```yaml
file_format: 1.0
instrumentation/development:
  java:
    micrometer:
      base_time_unit: s
    log4j_appender:
      experimental_log_attributes: "true"
```

And now the customizer that applies the defaults to the model:

```java
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class MyDistroDeclarativeConfig implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(model -> defaults.applyToModel(model));
  }
}
```

Explicit user configuration always takes precedence — defaults are only applied for properties not
already present (`putIfAbsent`).
