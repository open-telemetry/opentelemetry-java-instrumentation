# Trino Metrics

Here is the list of metrics based on MBeans exposed by Trino.

| Metric Name                                    | Type          | Unit    | Attributes               | Description                                                                |
| ---------------------------------------------- | ------------- | ------- | ------------------------ | -------------------------------------------------------------------------- |
| trino.node.active.count                        | UpDownCounter | {node}  |                          | The number of active Trino nodes.                                          |
| trino.memory.pool.free                         | UpDownCounter | By      | trino.memory.pool.name   | The amount of distributed memory currently free in the memory pool.        |
| trino.memory.query.killed.count                | Counter       | {query} |                          | The number of queries killed due to running out of memory.                 |
| trino.query.running.count                      | UpDownCounter | {query} |                          | The number of queries currently running or queued.                         |
| trino.query.started.count                      | Gauge         | {query} |                          | The number of queries started in the last five minutes.                    |
| trino.query.failed.count                       | Gauge         | {query} |                          | The number of failed queries in the last five minutes.                     |
| trino.query.failure.count                      | Gauge         | {query} | trino.query.failure.type | The number of failed queries in the last five minutes by failure type.     |
| trino.query.execution.duration.p50             | Gauge         | s       |                          | The 50th percentile query execution duration over the last five minutes.   |
| trino.query.input.rate.p90                     | Gauge         | By/s    |                          | The 90th percentile wall-clock input data rate over the last five minutes. |
| trino.query.waiting_for_resources.count        | UpDownCounter | {query} |                          | The number of queries currently waiting for resources.                     |
| trino.query.waiting_for_resources.duration.max | Gauge         | s       |                          | The longest time a query has been waiting for resources.                   |
| trino.task.input.data.size                     | Gauge         | By      |                          | The input data size processed by tasks in the last five minutes.           |
| trino.task.input.row.count                     | Gauge         | {row}   |                          | The number of input rows processed by tasks in the last five minutes.      |
