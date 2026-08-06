# Library Instrumentation for Apache RocketMQ remoting-based client 4.0.0+

Provides OpenTelemetry instrumentation for [Apache RocketMQ](https://rocketmq.apache.org/) remoting-based client.

## Quickstart

### Add the following dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rocketmq-client-4.8).

For Maven, add the following to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rocketmq-client-4.8</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add the following to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-rocketmq-client-4.8:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides the implementation of `SendMessageHook` and `ConsumeMessageHook` to provide OpenTelemetry-based spans and context propagation.

```java
RocketMqTelemetry rocketMqTelemetry;

void configure(OpenTelemetry openTelemetry, DefaultMQProducerImpl producer, DefaultMQPushConsumerImpl pushConsumer) {
  rocketMqTelemetry = RocketMqTelemetry.create(openTelemetry);
  // For producer.
  producer.registerSendMessageHook(rocketMqTelemetry.createSendMessageHook());
  // For push consumer.
  pushConsumer.registerConsumeMessageHook(rocketMqTelemetry.createConsumeMessageHook());
}
```

## Reported errors

When RocketMQ reports a [`ConsumeReturnType`][consume-return-type] other than `SUCCESS` for a
consume operation, the process span is marked as errored and `error.type` is set to that consume
return type:

| `error.type` | Meaning                                                                                                        |
| ------------ | -------------------------------------------------------------------------------------------------------------- |
| `EXCEPTION`  | The message listener threw.                                                                                    |
| `RETURNNULL` | The message listener returned `null`.                                                                          |
| `TIME_OUT`   | The message listener exceeded the configured consume timeout, even if it eventually returned a success status. |
| `FAILED`     | The message listener returned `RECONSUME_LATER` / `SUSPEND_CURRENT_QUEUE_A_MOMENT`.                            |

If RocketMQ does not report a consume return type, the consume status is used instead, and the
process span is marked as errored when that status is not a success.

`error.type` is only reported when the messaging semantic conventions are enabled. The process span
is likewise only marked as errored when they are enabled.

[consume-return-type]: https://github.com/apache/rocketmq/blob/rocketmq-all-4.8.0/client/src/main/java/org/apache/rocketmq/client/consumer/listener/ConsumeReturnType.java
