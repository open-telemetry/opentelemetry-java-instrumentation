# Library Instrumentation for AWS Lambda Events version 3.11 and higher

Provides OpenTelemetry instrumentation for [AWS Lambda Java handlers](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html)
that use AWS Lambda event types.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-lambda-events-3.11).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-lambda-events-3.11</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-lambda-events-3.11:OPENTELEMETRY_VERSION")
```

### Usage

#### Using wrappers

The wrappers initialize the OpenTelemetry SDK using autoconfiguration.
In addition to the instrumentation dependency above, the deployed function needs
`io.opentelemetry:opentelemetry-sdk-extension-autoconfigure` and dependencies for the configured
exporters at runtime.
See [SDK autoconfiguration](https://opentelemetry.io/docs/languages/java/configuration/#zero-code-sdk-autoconfigure)
for dependency setup and exporter configuration.

To use the instrumentation, configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER` env property to your lambda handler method in following format `package.ClassName::methodName`
and use one of wrappers as your lambda `Handler`.

In order to configure a span flush timeout (default is set to 10 seconds), please configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT` env property. The value is in milliseconds.

Available wrappers:

- `io.opentelemetry.instrumentation.awslambdaevents.v3_11.TracingRequestWrapper` - for wrapping regular handlers (implementing `RequestHandler`)
- `io.opentelemetry.instrumentation.awslambdaevents.v3_11.TracingRequestApiGatewayWrapper` - for wrapping regular handlers (implementing `RequestHandler`) proxied through API Gateway, enabling HTTP context propagation
- `io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestStreamWrapper` - for wrapping streaming handlers (implementing `RequestStreamHandler`), enabling HTTP context propagation for HTTP requests

If you are only using `TracingRequestStreamWrapper`, consider using [aws-lambda-core-1.0](../../aws-lambda-core-1.0/library) instead to reduce the size of
your compiled function.

#### Using handlers

To use the instrumentation, replace your function classes that implement `RequestHandler` (or `RequestStreamHandler`) with those
that extend `TracingRequestHandler` (or `TracingRequestStreamHandler`). You will need to change the method name to `doHandleRequest`
and pass an initialized `OpenTelemetrySdk` to the base class.

```java
public class MyRequestHandler extends TracingRequestHandler<String, String> {

  private static final OpenTelemetrySdk SDK = OpenTelemetrySdk.builder()
      .addSpanProcessor(spanProcessor)
      .buildAndRegisterGlobal();

  public MyRequestHandler() {
    super(SDK);
  }

  // Note the method is named doHandleRequest instead of handleRequest.
  @Override
  protected String doHandleRequest(String input, Context context) {
    if (input.equals("hello")) {
      return "world";
    }
    return "goodbye";
  }
}
```

A `SERVER` span will be created with the name you specify for the function when deploying it.

#### SQS Handler

This package provides a special handler for SQS-triggered functions to include messaging data.
If using SQS, it is recommended to use them instead of `TracingRequestHandler`.

If your application processes one message at a time, each independently, it is recommended to extend
`TracingSQSMessageHandler`. This will create a single span corresponding to a received batch of
messages along with one span for each of the messages as you process them.

```java
public class MyMessageHandler extends TracingSQSMessageHandler {
  @Override
  protected boolean handleMessage(SQSMessage message, Context context) {
    System.out.println(message.getBody());
    return true;
  }
}
```

If you handle a batch of messages together, for example by aggregating them into a single unit,
extend `TracingSQSEventHandler` to process a batch at a time.

```java
public class MyBatchHandler extends TracingSQSEventHandler {
  @Override
  protected SQSBatchResponse handleEvent(SQSEvent event, Context context) {
    System.out.println(event.getRecords().size());
    return null;
  }
}
```

## Trace propagation

### X-Ray propagation

The Events library brings in the X-Ray propagator at runtime, but the SDK does not enable it by
default. For non-SQS wrappers, set `OTEL_PROPAGATORS=tracecontext,baggage,xray-lambda`.
If you build the SDK yourself, follow the [Core X-Ray setup](../../aws-lambda-core-1.0/library/README.md#x-ray-propagation)
to add a compile dependency and register the propagator.

SQS handlers extract X-Ray context from messages without any SDK propagator configuration.

### HTTP headers based propagation

For API Gateway (HTTP) requests instrumented by using one of following methods:

- extending `TracingRequestStreamHandler` or `TracingRequestHandler`
- wrapping with `TracingRequestStreamWrapper` or `TracingRequestApiGatewayWrapper`
  traces can be propagated with supported HTTP headers (see <https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/trace-propagators>).

In order to enable requested propagation for a handler, configure it on the SDK you build.

```java
  static {
    OpenTelemetrySdk.builder()
      ...
      .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
      .buildAndRegisterGlobal();
  }
```

If using the wrappers, set the `OTEL_PROPAGATORS` environment variable as described [here](https://opentelemetry.io/docs/languages/sdk-configuration/general/#otel_propagators).
