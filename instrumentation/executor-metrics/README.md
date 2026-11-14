# Settings for the executor metrics instrumentation

The `jvm.executor.name` metric attribute is derived from the worker thread name observed when
executor metrics are registered. Empty or unavailable thread names are reported as `unknown`;
otherwise, the configured `trailing` or `all` normalization rule is applied.

Executors with the same normalized name, type, and owner share metric series.
This can occur with unnamed executors or thread factories whose names become
identical after normalization.

For ThreadPoolExecutor task counters, terminating or collecting one of several
executors sharing a series can decrease the reported cumulative value. Configure
thread names that remain distinct after normalization when accurate per-executor
counts and rates are required.

The optional `jvm.executor.owner.name` attribute is omitted by default.
Instrumentations that know the logical component owning an executor SHOULD
configure it before the first worker starts metric registration. Registration
captures the name-normalization rule and owner together; later `reregister` calls
do not change an in-progress or completed registration.

| System property                                                         | Type    | Default    | Description                                                                                                                                   |
| ----------------------------------------------------------------------- | ------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executor-metrics.enabled`                         | Boolean | `false`    | Enables executor metrics instrumentation.                                                                                                     |
| `otel.instrumentation.executor-metrics.experimental.name-normalization` | String  | `trailing` | Replaces trailing digits in executor thread names with `*` for `trailing`; `all` replaces every group of digits; other values use `trailing`. |
