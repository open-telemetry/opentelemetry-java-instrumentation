# Kafka Connect Metrics

Here is the list of metrics based on MBeans exposed by Apache Kafka Connect. String-valued JMX
attributes are exported as `UpDownCounter` metrics with value `1` and only include connector/task
identifiers alongside any state-mapping attributes.

## Compatibility

This rule set targets both Apache Kafka Connect and Confluent Platform. Apache documents several
metrics not surfaced in Confluent docs (worker rebalance protocol, per-connector task counts on
workers, source transaction size stats, and sink record lag max); all of them are included below. Status metrics use
the superset of values across both variants (connector: running, paused, stopped, failed,
restarting, unassigned, degraded; task: running, paused, failed, restarting, unassigned,
destroyed) and fall back to `unknown` for any new values. Differences in bean placeholder
formatting between the docs are cosmetic; bean names align across both variants.

## Worker metrics

| Metric Name | Type | Unit | Attributes | Description |
| --- | --- | --- | --- | --- |
| kafka.connect.worker.connector.count | UpDownCounter | {connector} | | The number of connectors run in this worker. |
| kafka.connect.worker.connector.startup.count | Counter | {startup} | kafka.connect.worker.connector.startup.result | The number of connector starts for this worker. |
| kafka.connect.worker.task.count | UpDownCounter | {task} | | The number of currently running tasks for this worker. |
| kafka.connect.worker.task.startup.count | Counter | {startup} | kafka.connect.worker.task.startup.result | The number of task starts for this worker. |

Result values: success, failure.

## Worker connector task metrics

| Metric Name                               | Type          | Unit   | Attributes                                                            | Description                                                  |
|-------------------------------------------|---------------|--------|-----------------------------------------------------------------------|--------------------------------------------------------------|
| kafka.connect.worker.connector.task.count | UpDownCounter | {task} | kafka.connect.connector, kafka.connect.worker.connector.task.state    | The number of tasks of the connector on the worker by state. |

State values: destroyed, failed, paused, restarting, running, unassigned.

## Worker rebalance metrics

| Metric Name                                    | Type          | Unit        | Attributes                           | Description                                                                     |
|------------------------------------------------|---------------|-------------|--------------------------------------|---------------------------------------------------------------------------------|
| kafka.connect.worker.rebalance.completed.count | Counter       | {rebalance} |                                      | The number of rebalances completed by this worker.                              |
| kafka.connect.worker.rebalance.protocol        | UpDownCounter | 1           | kafka.connect.protocol.state         | The Connect protocol used by this cluster.                                      |
| kafka.connect.worker.rebalance.epoch           | Counter       | {epoch}     |                                      | The epoch or generation number of this worker.                                  |
| kafka.connect.worker.rebalance.time.average    | Gauge         | s           |                                      | The average time in milliseconds spent by this worker to rebalance.             |
| kafka.connect.worker.rebalance.time.max        | Gauge         | s           |                                      | The maximum time in milliseconds spent by this worker to rebalance.             |
| kafka.connect.worker.rebalance.active          | UpDownCounter | 1           | kafka.connect.worker.rebalance.state | Whether this worker is currently rebalancing.                                   |

## Connector metrics

Attributes: `kafka.connect.connector` and the state attribute shown.

| Metric Name | Type | Unit | Attributes | Description |
| --- | --- | --- | --- | --- |
| kafka.connect.connector.status | UpDownCounter | 1 | kafka.connect.connector.state | Connector lifecycle state indicator (1 when the state matches the attribute value). Supports Apache and Confluent status values. |

## Connector task metrics

All metrics include `kafka.connect.connector` and `kafka.connect.task.id`. Attributes column lists any additional state attributes.

| Metric Name | Type | Unit | Attributes | Description |
| --- | --- | --- | --- | --- |
| kafka.connect.task.batch.size.average | Gauge | {record} | | The average number of records in the batches the task has processed so far. |
| kafka.connect.task.batch.size.max | Gauge | {record} | | The number of records in the largest batch the task has processed so far. |
| kafka.connect.task.offset.commit.time.average | Gauge | s | | The average time in milliseconds taken by this task to commit offsets. |
| kafka.connect.task.offset.commit.failure.ratio | Gauge | 1 | | The average ratio of this task's offset commit attempts that failed. |
| kafka.connect.task.offset.commit.time.max | Gauge | s | | The maximum time in milliseconds taken by this task to commit offsets. |
| kafka.connect.task.running.ratio | Gauge | 1 | | The fraction of time this task has spent in the running state. |
| kafka.connect.task.status | UpDownCounter | 1 | kafka.connect.task.state | The status of the connector task. Supports Apache (unassigned, running, paused, failed, restarting) and Confluent (unassigned, running, paused, failed, destroyed) values. |

