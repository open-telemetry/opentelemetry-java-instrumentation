# Settings for the Runtime Telemetry instrumentation

| System property                                                          | Type    | Default | Description                                                                       |
|--------------------------------------------------------------------------|---------|---------|-----------------------------------------------------------------------------------|
| `otel.instrumentation.runtime-telemetry.capture-gc-cause`                | Boolean | `false` | Enable the capture of the jvm.gc.cause attribute with the jvm.gc.duration metric. |
| `otel.instrumentation.runtime-telemetry.emit-experimental-telemetry`     | Boolean | `false` | Enable the capture of experimental metrics.                                       |
| `otel.instrumentation.runtime-telemetry-java17.enable-all`               | Boolean | `false` | Enable the capture of all JFR based metrics.                                      |
| `otel.instrumentation.runtime-telemetry-java17.enabled`                  | Boolean | `false` | Enable the capture of JFR based metrics.                                          |
| `otel.instrumentation.runtime-telemetry.package-emitter.enabled`         | Boolean | `false` | Enable creating events for JAR libraries used by the application.                 |
| `otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second` | Integer | 10      | The number of JAR files processed per second.                                     |
