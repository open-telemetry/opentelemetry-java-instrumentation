# Kafka Broker Metrics

Here is the list of metrics based on MBeans exposed by Kafka broker.

## Server metrics

| Metric Name                             | Type          | Unit          | Attributes                             | Description                                                  |
| --------------------------------------- | ------------- | ------------- | -------------------------------------- | ------------------------------------------------------------ |
| kafka.server.messages                   | Counter       | {message}     | kafka.topic.name                       | The number of messages received by the broker                |
| kafka.server.requests                   | Counter       | {request}     | kafka.request.type, kafka.topic.name   | The number of requests received by the broker                |
| kafka.server.failed_requests            | Counter       | {request}     | kafka.request.type, kafka.topic.name   | The number of requests to the broker resulting in a failure  |
| kafka.server.network.io                 | Counter       | By            | kafka.topic.name, network.io.direction | The bytes received or sent by the broker                     |
| kafka.server.purgatory.size             | UpDownCounter | {request}     | kafka.request.type                     | The number of requests waiting in purgatory                  |
| kafka.server.partition.count            | UpDownCounter | {partition}   |                                        | The number of partitions on the broker                       |
| kafka.server.partition.under_replicated | UpDownCounter | {partition}   |                                        | The number of under-replicated partitions                    |
| kafka.server.isr.operations             | Counter       | {operation}   | kafka.server.isr.operation             | The number of in-sync replica operations                     |
| kafka.server.lag.max                    | Gauge         | {message}     |                                        | The max lag in messages between follower and leader replicas |
| kafka.leader.count                      | Gauge         | {leader}      |                                        | The number of leaders assigned to the broker.                |
| kafka.isr.operation.rate.1m             | Gauge         | {operation}/s | kafka.server.isr.operation             | The one-minute rate of ISR expand and shrink operations.     |
| kafka.isr.operation.rate.5m             | Gauge         | {operation}/s | kafka.server.isr.operation             | The five-minute rate of ISR expand and shrink operations.    |
| kafka.isr.operation.rate.15m            | Gauge         | {operation}/s | kafka.server.isr.operation             | The fifteen-minute rate of ISR expand and shrink operations. |
| kafka.isr.operation.rate.mean           | Gauge         | {operation}/s | kafka.server.isr.operation             | The mean rate of ISR expand and shrink operations.           |
| kafka.network.io.rate.1m                | Gauge         | By/s          | network.io.direction                   | The one-minute inbound or outbound byte rate.                |
| kafka.network.io.rate.5m                | Gauge         | By/s          | network.io.direction                   | The five-minute inbound or outbound byte rate.               |
| kafka.network.io.rate.15m               | Gauge         | By/s          | network.io.direction                   | The fifteen-minute inbound or outbound byte rate.            |
| kafka.network.io.rate.mean              | Gauge         | By/s          | network.io.direction                   | The mean inbound or outbound byte rate.                      |
| kafka.request.handler.idle.mean         | Gauge         | 1             |                                        | The mean fraction of time request handlers are idle.         |
| kafka.request.handler.idle.1m           | Gauge         | 1             |                                        | The one-minute fraction of time request handlers are idle.   |

## Network metrics

| Metric Name                              | Type          | Unit      | Attributes                 | Description                                                                   |
| ---------------------------------------- | ------------- | --------- | -------------------------- | ----------------------------------------------------------------------------- |
| kafka.network.requests                   | Counter       | {request} | kafka.request.type         | The number of network requests received by the broker                         |
| kafka.network.request.duration.p50       | Gauge         | s         | kafka.request.type         | The 50th percentile duration the broker has taken to service network requests |
| kafka.network.request.duration.p99       | Gauge         | s         | kafka.request.type         | The 99th percentile duration the broker has taken to service network requests |
| kafka.network.request.queue              | UpDownCounter | {request} |                            | Size of the network request queue                                             |
| kafka.request.total.time.p99             | Gauge         | s         | kafka.request.type         | The 99th percentile total request time.                                       |
| kafka.request.queue.time.p99             | Gauge         | s         | kafka.request.type         | The 99th percentile time requests spend queued before processing.             |
| kafka.request.local.time.p99             | Gauge         | s         | kafka.request.type         | The 99th percentile local processing time for requests.                       |
| kafka.request.remote.time.p99            | Gauge         | s         | kafka.request.type         | The 99th percentile remote processing time for requests.                      |
| kafka.request.response.queue.time.p99    | Gauge         | s         | kafka.request.type         | The 99th percentile time responses spend queued before send.                  |
| kafka.request.response.send.time.p99     | Gauge         | s         | kafka.request.type         | The 99th percentile response send time.                                       |
| kafka.network.processor.idle.average     | Gauge         | 1         |                            | The average fraction of time network processors are idle.                     |
| kafka.network.processor.idle.utilization | Gauge         | 1         | kafka.network.processor.id | The idle fraction of an individual network processor.                         |

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
