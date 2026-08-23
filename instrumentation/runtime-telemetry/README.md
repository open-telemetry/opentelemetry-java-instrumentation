# Settings for the Runtime Telemetry instrumentation

| System property                                                                       | Type    | Default | Description                                                                                                                        |
| ------------------------------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.runtime-telemetry.emit-experimental-metrics`                    | Boolean | `false` | Enable the capture of experimental JMX-based metrics.                                                                              |
| `otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics`                | Boolean | `false` | Add the experimental metrics that JFR can produce to the JFR metric selector on Java 17+.                                          |
| `otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included`            | List    |         | Include metrics to source from JFR using case-sensitive `*` and `?` glob patterns. Use `*` to select all metrics.                  |
| `otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.excluded`            | List    |         | Exclude metrics from JFR using case-sensitive `*` and `?` glob patterns. Excluded patterns take precedence over included patterns. |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled`         | Boolean | `false` | Enable creating events for JAR libraries used by the application.                                                                  |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second` | Integer | 10      | The number of JAR files processed per second.                                                                                      |

## Deprecated Properties (to be removed in 3.0)

| System property                                                          | Type    | Default | Description                                                                                |
| ------------------------------------------------------------------------ | ------- | ------- | ------------------------------------------------------------------------------------------ |
| `otel.instrumentation.runtime-telemetry.capture-gc-cause`                | Boolean | `false` | Enable the capture of the jvm.gc.cause attribute. Will always be captured in 3.0.          |
| `otel.instrumentation.runtime-telemetry.emit-experimental-telemetry`     | Boolean | `false` | Use `emit-experimental-metrics` instead.                                                   |
| `otel.instrumentation.runtime-telemetry.package-emitter.enabled`         | Boolean | `false` | Use `experimental.package-emitter.enabled` instead.                                        |
| `otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second` | Integer | 10      | Use `experimental.package-emitter.jars-per-second` instead.                                |
| `otel.instrumentation.runtime-telemetry.experimental.prefer-jfr`         | Boolean | `false` | Use `experimental.jfr-metrics.included` instead. May be removed in the next minor release. |
| `otel.instrumentation.runtime-telemetry-java17.enabled`                  | Boolean | `false` | Deprecated. Use `emit-experimental-jfr-metrics` instead.                                   |
| `otel.instrumentation.runtime-telemetry-java17.enable-all`               | Boolean | `false` | Deprecated. Use `experimental.jfr-metrics.included=*` instead.                             |
