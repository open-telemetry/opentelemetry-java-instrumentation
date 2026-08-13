# Messaging receive telemetry

`otel.instrumentation.messaging.experimental.receive-telemetry.enabled` (default: `false`)

Declarative config: `java.common.messaging.receive_telemetry/development.enabled`

When `true`, messaging instrumentations produce a separate "receive" span for the operation that takes messages off the broker, and process spans become children of that receive span rather than of the message creation context.

## What the setting does not control

The setting suppresses receive spans only where a "process" span already represents the delivery. Where nothing else would represent it, the receive span is created regardless:

| Instrumentation | Receive span when `false` | Why |
| --- | --- | --- |
| Kafka | no | iterating the returned records creates a process span per record |
| RabbitMQ | no | each delivery creates a process span |
| AWS SQS | no | each received message creates a process span |
| Pulsar | yes, when no `MessageListener` is registered | `consumer.receive()` creates no process span |
| JMS | yes, for explicit `MessageConsumer.receive()` | an explicit receive creates no process span |

Without this, an application that pulls messages directly would have no consumer-side telemetry at all.

## What changes with stable messaging semantic conventions

| | Legacy | Stable |
| --- | --- | --- |
| Default | `false` | `false` (unchanged) |
| Which instrumentations create receive spans while `false` | see table above | unchanged |
| Producer context on a receive span | the span's parent when `false`, a span link when `true` | always a span link, never the parent |
| Process span parent | extracted from the message when `false`, the receive span when `true` | the ambient span if there is one, otherwise the message creation context |
| Message creation context linked from a process span | only when `true` | always, including when it is also the parent |
| `messaging.client.consumed.messages` | not recorded | recorded by the receive operation, or by the process operation where the receive span is suppressed |

Under stable semantic conventions the producer is never the parent of a receive span, and is the parent of a process span only when the message is processed outside the scope of any other span. Setting this to `true` therefore adds the receive span without changing how process spans are parented. Under legacy it changes the trace shape: when `false` the producer parents the consumer span directly, and when `true` the consumer spans are linked to the producer instead.

## Library instrumentation

Library instrumentation does not read the configuration property. Where a builder exposes an equivalent setter, such as `KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(boolean)`, it defaults to `false` and must be set explicitly to opt in.
