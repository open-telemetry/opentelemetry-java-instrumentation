# Messaging receive telemetry

`otel.instrumentation.messaging.experimental.receive-telemetry.enabled`, or
`java.common.messaging.receive_telemetry/development.enabled` in declarative config. It defaults to
`false` in every semantic convention mode.

## When a receive span exists

Every delivery gets at least one consumer-side span. A "process" span represents it wherever one
exists, and a "receive" span is created only where nothing else would. So this setting adds a
receive span exactly where a process span already covers the delivery, and changes nothing about
which spans exist elsewhere.

| Instrumentation | Receive span when `false` | When `true` |
| --- | --- | --- |
| Kafka, RabbitMQ, AWS SQS, RocketMQ 5.0 | none, a process span covers each delivery | adds a receive span |
| Pulsar with a `MessageListener` | none, the listener produces a process span | adds a receive span |
| Pulsar `consumer.receive()`, JMS `MessageConsumer.receive()` | yes, nothing else represents the pull | unchanged, apart from parenting and links |
| RocketMQ 4.8 | legacy only, and unconditional | ignored |

RabbitMQ sits in the first row for pushed deliveries, but `basicGet()` is a pull with no process
span behind it, so turning this off leaves that path with no consumer-side span at all. That is
inconsistent with row three and worth fixing.

## Empty and failed receives

A receive span represents passing messages to the application, so a receive that returns nothing
creates no span and records no metrics, in any mode. Failed receive behavior is
instrumentation-specific. For example, Kafka and JMS do not create receive telemetry when a receive
call throws.

RabbitMQ deviates here too: an empty `basicGet()` produces a receive span when this setting is on,
because the call site has no guard rather than because an empty pull is worth recording.

## Legacy versus stable

Under stable conventions the producer is always a span link. Whether it is also the parent depends
on the ambient context and the instrumentation, not this setting.

Under legacy the setting decides. With it off, the producer parents the consumer span. With it on,
the producer is linked instead, from the receive span for RabbitMQ, Pulsar and JMS, and from the
process span for Kafka, AWS SQS and RocketMQ 5.0.

`messaging.client.consumed.messages` is recorded only under stable conventions, by the receive
operation where one exists and by the process operation otherwise. Today only Pulsar and Spring
Pulsar record it.

## Known limitation

Kafka's advice matches `KafkaConsumer.poll` and cannot see its caller, so enabling this setting also
instruments the idle polls of a framework's own loop, such as Spring for Apache Kafka or Kafka
Streams. Pulsar can tell an application pull from a listener dispatch; Kafka cannot.

## Library instrumentation

Library instrumentation does not read the configuration property. Where a builder exposes an
equivalent setter, such as `KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(boolean)`, it
defaults to `false` and must be set explicitly.
