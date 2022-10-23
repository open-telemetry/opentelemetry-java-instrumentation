# Instrumenting Kafka clients with OpenTelemetry

In order to get tracing information out of your application using Kafka clients, there are two ways to do so:

* instrumenting your application by enabling the tracing on the Kafka clients;
* using an external agent running alongside your application to add tracing;

For both of them, pick the latest `OPENTELEMETRY_RELEASE` from [here](https://mvnrepository.com/artifact/io.opentelemetry.instrumentation/opentelemetry-instrumentation-api).

## Instrumenting the Kafka clients based application

Instrumenting the application means enabling the tracing in the Kafka clients.
First, you need to add the dependency to the instrumented Kafka clients.

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-kafka-clients-2.6</artifactId>
    <version>OPENTELEMETRY_RELEASE</version>
</dependency>
```

It is also need that you set up an `OpenTelemetry` instance in your application.
To do so, programmatically or by using the SDK auto-configuration extension, please follow the official documentation [here](https://opentelemetry.io/docs/instrumentation/java/manual/).

### Using interceptors

The Kafka clients API provides a way to "intercept" messages before they are sent to the brokers as well as messages received from the broker before being passed to the application.
The OpenTelemetry instrumented Kafka library provides two interceptors to be configured to add tracing information automatically.
The interceptor class has to be set in the properties bag used to create the Kafka client.

Use the `TracingProducerInterceptor` for the producer in order to create a "send" span automatically, each time a message is sent.

```java
props.setProperty(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
```

Use the `TracingConsumerInterceptor` for the consumer in order to create a "receive" span automatically, each time a message is received.

```java
props.setProperty(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingConsumerInterceptor.class.getName());
```

### Wrapping clients

The other way is by wrapping the Kafka client with a tracing enabled Kafka client.

Assuming you have a `Producer<K, V> producer` instance, you can wrap it in the following way.

```java
KafkaTelemetry telemetry = KafkaTelemetry.create(GlobalOpenTelemetry.get());
Producer<String, String> tracingProducer = telemetry.wrap(producer);
```

Then use the `tracingProducer` as usual for sending messages to the Kafka cluster.

Assuming you have a `Consumer<K, V> consumer` instance, you can wrap it in the following way.

```java
KafkaTelemetry telemetry = KafkaTelemetry.create(GlobalOpenTelemetry.get());
Consumer<String, String> tracingConsumer = telemetry.wrap(this.consumer);
```

Then use the `tracingConsumer` as usual for receiving messages from the Kafka cluster.

## Using agent

Another way is by adding tracing to your application with no changes or additions into your application code.
You also don't need to add any dependencies to the Kafka clients instrumentation library.
This is about running an agent alongside your application in order to inject the logic for tracing messages sent and received to/from a Kafka cluster.

For more details, please follow the official documentation [here](https://opentelemetry.io/docs/instrumentation/java/automatic/).