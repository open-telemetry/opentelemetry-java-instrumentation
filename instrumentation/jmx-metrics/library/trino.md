# Trino Metrics

Here is the list of metrics based on MBeans exposed by Trino.

| Metric Name                                    | Type          | Unit    | Attributes               | Description                                                                                       |
| ---------------------------------------------- | ------------- | ------- | ------------------------ | ------------------------------------------------------------------------------------------------- |
| trino.node.active.count                        | UpDownCounter | {node}  |                          | The number of active Trino nodes monitored by the failure detector, excluding the reporting node. |
| trino.memory.pool.free                         | UpDownCounter | By      | trino.memory.pool.name   | The amount of distributed memory currently free in the memory pool.                               |
| trino.memory.query.killed.count                | Counter       | {query} |                          | The number of queries killed due to running out of memory.                                        |
| trino.query.running.count                      | UpDownCounter | {query} |                          | The number of queries currently running.                                                          |
| trino.query.started.count                      | Gauge         | {query} |                          | The five-minute exponentially decayed value of the number of queries started.                     |
| trino.query.failed.count                       | Gauge         | {query} |                          | The five-minute exponentially decayed value of the number of failed queries.                      |
| trino.query.failure.count                      | Gauge         | {query} | trino.query.failure.type | The five-minute exponentially decayed value of the number of failed queries by failure type.      |
| trino.query.execution.duration.p50             | Gauge         | s       |                          | The five-minute exponentially decayed value of the 50th percentile query execution duration.      |
| trino.query.input.rate.p90                     | Gauge         | By/s    |                          | The five-minute exponentially decayed value of the 90th percentile wall-clock input data rate.    |
| trino.query.waiting_for_resources.count        | UpDownCounter | {query} |                          | The number of queries currently waiting for resources.                                            |
| trino.query.waiting_for_resources.duration.max | Gauge         | s       |                          | The longest time a query has been waiting for resources.                                          |
| trino.task.input.data.size                     | Gauge         | By      |                          | The five-minute exponentially decayed value of the input data size processed by tasks.            |
| trino.task.input.row.count                     | Gauge         | {row}   |                          | The five-minute exponentially decayed value of the number of input rows processed by tasks.       |
