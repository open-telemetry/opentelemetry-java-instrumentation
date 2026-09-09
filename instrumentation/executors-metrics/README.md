# Settings for the executors metrics instrumentation

The `jvm.executor.name` metric attribute is derived from the worker thread name observed when
executor metrics are registered. Empty or unavailable thread names are reported as `unknown`;
otherwise, the configured `trailing` or `all` normalization rule is applied.

The `unknown` value does not identify an executor, so metrics from multiple unnamed executors may
be aggregated. Users requiring per-executor metric series should configure distinct names through
the executor's `ThreadFactory`.

The optional `jvm.executor.owner.name` attribute is omitted by default. Instrumentations that know
the logical component owning an executor can provide or update it through
`JdkExecutorMetrics.reregister`; passing a null owner removes the attribute.

| System property                                                          | Type    | Default    | Description                                                                                                                                   |
| ------------------------------------------------------------------------ | ------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executors-metrics.enabled`                         | Boolean | `false`    | Enables executor metrics instrumentation.                                                                                                     |
| `otel.instrumentation.executors-metrics.experimental.name-normalization` | String  | `trailing` | Replaces trailing digits in executor thread names with `*` for `trailing`; `all` replaces every group of digits; other values use `trailing`. |
