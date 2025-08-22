# OpenTelemetry Instrumentation API Incubator

Instrumentation API Incubator is a collection of libraries that provide additional functionality
for OpenTelemetry instrumentation and auto-configuration. It is intended to be used by
instrumentation authors and auto-configuration providers to enhance their capabilities and provide a
more consistent experience when working with OpenTelemetry.

## Declarative Config Bridge

Declarative Config Bridge allows instrumentation authors to access configuration in a uniform way,
regardless of the configuration source.

The bridge allows you to read configuration using the system property style when dealing with
declarative configuration.

### Example

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
file_format: 1.0-rc.1
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
public class InferredSpansComponentProvider implements ComponentProvider<SpanProcessor> {

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
