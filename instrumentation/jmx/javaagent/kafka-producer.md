# Kafka Producer Metrics

Here is the list of metrics based on MBeans exposed by Kafka producer. <br /><br />

Producer metrics:

| Metric Name                                  | Type  | Attributes       | Description                                                                          |
|----------------------------------------------|-------|------------------|--------------------------------------------------------------------------------------|
| kafka.producer.all.batch-size-avg            | Gauge | client-id        | Average number of bytes sent per partition per request                               |
| kafka.producer.all.bufferpool-wait-ratio     | Gauge | client-id        | The fraction of time an appender waits for space allocation                          |
| kafka.producer.all.compression-rate-avg      | Gauge | client-id        | Average ratio of the compressed batch size to the uncompressed size                  |
| kafka.producer.all.io-wait-time-ns-avg       | Gauge | client-id        | The average time the I/O thread spent waiting for a socket ready for reads or writes |
| kafka.producer.all.outgoing-byte-rate        | Gauge | client-id        | The average number of outgoing bytes sent per second to all servers                  |
| kafka.producer.all.produce-throttle-time-avg | Gauge | client-id        | Average time a request was throttled by a broker                                     |
| kafka.producer.all.record-queue-time-avg     | Gauge | client-id        | The average time record batches spent in the send buffer                             |
| kafka.producer.all.record-retry-rate         | Gauge | client-id        | The average number of retried record sends per second                                |
| kafka.producer.all.request-latency-avg       | Gauge | client-id        | The average request latency                                                          |
| kafka.producer.all.request-rate              | Gauge | client-id        | The average number of requests sent per second                                       |
| kafka.producer.all.response-rate             | Gauge | client-id        | The average number of response sent per second                                       |
| kafka.producer.byte-rate                     | Gauge | client-id, topic | The average number of bytes sent per second for a topic                              |
| kafka.producer.compression-rate              | Gauge | client-id, topic | The average compression rate of record batches for a topic                           |
| kafka.producer.record-error-rate             | Gauge | client-id, topic | The average per-second number of record sends that resulted in errors for a topic    |
| kafka.producer.record-retry-rate             | Gauge | client-id, topic | The average per-second number of retried record sends for a topic                    |
| kafka.producer.record-send-rate              | Gauge | client-id, topic | The average number of records sent per second for a topic                            |
