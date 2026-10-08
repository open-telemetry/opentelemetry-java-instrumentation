# Settings for the Spymemcached instrumentation

| System property                                                  | Type    | Default | Description                                          |
| ---------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------- |
| `otel.instrumentation.spymemcached.experimental-span-attributes` | Boolean | `false` | Enables the capture of experimental span attributes. |

## Error classification

For failed operations, `error.type` uses the `OperationException` category: `GENERAL`, `CLIENT`,
or `SERVER`. Errors without a category use the exception class name. Successful and cancelled
asynchronous operations do not set `error.type`.
