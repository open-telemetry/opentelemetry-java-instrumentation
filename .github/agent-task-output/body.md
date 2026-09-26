Stabilize the controller and view span opt-ins as part of #19828. Both remain disabled by default.

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

The old flat and YAML names remain deprecated fallbacks until 3.0, warning once per setting when applied. With `otel.instrumentation.common.v3-preview=true` or YAML `java.common.v3_preview: true`, the old names are ignored without warnings. The stable names take precedence in both modes.
