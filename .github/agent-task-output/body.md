Add the stable declarative span suppression key, `java.common.span_suppression_strategy`, without introducing a stable flat property. The programmatic setter still takes precedence, and the default remains `semconv`.

```yaml
instrumentation/development:
  java:
    common:
      span_suppression_strategy: span-kind
```

Migrate YAML configurations from `java.common.span_suppression_strategy/development` to the new key. The old YAML key and the previously deprecated `otel.instrumentation.experimental.span-suppression-strategy` flat property remain as fallbacks until 3.0, continue to work under v3-preview, and warn only when applied. The stable YAML key takes precedence over both deprecated settings, and the old YAML key takes precedence over the flat property. Existing flat-property bridge mapping remains unchanged.
