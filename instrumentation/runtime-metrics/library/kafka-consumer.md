# Kafka Consumer Metrics

Here is the list of metrics based on MBeans exposed by Kafka consumer. <br /><br />

Consumer metrics:

| Metric Name                                       | Type  | Attributes       | Description                                                                    |
|---------------------------------------------------|-------|------------------|--------------------------------------------------------------------------------|
| kafka.consumer.all.rebalance-latency-avg          | Gauge | client-id        | The average time taken for a group to complete a successful rebalance          |
| kafka.consumer.all.rebalance-latency-max          | Gauge | client-id        | The maximum time taken for a group to complete a successful rebalance          |
| kafka.consumer.all.rebalance-latency-total        | Gauge | client-id        | The total time this consumer has spent in successful rebalances since creation |
| kafka.consumer.all.rebalance-rate-per-hour        | Gauge | client-id        | The number of successful rebalance events per hour                             |
| kafka.consumer.all.rebalance-total                | Gauge | client-id        | The total number of successful rebalance events                                |
| kafka.consumer.all.last-rebalance-seconds-ago     | Gauge | client-id        | Number of seconds since the last rebalance event                               |
| kafka.consumer.all.failed-rebalance-rate-per-hour | Gauge | client-id        | Number of failed rebalance events per hour                                     |
| kafka.consumer.all.failed-rebalance-total         | Gauge | client-id        | Total number of failed rebalance events                                        |
| kafka.consumer.all.commit-latency-avg             | Gauge | client-id        | The average time taken for a commit request                                    |
| kafka.consumer.all.commit-rate                    | Gauge | client-id        | The number of commit calls per second                                          |
| kafka.consumer.all.fetch-rate                     | Gauge | client-id        | The number of fetch requests for all topics per second                         |
| kafka.consumer.all.fetch-size-avg                 | Gauge | client-id        | The average number of bytes fetched per request for all topics                 |
| kafka.consumer.all.fetch-latency-avg              | Gauge | client-id        | The average time taken for a fetch request                                     |
| kafka.consumer.all.fetch-throttle-time-avg        | Gauge | client-id        | The average throttle time                                                      |
| kafka.consumer.all.records-lag-max                | Gauge | client-id        | Number of messages the consumer lags behind the producer                       |
| kafka.consumer.all.records-consumed-rate          | Gauge | client-id        | The average number of records consumed for all topics per second               |
| kafka.consumer.all.bytes-consumed-rate            | Gauge | client-id        | The average number of bytes consumed for all topics per second                 |
| kafka.consumer.bytes-consumed-rate                | Gauge | client-id, topic | The average number of bytes consumed per second                                |
| kafka.consumer.fetch-size-avg                     | Gauge | client-id, topic | The average number of bytes fetched per request                                |
| kafka.consumer.records-consumed-rate              | Gauge | client-id, topic | The average number of records consumed per second                              |
