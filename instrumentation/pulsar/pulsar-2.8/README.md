# Settings for the Apache Pulsar instrumentation

| System property                                            | Type    | Default | Description                                         |
|------------------------------------------------------------|---------|---------|-----------------------------------------------------|
| `otel.instrumentation.pulsar.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
| `otel.instrumentation.pulsar-clients-metrics.enable`       | Boolean | `true`  | Enable the Pulsar producer and consumer metrics.    |

# Pulsar Client Metrics

The following table shows the full set of metrics exposed by the OpenTelemetry Pulsar client
instrumentation.

## Producer Metrics

| Metric Name                                    | Attribute Keys                            | Unit     | Metric Description                 | Metric Type               |
|------------------------------------------------|-------------------------------------------|----------|------------------------------------|---------------------------|
| `pulsar.client.producer.message.sent.size`     | `topic` `producer.name`                   | bytes    | Counts the size of sent messages   | `LONG_OBSERVABLE_GAUGE`   |
| `pulsar.client.producer.message.sent.count`    | `topic` `producer.name` `response.status` | messages | Counts the number of sent messages | `LONG_OBSERVABLE_GAUGE`   |
| `pulsar.client.producer.message.sent.duration` | `topic` `producer.name` `quantile`        | ms       | The duration of sent messages      | `DOUBLE_OBSERVABLE_GAUGE` |

## Consumer Metrics

| Metric Name                                     | Attribute Keys                                           | Unit     | Metric Description                                 | Metric Type             |
|-------------------------------------------------|----------------------------------------------------------|----------|----------------------------------------------------|-------------------------|
| `pulsar.client.consumer.message.received.size`  | `topic` `subscription` `consumer.name`                   | bytes    | Counts the size of received messages               | `LONG_OBSERVABLE_GAUGE` |
| `pulsar.client.consumer.message.received.count` | `topic` `subscription` `consumer.name` `response.status` | messages | Counts the number of received messages             | `LONG_OBSERVABLE_GAUGE` |
| `pulsar.client.consumer.acks.sent.count`        | `topic` `subscription` `consumer.name` `response.status` | acks     | Counts the number of sent message acknowledgements | `LONG_OBSERVABLE_GAUGE` |
| `pulsar.client.consumer.receiver.queue.usage`   | `topic` `subscription` `consumer.name`                   | messages | Number of the messages in the receiver queue       | `LONG_OBSERVABLE_GAUGE` |
