# Hadoop Metrics

Here is the list of metrics based on MBeans exposed by Hadoop.

| Metric Name                       | Type          | Attributes       | Description                                           |
| --------------------------------- | ------------- | ---------------- | ----------------------------------------------------- |
| hadoop.capacity.CapacityUsed      | UpDownCounter | node_name        | Current used capacity across all data nodes           |
| hadoop.capacity.CapacityTotal     | UpDownCounter | node_name        | Current raw capacity of data nodes                    |
| hadoop.block.BlocksTotal          | UpDownCounter | node_name        | Current number of allocated blocks in the system      |
| hadoop.block.MissingBlocks        | UpDownCounter | node_name        | Current number of missing blocks                      |
| hadoop.block.CorruptBlocks        | UpDownCounter | node_name        | Current number of blocks with corrupt replicas        |
| hadoop.volume.VolumeFailuresTotal | UpDownCounter | node_name        | Total number of volume failures across all data nodes |
| hadoop.file.FilesTotal            | UpDownCounter | node_name        | Current number of files and directories               |
| hadoop.file.TotalLoad             | UpDownCounter | node_name        | Current number of connection                          |
| hadoop.datanode.Count             | UpDownCounter | node_name, state | The Number of data nodes                              |
