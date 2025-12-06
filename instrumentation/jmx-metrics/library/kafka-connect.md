# Kafka Connect Metrics

Here is the list of metrics based on MBeans exposed by Apache Kafka Connect. String-valued JMX
attributes (class/type/version information) are exported as state metrics with value `1` and
carry the raw string value as metric attributes.

## Compatibility

This rule set targets both Apache Kafka Connect and Confluent Platform. Apache documents several
metrics not surfaced in Confluent docs (worker rebalance protocol, per-connector task counts on
workers, predicate/transform metadata, connector task metadata including converter info, source
transaction size stats, and sink record lag max); all of them are included below. Status metrics use
the superset of values across both variants (connector: running, paused, stopped, failed,
restarting, unassigned, degraded; task: running, paused, failed, restarting, unassigned,
destroyed) and fall back to `unknown` for any new values. Differences in bean placeholder
formatting between the docs are cosmetic; bean names align across both variants.

## Worker metrics

| Metric Name                                               | Type          | Unit        | Attributes | Description                                                               |
|-----------------------------------------------------------|---------------|-------------|------------|---------------------------------------------------------------------------|
| kafka.connect.worker.connector.count                      | UpDownCounter | {connector} |            | The number of connectors run in this worker.                              |
| kafka.connect.worker.connector.startup.attempts           | Counter       | {attempt}   |            | The total number of connector startups that this worker has attempted.    |
| kafka.connect.worker.connector.startup.failure.percentage | Gauge         | 1           |            | The average percentage of this worker's connectors starts that failed.    |
| kafka.connect.worker.connector.startup.failure.total      | Counter       | {startup}   |            | The total number of connector starts that failed.                         |
| kafka.connect.worker.connector.startup.success.percentage | Gauge         | 1           |            | The average percentage of this worker's connectors starts that succeeded. |
| kafka.connect.worker.connector.startup.success.total      | Counter       | {startup}   |            | The total number of connector starts that succeeded.                      |
| kafka.connect.worker.task.count                           | UpDownCounter | {task}      |            | The number of tasks run in this worker.                                   |
| kafka.connect.worker.task.startup.attempts                | Counter       | {attempt}   |            | The total number of task startups that this worker has attempted.         |
| kafka.connect.worker.task.startup.failure.percentage      | Gauge         | 1           |            | The average percentage of this worker's tasks starts that failed.         |
| kafka.connect.worker.task.startup.failure.total           | Counter       | {startup}   |            | The total number of task starts that failed.                              |
| kafka.connect.worker.task.startup.success.percentage      | Gauge         | 1           |            | The average percentage of this worker's tasks starts that succeeded.      |
| kafka.connect.worker.task.startup.success.total           | Counter       | {startup}   |            | The total number of task starts that succeeded.                           |

## Worker connector task metrics

| Metric Name                                    | Type          | Unit   | Attributes              | Description                                                    |
|------------------------------------------------|---------------|--------|-------------------------|----------------------------------------------------------------|
| kafka.connect.worker.connector.task.destroyed  | UpDownCounter | {task} | kafka.connect.connector | The number of destroyed tasks of the connector on the worker.  |
| kafka.connect.worker.connector.task.failed     | UpDownCounter | {task} | kafka.connect.connector | The number of failed tasks of the connector on the worker.     |
| kafka.connect.worker.connector.task.paused     | UpDownCounter | {task} | kafka.connect.connector | The number of paused tasks of the connector on the worker.     |
| kafka.connect.worker.connector.task.restarting | UpDownCounter | {task} | kafka.connect.connector | The number of restarting tasks of the connector on the worker. |
| kafka.connect.worker.connector.task.running    | UpDownCounter | {task} | kafka.connect.connector | The number of running tasks of the connector on the worker.    |
| kafka.connect.worker.connector.task.total      | UpDownCounter | {task} | kafka.connect.connector | The number of tasks of the connector on the worker.            |
| kafka.connect.worker.connector.task.unassigned | UpDownCounter | {task} | kafka.connect.connector | The number of unassigned tasks of the connector on the worker. |

## Worker rebalance metrics

All metrics include `kafka.connect.worker.leader`.

