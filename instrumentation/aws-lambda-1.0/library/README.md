# AWS Lambda Instrumentation

This package contains libraries to help instrument AWS lambda functions in your code.

## Using wrappers
To use the instrumentation, configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER` env property to your lambda handler method in following format `package.ClassName::methodName`
and use one of wrappers as your lambda `Handler`.

In order to configure a span flush timeout (default is set to 1 second), please configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT` env property. The value is in seconds.

Available wrappers:
- `io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestWrapper` - for wrapping regular handlers (implementing `RequestHandler`)
- `io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestApiGatewayWrapper` - for wrapping regular handlers (implementing `RequestHandler`) proxied through API Gateway, enabling HTTP context propagation
- `io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestStreamWrapper` - for wrapping streaming handlers (implementing `RequestStreamHandler`), enabling HTTP context propagation for HTTP requests

## Using handlers
To use the instrumentation, replace your function classes that implement `RequestHandler` (or `RequestStreamHandler`) with those
that extend `TracingRequestHandler` (or `TracingRequestStreamHandler`). You will need to change the method name to `doHandleRequest`.

```java
public class MyRequestHandler extends TracingRequestHandler<String, String> {
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

In addition, it is recommended to set up X-Ray trace propagation to be able to
link to tracing information provided by Lambda itself. To do so, add a dependency on
`opentelemetry-extension-tracepropagators`. Make sure the version matches the version of the SDK
you use.

Gradle:
```kotlin
dependencies {
  implementation("io.opentelemetry:opentelemetry-extension-trace-propagators:0.8.0")
}
```

Maven:
```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-extension-trace-propagators</artifactId>
    <version>0.8.0</version>
  </dependency>
</dependencies>
```

## SQS Handler

This package provides a special handler for SQS-triggered functions to include messaging data.
If using SQS, it is recommended to use them instead of `TracingRequestHandler`.

If your application processes one message at a time, each independently, it is recommended to extend
`TracingSQSMessageHandler`. This will create a single span corresponding to a received batch of
messages along with one span for each of the messages as you process them.

```java
public class MyMessageHandler extends TracingSQSMessageHandler {
  @Override
  protected void handleMessage(SQSMessage message, Context context) {
    System.out.println(message.getBody());
  }
}
```

If you handle a batch of messages together, for example by aggregating them into a single unit,
extend `TracingSQSEventHandler` to process a batch at a time.

```java
public class MyBatchHandler extends TracingSQSEventHandler {
  @Override
  protected void handleEvent(SQSEvent event, Context context) {
    System.out.println(event.getRecords().size());
  }
}
```

## Trace propagation

### X-Ray propagation
This instrumentation supports propagating traces using the `X-Amzn-Trace-Id` format for both normal
requests and SQS requests. To enable this propagation, in your code as early as possible,
configure the `AwsXrayPropagator` along with any other propagators you use. If in doubt, you can
configure X-Ray along with the default W3C propagator like this in a static block of your handler.

```java
class MyRequestHandler extends TracingRequestHandler<String, String> {

  static {
    OpenTelemetry.setGlobalPropagators(
      DefaultContextPropagators.builder()
        .addTextMapPropagator(HttpTraceContext.getInstance())
        .addTextMapPropagator(AwsXrayPropagator.getInstance())
        .build());
  }

  @Override
  protected String doHandleRequest(String input, Context context) {
    // logic
  }
}
```

If you are using this instrumentation with SQS, you should always enable the `AwsXrayPropagator` to
allow linking between messages in a backend-agnostic way.

Otherwise, only enable the above if you are using AWS X-Ray as your tracing backend. You should not
enable the X-Ray propagator if you are not using X-Ray as it will cause the spans in Lambda to not
have the correct parent/child connection between client and server spans.

### HTTP headers based propagation
For API Gateway (HTTP) requests instrumented by using one of following methods:
- extending `TracingRequestStreamHandler` or `TracingRequestHandler`
- wrapping with `TracingRequestStreamWrapper` or `TracingRequestApiGatewayWrapper`
traces can be propagated with supported HTTP headers (see https://github.com/open-telemetry/opentelemetry-java/tree/master/extensions/trace_propagators).

In order to enable requested propagation, configure it in your code as early as possible. For example B3 propagation configuration would look like as follows:

```java
  static {
    OpenTelemetry.setGlobalPropagators(
      DefaultContextPropagators.builder()
        .addTextMapPropagator(B3Propagator.getInstance())
        .build());
  }
```
