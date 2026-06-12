# Settings for the JBoss Log Manager instrumentation

| System property                                                             | Type    | Default | Description                                                                                                                                                                                            |
| --------------------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `otel.instrumentation.jboss-logmanager.experimental-log-attributes`         | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                       |
| `otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes` | String  |         | Comma separated list of MDC attributes to capture. Use the wildcard character `*` to capture all attributes; glob wildcards (`*` and `?`) are supported. Use `exclude-mdc-attributes` to exclude keys. |
| `otel.instrumentation.jboss-logmanager.experimental.exclude-mdc-attributes` | String  |         | Comma separated list of MDC attributes to exclude from capture (glob wildcards `*` and `?` supported). Only takes effect alongside a non-empty `capture-mdc-attributes` list.                          |

The `otel.event.name` key is supported in MDC entries. When present, its value is used as the log event name and is not emitted as an attribute.
