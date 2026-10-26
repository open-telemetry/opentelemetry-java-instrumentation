# Kafka Broker Metrics

Here is the list of metrics based on MBeans exposed by Kafka broker.

## Server metrics

| Metric Name                             | Type          | Unit        | Attributes                             | Description                                                  |
| --------------------------------------- | ------------- | ----------- | -------------------------------------- | ------------------------------------------------------------ |
| kafka.server.messages                   | Counter       | {message}   | kafka.topic.name                       | The number of messages received by the broker                |
| kafka.server.requests                   | Counter       | {request}   | kafka.request.type, kafka.topic.name   | The number of requests received by the broker                |
| kafka.server.failed_requests            | Counter       | {request}   | kafka.request.type, kafka.topic.name   | The number of requests to the broker resulting in a failure  |
| kafka.server.network.io                 | Counter       | By          | kafka.topic.name, network.io.direction | The bytes received or sent by the broker                     |
| kafka.server.purgatory.size             | UpDownCounter | {request}   | kafka.request.type                     | The number of requests waiting in purgatory                  |
| kafka.server.partition.count            | UpDownCounter | {partition} |                                        | The number of partitions on the broker                       |
| kafka.server.partition.under_replicated | UpDownCounter | {partition} |                                        | The number of under-replicated partitions                    |
| kafka.server.isr.operations             | Counter       | {operation} | kafka.server.isr.operation             | The number of in-sync replica operations                     |
| kafka.server.lag.max                    | Gauge         | {message}   |                                        | The max lag in messages between follower and leader replicas |

## Network metrics

| Metric Name                        | Type          | Unit      | Attributes         | Description                                                                   |
| ---------------------------------- | ------------- | --------- | ------------------ | ----------------------------------------------------------------------------- |
| kafka.network.requests             | Counter       | {request} | kafka.request.type | The number of network requests received by the broker                         |
| kafka.network.request.duration.p50 | Gauge         | s         | kafka.request.type | The 50th percentile duration the broker has taken to service network requests |
| kafka.network.request.duration.p99 | Gauge         | s         | kafka.request.type | The 99th percentile duration the broker has taken to service network requests |
| kafka.network.request.queue        | UpDownCounter | {request} |                    | Size of the network request queue                                             |

## Controller metrics

| Metric Name                               | Type          | Unit         | Description                                    |
| ----------------------------------------- | ------------- | ------------ | ---------------------------------------------- |
| kafka.controller.partition.offline        | UpDownCounter | {partition}  | The number of partitions offline               |
| kafka.controller.active.count             | UpDownCounter | {controller} | The number of controllers active on the broker |
| kafka.controller.leader_elections         | Counter       | {election}   | The leader election count                      |
| kafka.controller.unclean_leader_elections | Counter       | {election}   | Unclean leader election count                  |

## Log metrics

| Metric Name                  | Type    | Unit    | Description                        |
| ---------------------------- | ------- | ------- | ---------------------------------- |
| kafka.log.flushes            | Counter | {flush} | Log flush count                    |
| kafka.log.flush.duration.p50 | Gauge   | s       | Log flush duration 50th percentile |
| kafka.log.flush.duration.p99 | Gauge   | s       | Log flush duration 99th percentile |
