# Settings for the Logback Appender instrumentation

| System property                                                                        | Type    | Default | Description                                                                                                                                   |
| -------------------------------------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.logback-appender.experimental-log-attributes`                    | Boolean | `false` | Enable the capture of experimental span attributes `thread.name` and `thread.id`.                                                             |
| `otel.instrumentation.logback-appender.experimental.capture-code-attributes`           | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead. |
| `otel.instrumentation.logback-appender.experimental.capture-marker-attribute`          | Boolean | `false` | Enable the capture of Logback markers as attributes.                                                                                          |
| `otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes` | Boolean | `false` | Enable the capture of Logback key value pairs as attributes.                                                                                  |
| `otel.instrumentation.logback-appender.experimental.capture-mdc-attributes`            | String  |         | Comma separated list of MDC attributes to capture. Use the wildcard character `*` to capture all attributes.                                  |

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
