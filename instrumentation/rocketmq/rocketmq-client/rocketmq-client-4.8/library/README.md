# Manual Instrumentation for Apache RocketMQ Remoting-based Client version 4.0.0+

Provides OpenTelemetry instrumentation for [Apache RocketMQ](https://rocketmq.apache.org/) Remoting-based Client.

## Quickstart

### Add the following dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the latest stable
[release](https://mvnrepository.com/artifact/io.opentelemetry). Minimum version is 1.1.0.

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

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-rocketmq-client-4.8:OPENTELEMETRY_VERSION")
```

### Usage

Remoting-based Client of RocketMQ provides the native interceptor to register the message hook in the instrumentation library.

```java
RocketMqTelemetry rocketMqTelemetry;

void configure(OpenTelemetry openTelemetry, DefaultMQProducerImpl producer, DefaultMQPushConsumerImpl pushConsumer) {
  rocketMqTelemetry = RocketMqTelemetry.create(openTelemetry);
  // For producer.
  SendMessageHook sendMessageHook = rocketMqTelemetry.newTracingSendMessageHook();
  producer.registerSendMessageHook(sendMessageHook);
  // For push consumer.
  ConsumeMessageHook consumeMessageHook = rocketMqTelemetry.newTracingConsumeMessageHook();
  pushConsumer.registerConsumeMessageHook(consumeMessageHook);
}
```
