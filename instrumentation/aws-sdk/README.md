# Settings for the AWS SDK instrumentation

For more information, see the respective public setters in the `AwsSdkTelemetryBuilder` classes:

* [SDK v1](./aws-sdk-1.11/library/src/main/java/io/opentelemetry/instrumentation/awssdk/v1_11/AwsSdkTelemetryBuilder.java)
* [SDK v2](./aws-sdk-2.2/library/src/main/java/io/opentelemetry/instrumentation/awssdk/v2_2/AwsSdkTelemetryBuilder.java)

| System property | Type | Default | Description                                                                                                                                    |
|---|---|---|------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.aws-sdk.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                            |
| `otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging` | Boolean | `false` | Enable propagation via message attributes using configured propagator (in addition to X-Ray). At the moment, Supports only SQS and the v2 SDK. |
