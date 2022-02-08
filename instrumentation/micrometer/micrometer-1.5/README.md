# Settings for the Micrometer bridge instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.micrometer.base-time-unit` | String | `ms` | Set the base time unit for the OpenTelemetry `MeterRegistry` implementation. <details><summary>Valid values</summary>`ns`, `nanoseconds`, `us`, `microseconds`, `ms`, `microseconds`, `s`, `seconds`, `min`, `minutes`, `h`, `hours`, `d`, `days`</details> |