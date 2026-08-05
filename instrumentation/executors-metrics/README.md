# Settings for the executors metrics instrumentation

The `jvm.executor.name` metric attribute is derived from the worker thread name observed when
executor metrics are registered. Empty thread names are reported as `unknown`; otherwise, the
configured `trailing` or `all` normalization rule is applied.

| System property                                                          | Type    | Default    | Description                                                                                                                                   |
| ------------------------------------------------------------------------ | ------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executors-metrics.enabled`                         | Boolean | `false`    | Enables executor metrics instrumentation.                                                                                                     |
| `otel.instrumentation.executors-metrics.experimental.name-normalization` | String  | `trailing` | Replaces trailing digits in executor thread names with `*` for `trailing`; `all` replaces every group of digits; other values use `trailing`. |