| Metric Name                                    | Type          | Unit        | Attributes                           | Description                                                                     |
|------------------------------------------------|---------------|-------------|--------------------------------------|---------------------------------------------------------------------------------|
| kafka.connect.worker.rebalance.completed.total | Counter       | {rebalance} |                                      | The total number of rebalances completed by this worker.                        |
| kafka.connect.worker.rebalance.protocol        | UpDownCounter | 1           | kafka.connect.protocol.state         | The Connect protocol used by this cluster.                                      |
| kafka.connect.worker.rebalance.epoch           | UpDownCounter | {epoch}     |                                      | The epoch or generation number of this worker.                                  |
| kafka.connect.worker.rebalance.leader          | UpDownCounter | 1           | kafka.connect.worker.leader.state    | The name of the group leader.                                                   |
| kafka.connect.worker.rebalance.avg.time        | Gauge         | s           |                                      | The average time in milliseconds spent by this worker to rebalance.             |
| kafka.connect.worker.rebalance.max.time        | Gauge         | s           |                                      | The maximum time in milliseconds spent by this worker to rebalance.             |
| kafka.connect.worker.rebalance.active          | UpDownCounter | 1           | kafka.connect.worker.rebalance.state | Whether this worker is currently rebalancing.                                   |
| kafka.connect.worker.rebalance.since_last      | Gauge         | s           |                                      | The time in milliseconds since this worker completed the most recent rebalance. |

## Connector metrics

Attributes: `kafka.connect.connector`, `kafka.connect.connector.class`, `kafka.connect.connector.version`, `kafka.connect.connector.type.raw`.

| Metric Name                     | Type          | Unit | Attributes                            | Description                                                                                                                                                                 |
|---------------------------------|---------------|------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafka.connect.connector.class   | UpDownCounter | 1    | kafka.connect.connector.class.state   | The name of the connector class.                                                                                                                                            |
| kafka.connect.connector.type    | UpDownCounter | 1    | kafka.connect.connector.type          | The type of the connector. One of 'source' or 'sink'.                                                                                                                       |
| kafka.connect.connector.version | UpDownCounter | 1    | kafka.connect.connector.version.state | The version of the connector class, as reported by the connector.                                                                                                           |
| kafka.connect.connector.status  | UpDownCounter | 1    | kafka.connect.connector.state         | Connector lifecycle state indicator (1 when the state matches the attribute value); accepts running, paused, stopped, failed, restarting, unassigned, degraded, or unknown. |

## Predicate metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`, `kafka.connect.predicate`, `kafka.connect.predicate.class`, `kafka.connect.predicate.version`.

| Metric Name                     | Type          | Unit | Attributes                            | Description                            |
|---------------------------------|---------------|------|---------------------------------------|----------------------------------------|
| kafka.connect.predicate.class   | UpDownCounter | 1    | kafka.connect.predicate.class.state   | The class name of the predicate class. |
| kafka.connect.predicate.version | UpDownCounter | 1    | kafka.connect.predicate.version.state | The version of the predicate class.    |

## Connector task metrics

Attributes include `kafka.connect.connector`, `kafka.connect.task.id`, connector class/type/version, converter class/version attributes, and task class/version.

