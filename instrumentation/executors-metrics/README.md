# Settings for the executors metrics instrumentation

| System property                                                                      | Type    | Default | Description                                                                                                                                    |
| ------------------------------------------------------------------------------------ | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.executors-metrics.enabled`                                     | Boolean | `false` | Enables executor metrics instrumentation.                                                                                                      |
| `otel.instrumentation.executors-metrics.experimental.name-normalization`             | String  | `all`   | Replaces all consecutive digits in executor thread names with `*` for `all`; `trailing` replaces only trailing digits; other values use `all`. |
