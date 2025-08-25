# Hadoop Metrics

Here is the list of metrics based on MBeans exposed by Hadoop.

| Metric Name                     | Type          | Attributes                          | Description                                            |
|---------------------------------|---------------|-------------------------------------|--------------------------------------------------------|
| hadoop.dfs.capacity             | UpDownCounter | hadoop.node.name                    | Current raw capacity of data nodes.                    |
| hadoop.dfs.capacity.used        | UpDownCounter | hadoop.node.name                    | Current used capacity across all data nodes.           |
| hadoop.dfs.block.count          | UpDownCounter | hadoop.node.name                    | Current number of allocated blocks in the system.      |
| hadoop.dfs.block.missing        | UpDownCounter | hadoop.node.name                    | Current number of missing blocks.                      |
| hadoop.dfs.block.corrupt        | UpDownCounter | hadoop.node.name                    | Current number of blocks with corrupt replicas.        |
| hadoop.dfs.volume.failure.count | Counter       | hadoop.node.name                    | Total number of volume failures across all data nodes. |
| hadoop.dfs.file.count           | UpDownCounter | hadoop.node.name                    | Current number of files and directories.               |
| hadoop.dfs.connection.count     | UpDownCounter | hadoop.node.name                    | Current number of connection.                          |
| hadoop.dfs.data_node.count      | UpDownCounter | hadoop.node.name, hadoop.node.state | The number of data nodes.                              |
