# Cassandra Metrics

Here is the list of metrics based on MBeans exposed by Cassandra.

| Metric Name                               | Type          | Unit      | Attributes                            | Description                                                      |
| ----------------------------------------- | ------------- | --------- | ------------------------------------- | ---------------------------------------------------------------- |
| cassandra.client.request.count            | Counter       | {request} | cassandra.operation                   | Number of requests by operation.                                 |
| cassandra.client.request.error            | Counter       | {error}   | cassandra.operation, cassandra.status | Number of request errors by operation.                           |
| cassandra.client.request.latency.p50      | Gauge         | s         | cassandra.operation                   | Request latency 50th percentile by operation.                    |
| cassandra.client.request.latency.p99      | Gauge         | s         | cassandra.operation                   | Request latency 99th percentile by operation.                    |
| cassandra.client.request.latency.max      | Gauge         | s         | cassandra.operation                   | Maximum request latency by operation.                            |
| cassandra.compaction.tasks.completed      | Counter       | {task}    |                                       | Number of completed compactions since server start.              |
| cassandra.compaction.tasks.pending        | Gauge         | {task}    |                                       | Estimated number of compactions remaining to perform.            |
| cassandra.storage.load                    | UpDownCounter | By        |                                       | Size of the on disk data size this node manages.                 |
| cassandra.storage.total_hints.count       | Counter       | {hint}    |                                       | Number of hint messages written to this node since server start. |
| cassandra.storage.total_hints.in_progress | UpDownCounter | {hint}    |                                       | Number of hints attempting to be sent currently.                 |
