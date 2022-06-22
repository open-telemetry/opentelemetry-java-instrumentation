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

Note: Kafka reports several metrics at multiple attribute granularities. For
example, `records-consumed-total` is reported with attribute key `[client-id]`
and `[client-id, topic]`. If you analyze the sum of records consumed, ignoring dimensions, backends
are likely to double count. To alleviate this, `OpenTelemetryKafkaMetrics` detects this
scenario and only records the most granular set of attributes available. In the case
of `records-consumed-total`, it reports `[client-id, topic]` and ignores `[client-id]`.

The following table shows the full set of metrics exposed by the kafka client, and the corresponding
OpenTelemetry metric each maps to (if available). Empty values in the Instrument Name, Instrument
Description, etc column indicates there is no registered mapping for the metric and data is NOT
collected.

| # | Metric Group | Metric Name | Attribute Keys | Instrument Name | Instrument Description | Instrument Type |
|---|--------------|-------------|----------------|-----------------|------------------------|-----------------|
| 1 | `app-info` | `commit-id` | `client-id` |  |  |  |
| 2 | `app-info` | `start-time-ms` | `client-id` |  |  |  |
| 3 | `app-info` | `version` | `client-id` |  |  |  |
| 4 | `consumer-coordinator-metrics` | `assigned-partitions` | `client-id` | `kafka.consumer.assigned_partitions` | The number of partitions currently assigned to this consumer | `DOUBLE_OBSERVABLE_GAUGE` |
| 5 | `consumer-coordinator-metrics` | `commit-latency-avg` | `client-id` | `kafka.consumer.commit_latency_avg` | The average time taken for a commit request | `DOUBLE_OBSERVABLE_GAUGE` |
| 6 | `consumer-coordinator-metrics` | `commit-latency-max` | `client-id` | `kafka.consumer.commit_latency_max` | The max time taken for a commit request | `DOUBLE_OBSERVABLE_GAUGE` |
| 7 | `consumer-coordinator-metrics` | `commit-rate` | `client-id` | `kafka.consumer.commit_rate` | The number of commit calls per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 8 | `consumer-coordinator-metrics` | `commit-total` | `client-id` | `kafka.consumer.commit_total` | The total number of commit calls | `DOUBLE_OBSERVABLE_COUNTER` |
| 9 | `consumer-coordinator-metrics` | `failed-rebalance-rate-per-hour` | `client-id` | `kafka.consumer.failed_rebalance_rate_per_hour` | The number of failed rebalance events per hour | `DOUBLE_OBSERVABLE_GAUGE` |
| 10 | `consumer-coordinator-metrics` | `failed-rebalance-total` | `client-id` | `kafka.consumer.failed_rebalance_total` | The total number of failed rebalance events | `DOUBLE_OBSERVABLE_COUNTER` |
| 11 | `consumer-coordinator-metrics` | `heartbeat-rate` | `client-id` | `kafka.consumer.heartbeat_rate` | The number of heartbeats per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 12 | `consumer-coordinator-metrics` | `heartbeat-response-time-max` | `client-id` | `kafka.consumer.heartbeat_response_time_max` | The max time taken to receive a response to a heartbeat request | `DOUBLE_OBSERVABLE_GAUGE` |
| 13 | `consumer-coordinator-metrics` | `heartbeat-total` | `client-id` | `kafka.consumer.heartbeat_total` | The total number of heartbeats | `DOUBLE_OBSERVABLE_COUNTER` |
| 14 | `consumer-coordinator-metrics` | `join-rate` | `client-id` | `kafka.consumer.join_rate` | The number of group joins per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 15 | `consumer-coordinator-metrics` | `join-time-avg` | `client-id` | `kafka.consumer.join_time_avg` | The average time taken for a group rejoin | `DOUBLE_OBSERVABLE_GAUGE` |
| 16 | `consumer-coordinator-metrics` | `join-time-max` | `client-id` | `kafka.consumer.join_time_max` | The max time taken for a group rejoin | `DOUBLE_OBSERVABLE_GAUGE` |
| 17 | `consumer-coordinator-metrics` | `join-total` | `client-id` | `kafka.consumer.join_total` | The total number of group joins | `DOUBLE_OBSERVABLE_COUNTER` |
| 18 | `consumer-coordinator-metrics` | `last-heartbeat-seconds-ago` | `client-id` | `kafka.consumer.last_heartbeat_seconds_ago` | The number of seconds since the last coordinator heartbeat was sent | `DOUBLE_OBSERVABLE_GAUGE` |
| 19 | `consumer-coordinator-metrics` | `last-rebalance-seconds-ago` | `client-id` | `kafka.consumer.last_rebalance_seconds_ago` | The number of seconds since the last successful rebalance event | `DOUBLE_OBSERVABLE_GAUGE` |
| 20 | `consumer-coordinator-metrics` | `partition-assigned-latency-avg` | `client-id` | `kafka.consumer.partition_assigned_latency_avg` | The average time taken for a partition-assigned rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 21 | `consumer-coordinator-metrics` | `partition-assigned-latency-max` | `client-id` | `kafka.consumer.partition_assigned_latency_max` | The max time taken for a partition-assigned rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 22 | `consumer-coordinator-metrics` | `partition-lost-latency-avg` | `client-id` | `kafka.consumer.partition_lost_latency_avg` | The average time taken for a partition-lost rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 23 | `consumer-coordinator-metrics` | `partition-lost-latency-max` | `client-id` | `kafka.consumer.partition_lost_latency_max` | The max time taken for a partition-lost rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 24 | `consumer-coordinator-metrics` | `partition-revoked-latency-avg` | `client-id` | `kafka.consumer.partition_revoked_latency_avg` | The average time taken for a partition-revoked rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 25 | `consumer-coordinator-metrics` | `partition-revoked-latency-max` | `client-id` | `kafka.consumer.partition_revoked_latency_max` | The max time taken for a partition-revoked rebalance listener callback | `DOUBLE_OBSERVABLE_GAUGE` |
| 26 | `consumer-coordinator-metrics` | `rebalance-latency-avg` | `client-id` | `kafka.consumer.rebalance_latency_avg` | The average time taken for a group to complete a successful rebalance, which may be composed of several failed re-trials until it succeeded | `DOUBLE_OBSERVABLE_GAUGE` |
| 27 | `consumer-coordinator-metrics` | `rebalance-latency-max` | `client-id` | `kafka.consumer.rebalance_latency_max` | The max time taken for a group to complete a successful rebalance, which may be composed of several failed re-trials until it succeeded | `DOUBLE_OBSERVABLE_GAUGE` |
| 28 | `consumer-coordinator-metrics` | `rebalance-latency-total` | `client-id` | `kafka.consumer.rebalance_latency_total` | The total number of milliseconds this consumer has spent in successful rebalances since creation | `DOUBLE_OBSERVABLE_COUNTER` |
| 29 | `consumer-coordinator-metrics` | `rebalance-rate-per-hour` | `client-id` | `kafka.consumer.rebalance_rate_per_hour` | The number of successful rebalance events per hour, each event is composed of several failed re-trials until it succeeded | `DOUBLE_OBSERVABLE_GAUGE` |
| 30 | `consumer-coordinator-metrics` | `rebalance-total` | `client-id` | `kafka.consumer.rebalance_total` | The total number of successful rebalance events, each event is composed of several failed re-trials until it succeeded | `DOUBLE_OBSERVABLE_COUNTER` |
| 31 | `consumer-coordinator-metrics` | `sync-rate` | `client-id` | `kafka.consumer.sync_rate` | The number of group syncs per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 32 | `consumer-coordinator-metrics` | `sync-time-avg` | `client-id` | `kafka.consumer.sync_time_avg` | The average time taken for a group sync | `DOUBLE_OBSERVABLE_GAUGE` |
| 33 | `consumer-coordinator-metrics` | `sync-time-max` | `client-id` | `kafka.consumer.sync_time_max` | The max time taken for a group sync | `DOUBLE_OBSERVABLE_GAUGE` |
| 34 | `consumer-coordinator-metrics` | `sync-total` | `client-id` | `kafka.consumer.sync_total` | The total number of group syncs | `DOUBLE_OBSERVABLE_COUNTER` |
| 35 | `consumer-fetch-manager-metrics` | `bytes-consumed-rate` | `client-id` |  |  |  |
| 36 | `consumer-fetch-manager-metrics` | `bytes-consumed-rate` | `client-id`,`topic` | `kafka.consumer.bytes_consumed_rate` | The average number of bytes consumed per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 37 | `consumer-fetch-manager-metrics` | `bytes-consumed-total` | `client-id` |  |  |  |
| 38 | `consumer-fetch-manager-metrics` | `bytes-consumed-total` | `client-id`,`topic` | `kafka.consumer.bytes_consumed_total` | The total number of bytes consumed | `DOUBLE_OBSERVABLE_COUNTER` |
| 39 | `consumer-fetch-manager-metrics` | `fetch-latency-avg` | `client-id` | `kafka.consumer.fetch_latency_avg` | The average time taken for a fetch request. | `DOUBLE_OBSERVABLE_GAUGE` |
| 40 | `consumer-fetch-manager-metrics` | `fetch-latency-max` | `client-id` | `kafka.consumer.fetch_latency_max` | The max time taken for any fetch request. | `DOUBLE_OBSERVABLE_GAUGE` |
| 41 | `consumer-fetch-manager-metrics` | `fetch-rate` | `client-id` | `kafka.consumer.fetch_rate` | The number of fetch requests per second. | `DOUBLE_OBSERVABLE_GAUGE` |
| 42 | `consumer-fetch-manager-metrics` | `fetch-size-avg` | `client-id`,`topic` | `kafka.consumer.fetch_size_avg` | The average number of bytes fetched per request | `DOUBLE_OBSERVABLE_GAUGE` |
| 43 | `consumer-fetch-manager-metrics` | `fetch-size-avg` | `client-id` |  |  |  |
| 44 | `consumer-fetch-manager-metrics` | `fetch-size-max` | `client-id`,`topic` | `kafka.consumer.fetch_size_max` | The maximum number of bytes fetched per request | `DOUBLE_OBSERVABLE_GAUGE` |
| 45 | `consumer-fetch-manager-metrics` | `fetch-size-max` | `client-id` |  |  |  |
| 46 | `consumer-fetch-manager-metrics` | `fetch-throttle-time-avg` | `client-id` | `kafka.consumer.fetch_throttle_time_avg` | The average throttle time in ms | `DOUBLE_OBSERVABLE_GAUGE` |
| 47 | `consumer-fetch-manager-metrics` | `fetch-throttle-time-max` | `client-id` | `kafka.consumer.fetch_throttle_time_max` | The maximum throttle time in ms | `DOUBLE_OBSERVABLE_GAUGE` |
| 48 | `consumer-fetch-manager-metrics` | `fetch-total` | `client-id` | `kafka.consumer.fetch_total` | The total number of fetch requests. | `DOUBLE_OBSERVABLE_COUNTER` |
| 49 | `consumer-fetch-manager-metrics` | `preferred-read-replica` | `client-id`,`topic`,`partition` |  |  |  |
| 50 | `consumer-fetch-manager-metrics` | `records-consumed-rate` | `client-id`,`topic` | `kafka.consumer.records_consumed_rate` | The average number of records consumed per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 51 | `consumer-fetch-manager-metrics` | `records-consumed-rate` | `client-id` |  |  |  |
| 52 | `consumer-fetch-manager-metrics` | `records-consumed-total` | `client-id` |  |  |  |
| 53 | `consumer-fetch-manager-metrics` | `records-consumed-total` | `client-id`,`topic` | `kafka.consumer.records_consumed_total` | The total number of records consumed | `DOUBLE_OBSERVABLE_COUNTER` |
| 54 | `consumer-fetch-manager-metrics` | `records-lag` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lag` | The latest lag of the partition | `DOUBLE_OBSERVABLE_GAUGE` |
| 55 | `consumer-fetch-manager-metrics` | `records-lag-avg` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lag_avg` | The average lag of the partition | `DOUBLE_OBSERVABLE_GAUGE` |
| 56 | `consumer-fetch-manager-metrics` | `records-lag-max` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lag_max` | The maximum lag in terms of number of records for any partition in this window | `DOUBLE_OBSERVABLE_GAUGE` |
| 57 | `consumer-fetch-manager-metrics` | `records-lag-max` | `client-id` |  |  |  |
| 58 | `consumer-fetch-manager-metrics` | `records-lead` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lead` | The latest lead of the partition | `DOUBLE_OBSERVABLE_GAUGE` |
| 59 | `consumer-fetch-manager-metrics` | `records-lead-avg` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lead_avg` | The average lead of the partition | `DOUBLE_OBSERVABLE_GAUGE` |
| 60 | `consumer-fetch-manager-metrics` | `records-lead-min` | `client-id` |  |  |  |
| 61 | `consumer-fetch-manager-metrics` | `records-lead-min` | `client-id`,`topic`,`partition` | `kafka.consumer.records_lead_min` | The minimum lead in terms of number of records for any partition in this window | `DOUBLE_OBSERVABLE_GAUGE` |
| 62 | `consumer-fetch-manager-metrics` | `records-per-request-avg` | `client-id` |  |  |  |
| 63 | `consumer-fetch-manager-metrics` | `records-per-request-avg` | `client-id`,`topic` | `kafka.consumer.records_per_request_avg` | The average number of records in each request | `DOUBLE_OBSERVABLE_GAUGE` |
| 64 | `consumer-metrics` | `connection-close-rate` | `client-id` | `kafka.consumer.connection_close_rate` | The number of connections closed per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 65 | `consumer-metrics` | `connection-close-total` | `client-id` | `kafka.consumer.connection_close_total` | The total number of connections closed | `DOUBLE_OBSERVABLE_COUNTER` |
| 66 | `consumer-metrics` | `connection-count` | `client-id` | `kafka.consumer.connection_count` | The current number of active connections. | `DOUBLE_OBSERVABLE_GAUGE` |
| 67 | `consumer-metrics` | `connection-creation-rate` | `client-id` | `kafka.consumer.connection_creation_rate` | The number of new connections established per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 68 | `consumer-metrics` | `connection-creation-total` | `client-id` | `kafka.consumer.connection_creation_total` | The total number of new connections established | `DOUBLE_OBSERVABLE_COUNTER` |
| 69 | `consumer-metrics` | `failed-authentication-rate` | `client-id` | `kafka.consumer.failed_authentication_rate` | The number of connections with failed authentication per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 70 | `consumer-metrics` | `failed-authentication-total` | `client-id` | `kafka.consumer.failed_authentication_total` | The total number of connections with failed authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 71 | `consumer-metrics` | `failed-reauthentication-rate` | `client-id` | `kafka.consumer.failed_reauthentication_rate` | The number of failed re-authentication of connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 72 | `consumer-metrics` | `failed-reauthentication-total` | `client-id` | `kafka.consumer.failed_reauthentication_total` | The total number of failed re-authentication of connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 73 | `consumer-metrics` | `incoming-byte-rate` | `client-id` |  |  |  |
| 74 | `consumer-metrics` | `incoming-byte-total` | `client-id` |  |  |  |
| 75 | `consumer-metrics` | `io-ratio` | `client-id` | `kafka.consumer.io_ratio` | The fraction of time the I/O thread spent doing I/O | `DOUBLE_OBSERVABLE_GAUGE` |
| 76 | `consumer-metrics` | `io-time-ns-avg` | `client-id` | `kafka.consumer.io_time_ns_avg` | The average length of time for I/O per select call in nanoseconds. | `DOUBLE_OBSERVABLE_GAUGE` |
| 77 | `consumer-metrics` | `io-wait-ratio` | `client-id` | `kafka.consumer.io_wait_ratio` | The fraction of time the I/O thread spent waiting | `DOUBLE_OBSERVABLE_GAUGE` |
| 78 | `consumer-metrics` | `io-wait-time-ns-avg` | `client-id` | `kafka.consumer.io_wait_time_ns_avg` | The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds. | `DOUBLE_OBSERVABLE_GAUGE` |
| 79 | `consumer-metrics` | `io-waittime-total` | `client-id` | `kafka.consumer.io_waittime_total` | The total time the I/O thread spent waiting | `DOUBLE_OBSERVABLE_COUNTER` |
| 80 | `consumer-metrics` | `iotime-total` | `client-id` | `kafka.consumer.iotime_total` | The total time the I/O thread spent doing I/O | `DOUBLE_OBSERVABLE_COUNTER` |
| 81 | `consumer-metrics` | `last-poll-seconds-ago` | `client-id` | `kafka.consumer.last_poll_seconds_ago` | The number of seconds since the last poll() invocation. | `DOUBLE_OBSERVABLE_GAUGE` |
| 82 | `consumer-metrics` | `network-io-rate` | `client-id` | `kafka.consumer.network_io_rate` | The number of network operations (reads or writes) on all connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 83 | `consumer-metrics` | `network-io-total` | `client-id` | `kafka.consumer.network_io_total` | The total number of network operations (reads or writes) on all connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 84 | `consumer-metrics` | `outgoing-byte-rate` | `client-id` |  |  |  |
| 85 | `consumer-metrics` | `outgoing-byte-total` | `client-id` |  |  |  |
| 86 | `consumer-metrics` | `poll-idle-ratio-avg` | `client-id` | `kafka.consumer.poll_idle_ratio_avg` | The average fraction of time the consumer's poll() is idle as opposed to waiting for the user code to process records. | `DOUBLE_OBSERVABLE_GAUGE` |
| 87 | `consumer-metrics` | `reauthentication-latency-avg` | `client-id` | `kafka.consumer.reauthentication_latency_avg` | The average latency observed due to re-authentication | `DOUBLE_OBSERVABLE_GAUGE` |
| 88 | `consumer-metrics` | `reauthentication-latency-max` | `client-id` | `kafka.consumer.reauthentication_latency_max` | The max latency observed due to re-authentication | `DOUBLE_OBSERVABLE_GAUGE` |
| 89 | `consumer-metrics` | `request-rate` | `client-id` |  |  |  |
| 90 | `consumer-metrics` | `request-size-avg` | `client-id` |  |  |  |
| 91 | `consumer-metrics` | `request-size-max` | `client-id` |  |  |  |
| 92 | `consumer-metrics` | `request-total` | `client-id` |  |  |  |
| 93 | `consumer-metrics` | `response-rate` | `client-id` |  |  |  |
| 94 | `consumer-metrics` | `response-total` | `client-id` |  |  |  |
| 95 | `consumer-metrics` | `select-rate` | `client-id` | `kafka.consumer.select_rate` | The number of times the I/O layer checked for new I/O to perform per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 96 | `consumer-metrics` | `select-total` | `client-id` | `kafka.consumer.select_total` | The total number of times the I/O layer checked for new I/O to perform | `DOUBLE_OBSERVABLE_COUNTER` |
| 97 | `consumer-metrics` | `successful-authentication-no-reauth-total` | `client-id` | `kafka.consumer.successful_authentication_no_reauth_total` | The total number of connections with successful authentication where the client does not support re-authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 98 | `consumer-metrics` | `successful-authentication-rate` | `client-id` | `kafka.consumer.successful_authentication_rate` | The number of connections with successful authentication per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 99 | `consumer-metrics` | `successful-authentication-total` | `client-id` | `kafka.consumer.successful_authentication_total` | The total number of connections with successful authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 100 | `consumer-metrics` | `successful-reauthentication-rate` | `client-id` | `kafka.consumer.successful_reauthentication_rate` | The number of successful re-authentication of connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 101 | `consumer-metrics` | `successful-reauthentication-total` | `client-id` | `kafka.consumer.successful_reauthentication_total` | The total number of successful re-authentication of connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 102 | `consumer-metrics` | `time-between-poll-avg` | `client-id` | `kafka.consumer.time_between_poll_avg` | The average delay between invocations of poll(). | `DOUBLE_OBSERVABLE_GAUGE` |
| 103 | `consumer-metrics` | `time-between-poll-max` | `client-id` | `kafka.consumer.time_between_poll_max` | The max delay between invocations of poll(). | `DOUBLE_OBSERVABLE_GAUGE` |
| 104 | `consumer-node-metrics` | `incoming-byte-rate` | `client-id`,`node-id` | `kafka.consumer.incoming_byte_rate` | The number of bytes read off all sockets per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 105 | `consumer-node-metrics` | `incoming-byte-total` | `client-id`,`node-id` | `kafka.consumer.incoming_byte_total` | The total number of bytes read off all sockets | `DOUBLE_OBSERVABLE_COUNTER` |
| 106 | `consumer-node-metrics` | `outgoing-byte-rate` | `client-id`,`node-id` | `kafka.consumer.outgoing_byte_rate` | The number of outgoing bytes sent to all servers per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 107 | `consumer-node-metrics` | `outgoing-byte-total` | `client-id`,`node-id` | `kafka.consumer.outgoing_byte_total` | The total number of outgoing bytes sent to all servers | `DOUBLE_OBSERVABLE_COUNTER` |
| 108 | `consumer-node-metrics` | `request-latency-avg` | `client-id`,`node-id` | `kafka.consumer.request_latency_avg` |  | `DOUBLE_OBSERVABLE_GAUGE` |
| 109 | `consumer-node-metrics` | `request-latency-max` | `client-id`,`node-id` | `kafka.consumer.request_latency_max` |  | `DOUBLE_OBSERVABLE_GAUGE` |
| 110 | `consumer-node-metrics` | `request-rate` | `client-id`,`node-id` | `kafka.consumer.request_rate` | The number of requests sent per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 111 | `consumer-node-metrics` | `request-size-avg` | `client-id`,`node-id` | `kafka.consumer.request_size_avg` | The average size of requests sent. | `DOUBLE_OBSERVABLE_GAUGE` |
| 112 | `consumer-node-metrics` | `request-size-max` | `client-id`,`node-id` | `kafka.consumer.request_size_max` | The maximum size of any request sent. | `DOUBLE_OBSERVABLE_GAUGE` |
| 113 | `consumer-node-metrics` | `request-total` | `client-id`,`node-id` | `kafka.consumer.request_total` | The total number of requests sent | `DOUBLE_OBSERVABLE_COUNTER` |
| 114 | `consumer-node-metrics` | `response-rate` | `client-id`,`node-id` | `kafka.consumer.response_rate` | The number of responses received per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 115 | `consumer-node-metrics` | `response-total` | `client-id`,`node-id` | `kafka.consumer.response_total` | The total number of responses received | `DOUBLE_OBSERVABLE_COUNTER` |
| 116 | `kafka-metrics-count` | `count` | `client-id` |  |  |  |
| 117 | `producer-metrics` | `batch-size-avg` | `client-id` | `kafka.producer.batch_size_avg` | The average number of bytes sent per partition per-request. | `DOUBLE_OBSERVABLE_GAUGE` |
| 118 | `producer-metrics` | `batch-size-max` | `client-id` | `kafka.producer.batch_size_max` | The max number of bytes sent per partition per-request. | `DOUBLE_OBSERVABLE_GAUGE` |
| 119 | `producer-metrics` | `batch-split-rate` | `client-id` | `kafka.producer.batch_split_rate` | The average number of batch splits per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 120 | `producer-metrics` | `batch-split-total` | `client-id` | `kafka.producer.batch_split_total` | The total number of batch splits | `DOUBLE_OBSERVABLE_COUNTER` |
| 121 | `producer-metrics` | `buffer-available-bytes` | `client-id` | `kafka.producer.buffer_available_bytes` | The total amount of buffer memory that is not being used (either unallocated or in the free list). | `DOUBLE_OBSERVABLE_GAUGE` |
| 122 | `producer-metrics` | `buffer-exhausted-rate` | `client-id` | `kafka.producer.buffer_exhausted_rate` | The average per-second number of record sends that are dropped due to buffer exhaustion | `DOUBLE_OBSERVABLE_GAUGE` |
| 123 | `producer-metrics` | `buffer-exhausted-total` | `client-id` | `kafka.producer.buffer_exhausted_total` | The total number of record sends that are dropped due to buffer exhaustion | `DOUBLE_OBSERVABLE_COUNTER` |
| 124 | `producer-metrics` | `buffer-total-bytes` | `client-id` | `kafka.producer.buffer_total_bytes` | The maximum amount of buffer memory the client can use (whether or not it is currently used). | `DOUBLE_OBSERVABLE_GAUGE` |
| 125 | `producer-metrics` | `bufferpool-wait-ratio` | `client-id` | `kafka.producer.bufferpool_wait_ratio` | The fraction of time an appender waits for space allocation. | `DOUBLE_OBSERVABLE_GAUGE` |
| 126 | `producer-metrics` | `bufferpool-wait-time-total` | `client-id` | `kafka.producer.bufferpool_wait_time_total` | The total time an appender waits for space allocation. | `DOUBLE_OBSERVABLE_COUNTER` |
| 127 | `producer-metrics` | `compression-rate-avg` | `client-id` | `kafka.producer.compression_rate_avg` | The average compression rate of record batches, defined as the average ratio of the compressed batch size over the uncompressed size. | `DOUBLE_OBSERVABLE_GAUGE` |
| 128 | `producer-metrics` | `connection-close-rate` | `client-id` | `kafka.producer.connection_close_rate` | The number of connections closed per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 129 | `producer-metrics` | `connection-close-total` | `client-id` | `kafka.producer.connection_close_total` | The total number of connections closed | `DOUBLE_OBSERVABLE_COUNTER` |
| 130 | `producer-metrics` | `connection-count` | `client-id` | `kafka.producer.connection_count` | The current number of active connections. | `DOUBLE_OBSERVABLE_GAUGE` |
| 131 | `producer-metrics` | `connection-creation-rate` | `client-id` | `kafka.producer.connection_creation_rate` | The number of new connections established per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 132 | `producer-metrics` | `connection-creation-total` | `client-id` | `kafka.producer.connection_creation_total` | The total number of new connections established | `DOUBLE_OBSERVABLE_COUNTER` |
| 133 | `producer-metrics` | `failed-authentication-rate` | `client-id` | `kafka.producer.failed_authentication_rate` | The number of connections with failed authentication per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 134 | `producer-metrics` | `failed-authentication-total` | `client-id` | `kafka.producer.failed_authentication_total` | The total number of connections with failed authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 135 | `producer-metrics` | `failed-reauthentication-rate` | `client-id` | `kafka.producer.failed_reauthentication_rate` | The number of failed re-authentication of connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 136 | `producer-metrics` | `failed-reauthentication-total` | `client-id` | `kafka.producer.failed_reauthentication_total` | The total number of failed re-authentication of connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 137 | `producer-metrics` | `incoming-byte-rate` | `client-id` |  |  |  |
| 138 | `producer-metrics` | `incoming-byte-total` | `client-id` |  |  |  |
| 139 | `producer-metrics` | `io-ratio` | `client-id` | `kafka.producer.io_ratio` | The fraction of time the I/O thread spent doing I/O | `DOUBLE_OBSERVABLE_GAUGE` |
| 140 | `producer-metrics` | `io-time-ns-avg` | `client-id` | `kafka.producer.io_time_ns_avg` | The average length of time for I/O per select call in nanoseconds. | `DOUBLE_OBSERVABLE_GAUGE` |
| 141 | `producer-metrics` | `io-wait-ratio` | `client-id` | `kafka.producer.io_wait_ratio` | The fraction of time the I/O thread spent waiting | `DOUBLE_OBSERVABLE_GAUGE` |
| 142 | `producer-metrics` | `io-wait-time-ns-avg` | `client-id` | `kafka.producer.io_wait_time_ns_avg` | The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds. | `DOUBLE_OBSERVABLE_GAUGE` |
| 143 | `producer-metrics` | `io-waittime-total` | `client-id` | `kafka.producer.io_waittime_total` | The total time the I/O thread spent waiting | `DOUBLE_OBSERVABLE_COUNTER` |
| 144 | `producer-metrics` | `iotime-total` | `client-id` | `kafka.producer.iotime_total` | The total time the I/O thread spent doing I/O | `DOUBLE_OBSERVABLE_COUNTER` |
| 145 | `producer-metrics` | `metadata-age` | `client-id` | `kafka.producer.metadata_age` | The age in seconds of the current producer metadata being used. | `DOUBLE_OBSERVABLE_GAUGE` |
| 146 | `producer-metrics` | `network-io-rate` | `client-id` | `kafka.producer.network_io_rate` | The number of network operations (reads or writes) on all connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 147 | `producer-metrics` | `network-io-total` | `client-id` | `kafka.producer.network_io_total` | The total number of network operations (reads or writes) on all connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 148 | `producer-metrics` | `outgoing-byte-rate` | `client-id` |  |  |  |
| 149 | `producer-metrics` | `outgoing-byte-total` | `client-id` |  |  |  |
| 150 | `producer-metrics` | `produce-throttle-time-avg` | `client-id` | `kafka.producer.produce_throttle_time_avg` | The average time in ms a request was throttled by a broker | `DOUBLE_OBSERVABLE_GAUGE` |
| 151 | `producer-metrics` | `produce-throttle-time-max` | `client-id` | `kafka.producer.produce_throttle_time_max` | The maximum time in ms a request was throttled by a broker | `DOUBLE_OBSERVABLE_GAUGE` |
| 152 | `producer-metrics` | `reauthentication-latency-avg` | `client-id` | `kafka.producer.reauthentication_latency_avg` | The average latency observed due to re-authentication | `DOUBLE_OBSERVABLE_GAUGE` |
| 153 | `producer-metrics` | `reauthentication-latency-max` | `client-id` | `kafka.producer.reauthentication_latency_max` | The max latency observed due to re-authentication | `DOUBLE_OBSERVABLE_GAUGE` |
| 154 | `producer-metrics` | `record-error-rate` | `client-id` |  |  |  |
| 155 | `producer-metrics` | `record-error-total` | `client-id` |  |  |  |
| 156 | `producer-metrics` | `record-queue-time-avg` | `client-id` | `kafka.producer.record_queue_time_avg` | The average time in ms record batches spent in the send buffer. | `DOUBLE_OBSERVABLE_GAUGE` |
| 157 | `producer-metrics` | `record-queue-time-max` | `client-id` | `kafka.producer.record_queue_time_max` | The maximum time in ms record batches spent in the send buffer. | `DOUBLE_OBSERVABLE_GAUGE` |
| 158 | `producer-metrics` | `record-retry-rate` | `client-id` |  |  |  |
| 159 | `producer-metrics` | `record-retry-total` | `client-id` |  |  |  |
| 160 | `producer-metrics` | `record-send-rate` | `client-id` |  |  |  |
| 161 | `producer-metrics` | `record-send-total` | `client-id` |  |  |  |
| 162 | `producer-metrics` | `record-size-avg` | `client-id` | `kafka.producer.record_size_avg` | The average record size | `DOUBLE_OBSERVABLE_GAUGE` |
| 163 | `producer-metrics` | `record-size-max` | `client-id` | `kafka.producer.record_size_max` | The maximum record size | `DOUBLE_OBSERVABLE_GAUGE` |
| 164 | `producer-metrics` | `records-per-request-avg` | `client-id` | `kafka.producer.records_per_request_avg` | The average number of records per request. | `DOUBLE_OBSERVABLE_GAUGE` |
| 165 | `producer-metrics` | `request-latency-avg` | `client-id` |  |  |  |
| 166 | `producer-metrics` | `request-latency-max` | `client-id` |  |  |  |
| 167 | `producer-metrics` | `request-rate` | `client-id` |  |  |  |
| 168 | `producer-metrics` | `request-size-avg` | `client-id` |  |  |  |
| 169 | `producer-metrics` | `request-size-max` | `client-id` |  |  |  |
| 170 | `producer-metrics` | `request-total` | `client-id` |  |  |  |
| 171 | `producer-metrics` | `requests-in-flight` | `client-id` | `kafka.producer.requests_in_flight` | The current number of in-flight requests awaiting a response. | `DOUBLE_OBSERVABLE_GAUGE` |
| 172 | `producer-metrics` | `response-rate` | `client-id` |  |  |  |
| 173 | `producer-metrics` | `response-total` | `client-id` |  |  |  |
| 174 | `producer-metrics` | `select-rate` | `client-id` | `kafka.producer.select_rate` | The number of times the I/O layer checked for new I/O to perform per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 175 | `producer-metrics` | `select-total` | `client-id` | `kafka.producer.select_total` | The total number of times the I/O layer checked for new I/O to perform | `DOUBLE_OBSERVABLE_COUNTER` |
| 176 | `producer-metrics` | `successful-authentication-no-reauth-total` | `client-id` | `kafka.producer.successful_authentication_no_reauth_total` | The total number of connections with successful authentication where the client does not support re-authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 177 | `producer-metrics` | `successful-authentication-rate` | `client-id` | `kafka.producer.successful_authentication_rate` | The number of connections with successful authentication per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 178 | `producer-metrics` | `successful-authentication-total` | `client-id` | `kafka.producer.successful_authentication_total` | The total number of connections with successful authentication | `DOUBLE_OBSERVABLE_COUNTER` |
| 179 | `producer-metrics` | `successful-reauthentication-rate` | `client-id` | `kafka.producer.successful_reauthentication_rate` | The number of successful re-authentication of connections per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 180 | `producer-metrics` | `successful-reauthentication-total` | `client-id` | `kafka.producer.successful_reauthentication_total` | The total number of successful re-authentication of connections | `DOUBLE_OBSERVABLE_COUNTER` |
| 181 | `producer-metrics` | `waiting-threads` | `client-id` | `kafka.producer.waiting_threads` | The number of user threads blocked waiting for buffer memory to enqueue their records | `DOUBLE_OBSERVABLE_GAUGE` |
| 182 | `producer-node-metrics` | `incoming-byte-rate` | `client-id`,`node-id` | `kafka.producer.incoming_byte_rate` | The number of bytes read off all sockets per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 183 | `producer-node-metrics` | `incoming-byte-total` | `client-id`,`node-id` | `kafka.producer.incoming_byte_total` | The total number of bytes read off all sockets | `DOUBLE_OBSERVABLE_COUNTER` |
| 184 | `producer-node-metrics` | `outgoing-byte-rate` | `client-id`,`node-id` | `kafka.producer.outgoing_byte_rate` | The number of outgoing bytes sent to all servers per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 185 | `producer-node-metrics` | `outgoing-byte-total` | `client-id`,`node-id` | `kafka.producer.outgoing_byte_total` | The total number of outgoing bytes sent to all servers | `DOUBLE_OBSERVABLE_COUNTER` |
| 186 | `producer-node-metrics` | `request-latency-avg` | `client-id`,`node-id` | `kafka.producer.request_latency_avg` | The average request latency in ms | `DOUBLE_OBSERVABLE_GAUGE` |
| 187 | `producer-node-metrics` | `request-latency-max` | `client-id`,`node-id` | `kafka.producer.request_latency_max` | The maximum request latency in ms | `DOUBLE_OBSERVABLE_GAUGE` |
| 188 | `producer-node-metrics` | `request-rate` | `client-id`,`node-id` | `kafka.producer.request_rate` | The number of requests sent per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 189 | `producer-node-metrics` | `request-size-avg` | `client-id`,`node-id` | `kafka.producer.request_size_avg` | The average size of requests sent. | `DOUBLE_OBSERVABLE_GAUGE` |
| 190 | `producer-node-metrics` | `request-size-max` | `client-id`,`node-id` | `kafka.producer.request_size_max` | The maximum size of any request sent. | `DOUBLE_OBSERVABLE_GAUGE` |
| 191 | `producer-node-metrics` | `request-total` | `client-id`,`node-id` | `kafka.producer.request_total` | The total number of requests sent | `DOUBLE_OBSERVABLE_COUNTER` |
| 192 | `producer-node-metrics` | `response-rate` | `client-id`,`node-id` | `kafka.producer.response_rate` | The number of responses received per second | `DOUBLE_OBSERVABLE_GAUGE` |
| 193 | `producer-node-metrics` | `response-total` | `client-id`,`node-id` | `kafka.producer.response_total` | The total number of responses received | `DOUBLE_OBSERVABLE_COUNTER` |
| 194 | `producer-topic-metrics` | `byte-rate` | `client-id`,`topic` | `kafka.producer.byte_rate` | The average number of bytes sent per second for a topic. | `DOUBLE_OBSERVABLE_GAUGE` |
| 195 | `producer-topic-metrics` | `byte-total` | `client-id`,`topic` | `kafka.producer.byte_total` | The total number of bytes sent for a topic. | `DOUBLE_OBSERVABLE_COUNTER` |
| 196 | `producer-topic-metrics` | `compression-rate` | `client-id`,`topic` | `kafka.producer.compression_rate` | The average compression rate of record batches for a topic, defined as the average ratio of the compressed batch size over the uncompressed size. | `DOUBLE_OBSERVABLE_GAUGE` |
| 197 | `producer-topic-metrics` | `record-error-rate` | `client-id`,`topic` | `kafka.producer.record_error_rate` | The average per-second number of record sends that resulted in errors | `DOUBLE_OBSERVABLE_GAUGE` |
| 198 | `producer-topic-metrics` | `record-error-total` | `client-id`,`topic` | `kafka.producer.record_error_total` | The total number of record sends that resulted in errors | `DOUBLE_OBSERVABLE_COUNTER` |
| 199 | `producer-topic-metrics` | `record-retry-rate` | `client-id`,`topic` | `kafka.producer.record_retry_rate` | The average per-second number of retried record sends | `DOUBLE_OBSERVABLE_GAUGE` |
| 200 | `producer-topic-metrics` | `record-retry-total` | `client-id`,`topic` | `kafka.producer.record_retry_total` | The total number of retried record sends | `DOUBLE_OBSERVABLE_COUNTER` |
| 201 | `producer-topic-metrics` | `record-send-rate` | `client-id`,`topic` | `kafka.producer.record_send_rate` | The average number of records sent per second. | `DOUBLE_OBSERVABLE_GAUGE` |
| 202 | `producer-topic-metrics` | `record-send-total` | `client-id`,`topic` | `kafka.producer.record_send_total` | The total number of records sent. | `DOUBLE_OBSERVABLE_COUNTER` |
