# Settings for the executors instrumentation

| System property                                     | Type    | Default | Description                                                                                                                                    |
| --------------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executors.include`            | List    | Empty   | List of `Executor` subclasses to be instrumented.                                                                                              |
| `otel.instrumentation.executors.include-all`        | Boolean | `false` | Whether to instrument all classes that implement the `Executor` interface.                                                                     |
| `otel.instrumentation.executors.name-normalization` | String  | `all`   | Replaces all consecutive digits in executor thread names with `*` for `all`; `trailing` replaces only trailing digits; other values use `all`. |
| `otel.instrumentation.executors-metrics.enabled`    | Boolean | `false` | Enables executor metrics instrumentation.                                                                                                      |
