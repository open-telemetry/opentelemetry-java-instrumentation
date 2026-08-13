# Messaging receive telemetry

`otel.instrumentation.messaging.experimental.receive-telemetry.enabled`, or
`java.common.messaging.receive_telemetry/development.enabled` in declarative config. It defaults to
`false` in every semantic convention mode.

## When a receive span exists

Every delivery gets at least one consumer-side span. A "process" span represents it wherever one
exists, and a "receive" span is created only where nothing else would. So this setting adds a
receive span where a process span already covers the delivery, and changes only parenting and links
where it does not.

Consumers reach the broker in two ways, and the distinction matters because most instrumentation
sits on the call that fetches messages, not on the application's intent:

| Instrumentation | Application calls the pull API | A listener is fed by internal polling |
| --- | --- | --- |
| Kafka | `poll()`, process span per record, receive span added when `true` | the same `poll()`, so `true` also instruments idle polls |
| AWS SQS | `receiveMessage()`, process span per message, receive span added when `true` | the same call from `@SqsListener`, indistinguishable |
| Pulsar | `consumer.receive()`, receive span either way, no process span | detected at runtime, process span, receive span added when `true` |
| JMS | `MessageConsumer.receive()`, receive span either way, no process span | `MessageListener`, process span, no poll instrumented |
| RabbitMQ | `basicGet()`, **no span at all when `false`** | pushed delivery, process span, no poll instrumented |
| RocketMQ 5.0 | not instrumented | internal `ConsumerImpl.receiveMessage`, process span from the listener, receive span added when `true` |
| RocketMQ 4.8 | not instrumented | legacy batch receive span, unconditional, stable uses a batch process span instead |

Two things follow. Kafka and AWS SQS instrument one method that serves both columns and cannot see
their caller, so enabling this setting also instruments a framework's idle polls; Pulsar can tell the
two apart at runtime. And RabbitMQ `basicGet()` is a pull with no process span behind it, so turning
this off leaves it with no consumer-side span at all, which is inconsistent with Pulsar and JMS and
worth fixing.

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

## Library instrumentation

Library instrumentation does not read the configuration property. Where a builder exposes an
equivalent setter, such as `KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(boolean)`, it
defaults to `false` and must be set explicitly.
