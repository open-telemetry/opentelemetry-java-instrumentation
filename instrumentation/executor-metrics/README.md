# Settings for the executor metrics instrumentation

The `jvm.executor.name` metric attribute is derived from the worker thread name observed when
executor metrics are registered. Empty or unavailable thread names are reported as `unknown`;
otherwise, the configured `trailing` or `all` normalization rule is applied.

The `unknown` value does not identify an executor, so metrics from multiple unnamed executors may
be aggregated. Users requiring per-executor metric series should configure distinct names through
the executor's `ThreadFactory`.

The optional `jvm.executor.owner.name` attribute is omitted by default. Instrumentations that know
the logical component owning an executor SHOULD configure it before the first worker is registered.
Once executor metrics are registered, `jvm.executor.name` and `jvm.executor.owner.name` remain fixed
for that registration; later `reregister` calls do not create a new metric identity.

| System property                                                         | Type    | Default    | Description                                                                                                                                   |
| ----------------------------------------------------------------------- | ------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executor-metrics.enabled`                         | Boolean | `false`    | Enables executor metrics instrumentation.                                                                                                     |
| `otel.instrumentation.executor-metrics.experimental.name-normalization` | String  | `trailing` | Replaces trailing digits in executor thread names with `*` for `trailing`; `all` replaces every group of digits; other values use `trailing`. |
