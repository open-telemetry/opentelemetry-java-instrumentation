# Settings for the Spymemcached instrumentation

Query text includes keys and command parameters, with stored values replaced by `?`.
Keys may contain sensitive information. Bulk keys are omitted when supplied through iterators or
paired with iterator-based transcoders, to avoid consuming iterators or reporting keys that may not
be requested.

| System property                                                  | Type    | Default | Description                                          |
| ---------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------- |
| `otel.instrumentation.spymemcached.experimental-span-attributes` | Boolean | `false` | Enables the capture of experimental span attributes. |
