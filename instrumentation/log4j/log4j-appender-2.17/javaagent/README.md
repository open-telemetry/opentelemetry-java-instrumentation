# Settings for the Log4j Appender instrumentation

| System property                                                                   | Type    | Default | Description                                                                                                                                   |
|-----------------------------------------------------------------------------------|---------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.log4j-appender.experimental-log-attributes`                 | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                              |
| `otel.instrumentation.log4j-appender.experimental.capture-code-attributes`        | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead. |
| `otel.instrumentation.log4j-appender.experimental.capture-map-message-attributes` | Boolean | `false` | Enable the capture of `MapMessage` attributes.                                                                                                |
| `otel.instrumentation.log4j-appender.experimental.capture-marker-attribute`       | Boolean | `false` | Enable the capture of Log4j markers as attributes.                                                                                            |
| `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes`         | String  |         | Comma separated list of context data attributes to capture. Use the wildcard character `*` to capture all attributes.                         |

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
