# Settings for the Quartz instrumentation

| System property                                             | Type    | Default | Description                                                                                                                    |
| ----------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `otel.instrumentation.quartz.emit-experimental-telemetry`   | Boolean | `false` | Enables experimental telemetry, such as the `job.system` and `quartz.scheduler.name` attributes.                               |
| `otel.instrumentation.quartz.experimental-span-attributes`  | Boolean | `false` | Deprecated; use `otel.instrumentation.quartz.emit-experimental-telemetry` instead. The deprecated property will be removed in 3.0. |
