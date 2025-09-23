# Settings for the Lettuce instrumentation

| System property                                                             | Type    | Default | Description                                            |
|-----------------------------------------------------------------------------|---------|---------|--------------------------------------------------------|
| `otel.instrumentation.lettuce.experimental-span-attributes`                 | Boolean | `false` | Enable the capture of experimental span attributes.    |
| `otel.instrumentation.lettuce.connection-telemetry.enabled`                 | Boolean | `false` | Enable the creation of Connect spans.                  |
| `otel.instrumentation.lettuce.experimental.command-encoding-events.enabled` | Boolean | `true`  | Enable the capture of command encoding as span events. |
