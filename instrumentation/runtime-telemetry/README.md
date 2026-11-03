# Settings for the Runtime Telemetry instrumentation

| System property                                                                       | Type    | Default | Description                                                                                                                        |
| ------------------------------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.runtime-telemetry.emit-experimental-metrics`                    | Boolean | `false` | Enable the capture of experimental JMX-based metrics.                                                                              |
| `otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics`                | Boolean | `false` | Add the experimental metrics that JFR can produce to the JFR metric selector on Java 17+.                                          |
| `otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included`            | List    |         | Include metrics to source from JFR using case-sensitive `*` and `?` glob patterns. Use `*` to select all metrics.                  |
| `otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.excluded`            | List    |         | Exclude metrics from JFR using case-sensitive `*` and `?` glob patterns. Excluded patterns take precedence over included patterns. |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled`         | Boolean | `false` | Enable creating events for JAR libraries used by the application.                                                                  |
| `otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second` | Integer | 10      | The number of JAR files processed per second.                                                                                      |
