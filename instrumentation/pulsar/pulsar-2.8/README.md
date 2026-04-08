# Settings for the Apache Pulsar instrumentation

This instrumentation applies to the Pulsar Java client library only. It does not instrument
Pulsar server components (broker, functions, etc.).

| System property                                            | Type    | Default | Description                                         |
| ---------------------------------------------------------- | ------- | ------- | --------------------------------------------------- |
| `otel.instrumentation.pulsar.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
