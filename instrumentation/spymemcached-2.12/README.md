# Settings for the Spymemcached instrumentation

Query text includes keys and command parameters, with stored values replaced by `?`.
Keys may contain sensitive information. Bulk keys supplied as one-shot iterators are omitted to
avoid consuming the application's iterator.

| System property                                                  | Type    | Default | Description                                          |
| ---------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------- |
| `otel.instrumentation.spymemcached.experimental-span-attributes` | Boolean | `false` | Enables the capture of experimental span attributes. |
