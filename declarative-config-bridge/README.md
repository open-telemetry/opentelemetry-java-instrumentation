# Declarative Config Bridge

> [!WARNING]
> `DeclarativeConfigPropertiesBridgeBuilder` is deprecated and will be removed in 3.0. Read
> declarative component configuration through `DeclarativeConfigProperties` directly. To expose
> `ConfigProperties` through the declarative configuration API, use
> `ConfigPropertiesBackedConfigProvider`.

Declarative Config Bridge allows instrumentation authors to access configuration in a uniform way,
regardless of the configuration source.

The bridge lets callers expose flat `ConfigProperties` through the declarative configuration API,
including custom property mappings and custom declarative/flat prefixes.

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

```java
@AutoService(AutoConfigurationCustomizerProvider.class)
public class InferredSpansAutoConfig implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer config) {
    config.addTracerProviderCustomizer(
        (providerBuilder, properties) -> {
          DeclarativeConfigProperties declarativeProperties =
              ConfigPropertiesBackedConfigProvider.builder()
                  .setAccessPath("", "otel.inferred.spans.")
                  .build(properties)
                  .getInstrumentationConfig();
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
    - inferred_spans:
        backup_diagnostic_files: true
```

And now the component provider:

```java
@AutoService(ComponentProvider.class)
public class InferredSpansSpanProcessorProvider implements ComponentProvider {

  @Override
  public String getName() {
    return "inferred_spans";
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
`DeclarativeConfigPropertiesDurationUtil.getDuration(...)`:

```java
Duration minDuration =
    DeclarativeConfigPropertiesDurationUtil.getDuration(properties, "min_duration");
```

String duration values such as `42ms` are supported when the declarative config is backed by flat
`ConfigProperties`. For other declarative-config implementations, durations must already be
normalized to integer milliseconds.