## Sink task metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name | Type | Unit | Attributes | Description |
| --- | --- | --- | --- | --- |
| kafka.connect.sink.offset.commit.completed.count | Counter | {commit} | | The number of offset commit completions that were completed successfully. |
| kafka.connect.sink.offset.commit.seq | Counter | {sequence} | | The current sequence number for offset commits. |
| kafka.connect.sink.offset.commit.skipped.count | Counter | {commit} | | The number of offset commit completions that were received too late and skipped/ignored. |
| kafka.connect.sink.partition.count | UpDownCounter | {partition} | | The number of topic partitions assigned to this task belonging to the named sink connector in this worker. |
| kafka.connect.sink.put.batch.time.average | Gauge | s | | The average time taken by this task to put a batch of sinks records. |
| kafka.connect.sink.put.batch.time.max | Gauge | s | | The maximum time taken by this task to put a batch of sinks records. |
| kafka.connect.sink.record.active.count | UpDownCounter | {record} | | The number of records that have been read from Kafka but not yet completely committed/flushed/acknowledged by the sink task. |
| kafka.connect.sink.record.lag.max | Gauge | {record} | | The maximum lag in terms of number of records that the sink task is behind the consumer's position for any topic partitions. |
| kafka.connect.sink.record.read.count | Counter | {record} | | The count number of records read from Kafka by this task belonging to the named sink connector in this worker, since the task was last restarted. |
| kafka.connect.sink.record.send.count | Counter | {record} | | The number of records output from the transformations and sent/put to this task belonging to the named sink connector in this worker, since the task was last restarted. |

## Source task metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name | Type | Unit | Attributes | Description |
| --- | --- | --- | --- | --- |
| kafka.connect.source.poll.batch.time.average | Gauge | s | | The average time in milliseconds taken by this task to poll for a batch of source records. |
| kafka.connect.source.poll.batch.time.max | Gauge | s | | The maximum time in milliseconds taken by this task to poll for a batch of source records. |
| kafka.connect.source.record.active.count | UpDownCounter | {record} | | The number of records that have been produced by this task but not yet completely written to Kafka. |
| kafka.connect.source.record.poll.count | Counter | {record} | | The number of records produced/polled (before transformation) by this task belonging to the named source connector in this worker. |
| kafka.connect.source.record.write.count | Counter | {record} | | The number of records output written to Kafka for this task belonging to the named source connector in this worker, since the task was last restarted. This is after transformations are applied, and excludes any records filtered out by the transformations. |
| kafka.connect.source.transaction.size.average | Gauge | {record} | | The average number of records in the transactions the task has committed so far. |
| kafka.connect.source.transaction.size.max | Gauge | {record} | | The number of records in the largest transaction the task has committed so far. |
| kafka.connect.source.transaction.size.min | Gauge | {record} | | The number of records in the smallest transaction the task has committed so far. |

## Task error metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name                                                    | Type    | Unit      | Attributes | Description                                                   |
|----------------------------------------------------------------|---------|-----------|------------|---------------------------------------------------------------|
| kafka.connect.task.error.deadletterqueue.produce.failure.count | Counter | {failure} |            | The number of failed writes to the dead letter queue.         |
| kafka.connect.task.error.deadletterqueue.produce.request.count | Counter | {request} |            | The number of attempted writes to the dead letter queue.      |
| kafka.connect.task.error.last.error.timestamp                  | Gauge   | s         |            | The epoch timestamp when this task last encountered an error. |
| kafka.connect.task.error.logged.count                          | Counter | {error}   |            | The number of errors that were logged.                        |
| kafka.connect.task.error.record.error.count                    | Counter | {record}  |            | The number of record processing errors in this task.          |
| kafka.connect.task.error.record.failure.count                  | Counter | {record}  |            | The number of record processing failures in this task.        |
| kafka.connect.task.error.record.skipped.count                  | Counter | {record}  |            | The number of records skipped due to errors.                  |
| kafka.connect.task.error.retry.count                           | Counter | {retry}   |            | The number of operations retried.                             |
