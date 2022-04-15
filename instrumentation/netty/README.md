# Settings for the Netty instrumentation

| System property                                           | Type    | Default | Description                                                                                       |
|-----------------------------------------------------------|---------|---------|---------------------------------------------------------------------------------------------------|
| `otel.instrumentation.netty.connection-telemetry.enabled` | Boolean | `false` | Enable the creation of Connect and DNS spans by default for Netty 4.0 and higher instrumentation. |
| `otel.instrumentation.netty.ssl-telemetry.enabled`        | Boolean | `false` | Enable SSL telemetry for Netty 4.0 and higher instrumentation.                                    |
