Add the stable declarative span suppression key, `java.common.span_suppression_strategy`, without introducing a stable flat property. The programmatic setter still takes precedence, and the default remains `semconv`.

```yaml
instrumentation/development:
  java:
    common:
      span_suppression_strategy: span-kind
```

Migrate YAML configurations from `java.common.span_suppression_strategy/development` to the new key. The old YAML key and the previously deprecated `otel.instrumentation.experimental.span-suppression-strategy` flat property remain as fallbacks until 3.0 and warn once when applied, including under v3-preview. The stable YAML key takes precedence over both fallbacks, and the old YAML key takes precedence over the flat property.

Also add explicit timeouts to CI, smoke-test image, publishing, benchmark, and CodeQL workflow jobs.
