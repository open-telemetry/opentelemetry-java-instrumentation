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

Also, depending on the exporter that you want to use for exporting tracing information, you have to add the corresponding dependency as well.
For example, in order to use the Jaeger exporter, the dependency is the following one.

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-jaeger</artifactId>
    <version>OPENTELEMETRY_RELEASE</version>
</dependency>
```

### Setting up the OpenTelemetry instance

In order to enable tracing on the Kafka clients, it is needed to create and register an `OpenTelemetry` instance globally.
This can be done in two different ways:

* using the SDK extension for environment-based autoconfiguration;
* using SDK builders for programmatic configuration;

#### SDK extension: autoconfiguration

It is possible to configure a global `OpenTelemetry` instance by using environment variables thanks to the SDK extension for autoconfiguration, enabled with the following dependency.

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
    <version>OPENTELEMETRY_RELEASE</version>
</dependency>
```

The main environment variables to be set are the following:

* `OTEL_SERVICE_NAME`: specify the logical service name;
* `OTEL_TRACES_EXPORTER`: the list of exporters to be used for tracing. For example, by using `jaeger` you also need to have the corresponding dependency in the application;
* `OTEL_METRICS_EXPORTER`: the list of exporters to be used for metrics. If you don't use metrics then it has to be set to `none`;

Instead of using the above environment variables, it is also possible to use corresponding system properties to be set programmatically or on the command line.
They are `otel.service.name`, `otel.traces.exporter` and `otel.metrics.exporter`.

#### SDK builders: programmatic configuration

In order to build your own `OpenTelemetry` instance and not relying on autoconfiguration, it is possible to do so by using the SDK builders programmatically.
The OpenTelemetry SDK dependency is needed in order to have such SDK builders classes available.

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>OPENTELEMETRY_RELEASE</version>
</dependency>
```

In order to get an `OpenTelemetry` instance and registers it globally so that it can be used by the Kafka clients, follow the official documentation [here](https://opentelemetry.io/docs/instrumentation/java/manual/#set-up) for setting up the SDK.

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

Another way it by adding tracing to your application with no changes or additions into your application code.
You also don't need to add any dependencies to OpenTelemetry specific libraries.
It is possible by using the OpenTelemetry agent you can download from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases).
This agent has to run alongside your application in order to inject the logic for tracing messages sent and received to/from a Kafka cluster.

Run the producer application in the following way.

```shell
java -javaagent:path/to/opentelemetry-javaagent.jar \
      -Dotel.service.name=my-kafka-service \
      -Dotel.traces.exporter=jaeger \
      -Dotel.metrics.exporter=none \
      -jar target/kafka-producer-agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Run the consumer application similarly.

```shell
java -javaagent:path/to/opentelemetry-javaagent.jar \
      -Dotel.service.name=my-kafka-service \
      -Dotel.traces.exporter=jaeger \
      -Dotel.metrics.exporter=none \
      -jar target/kafka-consumer-agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```

As usual, the main three system properties are set to specify the logical service name, the exporter to be used (i.e. jaeger) and disable the metrics exporter.