| Metric Name                                         | Type          | Unit     | Attributes                                        | Description                                                                                                        |
|-----------------------------------------------------|---------------|----------|---------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| kafka.connect.task.batch.size.avg                   | Gauge         | {record} |                                                   | The average number of records in the batches the task has processed so far.                                        |
| kafka.connect.task.batch.size.max                   | Gauge         | {record} |                                                   | The number of records in the largest batch the task has processed so far.                                          |
| kafka.connect.task.connector.class                  | UpDownCounter | 1        | kafka.connect.task.connector.class.state          | The name of the connector class.                                                                                   |
| kafka.connect.task.connector.type                   | UpDownCounter | 1        | kafka.connect.task.connector.type                 | The type of the connector. One of 'source' or 'sink'.                                                              |
| kafka.connect.task.connector.version                | UpDownCounter | 1        | kafka.connect.task.connector.version.state        | The version of the connector class, as reported by the connector.                                                  |
| kafka.connect.task.header.converter.class           | UpDownCounter | 1        | kafka.connect.task.header.converter.class.state   | The fully qualified class name from header.converter.                                                              |
| kafka.connect.task.header.converter.version         | UpDownCounter | 1        | kafka.connect.task.header.converter.version.state | The version instantiated for header.converter. May be undefined.                                                   |
| kafka.connect.task.key.converter.class              | UpDownCounter | 1        | kafka.connect.task.key.converter.class.state      | The fully qualified class name from key.converter.                                                                 |
| kafka.connect.task.key.converter.version            | UpDownCounter | 1        | kafka.connect.task.key.converter.version.state    | The version instantiated for key.converter. May be undefined.                                                      |
| kafka.connect.task.offset.commit.avg.time           | Gauge         | s        |                                                   | The average time in milliseconds taken by this task to commit offsets.                                             |
| kafka.connect.task.offset.commit.failure.percentage | Gauge         | 1        |                                                   | The average percentage of this task's offset commit attempts that failed.                                          |
| kafka.connect.task.offset.commit.max.time           | Gauge         | s        |                                                   | The maximum time in milliseconds taken by this task to commit offsets.                                             |
| kafka.connect.task.offset.commit.success.percentage | Gauge         | 1        |                                                   | The average percentage of this task's offset commit attempts that succeeded.                                       |
| kafka.connect.task.pause.ratio                      | Gauge         | 1        |                                                   | The fraction of time this task has spent in the pause state.                                                       |
| kafka.connect.task.running.ratio                    | Gauge         | 1        |                                                   | The fraction of time this task has spent in the running state.                                                     |
| kafka.connect.task.status                           | UpDownCounter | 1        | kafka.connect.task.state                          | The status of the connector task; supports running, paused, failed, restarting, unassigned, destroyed, or unknown. |
| kafka.connect.task.class                            | UpDownCounter | 1        | kafka.connect.task.class.state                    | The class name of the task.                                                                                        |
| kafka.connect.task.version                          | UpDownCounter | 1        | kafka.connect.task.version.state                  | The version of the task.                                                                                           |
| kafka.connect.task.value.converter.class            | UpDownCounter | 1        | kafka.connect.task.value.converter.class.state    | The fully qualified class name from value.converter.                                                               |
| kafka.connect.task.value.converter.version          | UpDownCounter | 1        | kafka.connect.task.value.converter.version.state  | The version instantiated for value.converter. May be undefined.                                                    |

## Transform metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`, `kafka.connect.transform`, `kafka.connect.transform.class`, `kafka.connect.transform.version`.

| Metric Name                     | Type          | Unit | Attributes                            | Description                                 |
|---------------------------------|---------------|------|---------------------------------------|---------------------------------------------|
| kafka.connect.transform.class   | UpDownCounter | 1    | kafka.connect.transform.class.state   | The class name of the transformation class. |
| kafka.connect.transform.version | UpDownCounter | 1    | kafka.connect.transform.version.state | The version of the transformation class.    |

## Sink task metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name                                       | Type          | Unit        | Attributes | Description                                                                                                                          |
|---------------------------------------------------|---------------|-------------|------------|--------------------------------------------------------------------------------------------------------------------------------------|
| kafka.connect.sink.offset.commit.completion.rate  | Gauge         | {commit}/s  |            | The average per-second number of offset commit completions that were completed successfully.                                         |
| kafka.connect.sink.offset.commit.completion.total | Counter       | {commit}    |            | The total number of offset commit completions that were completed successfully.                                                      |
| kafka.connect.sink.offset.commit.seq              | UpDownCounter | {sequence}  |            | The current sequence number for offset commits.                                                                                      |
| kafka.connect.sink.offset.commit.skip.rate        | Gauge         | {commit}/s  |            | The average per-second number of offset commit completions that were received too late and skipped/ignored.                          |
| kafka.connect.sink.offset.commit.skip.total       | Counter       | {commit}    |            | The total number of offset commit completions that were received too late and skipped/ignored.                                       |
| kafka.connect.sink.partition.count                | UpDownCounter | {partition} |            | The number of topic partitions assigned to this task.                                                                                |
| kafka.connect.sink.put.batch.avg.time             | Gauge         | s           |            | The average time taken by this task to put a batch of sinks records.                                                                 |
| kafka.connect.sink.put.batch.max.time             | Gauge         | s           |            | The maximum time taken by this task to put a batch of sinks records.                                                                 |
| kafka.connect.sink.record.active.count            | UpDownCounter | {record}    |            | The number of records that have been read from Kafka but not yet completely committed/flushed/acknowledged by the sink task.         |
| kafka.connect.sink.record.active.count.avg        | Gauge         | {record}    |            | The average number of records that have been read from Kafka but not yet completely committed/flushed/acknowledged by the sink task. |
| kafka.connect.sink.record.active.count.max        | Gauge         | {record}    |            | The maximum number of records that have been read from Kafka but not yet completely committed/flushed/acknowledged by the sink task. |
| kafka.connect.sink.record.lag.max                 | Gauge         | {record}    |            | The maximum lag in terms of number of records that the sink task is behind the consumer's position for any topic partitions.         |
| kafka.connect.sink.record.read.rate               | Gauge         | {record}/s  |            | The average per-second number of records read from Kafka for this task before transformations are applied.                           |
| kafka.connect.sink.record.read.total              | Counter       | {record}    |            | The total number of records read from Kafka by this task since it was last restarted.                                                |
| kafka.connect.sink.record.send.rate               | Gauge         | {record}/s  |            | The average per-second number of records output from the transformations and sent/put to this task.                                  |
| kafka.connect.sink.record.send.total              | Counter       | {record}    |            | The total number of records output from the transformations and sent/put to this task since it was last restarted.                   |

