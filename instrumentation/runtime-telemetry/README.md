# Settings for the Runtime Telemetry instrumentation

| System property                                                                       | Type    | Default | Description                                                                             |
| ------------------------------------------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------- |
| `otel.instrumentation.runtime-telemetry.emit-experimental-metrics`                    | Boolean | `false` | Enable the capture of experimental metrics.                                             |
| `otel.instrumentation.runtime-telemetry.experimental.prefer-jfr`                      | Boolean | `false` | Prefer JFR over JMX for metrics where both collection methods are available (Java 17+). |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled`         | Boolean | `false` | Enable creating events for JAR libraries used by the application.                       |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second` | Integer | 10      | The number of JAR files processed per second.                                           |

## Deprecated Properties (to be removed in 3.0)

| System property                                                          | Type    | Default | Description                                                                       |
| ------------------------------------------------------------------------ | ------- | ------- | --------------------------------------------------------------------------------- |
| `otel.instrumentation.runtime-telemetry.capture-gc-cause`                | Boolean | `false` | Enable the capture of the jvm.gc.cause attribute. Will always be captured in 3.0. |
| `otel.instrumentation.runtime-telemetry.emit-experimental-telemetry`     | Boolean | `false` | Use `emit-experimental-metrics` instead.                                          |
| `otel.instrumentation.runtime-telemetry.package-emitter.enabled`         | Boolean | `false` | Use `experimental.package-emitter.enabled` instead.                               |
| `otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second` | Integer | 10      | Use `experimental.package-emitter.jars-per-second` instead.                       |
| `otel.instrumentation.runtime-telemetry-java17.enabled`                  | Boolean | `false` | Deprecated. Use `emit-experimental-metrics` for experimental JFR features.        |
| `otel.instrumentation.runtime-telemetry-java17.enable-all`               | Boolean | `false` | Deprecated. Use `emit-experimental-metrics` and `experimental.prefer-jfr`.        |
