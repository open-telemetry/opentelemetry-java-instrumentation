# Library Instrumentation for Apache RocketMQ Remoting-based Client 4.0.0+

Provides OpenTelemetry instrumentation for [Apache RocketMQ](https://rocketmq.apache.org/) remoting-based client.

## Quickstart

### Add the following dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-rocketmq-client-4.8).

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
  producer.registerSendMessageHook(rocketMqTelemetry.newTracingSendMessageHook());
  // For push consumer.
  pushConsumer.registerConsumeMessageHook(rocketMqTelemetry.newTracingConsumeMessageHook());
}
```
