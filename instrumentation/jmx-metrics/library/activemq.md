# ActiveMQ Metrics

Here is the list of metrics based on MBeans exposed by ActiveMQ.

| Metric Name                                  | Type          | Attributes          | Description                                                           |
| -------------------------------------------- | ------------- | ------------------- | --------------------------------------------------------------------- |
| activemq.ProducerCount                       | UpDownCounter | destination, broker | The number of producers attached to this destination                  |
| activemq.ConsumerCount                       | UpDownCounter | destination, broker | The number of consumers subscribed to this destination                |
| activemq.memory.MemoryPercentUsage           | Gauge         | destination, broker | The percentage of configured memory used                              |
| activemq.message.QueueSize                   | UpDownCounter | destination, broker | The current number of messages waiting to be consumed                 |
| activemq.message.ExpiredCount                | Counter       | destination, broker | The number of messages not delivered because they expired             |
| activemq.message.EnqueueCount                | Counter       | destination, broker | The number of messages sent to this destination                       |
| activemq.message.DequeueCount                | Counter       | destination, broker | The number of messages acknowledged and removed from this destination |
| activemq.message.AverageEnqueueTime          | Gauge         | destination, broker | The average time a message was held on this destination               |
| activemq.connections.CurrentConnectionsCount | UpDownCounter |                     | The total number of current connections                               |
| activemq.disc.StorePercentUsage              | Gauge         |                     | The percentage of configured disk used for persistent messages        |
| activemq.disc.TempPercentUsage               | Gauge         |                     | The percentage of configured disk used for non-persistent messages    |
