# Settings for the executors instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.executors.include` | List | Empty | List of `Executor` subclasses to be instrumented. |
| `otel.instrumentation.executors.include-all` | Boolean | `false` | Whether to instrument all classes that implement the `Executor` interface. |