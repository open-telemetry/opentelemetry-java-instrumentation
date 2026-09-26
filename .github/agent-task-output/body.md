Stabilize the controller and view span opt-ins as part of #19828. Both remain disabled by default, and span emission is unchanged.

Replace `otel.instrumentation.common.experimental.controller-telemetry.enabled` with `otel.instrumentation.common.controller-telemetry.enabled`, and `otel.instrumentation.common.experimental.view-telemetry.enabled` with `otel.instrumentation.common.view-telemetry.enabled`. For declarative YAML, replace the `controller_telemetry/development` and `view_telemetry/development` keys with:

```yaml
instrumentation/development:
  java:
    common:
      controller_telemetry:
        enabled: true
      view_telemetry:
        enabled: true
```

The old flat and YAML names remain deprecated fallbacks until 3.0. Each warns only once when applied.
