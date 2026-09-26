Add stable messaging header selectors in flat and declarative configuration as part of #19828. Header capture stays off by default, and case-sensitive glob matching and exclusion precedence are unchanged.

```properties
otel.instrumentation.common.messaging.headers.included=Trace-*
otel.instrumentation.common.messaging.headers.excluded=Trace-secret
```

```yaml
instrumentation/development:
  java:
    common:
      messaging:
        headers:
          included: ["Trace-*"]
          excluded: ["Trace-secret"]
```

The common `experimental.headers.*` properties and `headers/development` YAML keys remain available, including under v3-preview, warn when used as fallbacks, and will be removed in 3.0. Migrate each leaf to the stable spelling. The older `otel.instrumentation.messaging.experimental.headers.*` aliases retain their next-minor deprecation schedule; `capture-headers` and receive telemetry are unchanged.