## Source task metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name                                  | Type          | Unit       | Attributes | Description                                                                                                 |
|----------------------------------------------|---------------|------------|------------|-------------------------------------------------------------------------------------------------------------|
| kafka.connect.source.poll.batch.avg.time     | Gauge         | s          |            | The average time in milliseconds taken by this task to poll for a batch of source records.                  |
| kafka.connect.source.poll.batch.max.time     | Gauge         | s          |            | The maximum time in milliseconds taken by this task to poll for a batch of source records.                  |
| kafka.connect.source.record.active.count     | UpDownCounter | {record}   |            | The number of records that have been produced by this task but not yet completely written to Kafka.         |
| kafka.connect.source.record.active.count.avg | Gauge         | {record}   |            | The average number of records that have been produced by this task but not yet completely written to Kafka. |
| kafka.connect.source.record.active.count.max | Gauge         | {record}   |            | The maximum number of records that have been produced by this task but not yet completely written to Kafka. |
| kafka.connect.source.record.poll.rate        | Gauge         | {record}/s |            | The average per-second number of records produced/polled (before transformation) by this task.              |
| kafka.connect.source.record.poll.total       | Counter       | {record}   |            | The total number of records produced/polled (before transformation) by this task.                           |
| kafka.connect.source.record.write.rate       | Gauge         | {record}/s |            | The average per-second number of records written to Kafka for this task.                                    |
| kafka.connect.source.record.write.total      | Counter       | {record}   |            | The number of records output written to Kafka for this task.                                                |
| kafka.connect.source.transaction.size.avg    | Gauge         | {record}   |            | The average number of records in the transactions the task has committed so far.                            |
| kafka.connect.source.transaction.size.max    | Gauge         | {record}   |            | The number of records in the largest transaction the task has committed so far.                             |
| kafka.connect.source.transaction.size.min    | Gauge         | {record}   |            | The number of records in the smallest transaction the task has committed so far.                            |

## Task error metrics

Attributes: `kafka.connect.connector`, `kafka.connect.task.id`.

| Metric Name                                               | Type    | Unit      | Attributes | Description                                                   |
|-----------------------------------------------------------|---------|-----------|------------|---------------------------------------------------------------|
| kafka.connect.task.error.deadletterqueue.produce.failures | Counter | {failure} |            | The number of failed writes to the dead letter queue.         |
| kafka.connect.task.error.deadletterqueue.produce.requests | Counter | {request} |            | The number of attempted writes to the dead letter queue.      |
| kafka.connect.task.error.last.error.timestamp             | Gauge   | s         |            | The epoch timestamp when this task last encountered an error. |
| kafka.connect.task.error.total.errors.logged              | Counter | {error}   |            | The number of errors that were logged.                        |
| kafka.connect.task.error.total.record.errors              | Counter | {record}  |            | The number of record processing errors in this task.          |
| kafka.connect.task.error.total.record.failures            | Counter | {record}  |            | The number of record processing failures in this task.        |
| kafka.connect.task.error.total.records.skipped            | Counter | {record}  |            | The number of records skipped due to errors.                  |
| kafka.connect.task.error.total.retries                    | Counter | {retry}   |            | The number of operations retried.                             |
