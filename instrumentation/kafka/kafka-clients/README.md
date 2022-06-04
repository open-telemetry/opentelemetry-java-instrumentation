# Settings for the Kafka client instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.kafka.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
| `otel.instrumentation.kafka.client-propagation.enabled` | Boolean | `true` | Enables remote context propagation via Kafka message headers. |

# Kafka Metrics

The Kafka client exposes metrics via `org.apache.kafka.common.metrics.MetricsReporter` interface.
OpenTelemetry provides an implementation that bridges the metrics into OpenTelemetry.

To use, configure `OpenTelemetryKafkaMetrics` with an OpenTelemetry instance
via `OpenTelemetryKafkaMetrics#setOpenTelemetry(OpenTelemetry)`, and include a reference to this
class in kafka producer or consumer configuration, i.e.:

```java
Map<String, Object> config = new HashMap<>();
config.put(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, OpenTelemetryKafkaMetrics.class.getName());
config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getKafkaConnectString());
...
try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(config)) { ... }
```

The following table shows the full set of metrics exposed by the kafka client, and the corresponding
OpenTelemetry metric each maps to (if available). Empty values in the Instrument Name, Instrument
Description, etc column indicates there is no registered mapping for the metric and data is NOT
collected.

| Kafka Group | Kafka Name | Kafka Description | Attribute Keys | Instrument Name | Instrument Description | Instrument Unit | Instrument Type |
|-------------|------------|-------------------|----------------|-----------------|------------------------|-----------------|-----------------|
| app-info | commit-id | Metric indicating commit-id | client-id |  |  |  |  |
| app-info | start-time-ms | Metric indicating start-time-ms | client-id |  |  |  |  |
| app-info | version | Metric indicating version | client-id |  |  |  |  |
| consumer-coordinator-metrics | assigned-partitions | The number of partitions currently assigned to this consumer | client-id |  |  |  |  |
| consumer-coordinator-metrics | commit-latency-avg | The average time taken for a commit request | client-id |  |  |  |  |
| consumer-coordinator-metrics | commit-latency-max | The max time taken for a commit request | client-id |  |  |  |  |
| consumer-coordinator-metrics | commit-rate | The number of commit calls per second | client-id |  |  |  |  |
| consumer-coordinator-metrics | commit-total | The total number of commit calls | client-id |  |  |  |  |
| consumer-coordinator-metrics | failed-rebalance-rate-per-hour | The number of failed rebalance events per hour | client-id |  |  |  |  |
| consumer-coordinator-metrics | failed-rebalance-total | The total number of failed rebalance events | client-id |  |  |  |  |
| consumer-coordinator-metrics | heartbeat-rate | The number of heartbeats per second | client-id |  |  |  |  |
| consumer-coordinator-metrics | heartbeat-response-time-max | The max time taken to receive a response to a heartbeat request | client-id |  |  |  |  |
| consumer-coordinator-metrics | heartbeat-total | The total number of heartbeats | client-id |  |  |  |  |
| consumer-coordinator-metrics | join-rate | The number of group joins per second | client-id |  |  |  |  |
| consumer-coordinator-metrics | join-time-avg | The average time taken for a group rejoin | client-id |  |  |  |  |
| consumer-coordinator-metrics | join-time-max | The max time taken for a group rejoin | client-id |  |  |  |  |
| consumer-coordinator-metrics | join-total | The total number of group joins | client-id |  |  |  |  |
| consumer-coordinator-metrics | last-heartbeat-seconds-ago | The number of seconds since the last coordinator heartbeat was sent | client-id |  |  |  |  |
| consumer-coordinator-metrics | last-rebalance-seconds-ago | The number of seconds since the last successful rebalance event | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-assigned-latency-avg | The average time taken for a partition-assigned rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-assigned-latency-max | The max time taken for a partition-assigned rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-lost-latency-avg | The average time taken for a partition-lost rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-lost-latency-max | The max time taken for a partition-lost rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-revoked-latency-avg | The average time taken for a partition-revoked rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | partition-revoked-latency-max | The max time taken for a partition-revoked rebalance listener callback | client-id |  |  |  |  |
| consumer-coordinator-metrics | rebalance-latency-avg | The average time taken for a group to complete a successful rebalance, which may be composed of several failed re-trials until it succeeded | client-id |  |  |  |  |
| consumer-coordinator-metrics | rebalance-latency-max | The max time taken for a group to complete a successful rebalance, which may be composed of several failed re-trials until it succeeded | client-id |  |  |  |  |
| consumer-coordinator-metrics | rebalance-latency-total | The total number of milliseconds this consumer has spent in successful rebalances since creation | client-id |  |  |  |  |
| consumer-coordinator-metrics | rebalance-rate-per-hour | The number of successful rebalance events per hour, each event is composed of several failed re-trials until it succeeded | client-id |  |  |  |  |
| consumer-coordinator-metrics | rebalance-total | The total number of successful rebalance events, each event is composed of several failed re-trials until it succeeded | client-id |  |  |  |  |
| consumer-coordinator-metrics | sync-rate | The number of group syncs per second | client-id |  |  |  |  |
| consumer-coordinator-metrics | sync-time-avg | The average time taken for a group sync | client-id |  |  |  |  |
| consumer-coordinator-metrics | sync-time-max | The max time taken for a group sync | client-id |  |  |  |  |
| consumer-coordinator-metrics | sync-total | The total number of group syncs | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | bytes-consumed-rate | The average number of bytes consumed per second | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | bytes-consumed-rate | The average number of bytes consumed per second for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | bytes-consumed-total | The total number of bytes consumed for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | bytes-consumed-total | The total number of bytes consumed | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-latency-avg | The average time taken for a fetch request. | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-latency-max | The max time taken for any fetch request. | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-rate | The number of fetch requests per second. | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-size-avg | The average number of bytes fetched per request | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-size-avg | The average number of bytes fetched per request for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-size-max | The maximum number of bytes fetched per request for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-size-max | The maximum number of bytes fetched per request | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-throttle-time-avg | The average throttle time in ms | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-throttle-time-max | The maximum throttle time in ms | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | fetch-total | The total number of fetch requests. | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | preferred-read-replica | The current read replica for the partition, or -1 if reading from leader | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-consumed-rate | The average number of records consumed per second | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | records-consumed-rate | The average number of records consumed per second for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | records-consumed-total | The total number of records consumed for a topic | client-id,topic |  |  |  |  |
| consumer-fetch-manager-metrics | records-consumed-total | The total number of records consumed | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | records-lag | The latest lag of the partition | client-id,topic,partition | messaging.kafka.consumer.lag | Current approximate lag of consumer group at partition of topic. | {lag} | DOUBLE_OBSERVABLE_GAUGE |
| consumer-fetch-manager-metrics | records-lag-avg | The average lag of the partition | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-lag-max | The maximum lag in terms of number of records for any partition in this window | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | records-lag-max | The max lag of the partition | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-lead | The latest lead of the partition | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-lead-avg | The average lead of the partition | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-lead-min | The minimum lead in terms of number of records for any partition in this window | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | records-lead-min | The min lead of the partition | client-id,topic,partition |  |  |  |  |
| consumer-fetch-manager-metrics | records-per-request-avg | The average number of records in each request | client-id |  |  |  |  |
| consumer-fetch-manager-metrics | records-per-request-avg | The average number of records in each request for a topic | client-id,topic |  |  |  |  |
| consumer-metrics | connection-close-rate | The number of connections closed per second | client-id |  |  |  |  |
| consumer-metrics | connection-close-total | The total number of connections closed | client-id |  |  |  |  |
| consumer-metrics | connection-count | The current number of active connections. | client-id |  |  |  |  |
| consumer-metrics | connection-creation-rate | The number of new connections established per second | client-id |  |  |  |  |
| consumer-metrics | connection-creation-total | The total number of new connections established | client-id |  |  |  |  |
| consumer-metrics | failed-authentication-rate | The number of connections with failed authentication per second | client-id |  |  |  |  |
| consumer-metrics | failed-authentication-total | The total number of connections with failed authentication | client-id |  |  |  |  |
| consumer-metrics | failed-reauthentication-rate | The number of failed re-authentication of connections per second | client-id |  |  |  |  |
| consumer-metrics | failed-reauthentication-total | The total number of failed re-authentication of connections | client-id |  |  |  |  |
| consumer-metrics | incoming-byte-rate | The number of bytes read off all sockets per second | client-id |  |  |  |  |
| consumer-metrics | incoming-byte-total | The total number of bytes read off all sockets | client-id |  |  |  |  |
| consumer-metrics | io-ratio | The fraction of time the I/O thread spent doing I/O | client-id |  |  |  |  |
| consumer-metrics | io-time-ns-avg | The average length of time for I/O per select call in nanoseconds. | client-id |  |  |  |  |
| consumer-metrics | io-wait-ratio | The fraction of time the I/O thread spent waiting | client-id |  |  |  |  |
| consumer-metrics | io-wait-time-ns-avg | The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds. | client-id |  |  |  |  |
| consumer-metrics | io-waittime-total | The total time the I/O thread spent waiting | client-id |  |  |  |  |
| consumer-metrics | iotime-total | The total time the I/O thread spent doing I/O | client-id |  |  |  |  |
| consumer-metrics | last-poll-seconds-ago | The number of seconds since the last poll() invocation. | client-id |  |  |  |  |
| consumer-metrics | network-io-rate | The number of network operations (reads or writes) on all connections per second | client-id |  |  |  |  |
| consumer-metrics | network-io-total | The total number of network operations (reads or writes) on all connections | client-id |  |  |  |  |
| consumer-metrics | outgoing-byte-rate | The number of outgoing bytes sent to all servers per second | client-id |  |  |  |  |
| consumer-metrics | outgoing-byte-total | The total number of outgoing bytes sent to all servers | client-id |  |  |  |  |
| consumer-metrics | poll-idle-ratio-avg | The average fraction of time the consumer's poll() is idle as opposed to waiting for the user code to process records. | client-id |  |  |  |  |
| consumer-metrics | reauthentication-latency-avg | The average latency observed due to re-authentication | client-id |  |  |  |  |
| consumer-metrics | reauthentication-latency-max | The max latency observed due to re-authentication | client-id |  |  |  |  |
| consumer-metrics | request-rate | The number of requests sent per second | client-id |  |  |  |  |
| consumer-metrics | request-size-avg | The average size of requests sent. | client-id |  |  |  |  |
| consumer-metrics | request-size-max | The maximum size of any request sent. | client-id |  |  |  |  |
| consumer-metrics | request-total | The total number of requests sent | client-id |  |  |  |  |
| consumer-metrics | response-rate | The number of responses received per second | client-id |  |  |  |  |
| consumer-metrics | response-total | The total number of responses received | client-id |  |  |  |  |
| consumer-metrics | select-rate | The number of times the I/O layer checked for new I/O to perform per second | client-id |  |  |  |  |
| consumer-metrics | select-total | The total number of times the I/O layer checked for new I/O to perform | client-id |  |  |  |  |
| consumer-metrics | successful-authentication-no-reauth-total | The total number of connections with successful authentication where the client does not support re-authentication | client-id |  |  |  |  |
| consumer-metrics | successful-authentication-rate | The number of connections with successful authentication per second | client-id |  |  |  |  |
| consumer-metrics | successful-authentication-total | The total number of connections with successful authentication | client-id |  |  |  |  |
| consumer-metrics | successful-reauthentication-rate | The number of successful re-authentication of connections per second | client-id |  |  |  |  |
| consumer-metrics | successful-reauthentication-total | The total number of successful re-authentication of connections | client-id |  |  |  |  |
| consumer-metrics | time-between-poll-avg | The average delay between invocations of poll(). | client-id |  |  |  |  |
| consumer-metrics | time-between-poll-max | The max delay between invocations of poll(). | client-id |  |  |  |  |
| consumer-node-metrics | incoming-byte-rate | The number of incoming bytes per second | client-id,node-id |  |  |  |  |
| consumer-node-metrics | incoming-byte-total | The total number of incoming bytes | client-id,node-id |  |  |  |  |
| consumer-node-metrics | outgoing-byte-rate | The number of outgoing bytes per second | client-id,node-id |  |  |  |  |
| consumer-node-metrics | outgoing-byte-total | The total number of outgoing bytes | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-latency-avg |  | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-latency-max |  | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-rate | The number of requests sent per second | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-size-avg | The average size of requests sent. | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-size-max | The maximum size of any request sent. | client-id,node-id |  |  |  |  |
| consumer-node-metrics | request-total | The total number of requests sent | client-id,node-id |  |  |  |  |
| consumer-node-metrics | response-rate | The number of responses received per second | client-id,node-id |  |  |  |  |
| consumer-node-metrics | response-total | The total number of responses received | client-id,node-id |  |  |  |  |
| kafka-metrics-count | count | total number of registered metrics | client-id |  |  |  |  |
| producer-metrics | batch-size-avg | The average number of bytes sent per partition per-request. | client-id |  |  |  |  |
| producer-metrics | batch-size-max | The max number of bytes sent per partition per-request. | client-id |  |  |  |  |
| producer-metrics | batch-split-rate | The average number of batch splits per second | client-id |  |  |  |  |
| producer-metrics | batch-split-total | The total number of batch splits | client-id |  |  |  |  |
| producer-metrics | buffer-available-bytes | The total amount of buffer memory that is not being used (either unallocated or in the free list). | client-id |  |  |  |  |
| producer-metrics | buffer-exhausted-rate | The average per-second number of record sends that are dropped due to buffer exhaustion | client-id |  |  |  |  |
| producer-metrics | buffer-exhausted-total | The total number of record sends that are dropped due to buffer exhaustion | client-id |  |  |  |  |
| producer-metrics | buffer-total-bytes | The maximum amount of buffer memory the client can use (whether or not it is currently used). | client-id |  |  |  |  |
| producer-metrics | bufferpool-wait-ratio | The fraction of time an appender waits for space allocation. | client-id |  |  |  |  |
| producer-metrics | bufferpool-wait-time-total | The total time an appender waits for space allocation. | client-id |  |  |  |  |
| producer-metrics | compression-rate-avg | The average compression rate of record batches, defined as the average ratio of the compressed batch size over the uncompressed size. | client-id |  |  |  |  |
| producer-metrics | connection-close-rate | The number of connections closed per second | client-id |  |  |  |  |
| producer-metrics | connection-close-total | The total number of connections closed | client-id |  |  |  |  |
| producer-metrics | connection-count | The current number of active connections. | client-id |  |  |  |  |
| producer-metrics | connection-creation-rate | The number of new connections established per second | client-id |  |  |  |  |
| producer-metrics | connection-creation-total | The total number of new connections established | client-id |  |  |  |  |
| producer-metrics | failed-authentication-rate | The number of connections with failed authentication per second | client-id |  |  |  |  |
| producer-metrics | failed-authentication-total | The total number of connections with failed authentication | client-id |  |  |  |  |
| producer-metrics | failed-reauthentication-rate | The number of failed re-authentication of connections per second | client-id |  |  |  |  |
| producer-metrics | failed-reauthentication-total | The total number of failed re-authentication of connections | client-id |  |  |  |  |
| producer-metrics | incoming-byte-rate | The number of bytes read off all sockets per second | client-id |  |  |  |  |
| producer-metrics | incoming-byte-total | The total number of bytes read off all sockets | client-id |  |  |  |  |
| producer-metrics | io-ratio | The fraction of time the I/O thread spent doing I/O | client-id |  |  |  |  |
| producer-metrics | io-time-ns-avg | The average length of time for I/O per select call in nanoseconds. | client-id |  |  |  |  |
| producer-metrics | io-wait-ratio | The fraction of time the I/O thread spent waiting | client-id |  |  |  |  |
| producer-metrics | io-wait-time-ns-avg | The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds. | client-id |  |  |  |  |
| producer-metrics | io-waittime-total | The total time the I/O thread spent waiting | client-id |  |  |  |  |
| producer-metrics | iotime-total | The total time the I/O thread spent doing I/O | client-id |  |  |  |  |
| producer-metrics | metadata-age | The age in seconds of the current producer metadata being used. | client-id |  |  |  |  |
| producer-metrics | network-io-rate | The number of network operations (reads or writes) on all connections per second | client-id |  |  |  |  |
| producer-metrics | network-io-total | The total number of network operations (reads or writes) on all connections | client-id |  |  |  |  |
| producer-metrics | outgoing-byte-rate | The number of outgoing bytes sent to all servers per second | client-id | messaging.kafka.producer.outgoing-bytes.rate | The average number of outgoing bytes sent per second to all servers. | by/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-metrics | outgoing-byte-total | The total number of outgoing bytes sent to all servers | client-id |  |  |  |  |
| producer-metrics | produce-throttle-time-avg | The average time in ms a request was throttled by a broker | client-id |  |  |  |  |
| producer-metrics | produce-throttle-time-max | The maximum time in ms a request was throttled by a broker | client-id |  |  |  |  |
| producer-metrics | reauthentication-latency-avg | The average latency observed due to re-authentication | client-id |  |  |  |  |
| producer-metrics | reauthentication-latency-max | The max latency observed due to re-authentication | client-id |  |  |  |  |
| producer-metrics | record-error-rate | The average per-second number of record sends that resulted in errors | client-id |  |  |  |  |
| producer-metrics | record-error-total | The total number of record sends that resulted in errors | client-id |  |  |  |  |
| producer-metrics | record-queue-time-avg | The average time in ms record batches spent in the send buffer. | client-id |  |  |  |  |
| producer-metrics | record-queue-time-max | The maximum time in ms record batches spent in the send buffer. | client-id |  |  |  |  |
| producer-metrics | record-retry-rate | The average per-second number of retried record sends | client-id |  |  |  |  |
| producer-metrics | record-retry-total | The total number of retried record sends | client-id |  |  |  |  |
| producer-metrics | record-send-rate | The average number of records sent per second. | client-id |  |  |  |  |
| producer-metrics | record-send-total | The total number of records sent. | client-id |  |  |  |  |
| producer-metrics | record-size-avg | The average record size | client-id |  |  |  |  |
| producer-metrics | record-size-max | The maximum record size | client-id |  |  |  |  |
| producer-metrics | records-per-request-avg | The average number of records per request. | client-id |  |  |  |  |
| producer-metrics | request-latency-avg | The average request latency in ms | client-id |  |  |  |  |
| producer-metrics | request-latency-max | The maximum request latency in ms | client-id |  |  |  |  |
| producer-metrics | request-rate | The number of requests sent per second | client-id |  |  |  |  |
| producer-metrics | request-size-avg | The average size of requests sent. | client-id |  |  |  |  |
| producer-metrics | request-size-max | The maximum size of any request sent. | client-id |  |  |  |  |
| producer-metrics | request-total | The total number of requests sent | client-id |  |  |  |  |
| producer-metrics | requests-in-flight | The current number of in-flight requests awaiting a response. | client-id |  |  |  |  |
| producer-metrics | response-rate | The number of responses received per second | client-id | messaging.kafka.producer.responses.rate | The average number of responses received per second. | {responses}/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-metrics | response-total | The total number of responses received | client-id |  |  |  |  |
| producer-metrics | select-rate | The number of times the I/O layer checked for new I/O to perform per second | client-id |  |  |  |  |
| producer-metrics | select-total | The total number of times the I/O layer checked for new I/O to perform | client-id |  |  |  |  |
| producer-metrics | successful-authentication-no-reauth-total | The total number of connections with successful authentication where the client does not support re-authentication | client-id |  |  |  |  |
| producer-metrics | successful-authentication-rate | The number of connections with successful authentication per second | client-id |  |  |  |  |
| producer-metrics | successful-authentication-total | The total number of connections with successful authentication | client-id |  |  |  |  |
| producer-metrics | successful-reauthentication-rate | The number of successful re-authentication of connections per second | client-id |  |  |  |  |
| producer-metrics | successful-reauthentication-total | The total number of successful re-authentication of connections | client-id |  |  |  |  |
| producer-metrics | waiting-threads | The number of user threads blocked waiting for buffer memory to enqueue their records | client-id |  |  |  |  |
| producer-node-metrics | incoming-byte-rate | The number of incoming bytes per second | client-id,node-id |  |  |  |  |
| producer-node-metrics | incoming-byte-total | The total number of incoming bytes | client-id,node-id |  |  |  |  |
| producer-node-metrics | outgoing-byte-rate | The number of outgoing bytes per second | client-id,node-id |  |  |  |  |
| producer-node-metrics | outgoing-byte-total | The total number of outgoing bytes | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-latency-avg |  | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-latency-max |  | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-rate | The number of requests sent per second | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-size-avg | The average size of requests sent. | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-size-max | The maximum size of any request sent. | client-id,node-id |  |  |  |  |
| producer-node-metrics | request-total | The total number of requests sent | client-id,node-id |  |  |  |  |
| producer-node-metrics | response-rate | The number of responses received per second | client-id,node-id |  |  |  |  |
| producer-node-metrics | response-total | The total number of responses received | client-id,node-id |  |  |  |  |
| producer-topic-metrics | byte-rate | The average number of bytes sent per second for a topic. | client-id,topic | messaging.kafka.producer.bytes.rate | The average number of bytes sent per second for a specific topic. | by/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-topic-metrics | byte-total | The total number of bytes sent for a topic. | client-id,topic |  |  |  |  |
| producer-topic-metrics | compression-rate | The average compression rate of record batches for a topic, defined as the average ratio of the compressed batch size over the uncompressed size. | client-id,topic | messaging.kafka.producer.compression-ratio | The average compression ratio of record batches for a specific topic. | {compression} | DOUBLE_OBSERVABLE_GAUGE |
| producer-topic-metrics | record-error-rate | The average per-second number of record sends that resulted in errors for a topic | client-id,topic | messaging.kafka.producer.record-error.rate | The average per-second number of record sends that resulted in errors for a specific topic. | {errors}/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-topic-metrics | record-error-total | The total number of record sends that resulted in errors for a topic | client-id,topic |  |  |  |  |
| producer-topic-metrics | record-retry-rate | The average per-second number of retried record sends for a topic | client-id,topic | messaging.kafka.producer.record-retry.rate | The average per-second number of retried record sends for a specific topic. | {retries}/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-topic-metrics | record-retry-total | The total number of retried record sends for a topic | client-id,topic |  |  |  |  |
| producer-topic-metrics | record-send-rate | The average number of records sent per second for a topic. | client-id,topic | messaging.kafka.producer.record-sent.rate | The average number of records sent per second for a specific topic. | {records_sent}/s | DOUBLE_OBSERVABLE_GAUGE |
| producer-topic-metrics | record-send-total | The total number of records sent for a topic. | client-id,topic |  |  |  |  |