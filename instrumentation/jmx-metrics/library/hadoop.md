# Hadoop Metrics

Here is the list of metrics based on MBeans exposed by Hadoop.

| Metric Name                 | Type          | Attributes                              | Description                                            |
|-----------------------------|---------------|-----------------------------------------|--------------------------------------------------------|
| hadoop.capacity             | UpDownCounter | hadoop.node.name                        | Current raw capacity of data nodes.                    |
| hadoop.capacity.used        | UpDownCounter | hadoop.node.name                        | Current used capacity across all data nodes.           |
| hadoop.block.count          | UpDownCounter | hadoop.node.name                        | Current number of allocated blocks in the system.      |
| hadoop.block.missing        | UpDownCounter | hadoop.node.name                        | Current number of missing blocks.                      |
| hadoop.block.corrupt        | UpDownCounter | hadoop.node.name                        | Current number of blocks with corrupt replicas.        |
| hadoop.volume.failure.count | Counter       | hadoop.node.name                        | Total number of volume failures across all data nodes. |
| hadoop.file.count           | UpDownCounter | hadoop.node.name                        | Current number of files and directories.               |
| hadoop.connection.count     | UpDownCounter | hadoop.node.name                        | Current number of connection.                          |
| hadoop.datanode.count       | UpDownCounter | hadoop.node.name, hadoop.datanode.state | The number of data nodes.                              |
