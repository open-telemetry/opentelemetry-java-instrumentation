# Kafka Broker Metrics

Here is the list of metrics based on MBeans exposed by Kafka broker. <br /><br />
Broker metrics:

| Metric Name                        | Type          | Attributes | Description                                                          |
| ---------------------------------- | ------------- | ---------- | -------------------------------------------------------------------- |
| kafka.message.count                | Counter       |            | The number of messages received by the broker                        |
| kafka.request.count                | Counter       | type       | The number of requests received by the broker                        |
| kafka.request.failed               | Counter       | type       | The number of requests to the broker resulting in a failure          |
| kafka.request.time.total           | Counter       | type       | The total time the broker has taken to service requests              |
| kafka.request.time.50p             | Gauge         | type       | The 50th percentile time the broker has taken to service requests    |
| kafka.request.time.99p             | Gauge         | type       | The 99th percentile time the broker has taken to service requests    |
| kafka.request.queue                | UpDownCounter |            | Size of the request queue                                            |
| kafka.network.io                   | Counter       | direction  | The bytes received or sent by the broker                             |
| kafka.purgatory.size               | UpDownCounter | type       | The number of requests waiting in purgatory                          |
| kafka.partition.count              | UpDownCounter |            | The number of partitions on the broker                               |
| kafka.partition.offline            | UpDownCounter |            | The number of partitions offline                                     |
| kafka.partition.underReplicated    | UpDownCounter |            | The number of under replicated partitions                            |
| kafka.isr.operation.count          | UpDownCounter | operation  | The number of in-sync replica shrink and expand operations           |
| kafka.lag.max                      | Gauge         |            | The max lag in messages between follower and leader replicas         |
| kafka.controller.active.count      | UpDownCounter |            | The number of controllers active on the broker                       |
| kafka.leaderElection.count         | Counter       |            | The leader election count                                            |
| kafka.leaderElection.unclean.count | Counter       |            | Unclean leader election count - increasing indicates broker failures |

<br />
Log metrics:

| Metric Name               | Type    | Attributes | Description                      |
| ------------------------- | ------- | ---------- | -------------------------------- |
| kafka.logs.flush.count    | Counter |            | Log flush count                  |
| kafka.logs.flush.time.50p | Gauge   |            | Log flush time - 50th percentile |
| kafka.logs.flush.time.99p | Gauge   |            | Log flush time - 99th percentile |
