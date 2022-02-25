# AWS Lambda Instrumentation

This package contains libraries to help instrument AWS lambda functions in your code.

## Using wrappers
To use the instrumentation, configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER` env property to your lambda handler method in following format `package.ClassName::methodName`
and use one of wrappers as your lambda `Handler`.

In order to configure a span flush timeout (default is set to 1 second), please configure `OTEL_INSTRUMENTATION_AWS_LAMBDA_FLUSH_TIMEOUT` env property. The value is in seconds.

Available wrappers:
- `io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestStreamWrapper` - for wrapping streaming handlers (implementing `RequestStreamHandler`), enabling HTTP context propagation for HTTP requests

When using known Lambda event types as parameters, use [aws-lambda-events-2.2](../../aws-lambda-events-2.2/library).

## Using handlers
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


## Trace propagation

Context propagation for this instrumentation can be done either with X-Ray propagation or regular HTTP propagation. If X-Ray is enabled for instrumented lambda, it will be preferred. If X-Ray is disabled, HTTP propagation will be tried (that is HTTP headers will be read to check for a valid trace context).


### X-Ray propagation
This instrumentation supports propagating traces using the `X-Amzn-Trace-Id` format for both normal
requests and SQS requests. X-Ray propagation is always enabled, there is no need to configure it explicitely.

### HTTP headers based propagation
For API Gateway (HTTP) requests instrumented by using one of following methods:
- extending `TracingRequestStreamHandler` or `TracingRequestHandler`
- wrapping with `TracingRequestStreamWrapper` or `TracingRequestApiGatewayWrapper`
traces can be propagated with supported HTTP headers (see https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/trace-propagators).

In order to enable requested propagation for a handler, configure it on the SDK you build.

```java
  static {
    OpenTelemetrySdk.builder()
      ...
      .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
      .buildAndRegisterGlobal();
  }
```

If using the wrappers, set the `OTEL_PROPAGATORS` environment variable as descibed [here](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#propagator).
