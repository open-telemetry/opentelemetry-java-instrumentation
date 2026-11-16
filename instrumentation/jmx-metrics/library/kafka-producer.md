# Kafka Producer Metrics

JMX Metric Insight can collect Kafka producer metrics from the producer's local MBeans. Enable them
with the Java agent setting:

```properties
otel.jmx.metrics.experimental.included=kafka.producer.*
```

Kafka's JMX reporter must be enabled. The Kafka client metrics bridge can also report some of the
same underlying measurements. Use one source if you want to avoid collecting both sets.

All metrics below are gauges. Every data point has `messaging.client.id`. Topic metrics also have
`messaging.destination.name` and appear once the producer registers metrics for a topic.

| Metric | Unit | Description |
| --- | --- | --- |
| `kafka.producer.io.wait.time.average` | `s` | The average time the producer I/O thread waits for a socket to be ready. |
| `kafka.producer.outgoing.byte.rate` | `By/s` | The average number of bytes sent per second to all brokers. |
| `kafka.producer.request.latency.average` | `s` | The average request latency. |
| `kafka.producer.request.rate` | `{request}/s` | The average number of requests sent per second. |
| `kafka.producer.response.rate` | `{response}/s` | The average number of responses received per second. |
| `kafka.producer.topic.byte.rate` | `By/s` | The average number of bytes sent per second to the topic. |
| `kafka.producer.topic.compression.ratio` | `1` | The average ratio of compressed to uncompressed record batch size for the topic. |
| `kafka.producer.topic.record.error.rate` | `{record}/s` | The average number of record send errors per second for the topic. |
| `kafka.producer.topic.record.retry.rate` | `{record}/s` | The average number of retried record sends per second for the topic. |
| `kafka.producer.topic.record.send.rate` | `{record}/s` | The average number of records sent per second to the topic. |
