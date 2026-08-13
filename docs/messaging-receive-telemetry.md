# Messaging receive telemetry

`otel.instrumentation.messaging.experimental.receive-telemetry.enabled` (default: `false`)

Declarative config: `java.common.messaging.receive_telemetry/development.enabled`

When `true`, messaging instrumentations produce a separate "receive" span for the operation that takes messages off the broker. Under legacy messaging semantic conventions, process spans become children of that receive span rather than of the message creation context.

## What the setting does not control

Most instrumentations suppress receive spans where a "process" span already represents the delivery. Pulsar and JMS keep a receive span for their pull APIs because those calls have no process span:

| Instrumentation | Receive span when `false` | Why |
| --- | --- | --- |
| Kafka | no | iterating the returned records creates a process span per record |
| RabbitMQ | no | pushed deliveries create process spans; `basicGet()` has no replacement span |
| AWS SQS | no | each received message creates a process span |
| Pulsar | yes, when no `MessageListener` is registered | `consumer.receive()` creates no process span |
| JMS | yes, for explicit `MessageConsumer.receive()` | an explicit receive creates no process span |
| RocketMQ 5.0 | no | each consumed message creates a process span |
| RocketMQ 4.8 | yes with legacy conventions; no with stable conventions | the setting is ignored; the legacy batch receive span is unconditional, while stable conventions use a batch process span |

RabbitMQ `basicGet()` is an exception: when this setting is `false`, it produces no consumer-side span.

## What changes with stable messaging semantic conventions

| | Legacy | Stable |
| --- | --- | --- |
| Default | `false` | `false` (unchanged) |
| Which instrumentations create receive spans while `false` | see table above | see table above |
| Producer context placement | when `false`, the parent of the consumer span where one extracts it; when `true`, a receive-span link for RabbitMQ, Pulsar, and JMS, and a process-span link for Kafka, AWS SQS, and RocketMQ 5.0; RocketMQ 4.8 ignores the setting and links from its legacy process spans | always a span link, never the receive parent |
| Process span parent | extracted from the message when `false`, the receive span when `true` | the ambient span if there is one, otherwise the message creation context |
| Message creation context linked from a process span | only when `true` | always, including when it is also the parent |
| `messaging.client.consumed.messages` | not recorded | recorded only by Pulsar and Spring Pulsar, on the receive operation or on the process operation where the receive span is suppressed |

Under stable semantic conventions the producer is never the parent of a receive span, and is the parent of a process span only when the message is processed outside the scope of any other span. Setting this to `true` therefore adds the receive span without changing how process spans are parented. Under legacy it changes the trace shape for instrumentations that honor the setting: when `false` the producer parents the consumer span directly, and when `true` the receive or process span links to the producer as shown above.

## Library instrumentation

Library instrumentation does not read the configuration property. Where a builder exposes an equivalent setter, such as `KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(boolean)`, it defaults to `false` and must be set explicitly to opt in.
