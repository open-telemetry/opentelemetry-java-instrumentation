# Hadoop Metrics

Here is the list of metrics based on MBeans exposed by Hadoop.

| Metric Name                     | Type          | Unit         | Attributes        | Description                                            |
|---------------------------------|---------------|--------------|-------------------|--------------------------------------------------------|
| hadoop.dfs.capacity             | UpDownCounter | By           | hadoop.node.name  | Current raw capacity of data nodes.                    |
| hadoop.dfs.capacity.used        | UpDownCounter | By           | hadoop.node.name  | Current used capacity across all data nodes.           |
| hadoop.dfs.block.count          | UpDownCounter | {block}      | hadoop.node.name  | Current number of allocated blocks in the system.      |
| hadoop.dfs.block.missing        | UpDownCounter | {block}      | hadoop.node.name  | Current number of missing blocks.                      |
| hadoop.dfs.block.corrupt        | UpDownCounter | {block}      | hadoop.node.name  | Current number of blocks with corrupt replicas.        |
| hadoop.dfs.volume.failure.count | Counter       | {failure}    | hadoop.node.name  | Total number of volume failures across all data nodes. |
| hadoop.dfs.file.count           | UpDownCounter | {file}       | hadoop.node.name  | Current number of files and directories.               |
| hadoop.dfs.connection.count     | UpDownCounter | {connection} | hadoop.node.name  | Current number of connection.                          |
| hadoop.dfs.data_node.live       | UpDownCounter | {node}       | hadoop.node.name  | Number of data nodes which are currently live.         |
| hadoop.dfs.data_node.dead       | UpDownCounter | {node}       | hadoop.node.name  | Number of data nodes which are currently dead.         |
