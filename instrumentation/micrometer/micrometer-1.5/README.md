# Settings for the Micrometer bridge instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.micrometer.base-time-unit`          | String  | `ms`  | Set the base time unit for the OpenTelemetry `MeterRegistry` implementation. <details><summary>Valid values</summary>`ns`, `nanoseconds`, `us`, `microseconds`, `ms`, `microseconds`, `s`, `seconds`, `min`, `minutes`, `h`, `hours`, `d`, `days`</details> |
| `otel.instrumentation.micrometer.prometheus-mode.enabled` | boolean | false | Enable the "Prometheus mode" this will simulate the behavior of Micrometer's PrometheusMeterRegistry. The instruments will be renamed to match Micrometer instrument naming, and the base time unit will be set to seconds. |
