# ActiveMQ Metrics

Here is the list of metrics based on MBeans exposed by ActiveMQ.

For now, only ActiveMQ classic is supported.

| Metric Name                               | Type          | Unit         | Attributes                                                                                    | Description                                                           |
|-------------------------------------------|---------------|--------------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| activemq.producer.count                   | UpDownCounter | {producer}   | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of producers attached to this destination                  |
| activemq.consumer.count                   | UpDownCounter | {consumer}   | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of consumers subscribed to this destination                |
| activemq.memory.destination.usage         | UpDownCounter | By           | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of used memory by this destination                         |
| activemq.memory.destination.limit         | UpDownCounter | By           | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of configured memory limit for this destination            |
| activemq.temp.destination.utilization     | gauge         | 1            | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The percentage of non-persistent storage used by this destination     |
| activemq.temp.destination.limit           | UpDownCounter | By           | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The amount of configured non-persistent storage limit                 |
| activemq.message.queue.size               | UpDownCounter | {message}    | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The current number of messages waiting to be consumed                 |
| activemq.message.expired                  | Counter       | {message}    | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages not delivered because they expired             |
| activemq.message.enqueued                 | Counter       | {message}    | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages sent to this destination                       |
| activemq.message.dequeued                 | Counter       | {message}    | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The number of messages acknowledged and removed from this destination |
| activemq.message.enqueue.average_duration | Gauge         | s            | messaging.system, messaging.destination.name, activemq.broker.name, activemq.destination.type | The average time a message was held on this destination               |
| activemq.connection.count                 | UpDownCounter | {connection} | messaging.system, activemq.broker.name                                                        | The number of active connections                                      |
| activemq.memory.utilization               | Gauge         | 1            | messaging.system, activemq.broker.name                                                        | The percentage of broker memory used                                  |
| activemq.memory.limit                     | UpDownCounter | By           | messaging.system, activemq.broker.name                                                        | The amount of configured broker memory limit                          |
| activemq.store.utilization                | Gauge         | 1            | messaging.system, activemq.broker.name                                                        | The percentage of broker persistent storage used                      |
| activemq.store.limit                      | UpDownCounter | By           | messaging.system, activemq.broker.name                                                        | The amount of configured broker persistent storage limit              |
| activemq.temp.utilization                 | Gauge         | 1            | messaging.system, activemq.broker.name                                                        | The percentage of broker non-persistent storage used                  |
| activemq.temp.limit                       | UpDownCounter | By           | messaging.system, activemq.broker.name                                                        | The amount of configured broker non-persistent storage limit          |

## Attributes

- `messaging.system` is always set to `activemq` ([semconv](https://opentelemetry.io/docs/specs/semconv/registry/attributes/messaging/#messaging-system))
- `messaging.destination.name` contains the destination.name ([semconv](https://opentelemetry.io/docs/specs/semconv/registry/attributes/messaging/#messaging-destination-name))
- `activemq.broker.name` contains the name of the broker
- `activemq.destination.type` is set to `queue` for queues (point-to-point), `topic` for topics (multicast).
