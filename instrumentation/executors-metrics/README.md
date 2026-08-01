# Settings for the executors metrics instrumentation

| System property                                                          | Type    | Default    | Description                                                                                                                                    |
| ------------------------------------------------------------------------ | ------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executors-metrics.enabled`                         | Boolean | `false`    | Enables executor metrics instrumentation.                                                                                                      |
| `otel.instrumentation.executors-metrics.experimental.name-normalization` | String  | `trailing` | Replaces trailing digits in executor thread names with `*` for `trailing`; `all` replaces every group of digits; other values use `all`.        |
