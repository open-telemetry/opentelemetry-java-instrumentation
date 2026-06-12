# Settings for the Log4j Appender instrumentation

| System property                                                            | Type    | Default | Description                                                                                                                                                                                                     |
| -------------------------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.log4j-appender.experimental-log-attributes`          | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                |
| `otel.instrumentation.log4j-appender.experimental.capture-code-attributes` | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                   |
| `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes`  | String  |         | Comma separated list of context data attributes to capture. Use the wildcard character `*` to capture all attributes; glob wildcards (`*` and `?`) are supported. Use `exclude-mdc-attributes` to exclude keys. |
| `otel.instrumentation.log4j-appender.experimental.exclude-mdc-attributes`  | String  |         | Comma separated list of context data attributes to exclude from capture (glob wildcards `*` and `?` supported). Only takes effect alongside a non-empty `capture-mdc-attributes` list.                          |

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is not emitted as an attribute.

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
