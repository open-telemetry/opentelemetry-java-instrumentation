# Declarative Config Bridge

`DeclarativeConfigBridge` exposes flat `ConfigProperties` through the declarative configuration API,
allowing instrumentation to use `DeclarativeConfigProperties` regardless of the configuration source.

## Example

As an example, let's look at the contrib inferred spans configuration.
It reads declarative keys such as `backup_diagnostic_files`, while preserving flat property support
for `otel.inferred.spans.backup.diagnostic.files`.

```java
class InferredSpansConfig {
  static SpanProcessor createSpanProcessor(DeclarativeConfigProperties properties) {
    boolean backupDiagnosticFiles = properties.getBoolean("backup_diagnostic_files", false);
  }
}
```

The auto configuration path can bridge flat config into that declarative view:

`createComponentProperties(...)` returns the same root-relative configuration shape that
`ComponentProvider.create(...)` receives for native declarative configuration.

```java
@AutoService(AutoConfigurationCustomizerProvider.class)
public class InferredSpansAutoConfig implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer config) {
    config.addTracerProviderCustomizer(
        (providerBuilder, properties) -> {
          DeclarativeConfigProperties declarativeProperties =
              DeclarativeConfigBridge.createComponentProperties(
                properties, "otel.inferred.spans.");
          providerBuilder.addSpanProcessor(
              InferredSpansConfig.createSpanProcessor(declarativeProperties));
          return providerBuilder;
        });
  }
}
```

The declarative component provider can use the same config method directly:

Let's first look at the yaml file that is used to configure the inferred spans processor:

```yaml
file_format: 1.1
tracer_provider:
  processors:
    - inferred_spans/development:
        backup_diagnostic_files: true
```

And now the component provider:

```java
@AutoService(ComponentProvider.class)
public class InferredSpansSpanProcessorProvider implements ComponentProvider {

  @Override
  public String getName() {
    return "inferred_spans/development";
  }

  @Override
  public Class<SpanProcessor> getType() {
    return SpanProcessor.class;
  }

  @Override
  public SpanProcessor create(DeclarativeConfigProperties properties) {
    return InferredSpansConfig.createSpanProcessor(properties);
  }
}
```

For duration properties, contrib's `span-stacktrace` and `inferred-spans` use
`DeclarativeConfigDurationUtil.getDuration(...)`:

```java
Duration minDuration =
    DeclarativeConfigDurationUtil.getDuration(properties, "min_duration");
```

String duration values such as `42ms` are supported when the declarative config is backed by flat
`ConfigProperties`. For other declarative-config implementations, durations must already be
normalized to integer milliseconds.
