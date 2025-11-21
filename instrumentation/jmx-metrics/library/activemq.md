# ActiveMQ Metrics

Here is the list of metrics based on MBeans exposed by ActiveMQ.

For now, only ActiveMQ classic is supported.

| Metric Name                               | Type          | Unit         | Attributes                                                                  | Description                                                           |
|-------------------------------------------|---------------|--------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------|
| activemq.producer.count                   | UpDownCounter | {producer}   | messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of producers attached to this destination                  |
| activemq.consumer.count                   | UpDownCounter | {consumer}   | messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of consumers subscribed to this destination                |
| activemq.destination.memory.usage         | UpDownCounter | By           | messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of used memory by this destination                         |
| activemq.destination.memory.limit         | UpDownCounter | By           | messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of configured memory limit for this destination            |
| activemq.destination.temp.utilization     | Gauge         | 1            | messaging.destination.name, activemq.broker.name, activemq.destination.type | The fraction of non-persistent storage used by this destination       |
| activemq.destination.temp.limit           | UpDownCounter | By           | messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of configured non-persistent storage limit                 |
| activemq.message.queue.size               | UpDownCounter | {message}    | messaging.destination.name, activemq.broker.name, activemq.destination.type | The current number of messages waiting to be consumed                 |
| activemq.message.expired                  | Counter       | {message}    | messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages not delivered because they expired             |
| activemq.message.enqueued                 | Counter       | {message}    | messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages sent to this destination                       |
| activemq.message.dequeued                 | Counter       | {message}    | messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages acknowledged and removed from this destination |
| activemq.message.enqueue.average_duration | Gauge         | s            | messaging.destination.name, activemq.broker.name, activemq.destination.type | The average time a message was held on this destination               |
| activemq.connection.count                 | UpDownCounter | {connection} | activemq.broker.name                                                        | The number of active connections                                      |
| activemq.memory.utilization               | Gauge         | 1            | activemq.broker.name                                                        | The fraction of broker memory used                                    |
| activemq.memory.limit                     | UpDownCounter | By           | activemq.broker.name                                                        | The amount of configured broker memory limit                          |
| activemq.store.utilization                | Gauge         | 1            | activemq.broker.name                                                        | The fraction of broker persistent storage used                        |
| activemq.store.limit                      | UpDownCounter | By           | activemq.broker.name                                                        | The amount of configured broker persistent storage limit              |
| activemq.temp.utilization                 | Gauge         | 1            | activemq.broker.name                                                        | The fraction of broker non-persistent storage used                    |
| activemq.temp.limit                       | UpDownCounter | By           | activemq.broker.name                                                        | The amount of configured broker non-persistent storage limit          |

## Attributes

- `messaging.destination.name` contains the destination.name ([semconv](https://opentelemetry.io/docs/specs/semconv/registry/attributes/messaging/#messaging-destination-name))
- `activemq.broker.name` contains the name of the broker
- `activemq.destination.type` is set to `queue` for queues (point-to-point), `topic` for topics (multicast).
