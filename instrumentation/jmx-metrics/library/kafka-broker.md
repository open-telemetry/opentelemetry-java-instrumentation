# Kafka Broker Metrics

Here is the list of metrics based on MBeans exposed by Kafka broker.

Broker metrics:

| Metric Name                         | Type          | Unit         | Attributes                 | Description                                                            |
| ----------------------------------- | ------------- | ------------ | -------------------------- | ---------------------------------------------------------------------- |
| kafka.message.count                 | Counter       | {message}    |                            | The number of messages received by the broker                          |
| kafka.request.count                 | Counter       | {request}    | kafka.request.type         | The number of requests received by the broker                          |
| kafka.request.failed                | Counter       | {request}    | kafka.request.type         | The number of requests to the broker resulting in a failure            |
| kafka.request.duration.count        | Counter       | {request}    | kafka.request.type         | The number of requests the broker has measured the service duration of |
| kafka.request.duration.p50          | Gauge         | s            | kafka.request.type         | The 50th percentile duration the broker has taken to service requests  |
| kafka.request.duration.p99          | Gauge         | s            | kafka.request.type         | The 99th percentile duration the broker has taken to service requests  |
| kafka.request.queue                 | UpDownCounter | {request}    |                            | Size of the request queue                                              |
| kafka.network.io                    | Counter       | By           | kafka.network.io.direction | The bytes received or sent by the broker                               |
| kafka.purgatory.size                | UpDownCounter | {request}    | kafka.request.type         | The number of requests waiting in purgatory                            |
| kafka.partition.count               | UpDownCounter | {partition}  |                            | The number of partitions on the broker                                 |
| kafka.partition.offline             | UpDownCounter | {partition}  |                            | The number of partitions offline                                       |
| kafka.partition.under_replicated    | UpDownCounter | {partition}  |                            | The number of under-replicated partitions                              |
| kafka.isr.operation.count           | UpDownCounter | {operation}  | kafka.isr.operation        | The number of in-sync replica operations                               |
| kafka.lag.max                       | Gauge         | {message}    |                            | The max lag in messages between follower and leader replicas           |
| kafka.controller.active.count       | UpDownCounter | {controller} |                            | The number of controllers active on the broker                         |
| kafka.leader_election.count         | Counter       | {election}   |                            | The leader election count                                              |
| kafka.leader_election.unclean.count | Counter       | {election}   |                            | Unclean leader election count                                          |

Log metrics:

| Metric Name                   | Type    | Unit    | Attributes | Description                        |
| ----------------------------- | ------- | ------- | ---------- | ---------------------------------- |
| kafka.logs.flush.count        | Counter | {flush} |            | Log flush count                    |
| kafka.logs.flush.duration.p50 | Gauge   | s       |            | Log flush duration 50th percentile |
| kafka.logs.flush.duration.p99 | Gauge   | s       |            | Log flush duration 99th percentile |
