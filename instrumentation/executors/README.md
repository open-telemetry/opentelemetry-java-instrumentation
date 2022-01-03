# Settings for the executors instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.executors.include` | Boolean | `false` | Whether to include property names. |
| `otel.instrumentation.executors.include-all` | Boolean | `false` | Whether to instrument all classes that implement the `Executor` interface. |