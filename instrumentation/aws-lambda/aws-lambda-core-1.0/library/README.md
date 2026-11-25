# Library Instrumentation for AWS Lambda Core version 1.0 and higher

Provides OpenTelemetry instrumentation for [AWS Lambda Java handlers](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-lambda-core-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-lambda-core-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-lambda-core-1.0:OPENTELEMETRY_VERSION")
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

- `io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestStreamWrapper` - for wrapping streaming handlers (implementing `RequestStreamHandler`), enabling HTTP context propagation for HTTP requests

When using known Lambda event types as parameters, use [aws-lambda-events-3.11](../../aws-lambda-events-3.11/library).

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

## Trace propagation

### X-Ray propagation

To continue traces provided by Lambda, add a dependency on
`io.opentelemetry.contrib:opentelemetry-aws-xray-propagator`.

Replace `OPENTELEMETRY_CONTRIB_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.contrib/opentelemetry-aws-xray-propagator).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.contrib</groupId>
    <artifactId>opentelemetry-aws-xray-propagator</artifactId>
    <version>OPENTELEMETRY_CONTRIB_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator:OPENTELEMETRY_CONTRIB_VERSION")
```

For wrappers, set `OTEL_PROPAGATORS=tracecontext,baggage,xray-lambda` to enable X-Ray alongside
the default propagators.

If you build the SDK yourself, add `AwsXrayLambdaPropagator.getInstance()` after your other
propagators.

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
