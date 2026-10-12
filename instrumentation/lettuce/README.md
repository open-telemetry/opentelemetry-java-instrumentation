# Settings for the Lettuce instrumentation

| System property                                                             | Type    | Default | Description                                                                                             |
| --------------------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.lettuce.experimental-span-attributes`                 | Boolean | `false` | Enable the capture of experimental span attributes.                                                     |
| `otel.instrumentation.lettuce.connection-telemetry.enabled`                 | Boolean | `false` | Enable the creation of Connect spans.                                                                   |
| `otel.instrumentation.lettuce.experimental.command-encoding-events.enabled` | Boolean | `false` | Enable the capture of `redis.encode.start` and `redis.encode.end` span events in the standalone library instrumentation for Lettuce 5.1 and later. |
